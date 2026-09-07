package app.sourcescribe.data

import app.sourcescribe.extractor.physicalFreeBytes

import android.content.Context
import android.util.AtomicFile
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.sourcescribe.core.*
import app.sourcescribe.extractor.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
internal data class AttemptCheckpoint(
    val artifactId: String = UUID.randomUUID().toString(),
    val artifactCreatedAt: Long = System.currentTimeMillis(),
    val source: Source? = null,
    val caption: CaptionTrack? = null,
    val audio: AudioTrack? = null,
    val rawExtension: String? = null,
    val chunkIndex: Int = 0,
)

/** Room is the authority. WorkManager input carries an opaque attempt ID only. */
@Singleton
class JobCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SourceScribeDatabase,
    private val dao: SourceScribeDao,
    private val settings: SettingsStore,
    private val extractor: ExtractorEngine,
    private val engines: EngineUpdateManager,
    private val artifacts: ArtifactFiles,
    private val stt: SttStep,
    private val exports: ExportStore,
    private val notifications: JobNotifications,
    private val storage: StorageBudget,
    private val credentials: CredentialStore,
    private val providerHttp: ProviderHttp,
) {
    private val json = Json { encodeDefaults = true }
    private val startup = Mutex()
    private val lifecycle = SourceFiles.mutex
    private val executing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var reconciled = false
    private var recovered = false
    private val manager get() = WorkManager.getInstance(context)

    suspend fun inspect(source: Source): ResolvedSource = withContext(Dispatchers.IO) {
        val engine = engines.active()
        extractor.resolve(source, engines.file(engine))
    }

    suspend fun create(source: Source, config: JobConfig): String = createBatch(listOf(source to config)).single()

    suspend fun createBatch(sources: List<Pair<Source, JobConfig>>): List<String> = lifecycle.withLock {
        require(sources.size in 1..20)
        require(sources.map { it.first.id }.distinct().size == sources.size)
        sources.forEach { (source, config) ->
            require(source.kind == SourceKind.YOUTUBE || config.mode == AcquisitionMode.STT_ONLY)
            if (source.kind == SourceKind.YOUTUBE) {
                SourceResolver.requireMatchingVideo(
                    SourceResolver.youtube(requireNotNull(source.canonicalUrl)),
                    source.videoId,
                )
            }
        }
        val prepared = sources.map { (source, config) ->
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val engine = if (source.kind == SourceKind.YOUTUBE) engines.active() else null
            val branches = when (config.mode) {
                AcquisitionMode.STT_ONLY -> listOf(Branch.STT)
                AcquisitionMode.BOTH -> listOf(Branch.CAPTIONS, Branch.STT)
                else -> listOf(Branch.CAPTIONS)
            }
            val attempts = branches.map { branch ->
                AttemptRow(
                    UUID.randomUUID().toString(),
                    id,
                    branch,
                    1,
                    now,
                    engineId = engine?.id,
                    checkpoint = json.encodeToString(AttemptCheckpoint(source = source)),
                )
            }
            Triple(
                SourceRow(source.id, json.encodeToString(source), source.title ?: source.fileName ?: source.videoId ?: source.id),
                JobRow(id, source.id, json.encodeToString(config), now),
                attempts,
            ) to config
        }
        database.withTransaction {
            prepared.forEach { (rows, _) -> dao.createJob(rows.first, rows.second, rows.third) }
        }
        withContext(NonCancellable) {
            prepared.forEach { (rows, config) -> rows.third.forEach { enqueue(it, config) } }
        }
        prepared.map { it.first.second.id }
    }

    suspend fun recover() = startup.withLock {
        if (recovered) return@withLock
        if (!reconciled) lifecycle.withLock {
            // Startup runs before this singleton claims work. Every inherited lease is stale,
            // including leases on checkpoints already saved as QUEUED/WAITING/FINISHED.
            database.withTransaction {
                for (row in dao.allAttempts()) {
                    val cancelled = dao.job(row.jobId)?.cancelRequested == true
                    val uncertain = dao.submissions(row.id).any { it.state == SubmissionState.UNCERTAIN || cancelled && it.state == SubmissionState.SENDING }
                    val state = when {
                        cancelled && row.state != ExecutionState.FINISHED -> if (uncertain) ExecutionState.SUBMISSION_UNCERTAIN else ExecutionState.CANCELLED
                        row.state == ExecutionState.RUNNING -> if (uncertain) ExecutionState.SUBMISSION_UNCERTAIN else ExecutionState.QUEUED
                        else -> row.state
                    }
                    dao.updateAttempt(row.copy(state = state, leaseOwner = null, leaseUntil = 0,
                        error = if (uncertain) "SUBMISSION_UNCERTAIN" else row.error))
                }
                dao.clearResourceLeases()
            }
            recoverFinalizedArtifacts()
            for (job in dao.allJobs()) {
                try {
                    summarize(job.id)
                    if (job.deleteRequested) finishDeletion(job.id) else cleanupPersisted(job.id)
                } catch (failure: IOException) {
                    recordMaintenanceFailure(job.id, failure)
                } catch (failure: SecurityException) {
                    recordMaintenanceFailure(job.id, failure)
                } catch (failure: IllegalStateException) {
                    recordMaintenanceFailure(job.id, failure)
                }
            }
            cleanupAbandonedAttemptFiles()
            cleanupAbandonedImports()
            dao.allExports().forEach { exports.reconcile(it.id) }
            reconciled = true
        }
        for (row in dao.scheduled()) {
            val job = dao.job(row.jobId) ?: continue
            val config = configuration(job) ?: continue
            enqueue(row, config)
        }
        dao.completedWithArtifacts().forEach { enqueueExports(it) }
        recovered = true
    }

    suspend fun cancel(jobId: String) = lifecycle.withLock {
        if (dao.cancelJob(jobId)) {
            manager.cancelAllWorkByTag("job:$jobId")
            dao.job(jobId)?.let { notifications.update(it, dao.attempts(jobId)) }
            cleanupPersisted(jobId)
        }
    }

    /** Explicit user action: safe continuation never creates a new paid submission. */
    suspend fun resume(jobId: String) = lifecycle.withLock {
        val resumed = database.withTransaction {
            val job = requireNotNull(dao.job(jobId))
            check(!job.cancelRequested && !job.deleteRequested)
            val attempts = dao.attempts(jobId)
            if (attempts.any { it.id in executing }) throw JobActionException("JOB_STILL_RUNNING")
            attempts.filter { it.state == ExecutionState.WAITING_USER }.map { row ->
                val submissions = dao.submissions(row.id)
                if (submissions.any { it.state in setOf(SubmissionState.SENDING, SubmissionState.UNCERTAIN) }) {
                    throw JobActionException("SUBMISSION_UNCERTAIN")
                }
                submissions.filter { it.state == SubmissionState.REJECTED && it.rejectionCode in setOf("AUTHENTICATION", "ACCESS_DENIED") }.forEach {
                    dao.updateSubmission(it.copy(state = SubmissionState.PREPARED, rejectionCode = null))
                }
                row.copy(state = ExecutionState.QUEUED, nextAt = 0, retries = 0, error = null, leaseOwner = null, leaseUntil = 0).also { dao.updateAttempt(it) }
            }
        }
        val config = json.decodeFromString<JobConfig>(requireNotNull(dao.job(jobId)).config)
        resumed.forEach { enqueue(it, config, ExistingWorkPolicy.APPEND_OR_REPLACE) }
        summarize(jobId)
    }

    /** A confirmed new execution preserves every previous artifact and submission. */
    suspend fun retry(jobId: String, missingOnly: Boolean) = lifecycle.withLock {
        val job = requireNotNull(dao.job(jobId))
        if (job.deleteRequested) throw JobActionException("DELETE_PENDING")
        val previous = dao.attempts(jobId)
        if (previous.any { it.id in executing || it.state == ExecutionState.RUNNING }) throw JobActionException("JOB_STILL_RUNNING")
        val config = json.decodeFromString<JobConfig>(job.config)
        val source = json.decodeFromString<Source>(requireNotNull(dao.source(job.sourceId)).snapshot)
        val latest = previous.groupBy { it.branch }.mapValues { (_, rows) -> rows.maxBy { it.number } }
        val saved = dao.artifacts(jobId)
        val complete = saved.filter { it.complete == true && latest[it.branch]?.id == it.attemptId }.map { it.branch }.toSet()
        val branches = when (config.mode) {
            AcquisitionMode.STT_ONLY -> listOf(Branch.STT)
            AcquisitionMode.BOTH -> listOf(Branch.CAPTIONS, Branch.STT)
            AcquisitionMode.CAPTIONS_THEN_STT -> if (missingOnly && Branch.STT in latest) listOf(Branch.STT) else listOf(Branch.CAPTIONS)
            AcquisitionMode.CAPTIONS_ONLY -> listOf(Branch.CAPTIONS)
        }.filter { !missingOnly || it !in complete }
        if (branches.isEmpty()) throw JobActionException("NO_MISSING_BRANCH")
        val now = System.currentTimeMillis()
        val created = ArrayList<AttemptRow>()
        val reused = ArrayList<SubmissionRow>()
        try {
            for (branch in branches) {
                val prior = latest[branch]
                val partial = if (missingOnly && branch == Branch.STT) saved.singleOrNull { it.attemptId == prior?.id && it.complete != true } else null
                var row = AttemptRow(UUID.randomUUID().toString(), jobId, branch, (prior?.number ?: 0) + 1, now,
                    checkpoint = json.encodeToString(AttemptCheckpoint(source = source)))
                // Register before copying so even a preparation failure has a cleanup target.
                created += row
                if (partial != null) {
                    row = row.copy(engineId = requireNotNull(prior).engineId)
                    val prepared = stt.prepareMissingRetry(prior, row, config, artifacts.read(partial.id), storage)
                    row = prepared.attempt
                    reused += prepared.submissions
                } else {
                    if (source.kind == SourceKind.LOCAL_AUDIO) requireLocalAudio(requireNotNull(dao.source(source.id)))
                    row = row.copy(engineId = if (source.kind == SourceKind.YOUTUBE) engines.active().id else null)
                }
                created[created.lastIndex] = row
            }
            database.withTransaction {
                previous.filter { it.state !in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) }.forEach {
                    dao.updateAttempt(it.copy(state = ExecutionState.CANCELLED, outcome = Outcome.CANCELLED, leaseOwner = null, leaseUntil = 0))
                }
                dao.updateJob(job.copy(cancelRequested = false, state = ExecutionState.QUEUED, outcome = Outcome.NONE))
                created.forEach { dao.insertAttempt(it) }
                reused.forEach { dao.insertSubmission(it) }
            }
        } catch (failure: Exception) {
            discardUncommittedRetryFiles(created, failure)
            throw failure
        }
        withContext(NonCancellable) { created.forEach { enqueue(it, config) } }
    }

    internal suspend fun discardUncommittedRetryFiles(created: List<AttemptRow>, failure: Exception) = withContext(NonCancellable) {
        for (row in created) {
            try {
                // Cancellation can surface after Room committed: those files now belong to a durable attempt.
                if (dao.attempt(row.id) == null) storage.deleteAttemptFiles(row.id)
            } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
        }
    }

    suspend fun delete(jobId: String) = lifecycle.withLock {
        if (!dao.requestDeletion(jobId)) return@withLock
        manager.cancelAllWorkByTag("job:$jobId").result.get(30, TimeUnit.SECONDS)
        if (!finishDeletion(jobId)) throw JobActionException("DELETE_PENDING")
    }

    /** Remote deletion is an explicit separate action; it never claims cancellation or a refund. */
    suspend fun deleteRemote(jobId: String) = lifecycle.withLock {
        val job = requireNotNull(dao.job(jobId))
        if (job.state !in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) ||
            dao.attempts(jobId).any { it.id in executing }) throw JobActionException("JOB_STILL_RUNNING")
        val remote = dao.submissionsForJob(jobId).filter {
            it.provider == Provider.ASSEMBLYAI.name && it.remoteId != null && it.state != SubmissionState.REMOTE_DELETED
        }
        if (remote.isEmpty()) throw JobActionException("NO_REMOTE_HANDLE")
        val adapter = app.sourcescribe.core.providers.AssemblyAiAdapter(providerHttp)
        for (submission in remote) {
            val region = Region.valueOf(submission.region)
            val key = credentials.read(submission.credentialId, Provider.ASSEMBLYAI, region)
            withTimeout(60_000) { adapter.deleteRemote(RemoteHandle(Provider.ASSEMBLYAI, region, requireNotNull(submission.remoteId)), key) }
            dao.updateSubmission(submission.copy(state = SubmissionState.REMOTE_DELETED))
        }
    }

    private suspend fun finishDeletion(jobId: String): Boolean {
        val job = dao.job(jobId) ?: return true
        check(job.deleteRequested)
        val attempts = dao.attempts(jobId)
        if (attempts.any { it.id in executing }) return false
        recoverFinalizedArtifacts(jobId)
        val artifactIds = dao.artifacts(jobId).mapTo(LinkedHashSet()) { it.id }
        for ((id, owners) in artifactOwners(dao.allAttempts())) {
            if (owners.singleOrNull()?.jobId == jobId && dao.artifact(id)?.jobId.let { it == null || it == jobId }) {
                artifactIds += id
            }
        }
        // Finalization can precede the Room artifact row, including on a damaged job.
        artifactIds.forEach { artifacts.delete(it) }
        attempts.forEach { storage.deleteAttemptFiles(it.id) }
        val source = dao.source(job.sourceId)
        val keepSource = SourceFiles.hasPreview(job.sourceId)
        if (dao.sourceReferences(job.sourceId) == 1 && !keepSource) source?.importedPath?.let { path ->
            val entry = File(path)
            val imports = File(context.noBackupFilesDir, "imports")
            check(!java.nio.file.Files.isSymbolicLink(imports.toPath()) && !java.nio.file.Files.isSymbolicLink(entry.toPath()))
            check(entry.canonicalFile.parentFile == imports.canonicalFile)
            java.nio.file.Files.deleteIfExists(entry.toPath())
        }
        dao.finishDeletion(jobId, keepSource)
        notifications.dismiss(jobId)
        return true
    }

    suspend fun run(attemptId: String): Boolean = withContext(Dispatchers.IO) {
        recover()
        val owner = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        var row = lifecycle.withLock {
            val claimed = dao.claim(attemptId, owner, now, now + 9 * 60_000, settings.settings.first().parallelJobs)
            if (claimed != null) executing.add(attemptId)
            claimed
        }
            ?: run {
                val pending = dao.attempt(attemptId)
                val retry = pending != null && pending.state in runnable && dao.job(pending.jobId)?.cancelRequested == false
                if (retry) dao.deferUnclaimed(attemptId, now, now + retryDelayMillis(30_000))
                return@withContext retry
            }
        try {
            summarize(row.jobId)
            val job = requireNotNull(dao.job(row.jobId))
            val config = json.decodeFromString<JobConfig>(job.config)
            if (row.branch == Branch.CAPTIONS) {
                row = storage.withReservation(96L * 1024 * 1024) { withTimeout(7 * 60_000) { captions(row, owner, config) } }
            } else {
                val bytes = when (row.phase) {
                    Phase.DOWNLOAD_AUDIO -> availableGrowth(2L * 1024 * 1024 * 1024)
                    Phase.PREPARE_AUDIO -> 24_000_000L
                    Phase.NORMALIZE, Phase.PERSIST -> 96L * 1024 * 1024
                    else -> 16L * 1024 * 1024
                }
                row = storage.withReservation(bytes) { withTimeout(7 * 60_000) { stt.run(row, owner, config, bytes) } }
            }
            dao.saveClaimed(row, owner)
            if (row.state == ExecutionState.FINISHED) enqueueExports(row)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                row = dao.attempt(row.id)?.takeIf { it.leaseOwner == owner } ?: row
                if (row.state != ExecutionState.FINISHED) {
                    val uncertain = dao.submissions(row.id).any { it.state == SubmissionState.UNCERTAIN }
                    dao.saveClaimed(row.copy(state = if (uncertain) ExecutionState.SUBMISSION_UNCERTAIN else ExecutionState.QUEUED,
                        error = if (uncertain) "SUBMISSION_UNCERTAIN" else "INTERRUPTED"), owner)
                }
            }
            throw cancelled
        } catch (failure: Exception) {
            row = dao.attempt(row.id)?.takeIf { it.leaseOwner == owner } ?: row
            val extraction = failure as? ExtractionException
            val network = extraction != null && extraction.failure in setOf(ExtractionFailure.NETWORK, ExtractionFailure.RATE_LIMIT)
            val code = when (failure) {
                is ExtractionException -> failure.failure.name
                is CaptionParseException -> failure.reason
                is InvalidSource -> "SOURCE_VALIDATION"
                is EngineUpdateException -> "ENGINE_${failure.code.name}"
                is ArtifactFilesException -> failure.reason
                is StorageBudgetException -> failure.reason
                is JobActionException -> failure.code
                else -> "LOCAL_PROCESSING_FAILED"
            }
            val retry = network && row.retries < 3
            val config = dao.job(row.jobId)?.let { json.decodeFromString<JobConfig>(it.config) }
            val fetchFailure = network || failure is CaptionParseException || extraction?.failure == ExtractionFailure.INVALID_RESPONSE
            val fallback = !retry && fetchFailure && row.branch == Branch.CAPTIONS &&
                config?.mode == AcquisitionMode.CAPTIONS_THEN_STT && config.fallbackOnCaptionError
            row = row.copy(state = if (!retry) ExecutionState.WAITING_USER else if (extraction.failure == ExtractionFailure.RATE_LIMIT) ExecutionState.WAITING_RATE_LIMIT else ExecutionState.WAITING_NETWORK,
                error = code, retries = row.retries + 1,
                nextAt = if (retry) System.currentTimeMillis() + retryDelayMillis((extraction.retryAfterSeconds ?: (30L shl row.retries.coerceAtMost(5))).coerceIn(1, 86_400) * 1000) else 0)
            if (fallback) {
                addFallback(row, owner, requireNotNull(config))
                row = row.copy(state = ExecutionState.FINISHED, outcome = Outcome.FAILED)
            }
            dao.saveClaimed(row, owner)
        } finally {
            withContext(NonCancellable) {
                dao.release(attemptId, owner)
                dao.releaseResources(owner)
                summarize(row.jobId)
                lifecycle.withLock {
                    executing.remove(attemptId)
                    if (dao.job(row.jobId)?.deleteRequested == true) finishDeletion(row.jobId)
                    else cleanupPersisted(row.jobId)
                }
            }
        }
        dao.attempt(attemptId)?.state in runnable
    }

    private suspend fun captions(initial: AttemptRow, owner: String, config: JobConfig): AttemptRow {
        var row = initial
        var checkpoint = json.decodeFromString<AttemptCheckpoint>(row.checkpoint)
        val directory = attemptDirectory(row.id)
        val normalized = AtomicFile(File(directory, "normalized.json"))
        if (!normalized.baseFile.exists()) {
            val installation = engines.installations().single { it.id == row.engineId }
            val resolved = extractor.resolve(requireNotNull(checkpoint.source), engines.file(installation))
            val choices = TrackSelection.captions(resolved, config)
            if (choices.isEmpty()) {
                if (config.captionTrackId != null) return row.copy(state = ExecutionState.WAITING_USER, error = "CAPTION_TRACK_CHANGED")
                if (config.mode == AcquisitionMode.CAPTIONS_THEN_STT) addFallback(row, owner, config)
                return row.copy(state = ExecutionState.FINISHED, outcome = Outcome.FAILED, error = "NO_ACCEPTABLE_CAPTIONS")
            }
            if (config.captionTrackId == null && choices.size > 1) return row.copy(state = ExecutionState.WAITING_USER, error = "CHOOSE_CAPTION_TRACK")
            val track = choices.first()
            checkpoint = checkpoint.copy(source = resolved.source, caption = track, rawExtension = track.format)
            row = row.copy(phase = Phase.FETCH_CAPTIONS, checkpoint = json.encodeToString(checkpoint))
            if (!dao.saveClaimed(row, owner)) throw CancellationException()
            val raw = extractor.caption(resolved, track, engines.file(installation), directory)
            val parsed = CaptionParser.parse(raw.toString(Charsets.UTF_8), track.format)
            val document = TranscriptDocument(artifactId = checkpoint.artifactId, source = resolved.source, acquisition = config,
                provenance = Provenance(Origin.YOUTUBE, track.generation, track.translation, captionTrack = track,
                    languageEvidence = track.evidence, engineVersions = mapOf("yt-dlp" to installation.version, "yt-dlp-ejs" to installation.ejsVersion)),
                language = track.language, scope = TranscriptScope(resolved.source.durationMs, technicallyComplete = parsed.technicallyComplete),
                segments = parsed.segments, warnings = parsed.warnings, createdAt = checkpoint.artifactCreatedAt,
                rawHash = sha256(raw))
            if (config.retainRaw) atomicWrite(AtomicFile(File(directory, "raw.${track.format}")), raw)
            atomicWrite(normalized, json.encodeToString(document).toByteArray())
        }
        row = row.copy(phase = Phase.PERSIST)
        if (!dao.saveClaimed(row, owner)) throw CancellationException()
        val document = json.decodeFromString<TranscriptDocument>(boundedJsonText(readAttemptFile(normalized, ArtifactFiles.MAX_CANONICAL_BYTES).toString(Charsets.UTF_8)))
        if (!persistedCaptionBindingMatches(row, config, checkpoint, document)) {
            throw JobActionException("NORMALIZED_ARTIFACT_INVALID")
        }
        val raw = if (config.retainRaw) {
            if (checkpoint.rawExtension !in setOf("vtt", "srt", "json3")) throw JobActionException("NORMALIZED_ARTIFACT_INVALID")
            readAttemptFile(AtomicFile(File(directory, "raw.${checkpoint.rawExtension}")), ArtifactFiles.MAX_RAW_BYTES)
        } else null
        val stored = artifacts.write(document, raw, if (raw != null) checkpoint.rawExtension else null)
        database.withTransaction {
            val held = dao.attempt(row.id)
            if (held?.leaseOwner != owner || held.leaseUntil <= System.currentTimeMillis()) throw CancellationException()
            if (dao.artifact(document.artifactId) == null) dao.insertArtifact(ArtifactRow(document.artifactId, row.jobId, row.id, row.branch,
                document.createdAt, stored.sha256, stored.bytes, document.language, null, document.scope.technicallyComplete, document.warnings.size))
        }
        return row.copy(state = ExecutionState.FINISHED, outcome = if (!document.scope.technicallyComplete.orFalse()) Outcome.PARTIAL_SUCCESS
            else if (document.warnings.isNotEmpty()) Outcome.SUCCESS_WITH_WARNINGS else Outcome.SUCCESS, error = null)
    }

    private suspend fun persistedCaptionBindingMatches(
        row: AttemptRow,
        config: JobConfig,
        checkpoint: AttemptCheckpoint,
        document: TranscriptDocument,
    ): Boolean {
        val caption = checkpoint.caption ?: return false
        val expectedVersions = if (row.engineId == null) emptyMap() else {
            val engine = engines.installations().singleOrNull { it.id == row.engineId } ?: return false
            mapOf("yt-dlp" to engine.version, "yt-dlp-ejs" to engine.ejsVersion)
        }
        return row.branch == Branch.CAPTIONS &&
            document.artifactId == checkpoint.artifactId &&
            document.source == checkpoint.source &&
            document.acquisition == config &&
            document.createdAt == checkpoint.artifactCreatedAt &&
            document.provenance.origin == Origin.YOUTUBE &&
            document.provenance.captionTrack == caption &&
            document.provenance.provider == null &&
            document.provenance.generation == caption.generation &&
            document.provenance.translation == caption.translation &&
            document.provenance.engineVersions == expectedVersions
    }

    private suspend fun addFallback(row: AttemptRow, owner: String, config: JobConfig) {
        val fallback = AttemptRow(fallbackAttemptId(row.id), row.jobId, Branch.STT,
                (dao.attempts(row.jobId).filter { it.branch == Branch.STT }.maxOfOrNull { it.number } ?: 0) + 1, System.currentTimeMillis(),
                checkpoint = json.encodeToString(json.decodeFromString<AttemptCheckpoint>(row.checkpoint)
                    .copy(artifactId = UUID.randomUUID().toString(), artifactCreatedAt = System.currentTimeMillis())),
                engineId = row.engineId)
        if (dao.insertFallback(row, owner, fallback)) enqueue(fallback, config)
    }

    private suspend fun enqueueExports(row: AttemptRow) = scheduleExportsPersisted(row) {
        manager.enqueueUniqueWork("exports:${row.id}", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ArtifactExportWorker>().addTag("job:${row.jobId}").setInputData(workDataOf("attemptId" to row.id)).build()).result.get(30, TimeUnit.SECONDS)
    }

    /** Export scheduling errors remain export errors after successful acquisition. */
    internal suspend fun scheduleExportsPersisted(row: AttemptRow, schedule: suspend () -> Unit) {
        try { schedule() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            database.withTransaction {
                val job = dao.job(row.jobId) ?: return@withTransaction
                if (job.deleteRequested) return@withTransaction
                val config = json.decodeFromString<JobConfig>(job.config)
                for (artifact in dao.artifacts(job.id).filter { it.attemptId == row.id }) {
                    for (format in config.exportFormats) {
                        if (dao.exports(artifact.id).none { it.format == format.name }) dao.insertExport(
                            ExportRow(UUID.randomUUID().toString(), artifact.id, format.name, config.exportTreeUri.orEmpty(),
                                System.currentTimeMillis(), state = ExportState.FAILED, error = "EXPORT_SCHEDULING_FAILED"))
                    }
                }
            }
        }
    }

    suspend fun exportArtifact(artifactId: String, format: ExportFormat, treeUri: String): ExportRow {
        recover()
        return lifecycle.withLock { exports.export(artifactId, format, treeUri) }
    }

    suspend fun retryExport(exportId: String, treeUri: String): ExportRow {
        recover()
        return lifecycle.withLock { exports.retry(exportId, treeUri) }
    }

    suspend fun reconcileExports() {
        recover()
        lifecycle.withLock { dao.allExports().filter { it.state == ExportState.EXPORTED }.forEach { exports.reconcile(it.id) } }
    }

    suspend fun runExports(attemptId: String) {
        recover()
        withContext(Dispatchers.IO) { lifecycle.withLock {
        val row = dao.attempt(attemptId) ?: return@withLock
        val job = dao.job(row.jobId) ?: return@withLock
        if (job.deleteRequested) return@withLock
        val config = json.decodeFromString<JobConfig>(job.config)
        for (artifact in dao.artifacts(row.jobId).filter { it.attemptId == row.id }) {
            for (format in config.exportFormats) {
                if (dao.exports(artifact.id).any { it.format == format.name }) continue
                val tree = config.exportTreeUri
                if (tree == null) dao.insertExport(ExportRow(UUID.randomUUID().toString(), artifact.id,
                    format.name, "", System.currentTimeMillis(), state = ExportState.PERMISSION_REQUIRED, error = "PERMISSION_REQUIRED"))
                else exports.export(artifact.id, format, tree)
            }
        }
        } }
    }

    private suspend fun summarize(jobId: String) = database.withTransaction {
        val job = dao.job(jobId) ?: return@withTransaction
        if (job.cancelRequested) return@withTransaction
        val config = configuration(job) ?: return@withTransaction
        val attempts = dao.attempts(jobId).groupBy { it.branch }.values.map { rows -> rows.maxBy { it.number } }
        val latestIds = attempts.map { it.id }.toSet()
        val saved = dao.artifacts(jobId).filter { it.attemptId in latestIds }
        val remaining = attempts.filter { it.state !in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) }
        val state = remaining.firstOrNull { it.state == ExecutionState.RUNNING }?.state ?: remaining.firstOrNull()?.state ?: ExecutionState.FINISHED
        val outcome = if (remaining.isNotEmpty()) Outcome.NONE else AcquisitionPlanner.outcome(config.mode, saved.map { it.branch }.toSet(), saved.any { it.warningCount > 0 }, saved.all { it.complete == true })
        val updated = job.copy(state = state, outcome = outcome)
        dao.updateJob(updated)
        notifications.update(updated, attempts)
    }

    private suspend fun configuration(job: JobRow): JobConfig? {
        decodeStoredJobConfig(job.config)?.let { return it }
        var uncertain = false
        for (row in dao.attempts(job.id)) {
            val pending = dao.submissions(row.id).any { it.state in setOf(SubmissionState.SENDING, SubmissionState.UNCERTAIN) }
            uncertain = uncertain || pending
            dao.updateAttempt(row.copy(
                state = when { pending -> ExecutionState.SUBMISSION_UNCERTAIN
                    job.cancelRequested -> ExecutionState.CANCELLED; else -> ExecutionState.WAITING_USER },
                outcome = if (job.cancelRequested) Outcome.CANCELLED else Outcome.FAILED,
                error = "JOB_CONFIG_INVALID", leaseOwner = null, leaseUntil = 0,
            ))
        }
        dao.updateJob(job.copy(
            state = when { job.cancelRequested -> ExecutionState.CANCELLED
                uncertain -> ExecutionState.SUBMISSION_UNCERTAIN; else -> ExecutionState.WAITING_USER },
            outcome = if (job.cancelRequested) Outcome.CANCELLED else Outcome.FAILED,
        ))
        return null
    }

    suspend fun scheduleNext(attemptId: String) {
        val row = dao.attempt(attemptId) ?: return
        val job = dao.job(row.jobId) ?: return
        if (job.cancelRequested || job.deleteRequested || row.state !in runnable) return
        enqueue(row, json.decodeFromString(job.config), ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private suspend fun enqueue(row: AttemptRow, config: JobConfig, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) = schedulePersisted(row) {
        val source = dao.job(row.jobId)?.let { dao.source(it.sourceId) }?.let { json.decodeFromString<Source>(it.snapshot) }
        val needsNetwork = row.branch == Branch.CAPTIONS && row.phase != Phase.PERSIST ||
            row.branch == Branch.STT && (row.phase == Phase.RESOLVE && source?.kind != SourceKind.LOCAL_AUDIO || row.phase in setOf(Phase.DOWNLOAD_AUDIO, Phase.UPLOAD, Phase.SUBMIT, Phase.RETRIEVE))
        val network = if (!needsNetwork) NetworkType.NOT_REQUIRED else if (config.networkPolicy == NetworkPolicy.UNMETERED) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<AcquisitionWorker>()
            .setInputData(workDataOf("attemptId" to row.id)).addTag("job:${row.jobId}")
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setInitialDelay((row.nextAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        withContext(Dispatchers.IO) { manager.enqueueUniqueWork("attempt:${row.id}", policy, request).result.get(30, TimeUnit.SECONDS) }
    }

    /** A scheduling failure cannot undo the durable job or overwrite an active worker. */
    internal suspend fun schedulePersisted(row: AttemptRow, schedule: suspend () -> Unit) {
        try { schedule() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            dao.schedulingFailed(row.id)
            summarize(row.jobId)
        }
    }

    /** Pins retained local audio under the same lock as cleanup before showing it again. */
    suspend fun sourceForPreview(sourceId: String, owner: String): Source = lifecycle.withLock {
        val row = dao.source(sourceId) ?: throw JobActionException("SOURCE_MISSING")
        val source = json.decodeFromString<Source>(row.snapshot)
        if (source.kind == SourceKind.LOCAL_AUDIO) {
            requireLocalAudio(row)
            SourceFiles.reservePreview(sourceId, owner)
        }
        source
    }

    private fun requireLocalAudio(row: SourceRow) {
        val imports = File(context.noBackupFilesDir, "imports")
        val path = row.importedPath?.let(::File) ?: throw JobActionException("IMPORTED_AUDIO_NOT_FOUND")
        if (java.nio.file.Files.isSymbolicLink(imports.toPath()) || java.nio.file.Files.isSymbolicLink(path.toPath()) ||
            path.canonicalFile.parentFile != imports.canonicalFile || !path.isFile) throw JobActionException("IMPORTED_AUDIO_NOT_FOUND")
    }

    private suspend fun availableGrowth(maximum: Long): Long {
        val snapshot = storage.snapshot()
        val quota = snapshot.limitBytes - snapshot.usedBytes - snapshot.reservedBytes - StorageBudget.DATABASE_GROWTH_RESERVE_BYTES
        val disk = physicalFreeBytes(context.noBackupFilesDir) - snapshot.reservedBytes - StorageBudget.FREE_SPACE_MARGIN_BYTES - StorageBudget.DATABASE_GROWTH_RESERVE_BYTES - 1
        val available = minOf(maximum, quota, disk)
        if (available <= 0) throw StorageBudgetException(StorageBudgetException.STORAGE_LIMIT, "no capacity for next phase")
        return available
    }

    private fun artifactOwners(attempts: List<AttemptRow>): Map<String, List<AttemptRow>> =
        attempts.mapNotNull { row ->
            val id = try { json.parseBounded(row.checkpoint).jsonObject["artifactId"]?.jsonPrimitive?.contentOrNull }
            catch (_: IllegalArgumentException) { null }
            id?.let { it to row }
        }.groupBy({ it.first }, { it.second })

    private suspend fun recoverFinalizedArtifacts(onlyJobId: String? = null) {
        for ((artifactId, attributed) in artifactOwners(dao.allAttempts())) {
            val row = attributed.singleOrNull() ?: continue
            if (onlyJobId != null && row.jobId != onlyJobId) continue
            val existing = dao.artifact(artifactId)
            val recovered = try {
                artifacts.recoverable(artifactId)?.let { it to artifacts.read(artifactId) }
            } catch (failure: ArtifactFilesException) {
                recordRecoveryFailure(row, failure.reason)
                continue
            }
            if (recovered == null) {
                if (existing != null) recordRecoveryFailure(row, "ARTIFACT_FILE_MISSING")
                continue
            }
            val (stored, document) = recovered
            val job = dao.job(row.jobId) ?: continue
            val config = configuration(job) ?: continue
            val documentBound = when (row.branch) {
                Branch.CAPTIONS -> try {
                    persistedCaptionBindingMatches(row, config, json.decodeFromString<AttemptCheckpoint>(row.checkpoint), document)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                Branch.STT -> stt.persistedBindingMatches(row, config, document)
            }
            if (existing != null && (existing.jobId != job.id || existing.attemptId != row.id ||
                    existing.branch != row.branch || existing.sha256 != stored.sha256 || existing.bytes != stored.bytes) ||
                row.phase != Phase.PERSIST || document.source.id != job.sourceId || !documentBound) {
                recordRecoveryFailure(row, "ARTIFACT_BINDING_MISMATCH")
                continue
            }
            database.withTransaction {
                if (dao.artifact(artifactId) == null) dao.insertArtifact(ArtifactRow(artifactId,
                    job.id, row.id, row.branch, document.createdAt, stored.sha256, stored.bytes, document.language,
                    document.provenance.reportedModel ?: document.provenance.requestedModel,
                    document.scope.technicallyComplete, document.warnings.size))
                if (!job.cancelRequested) dao.updateAttempt(row.copy(state = ExecutionState.FINISHED,
                    outcome = when { document.scope.technicallyComplete != true -> Outcome.PARTIAL_SUCCESS
                        document.warnings.isNotEmpty() -> Outcome.SUCCESS_WITH_WARNINGS; else -> Outcome.SUCCESS },
                    error = null, leaseOwner = null, leaseUntil = 0))
            }
        }
    }

    private suspend fun recordRecoveryFailure(row: AttemptRow, error: String) {
        val cancelled = dao.job(row.jobId)?.cancelRequested == true
        dao.updateAttempt(row.copy(
            state = if (cancelled) row.state else ExecutionState.WAITING_USER,
            outcome = if (cancelled) row.outcome else Outcome.FAILED,
            error = error,
            leaseOwner = null,
            leaseUntil = 0,
        ))
    }

    private suspend fun recordMaintenanceFailure(jobId: String, failure: Exception) {
        val error = when (failure) {
            is ArtifactFilesException -> failure.reason
            is StorageBudgetException -> failure.reason
            else -> "CLEANUP_FAILED"
        }
        dao.attempts(jobId).forEach { dao.updateAttempt(it.copy(error = error)) }
    }

    private suspend fun cleanupAbandonedAttemptFiles() {
        val directory = File(context.noBackupFilesDir, "attempts").toPath()
        if (java.nio.file.Files.isSymbolicLink(directory) || !java.nio.file.Files.isDirectory(directory)) return
        val retained = dao.allAttempts().mapTo(HashSet()) { it.id }
        java.nio.file.Files.newDirectoryStream(directory).use { entries ->
            var inspected = 0
            for (entry in entries) {
                if (++inspected > StorageBudget.MAX_SCAN_ENTRIES) {
                    throw StorageBudgetException(StorageBudgetException.STORAGE_SCAN_FAILED, "attempt entry limit exceeded")
                }
                val id = entry.fileName.toString()
                val canonical = try { UUID.fromString(id).toString() == id } catch (_: IllegalArgumentException) { false }
                if (canonical && id !in retained && id !in executing &&
                    java.nio.file.Files.isDirectory(entry, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    storage.deleteAttemptFiles(id)
                }
            }
        }
    }

    private suspend fun cleanupAbandonedImports() {
        val directory = File(context.noBackupFilesDir, "imports")
        if (!directory.isDirectory || java.nio.file.Files.isSymbolicLink(directory.toPath())) return
        // A fresh process has no preview/import in progress. Only our named temporary files qualify.
        directory.listFiles()?.filter { it.name.matches(Regex("import-[A-Za-z0-9-]+\\.part")) }?.forEach {
            if (it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath())) java.nio.file.Files.deleteIfExists(it.toPath())
        }
        for (source in dao.unreferencedSources()) deleteUnreferencedImport(source)
        for (entry in directory.listFiles().orEmpty()) {
            if (!entry.name.matches(Regex("[a-f0-9]{64}\\.audio")) || !entry.isFile || java.nio.file.Files.isSymbolicLink(entry.toPath())) continue
            val sourceId = "local:${entry.name.removeSuffix(".audio")}"
            if (dao.source(sourceId) == null && !SourceFiles.hasPreview(sourceId)) java.nio.file.Files.deleteIfExists(entry.toPath())
        }
    }

    suspend fun discardImportPreview(sourceId: String) = lifecycle.withLock {
        if (dao.sourceReferences(sourceId) == 0 && !SourceFiles.hasPreview(sourceId)) dao.source(sourceId)?.let { deleteUnreferencedImport(it) }
    }

    private suspend fun deleteUnreferencedImport(source: SourceRow) {
        if (SourceFiles.hasPreview(source.id)) return
        val snapshot = json.decodeFromString<Source>(source.snapshot)
        val hash = snapshot.contentHash ?: return
        if (snapshot.kind != SourceKind.LOCAL_AUDIO || !hash.matches(Regex("[a-f0-9]{64}"))) return
        val directory = File(context.noBackupFilesDir, "imports")
        val expected = File(directory, "$hash.audio")
        if (java.nio.file.Files.isSymbolicLink(directory.toPath()) || java.nio.file.Files.isSymbolicLink(expected.toPath())) return
        if (source.importedPath != null && source.importedPath != expected.absolutePath) return
        java.nio.file.Files.deleteIfExists(expected.toPath())
        dao.deleteUnusedSource(source.id)
    }

    private suspend fun cleanupPersisted(jobId: String) {
        val job = dao.job(jobId) ?: return
        val config = configuration(job) ?: return
        val saved = dao.artifacts(jobId)
        val attempts = dao.attempts(jobId)
        val latestIds = attempts.groupBy { it.branch }.values.map { rows -> rows.maxBy { it.number }.id }.toSet()
        val completeBranches = saved.filter { it.complete == true && it.attemptId in latestIds }.map { it.branch }.toSet()
        val completeAttemptIds = saved.filter { it.complete == true }.map { it.attemptId }.toSet()
        for (row in attempts.filter { it.id !in executing && it.state in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) }) {
            val hasCompletedReplacement = attempts.any {
                it.branch == row.branch && it.number > row.number && it.id in completeAttemptIds
            }
            val hasPaidEvidence = dao.submissions(row.id).any {
                it.state in setOf(SubmissionState.SENDING, SubmissionState.ACCEPTED, SubmissionState.RESPONSE_SAVED, SubmissionState.UNCERTAIN)
            }
            if (row.id !in completeAttemptIds && !hasCompletedReplacement && hasPaidEvidence) continue
            // A partial transcript still needs its successful response spools for a missing-chunk retry.
            if (saved.any { it.attemptId == row.id && it.complete != true } && row.branch !in completeBranches) continue
            val keepAudio = config.audioRetention == AudioRetention.KEEP ||
                config.audioRetention == AudioRetention.UNTIL_PERSISTED && row.id !in completeAttemptIds
            try { storage.deleteAttemptFiles(row.id, keepAudio) }
            catch (_: StorageBudgetException) { dao.updateAttempt(row.copy(error = "CLEANUP_FAILED")) }
        }
        val source = dao.source(job.sourceId) ?: return
        if (SourceFiles.hasPreview(source.id)) return
        val path = source.importedPath ?: return
        val related = dao.jobsForSource(source.id)
        val relatedConfigs = related.associateWith { configuration(it) ?: return }
        if (related.any { it.state !in setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED) ||
                relatedConfigs.getValue(it).audioRetention == AudioRetention.KEEP }) return
        for (relatedJob in related) if (dao.attempts(relatedJob.id).any { it.id in executing }) return
        for (relatedJob in related) {
            val retention = relatedConfigs.getValue(relatedJob).audioRetention
            val latestStt = dao.attempts(relatedJob.id).filter { it.branch == Branch.STT }.maxByOrNull { it.number }
            if (retention == AudioRetention.UNTIL_PERSISTED && dao.artifacts(relatedJob.id).none { it.attemptId == latestStt?.id && it.complete == true }) return
        }
        val file = File(path)
        val imports = File(context.noBackupFilesDir, "imports")
        if (java.nio.file.Files.isSymbolicLink(file.toPath()) || java.nio.file.Files.isSymbolicLink(imports.toPath()) ||
            file.canonicalFile.parentFile != imports.canonicalFile) return
        try {
            java.nio.file.Files.deleteIfExists(file.toPath())
            dao.updateSource(source.copy(importedPath = null))
        } catch (_: java.io.IOException) {
            // Persisted artifacts stay successful; the quota still counts the retained import.
        }
    }

    internal fun attemptDirectory(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(context.noBackupFilesDir, "attempts/$id").also { check(it.isDirectory || it.mkdirs()) }
    }

    private fun readAttemptFile(target: AtomicFile, maximum: Int): ByteArray {
        val file = target.baseFile
        if (listOf(file, File(file.path + ".bak"), File(file.path + ".new"), requireNotNull(file.parentFile)).any { java.nio.file.Files.isSymbolicLink(it.toPath()) }) {
            throw JobActionException("NORMALIZED_ARTIFACT_INVALID")
        }
        return target.openRead().use { input ->
            val length = input.channel.size()
            if (length !in 1..maximum.toLong()) throw JobActionException("NORMALIZED_ARTIFACT_INVALID")
            ByteArray(length.toInt()).also {
                java.io.DataInputStream(input).readFully(it)
                if (input.read() != -1) throw JobActionException("NORMALIZED_ARTIFACT_INVALID")
            }
        }
    }

    private fun atomicWrite(target: AtomicFile, bytes: ByteArray) {
        val output = target.startWrite()
        try { output.write(bytes); target.finishWrite(output) }
        catch (failure: Exception) { target.failWrite(output); throw failure }
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun Boolean?.orFalse() = this == true
    private companion object { val runnable = setOf(ExecutionState.QUEUED, ExecutionState.WAITING_NETWORK, ExecutionState.WAITING_RATE_LIMIT, ExecutionState.WAITING_REMOTE) }
}

class JobActionException(val code: String) : java.io.IOException(code)

@HiltWorker
class AcquisitionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val coordinator: JobCoordinator,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("attemptId") ?: return Result.failure()
        if (coordinator.run(id)) coordinator.scheduleNext(id)
        return Result.success()
    }
}

/** Export failures belong to exports; this worker has no acquisition or provider retry path. */
@HiltWorker
class ArtifactExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val coordinator: JobCoordinator,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("attemptId") ?: return Result.failure()
        return try { coordinator.runExports(id); Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.failure() }
    }
}
