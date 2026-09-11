package app.sourcescribe.data

import android.content.Context
import androidx.room.withTransaction
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.ArtifactFilesException
import app.sourcescribe.core.AudioTrack
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Generation
import app.sourcescribe.core.Interval
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.JobLimits
import app.sourcescribe.core.Origin
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import app.sourcescribe.core.PollResult
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderAdapter
import app.sourcescribe.core.ProviderCapabilities
import app.sourcescribe.core.ProviderError
import app.sourcescribe.core.ProviderErrorCode
import app.sourcescribe.core.ProviderTranscript
import app.sourcescribe.core.Provenance
import app.sourcescribe.core.RemoteHandle
import app.sourcescribe.core.ResponseSpool
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.SubmissionResult
import app.sourcescribe.core.SourceResolver
import app.sourcescribe.core.TranscriptDocument
import app.sourcescribe.core.TranscriptScope
import app.sourcescribe.core.TrackSelection
import app.sourcescribe.core.TranscriptionRequest
import app.sourcescribe.core.Translation
import app.sourcescribe.core.parseBounded
import app.sourcescribe.core.retryDelayMillis
import app.sourcescribe.extractor.AudioPreparation
import app.sourcescribe.extractor.AudioPreparationException
import app.sourcescribe.extractor.EngineInstallation
import app.sourcescribe.extractor.EngineUpdateException
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractionException
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal data class PreparedRetry(
    val attempt: AttemptRow,
    val submissions: List<SubmissionRow>,
)

/** Executes exactly one durable STT phase for an already claimed attempt. */
@Singleton
class SttStep @Inject constructor(
    @ApplicationContext context: Context,
    private val database: SourceScribeDatabase,
    private val dao: SourceScribeDao,
    private val credentials: CredentialStore,
    private val runtime: NativeRuntime,
    private val extractor: ExtractorEngine,
    private val engines: EngineUpdateManager,
    private val artifacts: ArtifactFiles,
    private val providerHttp: app.sourcescribe.core.ProviderHttp,
) {
    private val appContext = context.applicationContext
    private val preparation = AudioPreparation(runtime)
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    /** Room owns the lease; this method never releases it. */
    suspend fun run(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        maxDownloadBytes: Long = MAX_SOURCE_AUDIO_BYTES,
    ): AttemptRow {
        require(maxDownloadBytes in 1..MAX_SOURCE_AUDIO_BYTES) { "maxDownloadBytes must be within the source bound" }
        require(row.branch == Branch.STT)
        if (row.state in TERMINAL_STATES) return row
        return try {
            ensureFence(row, owner)
            val checkpoint = checkpoint(row.checkpoint)
            when (row.phase) {
                Phase.RESOLVE -> resolve(row, owner, config, checkpoint)
                Phase.DOWNLOAD_AUDIO -> download(row, owner, config, checkpoint, maxDownloadBytes)
                Phase.PREPARE_AUDIO -> prepare(row, owner, config, checkpoint)
                Phase.UPLOAD, Phase.SUBMIT -> submit(row, owner, config, checkpoint)
                Phase.RETRIEVE -> retrieve(row, owner, config, checkpoint)
                Phase.NORMALIZE -> normalize(row, owner, config, checkpoint)
                Phase.PERSIST -> persist(row, owner, config, checkpoint)
                Phase.FETCH_CAPTIONS -> waitForUser(row, owner, "INVALID_STT_PHASE")
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { markSendingUncertain(row, owner) }
            throw cancelled
        } catch (failure: ExtractionException) {
            val latest = currentRow(row, owner)
            val retryable = failure.failure == app.sourcescribe.extractor.ExtractionFailure.NETWORK ||
                failure.failure == app.sourcescribe.extractor.ExtractionFailure.RATE_LIMIT
            val retries = latest.retries + 1
            val retry = retryable && retries <= MAX_NETWORK_RETRIES
            save(latest.copy(
                state = when {
                    !retry -> ExecutionState.WAITING_USER
                    failure.failure == app.sourcescribe.extractor.ExtractionFailure.RATE_LIMIT -> ExecutionState.WAITING_RATE_LIMIT
                    else -> ExecutionState.WAITING_NETWORK
                },
                nextAt = if (retry) {
                    System.currentTimeMillis() + retryDelayMillis(
                        (failure.retryAfterSeconds ?: MIN_REMOTE_RETRY_SECONDS)
                            .coerceIn(MIN_REMOTE_RETRY_SECONDS, MAX_RETRY_AFTER_SECONDS) * 1000L,
                    )
                } else 0,
                retries = retries,
                error = failure.failure.name,
            ), owner)
        } catch (failure: AudioPreparationException) {
            waitForUser(currentRow(row, owner), owner, "AUDIO_${failure.code.name}")
        } catch (failure: EngineUpdateException) {
            waitForUser(currentRow(row, owner), owner, "ENGINE_${failure.code.name}")
        } catch (failure: CredentialException) {
            waitForUser(currentRow(row, owner), owner, "CREDENTIAL_${failure.code.name}")
        } catch (failure: ArtifactFilesException) {
            waitForUser(currentRow(row, owner), owner, failure.reason)
        } catch (_: CheckpointDamaged) {
            waitForUser(currentRow(row, owner), owner, "CHECKPOINT_DAMAGED")
        } catch (failure: ProviderError) {
            waitForUser(currentRow(row, owner), owner, "PROVIDER_${failure.code.name}")
        }
    }

    /** Prepares a new attempt that resubmits only the chunks missing from [partial]. */
    internal suspend fun prepareMissingRetry(
        previous: AttemptRow,
        target: AttemptRow,
        config: JobConfig,
        partial: TranscriptDocument,
        storage: StorageBudget,
    ): PreparedRetry {
        fun invalid(): Nothing = throw JobActionException("MISSING_RETRY_DATA")
        fun responseAlreadySaved(): Nothing = throw JobActionException("MISSING_RETRY_RESPONSE_SAVED")

        val previousCheckpoint = try {
            checkpoint(previous.checkpoint)
        } catch (_: Exception) {
            invalid()
        }
        val targetCheckpoint = try {
            checkpoint(target.checkpoint)
        } catch (_: Exception) {
            invalid()
        }
        val job = dao.job(previous.jobId) ?: invalid()
        val sourceRow = dao.source(job.sourceId) ?: invalid()
        val storedConfig = try {
            json.decodeFromString<JobConfig>(job.config)
        } catch (_: Exception) {
            invalid()
        }
        val storedSource = decodeSource(sourceRow.snapshot) ?: invalid()
        val artifact = dao.artifact(partial.artifactId) ?: invalid()
        if (dao.attempt(previous.id) != previous || dao.attempt(target.id) != null ||
            dao.submissions(target.id).isNotEmpty() || storedConfig != config ||
            !(previous.state == ExecutionState.FINISHED && previous.outcome == Outcome.PARTIAL_SUCCESS ||
                previous.state == ExecutionState.CANCELLED && previous.outcome == Outcome.CANCELLED) ||
            previous.phase != Phase.PERSIST || previous.branch != Branch.STT ||
            target.id == previous.id || canonicalUuid(target.id) == null ||
            target.jobId != previous.jobId || target.branch != Branch.STT ||
            target.number != previous.number + 1 || target.engineId != previous.engineId ||
            target.state != ExecutionState.QUEUED || target.phase != Phase.RESOLVE ||
            target.outcome != Outcome.NONE || target.createdAt < previous.createdAt ||
            targetCheckpoint.artifactId == previousCheckpoint.artifactId ||
            canonicalUuid(targetCheckpoint.artifactId) == null ||
            dao.artifact(targetCheckpoint.artifactId) != null ||
            targetCheckpoint.artifactCreatedAt < target.createdAt ||
            targetCheckpoint.source != storedSource || targetCheckpoint.audio != null ||
            targetCheckpoint.durationMs != null || targetCheckpoint.prepared.isNotEmpty() ||
            targetCheckpoint.missingChunks.isNotEmpty() || targetCheckpoint.normalized ||
            targetCheckpoint.rawExtension != null || targetCheckpoint.reusedArtifactId != null
        ) invalid()

        val missing = previousCheckpoint.missingChunks
        val duration = previousCheckpoint.durationMs ?: invalid()
        val prepared = previousCheckpoint.prepared.sortedBy { it.index }
        val retainedChunkIndexes = prepared.mapTo(HashSet()) { it.index }.apply { removeAll(missing.toSet()) }
        val expectedIntervals = prepared.filter { it.index !in missing }
            .map { Interval(it.offsetMs, it.offsetMs + it.durationMs) }
        val encodedPartial = encode(partial).toByteArray(StandardCharsets.UTF_8)
        if (!previousCheckpoint.normalized || previousCheckpoint.nextChunkIndex != previousCheckpoint.chunkCount ||
            missing.isEmpty() || missing != missing.sorted() || missing != partial.scope.missingChunks ||
            previousCheckpoint.artifactId != partial.artifactId ||
            previousCheckpoint.artifactCreatedAt != partial.createdAt ||
            previousCheckpoint.source != storedSource || partial.source != storedSource ||
            partial.acquisition != config || partial.scope.requestedDurationMs != duration ||
            partial.scope.processedIntervals != expectedIntervals || partial.scope.technicallyComplete != false ||
            partial.provenance.origin != Origin.PROVIDER || partial.provenance.provider != config.provider ||
            partial.provenance.generation != Generation.UNKNOWN ||
            partial.provenance.translation != Translation.UNKNOWN || partial.provenance.captionTrack != null ||
            partial.provenance.requestedModel != config.model ||
            partial.provenance.sourceAudioTrack != previousCheckpoint.audio ||
            partial.provenance.engineVersions != previousCheckpoint.engineVersions ||
            partial.provenance.reusedArtifactId != previousCheckpoint.reusedArtifactId ||
            artifact.jobId != previous.jobId || artifact.attemptId != previous.id ||
            artifact.branch != Branch.STT || artifact.createdAt != partial.createdAt || artifact.complete != false ||
            artifact.sha256 != sha256(encodedPartial) || artifact.bytes != encodedPartial.size.toLong() ||
            artifact.language != partial.language || artifact.warningCount != partial.warnings.size ||
            artifact.providerModel != (partial.provenance.reportedModel ?: partial.provenance.requestedModel) ||
            partial.segments.any { it.chunkIndex == null || it.chunkIndex !in retainedChunkIndexes } ||
            partial.words.any { it.chunkIndex == null || it.chunkIndex !in retainedChunkIndexes }
        ) invalid()
        val valid = validate(config, requireCredential = false) ?: invalid()
        val previousDirectory = existingAttemptDirectory(previous.id) ?: invalid()
        if (targetAttemptEntry(target.id).let { it.exists() || Files.isSymbolicLink(it.toPath()) }) invalid()
        if (!retrySourceBindingMatches(storedSource, previousCheckpoint, previousDirectory)) invalid()

        var requiredBytes = 0L
        val audioFiles = ArrayList<Pair<PreparedChunk, File>>(prepared.size)
        for (chunk in prepared) {
            currentCoroutineContext().ensureActive()
            val audio = regularPrivateFile(File(previousDirectory, "audio-${chunk.index}.mp3"), previousDirectory)
                ?: invalid()
            if (audio.length() != chunk.bytes || audio.length() !in 1..MAX_CHUNK_BYTES ||
                try {
                    withContext(Dispatchers.IO) { sha256(audio) }
                } catch (_: IOException) {
                    invalid()
                } catch (_: SecurityException) {
                    invalid()
                } != chunk.sha256
            ) invalid()
            requiredBytes = retrySize(requiredBytes, chunk.bytes)
            audioFiles += chunk to audio
        }

        val previousSubmissions = dao.submissions(previous.id)
        val preparedIndexes = prepared.mapTo(HashSet()) { it.index }
        if (previousSubmissions.any { it.chunkIndex !in preparedIndexes }) invalid()
        val submissionsByChunk = previousSubmissions.associateBy { it.chunkIndex }
        if (submissionsByChunk.size != previousSubmissions.size) invalid()
        val expectedConfigHash = configHash(config)
        for (chunk in prepared) {
            val submission = submissionsByChunk[chunk.index] ?: continue
            if (submission.attemptId != previous.id || submission.provider != valid.adapter.provider.name ||
                submission.credentialId != config.credentialId || submission.region != config.region.name ||
                submission.inputHash != chunk.sha256 || submission.configHash != expectedConfigHash ||
                !responseBinding(submission, previous.id) ||
                (submission.state == SubmissionState.REJECTED) != (submission.rejectionCode != null)
            ) invalid()
            if (chunk.index in missing) {
                when (submission.state) {
                    SubmissionState.REJECTED -> Unit
                    SubmissionState.RESPONSE_SAVED -> responseAlreadySaved()
                    else -> invalid()
                }
            } else if (submission.state != SubmissionState.RESPONSE_SAVED || submission.rejectionCode != null) {
                invalid()
            }
        }
        val reusable = ArrayList<ReusableResponse>()
        val transcripts = ArrayList<ProviderTranscript>()
        val providerWarnings = ArrayList<String>()
        for (chunk in prepared.filter { it.index !in missing }) {
            currentCoroutineContext().ensureActive()
            val submission = submissionsByChunk[chunk.index] ?: invalid()
            if (submission.state != SubmissionState.RESPONSE_SAVED || submission.rejectionCode != null ||
                submission.attemptId != previous.id || submission.provider != valid.adapter.provider.name ||
                submission.credentialId != config.credentialId || submission.region != config.region.name ||
                submission.inputHash != chunk.sha256 || submission.configHash != expectedConfigHash
            ) invalid()
            val response = existingResponseFile(previousDirectory, submission) ?: invalid()
            val responseBytes = try {
                readBounded(response)
            } catch (_: IOException) {
                invalid()
            } catch (_: SecurityException) {
                invalid()
            }
            val parsed = try {
                valid.adapter.parseSavedResponse(responseBytes, request(previous.id, config, chunk))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                invalid()
            } as? SubmissionResult.Direct ?: invalid()
            if (valid.adapter.provider == Provider.ASSEMBLYAI &&
                (submission.remoteId == null || responseRemoteId(responseBytes) != canonicalRemoteId(submission.remoteId))
            ) invalid()
            if (parsed.transcript.segments != partial.segments.filter { it.chunkIndex == chunk.index } ||
                parsed.transcript.words != partial.words.filter { it.chunkIndex == chunk.index }
            ) invalid()
            val responseLength = response.length()
            if (responseLength != responseBytes.size.toLong() || responseLength !in 1..MAX_RESPONSE_BYTES.toLong()) invalid()
            requiredBytes = retrySize(requiredBytes, responseLength)
            reusable += ReusableResponse(submission, response, responseLength, sha256(responseBytes))
            transcripts += parsed.transcript
            providerWarnings += parsed.transcript.warnings.map { "CHUNK_${chunk.index}_$it" }
        }
        val languages = transcripts.flatMap { it.reportedLanguages + listOfNotNull(it.language) }.distinct()
        val models = transcripts.mapNotNull { it.reportedModel }.distinct()
        if (partial.language != transcripts.mapNotNull { it.language }.distinct().singleOrNull() ||
            partial.provenance.reportedLanguages != languages ||
            partial.provenance.reportedModel != models.singleOrNull() ||
            partial.provenance.languageEvidence != "provider_response" ||
            partial.segments != transcripts.flatMap { it.segments } ||
            partial.words != transcripts.flatMap { it.words } ||
            partial.warnings.filter { warning ->
                retainedChunkIndexes.any { warning.startsWith("CHUNK_${it}_") }
            } != providerWarnings.distinct()
        ) invalid()

        val nextCheckpoint = previousCheckpoint.copy(
            artifactId = targetCheckpoint.artifactId,
            artifactCreatedAt = targetCheckpoint.artifactCreatedAt,
            nextChunkIndex = previousCheckpoint.chunkCount,
            missingChunks = emptyList(),
            normalized = false,
            rawExtension = null,
            reusedArtifactId = partial.artifactId,
        ).also(::validateCheckpoint)
        val nextAttempt = target.copy(
            phase = Phase.SUBMIT,
            state = ExecutionState.QUEUED,
            outcome = Outcome.NONE,
            checkpoint = encode(nextCheckpoint),
            nextAt = 0,
            retries = 0,
            error = null,
            leaseOwner = null,
            leaseUntil = 0,
            processedBytes = prepared.sumOf { it.bytes },
            totalBytes = prepared.sumOf { it.bytes },
        )

        var targetCreated = false
        try {
            return storage.withReservation(requiredBytes) {
                val targetDirectory = attemptDirectory(target.id)
                targetCreated = true
                for ((chunk, source) in audioFiles) {
                    copyBounded(
                        source = source,
                        target = File(targetDirectory, "audio-${chunk.index}.mp3"),
                        expectedBytes = chunk.bytes,
                        maximumBytes = MAX_CHUNK_BYTES,
                        expectedSha256 = chunk.sha256,
                    )
                }
                val copiedSubmissions = reusable.map { reused ->
                    val id = UUID.randomUUID().toString()
                    val targetResponse = responsePath(target.id, id)
                    copyBounded(
                        source = reused.file,
                        target = targetResponse,
                        expectedBytes = reused.bytes,
                        maximumBytes = MAX_RESPONSE_BYTES.toLong(),
                        expectedSha256 = reused.sha256,
                    )
                    reused.submission.copy(
                        id = id,
                        attemptId = target.id,
                        state = SubmissionState.RESPONSE_SAVED,
                        createdAt = target.createdAt,
                        estimatedMicrousd = 0,
                        rawResponsePath = targetResponse.absolutePath,
                        reusedFromId = reused.submission.id,
                        rejectionCode = null,
                    )
                }
                PreparedRetry(nextAttempt, copiedSubmissions)
            }
        } catch (cancelled: CancellationException) {
            if (targetCreated) cleanupRetryTarget(storage, target.id)
            throw cancelled
        } catch (failure: StorageBudgetException) {
            if (targetCreated) cleanupRetryTarget(storage, target.id)
            throw failure
        } catch (failure: JobActionException) {
            if (targetCreated) cleanupRetryTarget(storage, target.id)
            throw failure
        } catch (_: Exception) {
            if (targetCreated) cleanupRetryTarget(storage, target.id)
            invalid()
        }
    }

    private suspend fun resolve(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        original: SttCheckpoint,
    ): AttemptRow {
        if (validate(config) == null) return waitForUser(row, owner, validationError(config))
        val sourceRow = sourceRow(row) ?: return waitForUser(row, owner, "SOURCE_NOT_FOUND")
        val source = original.source ?: decodeSource(sourceRow.snapshot)
            ?: return waitForUser(row, owner, "SOURCE_SNAPSHOT_INVALID")
        if (source.id != sourceRow.id) return waitForUser(row, owner, "SOURCE_ID_MISMATCH")
        val contentHash = source.contentHash
        if (source.kind == SourceKind.LOCAL_AUDIO &&
            (source.canonicalUrl != null || contentHash == null || !SHA256_PATTERN.matches(contentHash))
        ) return waitForUser(row, owner, "SOURCE_SNAPSHOT_INVALID")
        val checkpoint = initialize(original, source)
        if (source.kind == SourceKind.LOCAL_AUDIO) {
            val imported = importedFile(sourceRow.importedPath)
                ?: return waitForUser(row, owner, "IMPORTED_AUDIO_NOT_FOUND")
            if (!sourceMatchesInput(source, imported)) return waitForUser(row, owner, "SOURCE_CHANGED")
            return save(row.copy(
                phase = Phase.PREPARE_AUDIO,
                state = ExecutionState.QUEUED,
                nextAt = 0,
                checkpoint = encode(checkpoint.copy(
                    source = source,
                    audio = null,
                    sourceAudioTrackId = null,
                    sourceAudioSha256 = null,
                )),
                error = null,
            ), owner)
        }

        val installation = pinnedEngine(row) ?: return waitForUser(row, owner, "ENGINE_NOT_AVAILABLE")
        ensureFence(row, owner)
        currentCoroutineContext().ensureActive()
        val resolved = withTimeout(RESOLVE_TIMEOUT_MS) {
            extractor.resolve(source, engines.file(installation))
        }
        val audio = TrackSelection.audio(resolved, config.audioTrackId)
            ?: return waitForUser(row, owner, if (config.audioTrackId == null) "CHOOSE_AUDIO_TRACK" else "AUDIO_TRACK_CHANGED")
        val next = checkpoint.copy(
            source = resolved.source,
            audio = audio,
            engineVersions = mapOf("yt-dlp" to installation.version, "yt-dlp-ejs" to installation.ejsVersion),
        )
        return save(row.copy(
            phase = Phase.DOWNLOAD_AUDIO,
            state = ExecutionState.QUEUED,
            nextAt = 0,
            checkpoint = encode(next),
            error = null,
        ), owner)
    }

    private suspend fun download(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        checkpoint: SttCheckpoint,
        maxDownloadBytes: Long,
    ): AttemptRow {
        if (validate(config) == null) return waitForUser(row, owner, validationError(config))
        val source = checkpoint.source ?: return waitForUser(row, owner, "SOURCE_MISSING")
        val audio = checkpoint.audio ?: return waitForUser(row, owner, "AUDIO_TRACK_MISSING")
        val directory = attemptDirectory(row.id)
        val destination = File(directory, SOURCE_AUDIO_NAME)
        val existing = regularPrivateFile(destination, directory)
        if (existing == null && Files.isSymbolicLink(destination.toPath())) {
            return waitForUser(row, owner, "SOURCE_AUDIO_UNBOUND")
        }
        if (existing != null) {
            val bound = checkpoint.sourceAudioTrackId == audio.id &&
                audio.sourceVideoId == source.videoId &&
                checkpoint.sourceAudioSha256?.let { expected ->
                    try {
                        sha256(existing) == expected
                    } catch (_: IOException) {
                        false
                    } catch (_: SecurityException) {
                        false
                    }
                } == true
            if (bound) {
                return save(row.copy(phase = Phase.PREPARE_AUDIO, state = ExecutionState.QUEUED, nextAt = 0, error = null), owner)
            }
            // A file left behind before its binding checkpoint was durable is not
            // evidence for an upload. Rebuild it through the free extractor path.
            if (!existing.delete() && existing.exists()) {
                return waitForUser(row, owner, "SOURCE_AUDIO_UNBOUND")
            }
        }
        val installation = pinnedEngine(row) ?: return waitForUser(row, owner, "ENGINE_NOT_AVAILABLE")
        ensureFence(row, owner)
        currentCoroutineContext().ensureActive()
        val refreshed = withTimeout(DOWNLOAD_TIMEOUT_MS) {
            extractor.resolve(source, engines.file(installation))
        }
        SourceResolver.requireMatchingVideo(source, refreshed.source.videoId)
        val selected = TrackSelection.audio(refreshed, audio.id)
            ?: return waitForUser(row, owner, "AUDIO_TRACK_CHANGED")
        if (selected.id != audio.id) return waitForUser(row, owner, "AUDIO_TRACK_CHANGED")
        val downloaded = withTimeout(DOWNLOAD_TIMEOUT_MS) {
            ensureFence(row, owner)
            currentCoroutineContext().ensureActive()
            extractor.downloadAudio(
                source = source,
                formatId = selected.id,
                directory = directory,
                maxBytes = minOf(MAX_SOURCE_AUDIO_BYTES, maxDownloadBytes),
                engine = engines.file(installation),
            )
        }
        val canonicalDownloaded = downloaded.canonicalFile
        if (regularPrivateFile(canonicalDownloaded, directory) == null || canonicalDownloaded.name != DOWNLOADING_AUDIO_NAME) {
            return waitForUser(row, owner, "DOWNLOADED_AUDIO_INVALID")
        }
        moveAtomically(canonicalDownloaded, destination)
        val downloadedHash = try {
            withContext(Dispatchers.IO) { sha256(destination) }
        } catch (_: IOException) {
            return waitForUser(row, owner, "SOURCE_AUDIO_HASH_FAILED")
        } catch (_: SecurityException) {
            return waitForUser(row, owner, "SOURCE_AUDIO_HASH_FAILED")
        }
        val boundCheckpoint = checkpoint.copy(
            sourceAudioTrackId = selected.id,
            sourceAudioSha256 = downloadedHash,
        )
        return save(row.copy(
            phase = Phase.PREPARE_AUDIO,
            state = ExecutionState.QUEUED,
            nextAt = 0,
            checkpoint = encode(boundCheckpoint),
            error = null,
        ), owner)
    }

    private suspend fun prepare(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        original: SttCheckpoint,
    ): AttemptRow {
        val valid = validate(config) ?: return waitForUser(row, owner, validationError(config))
        if (!dao.claimResource(AUDIO_RESOURCE, owner, System.currentTimeMillis(), System.currentTimeMillis() + LEASE_MS)) {
            return save(row.copy(state = ExecutionState.QUEUED, nextAt = System.currentTimeMillis() + retryDelayMillis(RESOURCE_RETRY_MS), error = "AUDIO_RESOURCE_BUSY"), owner)
        }
        val source = original.source ?: return waitForUser(row, owner, "SOURCE_MISSING")
        val input = if (source.kind == SourceKind.LOCAL_AUDIO) {
            (sourceRow(row)?.importedPath?.let(::importedFile))
        } else {
            regularPrivateFile(File(attemptDirectory(row.id), SOURCE_AUDIO_NAME), attemptDirectory(row.id))
        } ?: return waitForUser(row, owner, "AUDIO_INPUT_MISSING")
        if (source.kind == SourceKind.LOCAL_AUDIO && !sourceMatchesInput(source, input)) {
            return waitForUser(row, owner, "SOURCE_CHANGED")
        }
        if (source.kind != SourceKind.LOCAL_AUDIO &&
            (original.audio?.id != original.sourceAudioTrackId ||
                original.audio?.sourceVideoId != source.videoId ||
                !sourceAudioMatches(original, input))
        ) {
            return waitForUser(row, owner, "SOURCE_AUDIO_UNBOUND")
        }

        val directory = attemptDirectory(row.id)
        if (!reconcileOrphans(directory, original)) {
            return waitForUser(row, owner, "PREPARED_AUDIO_ORPHAN")
        }

        var checkpoint = original
        if (checkpoint.durationMs == null || checkpoint.chunkCount == 0) {
            val info = withTimeout(PREPARE_TIMEOUT_MS) { preparation.probe(input) }
            val maxMs = maxDurationMs(config)
            // The measured length and the configured cap fail for different reasons and need different answers.
            if (info.durationMs <= 0) return waitForUser(row, owner, "AUDIO_DURATION_UNKNOWN")
            if (info.durationMs > maxMs || info.durationMs > MAX_AUDIO_DURATION_MS) {
                return waitForUser(row, owner, "AUDIO_LONGER_THAN_LIMIT")
            }
            checkpoint = checkpoint.copy(
                durationMs = info.durationMs,
                chunkCount = chunkPlan(info.durationMs).size,
                nextChunkIndex = 0,
            )
            // Keep probing and chunk conversion in separate durable invocations.
            return save(row.copy(
                phase = Phase.PREPARE_AUDIO,
                state = ExecutionState.QUEUED,
                nextAt = 0,
                processedBytes = 0,
                totalBytes = null,
                retries = 0,
                checkpoint = encode(checkpoint),
                error = null,
            ), owner)
        }
        if (checkpoint.nextChunkIndex >= checkpoint.chunkCount) {
            return save(row.copy(
                phase = Phase.SUBMIT,
                state = ExecutionState.QUEUED,
                nextAt = 0,
                processedBytes = checkpoint.prepared.sumOf { it.bytes },
                totalBytes = checkpoint.prepared.sumOf { it.bytes },
                retries = 0,
                checkpoint = encode(checkpoint),
                error = null,
            ), owner)
        }

        val window = chunkPlan(requireNotNull(checkpoint.durationMs))[checkpoint.nextChunkIndex]
        val existing = checkpoint.prepared.firstOrNull { it.index == window.index }
        val prepared = if (existing != null) {
            val file = File(directory, "audio-${window.index}.mp3")
            if (regularPrivateFile(file, directory) == null || sha256(file) != existing.sha256 || file.length() != existing.bytes) {
                return waitForUser(row, owner, "PREPARED_AUDIO_CHANGED")
            }
            existing
        } else {
            val result = withTimeout(PREPARE_TIMEOUT_MS) {
                preparation.chunk(
                    input = input,
                    outputDirectory = directory,
                    index = window.index,
                    startMs = window.offsetMs,
                    durationMs = window.durationMs,
                    maxBytes = minOf(MAX_CHUNK_BYTES, valid.capabilities.maxUploadBytes),
                )
            }
            val expectedFile = File(directory, "audio-${window.index}.mp3")
            if (result.offsetMs != window.offsetMs ||
                regularPrivateFile(result.file, directory)?.path != expectedFile.canonicalFile.path ||
                result.durationMs !in 1..MAX_CHUNK_DURATION_MS ||
                result.mimeType != AUDIO_MIME_TYPE ||
                kotlin.math.abs(result.durationMs - window.durationMs) > PREPARED_DURATION_TOLERANCE_MS
            ) return waitForUser(row, owner, "PREPARED_AUDIO_INVALID")
            PreparedChunk(window.index,
                result.offsetMs,
                result.durationMs,
                result.file.length(),
                result.sha256,
                result.mimeType)
        }
        checkpoint = checkpoint.copy(
            prepared = (checkpoint.prepared.filterNot { it.index == prepared.index } + prepared).sortedBy { it.index },
            nextChunkIndex = prepared.index + 1,
        )
        val processed = checkpoint.prepared.sumOf { it.bytes }
        return save(row.copy(
            phase = if (checkpoint.nextChunkIndex < checkpoint.chunkCount) Phase.PREPARE_AUDIO else Phase.SUBMIT,
            state = ExecutionState.QUEUED,
            nextAt = 0,
            processedBytes = processed,
            totalBytes = if (checkpoint.nextChunkIndex == checkpoint.chunkCount) processed else null,
            checkpoint = encode(checkpoint),
            error = null,
        ), owner)
    }

    private suspend fun submit(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        checkpoint: SttCheckpoint,
    ): AttemptRow {
        val valid = validate(config, requireCredential = false) ?: return waitForUser(row, owner, validationError(config))
        val chunks = checkpoint.prepared.sortedBy { it.index }
        val submissions = dao.submissions(row.id).associateBy { it.chunkIndex }
        if (submissions.values.any { it.state == SubmissionState.UNCERTAIN }) {
            return save(row.copy(state = ExecutionState.SUBMISSION_UNCERTAIN, nextAt = 0, error = "SUBMISSION_UNCERTAIN"), owner)
        }
        submissions.values.firstOrNull { it.state == SubmissionState.SENDING }?.let {
            return replaySending(row, owner, config, checkpoint, valid, it)
        }
        submissions.values.firstOrNull { it.state == SubmissionState.ACCEPTED }?.let {
            return save(row.copy(
                phase = Phase.RETRIEVE,
                state = ExecutionState.WAITING_REMOTE,
                nextAt = (System.currentTimeMillis() + retryDelayMillis(MIN_REMOTE_RETRY_MS)).coerceAtLeast(row.nextAt),
                retries = 0,
                error = null,
            ), owner)
        }
        val chunk = chunks.firstOrNull { submissions[it.index]?.state !in TERMINAL_SUBMISSION_STATES }
            ?: return normalizeOrFailed(row, owner, submissions)
        val existing = submissions[chunk.index]
        if (existing?.state == SubmissionState.PREPARED) {
            return sendPrepared(row, owner, config, checkpoint, valid, existing, chunk)
        }
        val estimate = estimateCostMicrousd(valid.capabilities, config, chunk.durationMs)
        val budgetError = budgetError(row.jobId, null, estimate, config.maxCostMicrousd)
        if (budgetError != null) {
            // Keep already spooled/charged siblings durable as a partial artifact. A
            // later retry must never resubmit those chunks merely because this one
            // exceeded the remaining budget.
            return if (submissions.values.any { it.state == SubmissionState.RESPONSE_SAVED }) {
                save(row.copy(
                    phase = Phase.NORMALIZE,
                    state = ExecutionState.QUEUED,
                    nextAt = 0,
                    error = budgetError,
                ), owner)
            } else {
                waitForUser(row, owner, budgetError)
            }
        }
        val id = UUID.randomUUID().toString()
        val rawPath = responsePath(row.id, id)
        val prepared = SubmissionRow(
            id = id,
            attemptId = row.id,
            chunkIndex = chunk.index,
            provider = valid.adapter.provider.name,
            credentialId = requireNotNull(config.credentialId),
            region = config.region.name,
            inputHash = chunk.sha256,
            configHash = configHash(config),
            state = SubmissionState.PREPARED,
            createdAt = System.currentTimeMillis(),
            estimatedMicrousd = estimate,
            rawResponsePath = rawPath.absolutePath,
        )
        database.withTransaction {
            requireFenceInTransaction(row, owner)
            if (dao.submissions(row.id).none { it.chunkIndex == chunk.index }) dao.insertSubmission(prepared)
        }
        return sendPrepared(row, owner, config, checkpoint, valid, prepared, chunk)
    }

    private suspend fun sendPrepared(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        checkpoint: SttCheckpoint,
        valid: Validated,
        submission: SubmissionRow,
        chunk: PreparedChunk,
    ): AttemptRow {
        if (!bindingMatches(submission, row.id, config, chunk)) {
            return waitForUser(row, owner, "SUBMISSION_BINDING_MISMATCH")
        }
        if (!preparedFileMatches(chunk, row.id)) {
            return waitForUser(row, owner, "PREPARED_AUDIO_CHANGED")
        }
        if (!dao.claimResource(providerResource(valid.adapter.provider), owner, System.currentTimeMillis(), System.currentTimeMillis() + LEASE_MS)) {
            return save(row.copy(state = ExecutionState.QUEUED, nextAt = System.currentTimeMillis() + retryDelayMillis(RESOURCE_RETRY_MS), error = "PROVIDER_RESOURCE_BUSY"), owner)
        }
        val key = try {
            credentials.read(requireNotNull(config.credentialId), valid.adapter.provider, config.region)
        } catch (failure: CredentialException) {
            return waitForUser(row, owner, "CREDENTIAL_${failure.code.name}")
        }
        val sending = submission.copy(state = SubmissionState.SENDING, rejectionCode = null)
        updateSubmissionClaimed(row, owner, sending)
        val request = request(row.id, config, chunk)
        val result = try {
            ensureFence(row, owner)
            currentCoroutineContext().ensureActive()
            withTimeout(SUBMIT_TIMEOUT_MS) {
                valid.adapter.submit(request, key, AtomicResponseSpool(responsePath(row.id, submission.id)))
            }
        } catch (failure: ProviderError) {
            return handleSubmitError(row, owner, config, sending, failure)
        }
        return saveSubmissionResult(row, owner, config, checkpoint, valid.adapter, sending, result)
    }

    private suspend fun replaySending(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        checkpoint: SttCheckpoint,
        valid: Validated,
        submission: SubmissionRow,
    ): AttemptRow {
        val chunk = checkpoint.prepared.firstOrNull { it.index == submission.chunkIndex }
            ?: return waitForUser(row, owner, "SUBMISSION_CHUNK_MISSING")
        if (!bindingMatches(submission, row.id, config, chunk)) return waitForUser(row, owner, "SUBMISSION_BINDING_MISMATCH")
        val response = responsePath(row.id, submission.id)
        if (regularResponseFile(response, row.id) == null) {
            return markSubmissionUncertain(row, owner, submission, "SUBMISSION_UNCERTAIN")
        }
        val request = request(row.id, config, chunk)
        val parsed = try {
            valid.adapter.parseSavedResponse(readBounded(response), request)
        } catch (failure: ProviderError) {
            return if (valid.adapter.provider == Provider.ASSEMBLYAI &&
                failure.code in setOf(ProviderErrorCode.REMOTE_FAILED, ProviderErrorCode.INVALID_RESPONSE)
            ) {
                // The process may have stopped after AAI spooled a status=error
                // receipt but before the live submit path recorded its ID.
                handleSubmitError(row, owner, config, submission, failure)
            } else {
                responseParseFailure(row, owner, submission, failure)
            }
        } catch (_: IOException) {
            return markSubmissionUncertain(row, owner, submission, "SUBMISSION_UNCERTAIN")
        } catch (_: SecurityException) {
            return markSubmissionUncertain(row, owner, submission, "SUBMISSION_UNCERTAIN")
        }
        return when (parsed) {
            is SubmissionResult.Direct -> markDirectResponseSaved(
                row,
                owner,
                config,
                checkpoint,
                submission,
                response,
                valid.adapter.provider,
            )
            is SubmissionResult.Remote -> {
                if (!validHandle(parsed.handle, valid.adapter.provider, config)) return waitForUser(row, owner, "REMOTE_HANDLE_INVALID")
                updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.ACCEPTED, remoteId = parsed.handle.id))
                save(row.copy(
                    phase = Phase.RETRIEVE,
                    state = ExecutionState.WAITING_REMOTE,
                    nextAt = System.currentTimeMillis() + retryDelayMillis(MIN_REMOTE_RETRY_MS),
                    retries = 0,
                    error = null,
                ), owner)
            }
        }
    }

    private suspend fun retrieve(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        checkpoint: SttCheckpoint,
    ): AttemptRow {
        val valid = validate(config, requireCredential = false) ?: return waitForUser(row, owner, validationError(config))
        var submission = dao.submissions(row.id).firstOrNull { it.state == SubmissionState.ACCEPTED }
            ?: return save(row.copy(phase = Phase.SUBMIT, state = ExecutionState.QUEUED, nextAt = 0, error = null), owner)
        val chunk = checkpoint.prepared.firstOrNull { it.index == submission.chunkIndex }
            ?: return waitForUser(row, owner, "SUBMISSION_CHUNK_MISSING")
        if (!bindingMatches(submission, row.id, config, chunk)) return waitForUser(row, owner, "SUBMISSION_BINDING_MISMATCH")
        val now = System.currentTimeMillis()
        val raw = responsePath(row.id, submission.id)
        val storedRaw = regularResponseFile(raw, row.id)
        if (storedRaw != null) {
            // A poll response is spooled before its Room state is advanced. Replay it
            // after a crash so a completed response is never needlessly re-polled.
            val response = try {
                readBounded(storedRaw)
            } catch (_: IOException) {
                return waitForUser(row, owner, "RESPONSE_STORAGE")
            } catch (_: SecurityException) {
                return waitForUser(row, owner, "RESPONSE_STORAGE")
            }
            if (!submission.remoteId.isNullOrBlank() &&
                !responseBelongsToRemote(valid.adapter.provider, response, submission.remoteId)
            ) {
                // A completed response for a different remote submission must never
                // turn the accepted submission into NORMALIZE. Discard only this
                // exact, private spool and poll the accepted id instead.
                if (!deleteResponseSpool(row.id, submission.id)) {
                    return preserveRemote(row, owner, "REMOTE_RESPONSE_ID_MISMATCH")
                }
            } else {
                val parsed = try {
                    valid.adapter.parseSavedResponse(response, request(row.id, config, chunk))
                } catch (failure: ProviderError) {
                    return replayRemoteResponseFailure(row, owner, submission, failure)
                } catch (_: IOException) {
                    return waitForUser(row, owner, "RESPONSE_STORAGE")
                } catch (_: SecurityException) {
                    return waitForUser(row, owner, "RESPONSE_STORAGE")
                }
                when (parsed) {
                    is SubmissionResult.Direct -> return markDirectResponseSaved(
                        row,
                        owner,
                        config,
                        checkpoint,
                        submission,
                        storedRaw,
                        valid.adapter.provider,
                    )
                    is SubmissionResult.Remote -> {
                        if (!validHandle(parsed.handle, valid.adapter.provider, config)) {
                            return waitForUser(row, owner, "REMOTE_HANDLE_INVALID")
                        }
                        if (submission.remoteId.isNullOrBlank()) {
                            submission = submission.copy(state = SubmissionState.ACCEPTED, remoteId = parsed.handle.id)
                            updateSubmissionClaimed(row, owner, submission)
                        }
                    }
                }
            }
        }
        val remoteId = submission.remoteId
        if (remoteId.isNullOrBlank()) return waitForUser(row, owner, "REMOTE_RECEIPT_MISSING")
        if (now - submission.createdAt > REMOTE_MAX_AGE_MS) {
            return preserveRemote(row, owner, "REMOTE_TIMEOUT")
        }
        val handle = RemoteHandle(valid.adapter.provider, config.region, remoteId)
        val key = try {
            credentials.read(requireNotNull(config.credentialId), valid.adapter.provider, config.region)
        } catch (failure: CredentialException) {
            return waitForUser(row, owner, "CREDENTIAL_${failure.code.name}")
        }
        val request = request(row.id, config, chunk)
        val result = try {
            ensureFence(row, owner)
            currentCoroutineContext().ensureActive()
            withTimeout(POLL_TIMEOUT_MS) {
                valid.adapter.poll(handle, request, key, AtomicResponseSpool(responsePath(row.id, submission.id)))
            }
        } catch (failure: ProviderError) {
            return handlePollError(row, owner, submission, failure)
        }
        return when (result) {
            is PollResult.Waiting -> save(row.copy(
                phase = Phase.RETRIEVE,
                state = ExecutionState.WAITING_REMOTE,
                nextAt = System.currentTimeMillis() + retryDelayMillis(
                    result.retryAfterSeconds
                        .coerceIn(MIN_REMOTE_RETRY_SECONDS, MAX_RETRY_AFTER_SECONDS) * 1000L,
                ),
                retries = 0,
                error = null,
            ), owner)
            is PollResult.Complete -> {
                val completedRaw = regularResponseFile(responsePath(row.id, submission.id), row.id)
                    ?: return waitForUser(row, owner, "RESPONSE_STORAGE")
                val completedResponse = try {
                    readBounded(completedRaw)
                } catch (_: IOException) {
                    return waitForUser(row, owner, "RESPONSE_STORAGE")
                } catch (_: SecurityException) {
                    return waitForUser(row, owner, "RESPONSE_STORAGE")
                }
                if (!responseBelongsToRemote(valid.adapter.provider, completedResponse, remoteId)) {
                    if (!deleteResponseSpool(row.id, submission.id)) {
                        return preserveRemote(row, owner, "RESPONSE_STORAGE")
                    }
                    return preserveRemote(row, owner, "REMOTE_RESPONSE_ID_MISMATCH")
                }
                if (completedResponse.isEmpty()) {
                    return waitForUser(row, owner, "RESPONSE_STORAGE")
                }
                updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.RESPONSE_SAVED))
                continueAfterTerminal(row, owner, checkpoint)
            }
        }
    }

    private suspend fun normalize(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        checkpoint: SttCheckpoint,
    ): AttemptRow {
        val submissions = dao.submissions(row.id).associateBy { it.chunkIndex }
        if (submissions.values.any { it.state !in TERMINAL_SUBMISSION_STATES }) {
            return continueAfterTerminal(row, owner, checkpoint)
        }
        val source = checkpoint.source ?: return waitForUser(row, owner, "SOURCE_MISSING")
        val valid = validate(config, requireCredential = false) ?: return waitForUser(row, owner, validationError(config))
        val transcripts = ArrayList<Pair<PreparedChunk, ProviderTranscript>>()
        val rawFiles = ArrayList<Pair<PreparedChunk, File>>()
        val missing = checkpoint.missingChunks.toMutableSet()
        val normalizationWarnings = ArrayList<String>()
        for (chunk in checkpoint.prepared.sortedBy { it.index }) {
            val submission = submissions[chunk.index]
            if (submission == null || submission.state in setOf(SubmissionState.REJECTED, SubmissionState.REMOTE_DELETED)) {
                missing += chunk.index
                continue
            }
            if (submission.state != SubmissionState.RESPONSE_SAVED || !bindingMatches(submission, row.id, config, chunk)) {
                return waitForUser(row, owner, "RESPONSE_NOT_READY")
            }
            val raw = responsePath(row.id, submission.id)
            val storedRaw = regularResponseFile(raw, row.id)
            if (storedRaw == null) {
                missing += chunk.index
                normalizationWarnings += "CHUNK_${chunk.index}_RESPONSE_STORAGE"
                continue
            }
            val bytes = storedRaw.length()
            if (bytes !in 1L..MAX_RESPONSE_BYTES.toLong()) {
                missing += chunk.index
                normalizationWarnings += "CHUNK_${chunk.index}_RAW_RESPONSE_TOO_LARGE"
                continue
            }
            rawFiles += chunk to storedRaw
        }
        val retainedResponses = if (config.retainRaw) ArrayList<RawResponse>() else null
        var retainedRawBytes = 0L
        var canonicalTranscriptBytes = 0L
        for ((chunk, raw) in rawFiles) {
            val response = try {
                readBounded(raw)
            } catch (_: IOException) {
                missing += chunk.index
                normalizationWarnings += "CHUNK_${chunk.index}_RESPONSE_STORAGE"
                continue
            } catch (_: SecurityException) {
                missing += chunk.index
                normalizationWarnings += "CHUNK_${chunk.index}_RESPONSE_STORAGE"
                continue
            }
            if (config.retainRaw) {
                if (retainedRawBytes > MAX_RAW_NORMALIZATION_BYTES - response.size) {
                    missing += chunk.index
                    normalizationWarnings += "CHUNK_${chunk.index}_RAW_RESPONSE_AGGREGATE_TOO_LARGE"
                    continue
                }
                retainedRawBytes += response.size
            }
            retainedResponses?.add(RawResponse(chunk.index, response))
            val parsed = try {
                valid.adapter.parseSavedResponse(response, request(row.id, config, chunk))
            } catch (failure: ProviderError) {
                missing += chunk.index
                normalizationWarnings += "CHUNK_${chunk.index}_RESPONSE_${failure.code.name}"
                continue
            }
            val direct = parsed as? SubmissionResult.Direct
            if (direct == null) {
                missing += chunk.index
                normalizationWarnings += "CHUNK_${chunk.index}_REMOTE_NOT_COMPLETE"
                continue
            }
            val encodedTranscript = json.encodeToString(direct.transcript)
                .toByteArray(StandardCharsets.UTF_8)
            if (canonicalTranscriptBytes > ArtifactFiles.MAX_CANONICAL_BYTES.toLong() - encodedTranscript.size) {
                missing += chunk.index
                normalizationWarnings += "CHUNK_${chunk.index}_CANONICAL_TRANSCRIPT_AGGREGATE_TOO_LARGE"
                continue
            }
            canonicalTranscriptBytes += encodedTranscript.size
            transcripts += chunk to direct.transcript
        }
        if (transcripts.isEmpty()) return save(row.copy(state = ExecutionState.FINISHED, outcome = Outcome.FAILED, error = "NO_TRANSCRIPT", nextAt = 0), owner)
        val payload = retainedResponses?.let(::rawPayload)
            ?: if (config.retainRaw) return waitForUser(row, owner, "RAW_RESPONSE_TOO_LARGE") else null
        val rawBundle = payload?.bytes
        val languages = transcripts.flatMap { it.second.reportedLanguages + listOfNotNull(it.second.language) }.distinct()
        val models = transcripts.mapNotNull { it.second.reportedModel }.distinct()
        val providerWarnings = transcripts.flatMap { (chunk, transcript) ->
            transcript.warnings.map { "CHUNK_${chunk.index}_$it" }
        }
        val warnings = buildList {
            addAll(providerWarnings)
            addAll(normalizationWarnings)
            if (missing.isNotEmpty()) add("MISSING_CHUNKS:${missing.sorted().joinToString(",")}")
            if (languages.size > 1) add("MULTIPLE_LANGUAGES")
            if (models.size > 1) add("MULTIPLE_REPORTED_MODELS")
        }.distinct()
        val orderedTranscripts = transcripts.sortedBy { it.first.offsetMs }
        val intervalDiscontinuity = missing.isEmpty() && orderedTranscripts.isNotEmpty() && (
            orderedTranscripts.first().first.offsetMs != 0L ||
                orderedTranscripts.last().first.offsetMs + orderedTranscripts.last().first.durationMs != checkpoint.durationMs ||
                orderedTranscripts.zipWithNext().any { (left, right) ->
                    left.first.offsetMs + left.first.durationMs != right.first.offsetMs
                }
            )
        val finalWarnings = if (intervalDiscontinuity) {
            warnings + "AUDIO_INTERVAL_GAP_OR_OVERLAP"
        } else {
            warnings
        }
        val allComplete = missing.isEmpty() && transcripts.all { it.second.technicallyComplete } && !intervalDiscontinuity
        val document = TranscriptDocument(
            artifactId = checkpoint.artifactId,
            source = source,
            acquisition = config,
            provenance = Provenance(
                origin = Origin.PROVIDER,
                provider = valid.adapter.provider,
                requestedModel = config.model,
                reportedModel = models.singleOrNull(),
                sourceAudioTrack = checkpoint.audio,
                reusedArtifactId = checkpoint.reusedArtifactId,
                languageEvidence = "provider_response",
                engineVersions = checkpoint.engineVersions,
                reportedLanguages = languages,
            ),
            language = transcripts.mapNotNull { it.second.language }.distinct().singleOrNull(),
            scope = TranscriptScope(
                requestedDurationMs = checkpoint.durationMs,
                processedIntervals = transcripts.map { (chunk, _) -> Interval(chunk.offsetMs, chunk.offsetMs + chunk.durationMs) },
                missingChunks = missing.sorted(),
                technicallyComplete = allComplete,
            ),
            segments = transcripts.flatMap { it.second.segments },
            warnings = finalWarnings,
            createdAt = checkpoint.artifactCreatedAt,
            rawHash = rawBundle?.let(::sha256),
            words = transcripts.flatMap { it.second.words },
        )
        val directory = attemptDirectory(row.id)
        val encodedDocument = encode(document).toByteArray(StandardCharsets.UTF_8)
        if (encodedDocument.size > ArtifactFiles.MAX_CANONICAL_BYTES) {
            return waitForUser(row, owner, "CANONICAL_ARTIFACT_TOO_LARGE")
        }
        writeAtomically(File(directory, NORMALIZED_NAME), encodedDocument)
        if (payload != null) {
            setOf("json", "zip").filter { it != payload.extension }
                .forEach { File(directory, rawProviderName(it)).delete() }
            writeAtomically(File(directory, rawProviderName(payload.extension)), payload.bytes)
        }
        val next = checkpoint.copy(
            missingChunks = missing.sorted(),
            normalized = true,
            rawExtension = payload?.extension,
        )
        return save(row.copy(phase = Phase.PERSIST, state = ExecutionState.QUEUED, nextAt = 0, checkpoint = encode(next), error = null), owner)
    }

    private suspend fun persist(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        checkpoint: SttCheckpoint,
    ): AttemptRow {
        val directory = attemptDirectory(row.id)
        val normalized = File(directory, NORMALIZED_NAME)
        if (regularPrivateFile(normalized, directory) == null) return save(row.copy(phase = Phase.NORMALIZE, state = ExecutionState.QUEUED, nextAt = 0), owner)
        val document = try {
            json.decodeFromString<TranscriptDocument>(readBounded(normalized, ArtifactFiles.MAX_CANONICAL_BYTES).toString(StandardCharsets.UTF_8))
        } catch (_: Exception) {
            return waitForUser(row, owner, "NORMALIZED_ARTIFACT_INVALID")
        }
        if (!persistedBindingMatches(config, checkpoint, document)) {
            return waitForUser(row, owner, "ARTIFACT_BINDING_MISMATCH")
        }
        val rawExtension = if (config.retainRaw) {
            checkpoint.rawExtension ?: "json"
        } else {
            null
        }
        val raw = if (rawExtension != null) {
            val rawFile = File(directory, rawProviderName(rawExtension))
            if (regularPrivateFile(rawFile, directory) == null) return waitForUser(row, owner, "RAW_RESPONSE_MISSING")
            try {
                readBounded(rawFile)
            } catch (_: IOException) {
                return waitForUser(row, owner, "RAW_RESPONSE_MISSING")
            } catch (_: SecurityException) {
                return waitForUser(row, owner, "RAW_RESPONSE_MISSING")
            }
        } else {
            null
        }
        val stored = artifacts.write(document, raw, rawExtension)
        database.withTransaction {
            requireFenceInTransaction(row, owner)
            val existing = dao.artifact(document.artifactId)
            if (existing == null) {
                dao.insertArtifact(ArtifactRow(
                    id = document.artifactId,
                    jobId = row.jobId,
                    attemptId = row.id,
                    branch = Branch.STT,
                    createdAt = document.createdAt,
                    sha256 = stored.sha256,
                    bytes = stored.bytes,
                    language = document.language,
                    providerModel = document.provenance.reportedModel ?: document.provenance.requestedModel,
                    complete = document.scope.technicallyComplete,
                    warningCount = document.warnings.size,
                ))
            } else if (existing.jobId != row.jobId || existing.attemptId != row.id ||
                existing.branch != Branch.STT || existing.sha256 != stored.sha256 || existing.bytes != stored.bytes
            ) {
                throw ArtifactFilesException(
                    ArtifactFilesException.CONFLICTING_CONTENT,
                    "artifact binding does not match attempt",
                )
            }
        }
        val outcome = when {
            document.scope.missingChunks.isNotEmpty() || document.scope.technicallyComplete != true -> app.sourcescribe.core.Outcome.PARTIAL_SUCCESS
            document.warnings.isNotEmpty() -> app.sourcescribe.core.Outcome.SUCCESS_WITH_WARNINGS
            else -> app.sourcescribe.core.Outcome.SUCCESS
        }
        return save(row.copy(state = ExecutionState.FINISHED, outcome = outcome, phase = Phase.PERSIST, nextAt = 0, error = null), owner)
    }

    internal fun persistedBindingMatches(row: AttemptRow, config: JobConfig, document: TranscriptDocument): Boolean {
        if (row.branch != Branch.STT) return false
        val persisted = try {
            checkpoint(row.checkpoint)
        } catch (_: CheckpointDamaged) {
            return false
        }
        return persistedBindingMatches(config, persisted, document)
    }

    private fun persistedBindingMatches(
        config: JobConfig,
        checkpoint: SttCheckpoint,
        document: TranscriptDocument,
    ): Boolean = document.artifactId == checkpoint.artifactId &&
        document.acquisition == config &&
        document.source == checkpoint.source &&
        document.createdAt == checkpoint.artifactCreatedAt &&
        document.provenance.origin == Origin.PROVIDER &&
        document.provenance.provider == config.provider &&
        document.provenance.requestedModel == config.model &&
        document.provenance.sourceAudioTrack == checkpoint.audio &&
        document.provenance.reusedArtifactId == checkpoint.reusedArtifactId &&
        document.provenance.engineVersions == checkpoint.engineVersions

    private suspend fun normalizeOrFailed(
        row: AttemptRow,
        owner: String,
        submissions: Map<Int, SubmissionRow>,
    ): AttemptRow {
        return if (submissions.isNotEmpty() && submissions.values.all { it.state in TERMINAL_SUBMISSION_STATES }) {
            save(row.copy(phase = Phase.NORMALIZE, state = ExecutionState.QUEUED, nextAt = 0, error = null), owner)
        } else {
            waitForUser(row, owner, "SUBMISSION_CHUNK_MISSING")
        }
    }

    private suspend fun markSubmissionUncertain(
        row: AttemptRow,
        owner: String,
        submission: SubmissionRow,
        error: String,
    ): AttemptRow {
        val next = row.copy(state = ExecutionState.SUBMISSION_UNCERTAIN, nextAt = 0, error = error)
        database.withTransaction {
            requireFenceInTransaction(row, owner)
            dao.updateSubmission(submission.copy(state = SubmissionState.UNCERTAIN))
            dao.updateAttempt(next)
        }
        return next
    }

    private suspend fun saveSubmissionResult(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        checkpoint: SttCheckpoint,
        adapter: ProviderAdapter,
        sending: SubmissionRow,
        result: SubmissionResult,
    ): AttemptRow {
        val raw = responsePath(row.id, sending.id)
        if (regularResponseFile(raw, row.id) == null) {
            return markSubmissionUncertain(row, owner, sending, "RESPONSE_STORAGE")
        }
        return when (result) {
            is SubmissionResult.Direct -> markDirectResponseSaved(
                row,
                owner,
                config,
                checkpoint,
                sending,
                raw,
                adapter.provider,
            )
            is SubmissionResult.Remote -> {
                if (!validHandle(result.handle, adapter.provider, config)) return waitForUser(row, owner, "REMOTE_HANDLE_INVALID")
                updateSubmissionClaimed(row, owner, sending.copy(state = SubmissionState.ACCEPTED, remoteId = result.handle.id))
                save(row.copy(
                    phase = Phase.RETRIEVE,
                    state = ExecutionState.WAITING_REMOTE,
                    nextAt = System.currentTimeMillis() + retryDelayMillis(MIN_REMOTE_RETRY_MS),
                    retries = 0,
                    error = null,
                ), owner)
            }
        }
    }

    private suspend fun markDirectResponseSaved(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        checkpoint: SttCheckpoint,
        submission: SubmissionRow,
        responseFile: File,
        provider: Provider,
    ): AttemptRow {
        val remoteId = if (provider == Provider.ASSEMBLYAI) {
            val response = try {
                readBounded(responseFile)
            } catch (_: IOException) {
                return markSubmissionUncertain(row, owner, submission, "RESPONSE_STORAGE")
            } catch (_: SecurityException) {
                return markSubmissionUncertain(row, owner, submission, "RESPONSE_STORAGE")
            }
            val receiptId = responseRemoteId(response)
            if (receiptId == null || !validHandle(RemoteHandle(provider, config.region, receiptId), provider, config)) {
                return markSubmissionUncertain(row, owner, submission, "REMOTE_RECEIPT_MISSING")
            }
            receiptId
        } else {
            null
        }
        return markResponseSaved(row, owner, checkpoint, submission, remoteId)
    }

    private suspend fun markResponseSaved(
        row: AttemptRow,
        owner: String,
        checkpoint: SttCheckpoint,
        submission: SubmissionRow,
        remoteId: String?,
    ): AttemptRow {
        updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.RESPONSE_SAVED, remoteId = remoteId))
        return continueAfterTerminal(row, owner, checkpoint)
    }

    private suspend fun continueAfterTerminal(
        row: AttemptRow,
        owner: String,
        checkpoint: SttCheckpoint,
    ): AttemptRow {
        val states = dao.submissions(row.id).map { it.state }
        if (states.any { it == SubmissionState.UNCERTAIN || it == SubmissionState.SENDING }) {
            return save(row.copy(state = ExecutionState.SUBMISSION_UNCERTAIN, nextAt = 0, error = "SUBMISSION_UNCERTAIN"), owner)
        }
        if (states.any { it == SubmissionState.ACCEPTED }) {
            return save(row.copy(phase = Phase.RETRIEVE, state = ExecutionState.WAITING_REMOTE, nextAt = System.currentTimeMillis() + retryDelayMillis(MIN_REMOTE_RETRY_MS), retries = 0, error = null), owner)
        }
        return save(row.copy(
            phase = if (states.all { it in TERMINAL_SUBMISSION_STATES } && states.size >= checkpoint.chunkCount) Phase.NORMALIZE else Phase.SUBMIT,
            state = ExecutionState.QUEUED,
            nextAt = 0,
            error = null,
        ), owner)
    }

    private suspend fun handleSubmitError(
        row: AttemptRow,
        owner: String,
        config: JobConfig,
        submission: SubmissionRow,
        failure: ProviderError,
    ): AttemptRow {
        val rawExists = regularResponseFile(responsePath(row.id, submission.id), row.id)
        if (config.provider == Provider.ASSEMBLYAI &&
            submission.provider == Provider.ASSEMBLYAI.name &&
            failure.code == ProviderErrorCode.REMOTE_FAILED &&
            submission.remoteId.isNullOrBlank() &&
            rawExists != null
        ) {
            // AAI can return a chargeable receipt with status=error. The adapter
            // reports that status as REMOTE_FAILED after spooling the body. Keep
            // the receipt as an accepted remote so a retry cannot submit again.
            val receiptId = try {
                responseRemoteId(readBounded(rawExists))
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
            if (receiptId != null && validHandle(RemoteHandle(Provider.ASSEMBLYAI, config.region, receiptId), Provider.ASSEMBLYAI, config)) {
                updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.ACCEPTED, remoteId = receiptId))
                return save(row.copy(
                    phase = Phase.RETRIEVE,
                    state = ExecutionState.WAITING_USER,
                    nextAt = 0,
                    error = failure.code.name,
                ), owner)
            }
        }
        return when {
            config.provider == Provider.ASSEMBLYAI &&
                submission.provider == Provider.ASSEMBLYAI.name &&
                failure.code == ProviderErrorCode.INVALID_RESPONSE &&
                rawExists == null -> {
                // Upload responses are non-chargeable and are not spooled. A malformed
                // upload receipt therefore happened before the paid transcript POST.
                updateSubmissionClaimed(row, owner, submission.copy(
                    state = SubmissionState.REJECTED,
                    rejectionCode = failure.code.name,
                ))
                save(row.copy(state = ExecutionState.WAITING_USER, nextAt = 0, error = failure.code.name), owner)
            }
            rawExists != null && submission.provider == Provider.ASSEMBLYAI.name &&
                failure.code == ProviderErrorCode.INVALID_RESPONSE -> {
                // A chargeable AAI response without a valid receipt must stay
                // uncertain. Treating it as a saved direct result could permit a
                // later retry without a durable remote binding.
                markSubmissionUncertain(row, owner, submission, "REMOTE_RECEIPT_MISSING")
            }
            rawExists != null && failure.code == ProviderErrorCode.INVALID_RESPONSE -> responseParseFailure(row, owner, submission, failure)
            submission.provider == Provider.ASSEMBLYAI.name &&
                failure.code in setOf(ProviderErrorCode.NETWORK, ProviderErrorCode.SERVER) -> {
                val retries = row.retries + 1
                updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.PREPARED, rejectionCode = null))
                if (retries <= MAX_NETWORK_RETRIES) {
                    save(row.copy(
                        state = ExecutionState.WAITING_NETWORK,
                        nextAt = System.currentTimeMillis() + retryDelayMillis(MIN_REMOTE_RETRY_MS),
                        retries = retries,
                        error = failure.code.name,
                    ), owner)
                } else {
                    save(row.copy(
                        state = ExecutionState.WAITING_USER,
                        nextAt = 0,
                        retries = retries,
                        error = failure.code.name,
                    ), owner)
                }
            }
            failure.code == ProviderErrorCode.RATE_LIMIT -> {
                val retries = row.retries + 1
                if (retries > MAX_NETWORK_RETRIES) {
                    // A rate limit is not a confirmed pre-accept rejection. Keep the
                    // prepared row so its budget reservation remains counted when the
                    // user resumes the bounded retry.
                    updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.PREPARED, rejectionCode = null))
                    save(row.copy(state = ExecutionState.WAITING_USER, nextAt = 0, retries = retries, error = failure.code.name), owner)
                } else {
                    updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.PREPARED, rejectionCode = null))
                    save(row.copy(state = ExecutionState.WAITING_RATE_LIMIT, nextAt = System.currentTimeMillis() + retryDelayMillis((failure.retryAfterSeconds ?: MIN_REMOTE_RETRY_SECONDS).coerceIn(MIN_REMOTE_RETRY_SECONDS, MAX_RETRY_AFTER_SECONDS) * 1000L), retries = retries, error = failure.code.name), owner)
                }
            }
            failure.code == ProviderErrorCode.SUBMISSION_UNCERTAIN -> {
                updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.UNCERTAIN, rejectionCode = null))
                save(row.copy(state = ExecutionState.SUBMISSION_UNCERTAIN, nextAt = 0, error = failure.code.name), owner)
            }
            failure.code in KNOWN_REJECTION_CODES -> {
                updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.REJECTED, rejectionCode = failure.code.name))
                save(row.copy(state = ExecutionState.WAITING_USER, nextAt = 0, error = failure.code.name), owner)
            }
            else -> {
                updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.UNCERTAIN, rejectionCode = null))
                save(row.copy(state = ExecutionState.SUBMISSION_UNCERTAIN, nextAt = 0, error = failure.code.name), owner)
            }
        }
    }

    private suspend fun handlePollError(
        row: AttemptRow,
        owner: String,
        submission: SubmissionRow,
        failure: ProviderError,
    ): AttemptRow {
        val retries = row.retries + 1
        return when (failure.code) {
            ProviderErrorCode.RATE_LIMIT -> if (retries <= MAX_NETWORK_RETRIES) {
                save(row.copy(state = ExecutionState.WAITING_RATE_LIMIT, nextAt = System.currentTimeMillis() + retryDelayMillis((failure.retryAfterSeconds ?: MIN_REMOTE_RETRY_SECONDS).coerceIn(MIN_REMOTE_RETRY_SECONDS, MAX_RETRY_AFTER_SECONDS) * 1000L), retries = retries, error = failure.code.name), owner)
            } else {
                preserveRemote(row, owner, failure.code.name, retries)
            }
            ProviderErrorCode.NETWORK, ProviderErrorCode.SERVER -> if (retries <= MAX_NETWORK_RETRIES) {
                save(row.copy(state = ExecutionState.WAITING_NETWORK, nextAt = System.currentTimeMillis() + retryDelayMillis(MIN_REMOTE_RETRY_MS), retries = retries, error = failure.code.name), owner)
            } else {
                preserveRemote(row, owner, failure.code.name, retries)
            }
            ProviderErrorCode.REMOTE_FAILED, ProviderErrorCode.AUTHENTICATION, ProviderErrorCode.ACCESS_DENIED, ProviderErrorCode.QUOTA -> {
                if (failure.code == ProviderErrorCode.REMOTE_FAILED) {
                    clearRemoteSpool(row.id, submission.id)
                }
                preserveRemote(row, owner, failure.code.name)
            }
            ProviderErrorCode.INVALID_RESPONSE -> {
                clearRemoteSpool(row.id, submission.id)
                preserveRemote(row, owner, failure.code.name)
            }
            else -> preserveRemote(row, owner, failure.code.name)
        }
    }

    private suspend fun replayRemoteResponseFailure(
        row: AttemptRow,
        owner: String,
        submission: SubmissionRow,
        failure: ProviderError,
    ): AttemptRow {
        if (failure.code == ProviderErrorCode.REMOTE_FAILED || failure.code == ProviderErrorCode.INVALID_RESPONSE) {
            if (!deleteResponseSpool(row.id, submission.id)) {
                return preserveRemote(row, owner, "RESPONSE_STORAGE")
            }
            return preserveRemote(row, owner, failure.code.name)
        }
        return waitForUser(row, owner, "RESPONSE_${failure.code.name}")
    }

    private fun responseBelongsToRemote(provider: Provider, response: ByteArray, remoteId: String): Boolean {
        if (provider != Provider.ASSEMBLYAI) return true
        return responseRemoteId(response) == canonicalRemoteId(remoteId)
    }

    private fun responseRemoteId(response: ByteArray): String? = try {
        val objectValue = json.parseBounded(response.toString(StandardCharsets.UTF_8)) as? JsonObject
        (objectValue?.get("id") as? JsonPrimitive)?.content?.let(::canonicalRemoteId)
    } catch (_: Exception) {
        null
    }

    private fun deleteResponseSpool(attemptId: String, submissionId: String): Boolean {
        return try {
            val target = regularResponseFile(responsePath(attemptId, submissionId), attemptId)
            target == null || target.delete() && !target.exists()
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun clearRemoteSpool(attemptId: String, submissionId: String): Boolean =
        deleteResponseSpool(attemptId, submissionId)

    private suspend fun preserveRemote(
        row: AttemptRow,
        owner: String,
        error: String,
        retries: Int = row.retries,
    ): AttemptRow = save(row.copy(
        phase = Phase.RETRIEVE,
        state = ExecutionState.WAITING_USER,
        nextAt = 0,
        retries = retries,
        error = error,
    ), owner)

    private suspend fun responseParseFailure(
        row: AttemptRow,
        owner: String,
        submission: SubmissionRow,
        failure: ProviderError,
    ): AttemptRow {
        updateSubmissionClaimed(row, owner, submission.copy(state = SubmissionState.RESPONSE_SAVED))
        return save(row.copy(state = ExecutionState.WAITING_USER, phase = Phase.NORMALIZE, nextAt = 0, error = "${failure.code.name}_RAW_SAVED"), owner)
    }

    private fun existingAttemptDirectory(id: String): File? {
        if (canonicalUuid(id) == null || Files.isSymbolicLink(appContext.noBackupFilesDir.toPath())) return null
        return try {
            val noBackupRoot = appContext.noBackupFilesDir.canonicalFile
            val attemptsEntry = File(noBackupRoot, ATTEMPTS_DIRECTORY)
            if (Files.isSymbolicLink(attemptsEntry.toPath())) return null
            val attempts = attemptsEntry.canonicalFile
            if (attempts.parentFile?.path != noBackupRoot.path || !attempts.isDirectory) return null
            val entry = File(attempts, id)
            if (Files.isSymbolicLink(entry.toPath())) return null
            entry.canonicalFile.takeIf { it.parentFile?.path == attempts.path && it.isDirectory }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun targetAttemptEntry(id: String): File {
        if (canonicalUuid(id) == null || Files.isSymbolicLink(appContext.noBackupFilesDir.toPath())) {
            throw JobActionException("MISSING_RETRY_DATA")
        }
        return try {
            val noBackupRoot = appContext.noBackupFilesDir.canonicalFile
            val attemptsEntry = File(noBackupRoot, ATTEMPTS_DIRECTORY)
            if (Files.isSymbolicLink(attemptsEntry.toPath())) throw JobActionException("MISSING_RETRY_DATA")
            val attempts = attemptsEntry.canonicalFile
            if (attempts.parentFile?.path != noBackupRoot.path) throw JobActionException("MISSING_RETRY_DATA")
            File(attempts, id).also { entry ->
                if (entry.canonicalFile.parentFile?.path != attempts.path) throw JobActionException("MISSING_RETRY_DATA")
            }
        } catch (failure: JobActionException) {
            throw failure
        } catch (_: IOException) {
            throw JobActionException("MISSING_RETRY_DATA")
        } catch (_: SecurityException) {
            throw JobActionException("MISSING_RETRY_DATA")
        }
    }

    private fun existingResponseFile(previousDirectory: File, submission: SubmissionRow): File? {
        if (canonicalUuid(submission.id) == null) return null
        return try {
            val responseEntry = File(previousDirectory, RESPONSES_DIRECTORY)
            if (Files.isSymbolicLink(responseEntry.toPath())) return null
            val responseDirectory = responseEntry.canonicalFile
            if (responseDirectory.parentFile?.path != previousDirectory.path || !responseDirectory.isDirectory) return null
            val expected = File(responseDirectory, "${submission.id}.json")
            val declared = submission.rawResponsePath?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
            if (Files.isSymbolicLink(expected.toPath()) || Files.isSymbolicLink(declared.toPath()) ||
                expected.canonicalFile.path != declared.canonicalFile.path
            ) return null
            regularPrivateFile(expected, responseDirectory)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private suspend fun copyBounded(
        source: File,
        target: File,
        expectedBytes: Long,
        maximumBytes: Long,
        expectedSha256: String,
    ) = withContext(Dispatchers.IO) {
        if (expectedBytes !in 1..maximumBytes || target.exists() || Files.isSymbolicLink(target.toPath())) {
            throw IOException("invalid retry copy")
        }
        val parent = target.parentFile ?: throw IOException("retry target has no parent")
        if (Files.isSymbolicLink(parent.toPath()) || target.canonicalFile.parentFile?.path != parent.canonicalFile.path) {
            throw IOException("retry target escaped")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var copied = 0L
        FileInputStream(source).use { input ->
            java.io.FileOutputStream(target).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (copied > maximumBytes - count || copied > expectedBytes - count) {
                        throw IOException("retry source changed")
                    }
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    copied += count
                }
                output.flush()
                output.fd.sync()
            }
        }
        if (copied != expectedBytes || source.length() != expectedBytes || target.length() != expectedBytes ||
            digestHex(digest.digest()) != expectedSha256
        ) throw IOException("retry source changed")
    }

    private suspend fun cleanupRetryTarget(storage: StorageBudget, attemptId: String) {
        withContext(NonCancellable) {
            try {
                storage.deleteAttemptFiles(attemptId)
            } catch (_: Exception) {
                // The bounded no-follow cleanup is best effort; never touch the predecessor.
            }
        }
    }

    private fun retrySize(current: Long, addition: Long): Long {
        if (addition < 0 || current > Long.MAX_VALUE - addition) throw JobActionException("MISSING_RETRY_DATA")
        return current + addition
    }

    private suspend fun sourceRow(row: AttemptRow): SourceRow? = dao.job(row.jobId)?.let { dao.source(it.sourceId) }

    private suspend fun pinnedEngine(row: AttemptRow): EngineInstallation? {
        val id = row.engineId ?: return null
        return engines.installations().firstOrNull { it.id == id && it.healthy }
    }

    private fun validate(config: JobConfig, requireCredential: Boolean = true): Validated? {
        val provider = config.provider ?: return null
        val model = config.model?.takeIf { it.isNotBlank() } ?: return null
        if (!config.uploadApproved || config.credentialId.isNullOrBlank() || config.maxCostMicrousd?.let { it < 0 } == true) return null
        if (config.contextTerms.any { it.isBlank() } || config.language?.isBlank() == true) return null
        if (config.maxAudioSeconds !in 1..MAX_AUDIO_SECONDS) return null
        val adapter = adapter(provider)
        val capabilities = try {
            adapter.capabilities(model)
        } catch (_: ProviderError) {
            return null
        }
        if (capabilities.provider != provider || config.region !in capabilities.regions ||
            config.wordTimestamps && !capabilities.wordTimestamps ||
            config.segmentTimestamps && !capabilities.segmentTimestamps ||
            config.diarization && !capabilities.diarization ||
            config.contextTerms.isNotEmpty() && !capabilities.contextTerms ||
            config.language == null && !capabilities.automaticLanguage ||
            requireCredential && config.maxCostMicrousd != null && capabilities.priceMicrousdPerHour == null
        ) return null
        if (requireCredential) {
            val credential = try { credentials.list().firstOrNull { it.id == config.credentialId } } catch (_: CredentialException) { return null }
            if (credential?.provider != provider || credential.region != config.region) return null
        }
        return Validated(adapter, capabilities)
    }

    private fun validationError(config: JobConfig): String = when {
        config.provider == null -> "PROVIDER_REQUIRED"
        config.model.isNullOrBlank() -> "MODEL_REQUIRED"
        !config.uploadApproved -> "UPLOAD_APPROVAL_REQUIRED"
        config.credentialId.isNullOrBlank() -> "CREDENTIAL_REQUIRED"
        config.maxCostMicrousd?.let { it < 0 } == true -> "BUDGET_INVALID"
        config.maxAudioSeconds !in 1..MAX_AUDIO_SECONDS -> "AUDIO_DURATION_LIMIT"
        else -> "PROVIDER_CAPABILITY_OR_CREDENTIAL_INVALID"
    }

    private fun adapter(provider: Provider): ProviderAdapter = when (provider) {
        Provider.GROQ -> app.sourcescribe.core.providers.GroqAdapter(providerHttp)
        Provider.OPENAI -> app.sourcescribe.core.providers.OpenAiAdapter(providerHttp)
        Provider.ASSEMBLYAI -> app.sourcescribe.core.providers.AssemblyAiAdapter(providerHttp)
    }

    private fun request(attemptId: String, config: JobConfig, chunk: PreparedChunk): TranscriptionRequest =
        TranscriptionRequest(
            audio = File(attemptDirectory(attemptId), "audio-${chunk.index}.mp3"),
            mimeType = chunk.mimeType,
            config = config,
            chunkIndex = chunk.index,
            chunkStartMs = chunk.offsetMs,
            durationMs = chunk.durationMs,
        )

    private fun configHash(config: JobConfig): String =
        sha256(json.encodeToString(config).toByteArray(StandardCharsets.UTF_8))

    private suspend fun budgetError(jobId: String, currentId: String?, estimate: Long?, budget: Long?): String? {
        val limit = budget ?: return null
        val expected = estimate ?: return "PRICE_UNKNOWN"
        return database.withTransaction {
            val submissions = dao.submissionsForJob(jobId)
                .filter { it.state != SubmissionState.REJECTED && it.id != currentId }
            if (submissions.any { it.estimatedMicrousd?.let { estimate -> estimate < 0L } != false }) {
                "PRICE_UNKNOWN"
            } else {
                val total = submissions.fold(0L) { sum, submission ->
                    saturatedAdd(sum, submission.estimatedMicrousd ?: 0L)
                }
                if (total > limit - expected) "BUDGET_EXCEEDED" else null
            }
        }
    }

    private fun bindingMatches(submission: SubmissionRow, attemptId: String, config: JobConfig, chunk: PreparedChunk): Boolean =
        submission.attemptId == attemptId && submission.provider == requireNotNull(config.provider).name &&
            submission.credentialId == config.credentialId && submission.region == config.region.name &&
            submission.inputHash == chunk.sha256 && submission.configHash == configHash(config) &&
            responseBinding(submission, attemptId)

    /** A persisted response path is part of the charge/replay binding. */
    private fun responseBinding(submission: SubmissionRow, attemptId: String): Boolean {
        val declared = submission.rawResponsePath?.takeIf { it.isNotBlank() }?.let(::File) ?: return false
        val expected = try { responsePath(attemptId, submission.id) } catch (_: IOException) { return false }
        return try {
            !Files.isSymbolicLink(declared.toPath()) &&
                declared.canonicalFile.path == expected.path &&
                declared.parentFile?.canonicalFile?.path == expected.parentFile?.path
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun validHandle(handle: RemoteHandle, provider: Provider, config: JobConfig): Boolean =
        handle.provider == provider && handle.region == config.region && handle.id.isNotBlank()

    private fun maxDurationMs(config: JobConfig): Long = config.maxAudioSeconds.coerceAtMost(MAX_AUDIO_SECONDS) * 1000L

    private fun initialize(original: SttCheckpoint, source: Source): SttCheckpoint = original.copy(
        artifactId = canonicalUuid(original.artifactId) ?: UUID.randomUUID().toString(),
        artifactCreatedAt = original.artifactCreatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        source = source,
    )

    /**
     * A stored checkpoint that no longer parses is damaged local data. It carries its own type rather than
     * a provider error, because a provider error tells the reader that a request was sent to a provider.
     */
    private class CheckpointDamaged : IllegalStateException("checkpoint unreadable")

    private fun checkpoint(value: String): SttCheckpoint = try {
        if (value.length > MAX_CHECKPOINT_BYTES) throw SerializationException("checkpoint too large")
        json.decodeFromString<SttCheckpoint>(value).also(::validateCheckpoint)
    } catch (_: Exception) {
        throw CheckpointDamaged()
    }

    private fun validateCheckpoint(checkpoint: SttCheckpoint) {
        if (checkpoint.artifactId.isNotBlank() && canonicalUuid(checkpoint.artifactId) == null) {
            throw SerializationException("invalid artifact id")
        }
        if (checkpoint.artifactCreatedAt < 0 || checkpoint.nextChunkIndex < 0 || checkpoint.chunkCount < 0) {
            throw SerializationException("invalid checkpoint")
        }
        if (checkpoint.rawExtension != null && checkpoint.rawExtension !in RAW_PROVIDER_EXTENSIONS) {
            throw SerializationException("invalid raw provider format")
        }
        if (checkpoint.sourceAudioSha256 != null && !SHA256_PATTERN.matches(checkpoint.sourceAudioSha256)) {
            throw SerializationException("invalid source audio hash")
        }
        if (checkpoint.sourceAudioTrackId != null && checkpoint.sourceAudioTrackId.isBlank()) {
            throw SerializationException("invalid source audio track")
        }
        if (checkpoint.reusedArtifactId != null && canonicalUuid(checkpoint.reusedArtifactId) == null) {
            throw SerializationException("invalid reused artifact id")
        }
        if ((checkpoint.sourceAudioSha256 == null) != (checkpoint.sourceAudioTrackId == null)) {
            throw SerializationException("incomplete source audio binding")
        }
        val duration = checkpoint.durationMs
        if (duration == null) {
            if (checkpoint.chunkCount != 0 || checkpoint.nextChunkIndex != 0 || checkpoint.prepared.isNotEmpty() || checkpoint.rawExtension != null) {
                throw SerializationException("incomplete checkpoint")
            }
            return
        }
        val windows = try { chunkPlan(duration) } catch (_: IllegalArgumentException) {
            throw SerializationException("invalid duration")
        }
        if (checkpoint.chunkCount != windows.size || checkpoint.nextChunkIndex > checkpoint.chunkCount) {
            throw SerializationException("invalid chunk plan")
        }
        val expectedPrepared = (0 until checkpoint.nextChunkIndex).toSet()
        if (checkpoint.prepared.size != expectedPrepared.size ||
            checkpoint.prepared.map { it.index }.toSet() != expectedPrepared
        ) {
            throw SerializationException("incomplete prepared chunks")
        }
        checkpoint.prepared.forEach { prepared ->
            val window = windows[prepared.index]
            val durationDelta = prepared.durationMs - window.durationMs
            if (prepared.offsetMs != window.offsetMs || durationDelta !in -PREPARED_DURATION_TOLERANCE_MS..PREPARED_DURATION_TOLERANCE_MS ||
                prepared.durationMs !in 1..MAX_CHUNK_DURATION_MS ||
                prepared.bytes !in 1..MAX_CHUNK_BYTES || !SHA256_PATTERN.matches(prepared.sha256) ||
                prepared.mimeType != AUDIO_MIME_TYPE
            ) {
                throw SerializationException("invalid prepared chunk")
            }
        }
        if (checkpoint.missingChunks.any { it !in 0 until checkpoint.chunkCount } ||
            checkpoint.missingChunks.size != checkpoint.missingChunks.toSet().size
        ) {
            throw SerializationException("invalid missing chunk list")
        }
    }

    private fun decodeSource(value: String): Source? = try { json.decodeFromString<Source>(value) } catch (_: Exception) { null }

    private suspend fun currentRow(row: AttemptRow, owner: String): AttemptRow =
        dao.attempt(row.id)?.takeIf { it.leaseOwner == owner } ?: row

    private suspend fun ensureFence(row: AttemptRow, owner: String) {
        val held = database.withTransaction { fenceHolds(row, owner) }
        if (!held) throw CancellationException("LEASE_LOST")
        currentCoroutineContext().ensureActive()
    }

    private suspend fun fenceHolds(row: AttemptRow, owner: String): Boolean =
        dao.attempt(row.id)?.let { attempt ->
            attempt.leaseOwner == owner && attempt.leaseUntil > System.currentTimeMillis()
        } == true && dao.job(row.jobId)?.cancelRequested == false

    private suspend fun requireFenceInTransaction(row: AttemptRow, owner: String) {
        if (!fenceHolds(row, owner)) throw CancellationException("LEASE_LOST")
    }

    private suspend fun updateSubmissionClaimed(row: AttemptRow, owner: String, submission: SubmissionRow) {
        database.withTransaction {
            requireFenceInTransaction(row, owner)
            dao.updateSubmission(submission)
        }
    }

    private suspend fun markSendingUncertain(row: AttemptRow, owner: String) {
        database.withTransaction {
            if (!fenceHolds(row, owner)) return@withTransaction
            dao.submissions(row.id)
                .filter { it.state == SubmissionState.SENDING }
                .forEach { dao.updateSubmission(it.copy(state = SubmissionState.UNCERTAIN)) }
        }
    }

    private suspend fun save(row: AttemptRow, owner: String): AttemptRow {
        database.withTransaction {
            requireFenceInTransaction(row, owner)
            dao.updateAttempt(row)
        }
        return row
    }

    private suspend fun waitForUser(row: AttemptRow, owner: String, error: String): AttemptRow =
        save(row.copy(state = ExecutionState.WAITING_USER, nextAt = 0, error = error), owner)

    private fun importedFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        return try {
            val noBackupEntry = appContext.noBackupFilesDir
            if (Files.isSymbolicLink(noBackupEntry.toPath())) return null
            val noBackupRoot = noBackupEntry.canonicalFile
            val importsEntry = File(noBackupRoot, "imports")
            if (Files.isSymbolicLink(importsEntry.toPath()) ||
                importsEntry.exists() && !importsEntry.isDirectory
            ) return null
            val importsRoot = importsEntry.canonicalFile
            if (importsRoot.parentFile?.path != noBackupRoot.path) return null
            val file = File(path).canonicalFile
            if (file.parentFile?.path != importsRoot.path) return null
            file.takeIf { it.isFile && it.canRead() && !Files.isSymbolicLink(it.toPath()) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private suspend fun sourceMatchesInput(source: Source, input: File): Boolean {
        val expected = source.contentHash ?: return false
        if (source.fileBytes != null && source.fileBytes != input.length()) return false
        return try {
            withContext(Dispatchers.IO) { sha256(input) == expected }
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private suspend fun sourceAudioMatches(checkpoint: SttCheckpoint, input: File): Boolean {
        val expected = checkpoint.sourceAudioSha256 ?: return false
        if (checkpoint.sourceAudioTrackId.isNullOrBlank()) return false
        return try {
            withContext(Dispatchers.IO) { sha256(input) == expected }
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private suspend fun retrySourceBindingMatches(
        source: Source,
        checkpoint: SttCheckpoint,
        directory: File,
    ): Boolean {
        if (source.kind == SourceKind.LOCAL_AUDIO) {
            return checkpoint.audio == null && checkpoint.sourceAudioTrackId == null &&
                checkpoint.sourceAudioSha256 == null
        }
        val audio = checkpoint.audio ?: return false
        if (audio.id != checkpoint.sourceAudioTrackId || audio.sourceVideoId != source.videoId) return false
        val sourceAudio = File(directory, SOURCE_AUDIO_NAME)
        if (!sourceAudio.exists() && !Files.isSymbolicLink(sourceAudio.toPath())) return true
        return regularPrivateFile(sourceAudio, directory) != null && sourceAudioMatches(checkpoint, sourceAudio)
    }

    private suspend fun preparedFileMatches(chunk: PreparedChunk, attemptId: String): Boolean {
        if (chunk.mimeType != AUDIO_MIME_TYPE) return false
        return try {
            val directory = attemptDirectory(attemptId)
            val file = regularPrivateFile(File(directory, "audio-${chunk.index}.mp3"), directory) ?: return false
            val length = file.length()
            if (length != chunk.bytes || length !in 1..MAX_CHUNK_BYTES) return false
            val digest = withContext(Dispatchers.IO) { sha256(file) }
            digest == chunk.sha256 && file.length() == length
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun reconcileOrphans(directory: File, checkpoint: SttCheckpoint): Boolean {
        val prepared = checkpoint.prepared.mapTo(HashSet()) { it.index }
        val count = checkpoint.chunkCount
        val entries = directory.listFiles() ?: return false
        for (entry in entries) {
            val match = ORPHAN_AUDIO_PATTERN.matchEntire(entry.name) ?: continue
            val index = match.groupValues[1].toIntOrNull() ?: return false
            if (index !in 0 until count) return false
            if (index in prepared) continue
            if (Files.isSymbolicLink(entry.toPath()) || regularPrivateFile(entry, directory) == null) return false
            if (!entry.delete() && entry.exists()) return false
        }
        return true
    }

    private fun attemptDirectory(id: String): File {
        if (canonicalUuid(id) == null) throw IOException("invalid attempt id")
        if (Files.isSymbolicLink(appContext.noBackupFilesDir.toPath())) throw IOException("private root is symbolic link")
        val rootEntry = File(appContext.noBackupFilesDir, ATTEMPTS_DIRECTORY)
        if (Files.isSymbolicLink(rootEntry.toPath())) throw IOException("attempt directory is symbolic link")
        val noBackupRoot = try { appContext.noBackupFilesDir.canonicalFile } catch (_: IOException) { throw IOException("private root unavailable") }
        val root = rootEntry.canonicalFile
        if (root.parentFile?.path != noBackupRoot.path) throw IOException("attempt root escaped")
        if (!root.exists() && !root.mkdirs()) throw IOException("attempt directory unavailable")
        if (!root.isDirectory) throw IOException("attempt root unavailable")
        val entry = File(root, id)
        if (Files.isSymbolicLink(entry.toPath())) throw IOException("attempt entry is symbolic link")
        val directory = entry.canonicalFile
        if (directory.parentFile?.path != root.path) throw IOException("attempt path escaped")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("attempt directory unavailable")
        if (!directory.isDirectory) throw IOException("attempt directory unavailable")
        return directory
    }

    private fun responseDirectory(attemptId: String): File {
        val attempt = attemptDirectory(attemptId)
        val responseEntry = File(attempt, RESPONSES_DIRECTORY)
        if (Files.isSymbolicLink(responseEntry.toPath())) throw IOException("response directory is symbolic link")
        if (!responseEntry.exists() && !responseEntry.mkdirs()) throw IOException("response directory unavailable")
        val directory = responseEntry.canonicalFile
        if (directory.parentFile?.path != attempt.path || !directory.isDirectory) throw IOException("response path escaped")
        return directory
    }

    private fun responsePath(attemptId: String, submissionId: String): File {
        if (canonicalUuid(submissionId) == null) throw IOException("invalid submission id")
        val directory = responseDirectory(attemptId)
        val target = File(directory, "$submissionId.json")
        if (Files.isSymbolicLink(target.toPath())) throw IOException("response target is symbolic link")
        return target.canonicalFile
    }

    private fun regularResponseFile(file: File, attemptId: String): File? =
        regularPrivateFile(file, responseDirectory(attemptId))

    private fun rawProviderName(extension: String): String = when (extension) {
        "json" -> RAW_PROVIDER_JSON_NAME
        "zip" -> RAW_PROVIDER_ZIP_NAME
        else -> throw IOException("unsupported raw provider format")
    }

    private fun regularPrivateFile(file: File, directory: File): File? {
        val canonical = try { file.canonicalFile } catch (_: IOException) { return null } catch (_: SecurityException) { return null }
        val parent = try { directory.canonicalFile } catch (_: IOException) { return null } catch (_: SecurityException) { return null }
        return canonical.takeIf {
            it.parentFile?.path == parent.path &&
                it.isFile && it.canRead() && !Files.isSymbolicLink(it.toPath())
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            if (target.exists()) throw IOException("target exists")
            if (Files.isSymbolicLink(target.toPath())) throw IOException("target is symbolic link")
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            throw IOException("atomic audio move unavailable")
        } catch (_: SecurityException) {
            throw IOException("audio move denied")
        }
    }

    private suspend fun readBounded(file: File, maxBytes: Int = MAX_RESPONSE_BYTES): ByteArray = withContext(Dispatchers.IO) {
        require(maxBytes in 1..ArtifactFiles.MAX_CANONICAL_BYTES)
        if (file.length() > maxBytes) throw IOException("response too large")
        file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream(file.length().toInt().coerceAtMost(maxBytes))
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() > maxBytes - count) throw IOException("response too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val directory = target.parentFile ?: throw IOException("missing parent")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("directory unavailable")
        val temporary = File.createTempFile(".stt-", ".tmp", directory)
        var committed = false
        try {
            java.io.FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            committed = true
        } catch (_: AtomicMoveNotSupportedException) {
            throw IOException("atomic response write unavailable")
        } finally {
            if (!committed) temporary.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digestHex(digest.digest())
    }

    private fun sha256(bytes: ByteArray): String =
        digestHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun digestHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(Locale.ROOT, it) }

    private fun encode(value: Any): String = when (value) {
        is SttCheckpoint -> json.encodeToString(value)
        is TranscriptDocument -> json.encodeToString(value)
        else -> error("unsupported checkpoint value")
    }

    private data class Validated(val adapter: ProviderAdapter, val capabilities: ProviderCapabilities)

    @Serializable
    private data class SttCheckpoint(
        val artifactId: String = "",
        val artifactCreatedAt: Long = 0,
        val source: Source? = null,
        val audio: AudioTrack? = null,
        val durationMs: Long? = null,
        val chunkCount: Int = 0,
        val nextChunkIndex: Int = 0,
        val prepared: List<PreparedChunk> = emptyList(),
        val missingChunks: List<Int> = emptyList(),
        val normalized: Boolean = false,
        val rawExtension: String? = null,
        val sourceAudioTrackId: String? = null,
        val sourceAudioSha256: String? = null,
        val reusedArtifactId: String? = null,
        val engineVersions: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class PreparedChunk(
        val index: Int,
        val offsetMs: Long,
        val durationMs: Long,
        val bytes: Long,
        val sha256: String,
        val mimeType: String,
    )

    private data class ReusableResponse(
        val submission: SubmissionRow,
        val file: File,
        val bytes: Long,
        val sha256: String,
    )

    private inner class AtomicResponseSpool(private val target: File) : ResponseSpool {
        override fun save(bytes: ByteArray) {
            if (bytes.size > MAX_RESPONSE_BYTES) throw IOException("response too large")
            writeAtomically(target, bytes)
        }
    }

    companion object {
        const val MAX_CHUNK_DURATION_MS = 600_000L
        const val MAX_AUDIO_SECONDS = JobLimits.MAX_AUDIO_SECONDS
        const val MAX_AUDIO_DURATION_MS = MAX_AUDIO_SECONDS * 1000L
        const val MAX_CHUNK_BYTES = 24_000_000L
        const val MIN_REMOTE_RETRY_SECONDS = 30L
        const val MIN_FINAL_CHUNK_DURATION_MS = 160L
        private const val PREPARED_DURATION_TOLERANCE_MS = 250L

        data class RawResponse(val index: Int, val bytes: ByteArray)
        data class RawPayload(val bytes: ByteArray, val extension: String)

        /** Keeps each provider response byte-for-byte intact; ZIP is used only for multiple responses. */
        fun rawPayload(responses: List<RawResponse>): RawPayload? {
            if (responses.isEmpty()) return null
            var total = 0L
            val indices = HashSet<Int>()
            for (response in responses) {
                if (response.index < 0 || !indices.add(response.index)) return null
                if (total > MAX_RAW_NORMALIZATION_BYTES - response.bytes.size) return null
                total += response.bytes.size
            }
            if (responses.size == 1) return RawPayload(responses.single().bytes, "json")
            val output = java.io.ByteArrayOutputStream(total.toInt())
            java.util.zip.ZipOutputStream(output).use { zip ->
                responses.forEach { response ->
                    zip.putNextEntry(java.util.zip.ZipEntry("chunk-${response.index}.json"))
                    zip.write(response.bytes)
                    zip.closeEntry()
                }
            }
            return output.toByteArray()
                .takeIf { it.size.toLong() <= MAX_RAW_NORMALIZATION_BYTES }
                ?.let { RawPayload(it, "zip") }
        }

        fun chunkPlan(durationMs: Long, maxChunkMs: Long = MAX_CHUNK_DURATION_MS): List<ChunkWindow> {
            require(durationMs in 1..MAX_AUDIO_DURATION_MS && maxChunkMs in 1..MAX_CHUNK_DURATION_MS)
            val count = ((durationMs - 1) / maxChunkMs + 1).toInt()
            val residual = durationMs - (count - 1).toLong() * maxChunkMs
            val shortFinal = count > 1 && residual < MIN_FINAL_CHUNK_DURATION_MS &&
                durationMs - MIN_FINAL_CHUNK_DURATION_MS >= (count - 2).toLong() * maxChunkMs
            val finalOffset = if (shortFinal) durationMs - MIN_FINAL_CHUNK_DURATION_MS else (count - 1).toLong() * maxChunkMs
            return (0 until count).map { index ->
                val offset = if (shortFinal && index == count - 1) finalOffset else index.toLong() * maxChunkMs
                val end = when {
                    index == count - 1 -> durationMs
                    shortFinal && index == count - 2 -> finalOffset
                    else -> (index + 1).toLong() * maxChunkMs
                }
                ChunkWindow(index, offset, end - offset)
            }
        }

        fun estimateCostMicrousd(capabilities: ProviderCapabilities, config: JobConfig, durationMs: Long): Long? {
            require(durationMs > 0)
            val base = capabilities.priceMicrousdPerHour ?: return null
            var hourly = base
            if (capabilities.provider == Provider.ASSEMBLYAI && config.diarization) {
                hourly = saturatedAdd(hourly, app.sourcescribe.core.providers.AssemblyAiAdapter.SPEAKER_LABELS_MICRO_USD_PER_HOUR)
            }
            // Only Universal-3.5 Pro is charged for a keyterms prompt; Universal-2 has it included in its
            // base rate, which is what the provider's add-on table says in the column beside it.
            if (capabilities.provider == Provider.ASSEMBLYAI &&
                config.model == app.sourcescribe.core.providers.AssemblyAiAdapter.MODEL_U35 &&
                config.contextTerms.isNotEmpty()
            ) {
                hourly = saturatedAdd(hourly, app.sourcescribe.core.providers.AssemblyAiAdapter.KEYTERMS_U35_MICRO_USD_PER_HOUR)
            }
            val minimum = capabilities.minimumBilledSeconds.toLong().coerceAtLeast(0) * 1000L
            val billedMs = maxOf(durationMs, minimum)
            if (hourly <= 0 || billedMs > Long.MAX_VALUE / hourly) return Long.MAX_VALUE
            val product = billedMs * hourly
            return product / 3_600_000L + if (product % 3_600_000L == 0L) 0 else 1
        }

        fun providerResource(provider: Provider): String = "provider:${provider.name.lowercase(Locale.ROOT)}"

        data class ChunkWindow(val index: Int, val offsetMs: Long, val durationMs: Long)

        private const val LEASE_MS = 9 * 60_000L
        private const val RESOLVE_TIMEOUT_MS = 6 * 60_000L
        private const val DOWNLOAD_TIMEOUT_MS = 6 * 60_000L
        private const val PREPARE_TIMEOUT_MS = 6 * 60_000L
        private const val SUBMIT_TIMEOUT_MS = 6 * 60_000L
        private const val POLL_TIMEOUT_MS = 6 * 60_000L
        private const val MIN_REMOTE_RETRY_MS = MIN_REMOTE_RETRY_SECONDS * 1000L
        private const val RESOURCE_RETRY_MS = MIN_REMOTE_RETRY_MS
        private const val MAX_NETWORK_RETRIES = 3
        private const val MAX_RETRY_AFTER_SECONDS = 86_400L
        private const val REMOTE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        private const val MAX_RAW_NORMALIZATION_BYTES = ArtifactFiles.MAX_RAW_BYTES.toLong()
        private const val MAX_CHECKPOINT_BYTES = 2 * 1024 * 1024
        private const val MAX_SOURCE_AUDIO_BYTES = 2L * 1024 * 1024 * 1024
        private const val ATTEMPTS_DIRECTORY = "attempts"
        private const val RESPONSES_DIRECTORY = "responses"
        private const val AUDIO_RESOURCE = "audio-cpu"
        private const val SOURCE_AUDIO_NAME = "source.audio"
        private const val DOWNLOADING_AUDIO_NAME = "audio.downloading"
        private const val NORMALIZED_NAME = "normalized.json"
        private const val RAW_PROVIDER_JSON_NAME = "raw.provider.json"
        private const val RAW_PROVIDER_ZIP_NAME = "raw.provider.zip"
        private const val AUDIO_MIME_TYPE = "audio/mpeg"
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val AAI_UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val RAW_PROVIDER_EXTENSIONS = setOf("json", "zip")
        private val ORPHAN_AUDIO_PATTERN = Regex("audio-([0-9]+)\\.mp3")
        private val TERMINAL_STATES = setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED)
        private val TERMINAL_SUBMISSION_STATES = setOf(
            SubmissionState.RESPONSE_SAVED,
            SubmissionState.REJECTED,
            SubmissionState.REMOTE_DELETED,
        )
        private val KNOWN_REJECTION_CODES = setOf(
            ProviderErrorCode.AUTHENTICATION,
            ProviderErrorCode.ACCESS_DENIED,
            ProviderErrorCode.INVALID_INPUT,
            ProviderErrorCode.UNSUPPORTED_OPTION,
            ProviderErrorCode.QUOTA,
        )

        private fun canonicalUuid(value: String): String? = try {
            UUID.fromString(value).toString().takeIf { it == value }
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun canonicalRemoteId(value: String): String? {
            if (!AAI_UUID_PATTERN.matches(value)) return null
            return try {
                UUID.fromString(value).toString()
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun saturatedAdd(first: Long, second: Long): Long =
            if (second > 0 && first > Long.MAX_VALUE - second) Long.MAX_VALUE else first + second
    }
}
