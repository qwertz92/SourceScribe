package app.sourcescribe.extractor

import android.content.Context
import android.util.AtomicFile
import app.sourcescribe.core.EngineVerificationException
import app.sourcescribe.core.EngineVerifier
import app.sourcescribe.core.ExtractorMetadata
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.SourceResolver
import app.sourcescribe.core.boundedJsonText
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class EngineChannel { STABLE, NIGHTLY }

enum class EngineUpdateCode {
    NETWORK,
    RATE_LIMIT,
    STORAGE,
    VERIFICATION,
    REQUIRES_APP_UPDATE,
    PROBE_FAILED,
    UNCERTAIN_PROBE,
    NO_PREVIOUS,
}

internal enum class EngineHttpResponseKind { NOT_MODIFIED, REDIRECT, SUCCESS, OTHER }

internal fun classifyHttpResponse(code: Int): EngineHttpResponseKind = when {
    code == 304 -> EngineHttpResponseKind.NOT_MODIFIED
    code in 300..399 -> EngineHttpResponseKind.REDIRECT
    code in 200..299 -> EngineHttpResponseKind.SUCCESS
    else -> EngineHttpResponseKind.OTHER
}

class EngineUpdateException(
    val code: EngineUpdateCode,
    val retryAfterSeconds: Long? = null,
) : IOException("engine_update:${code.name}")

/** A release discovered from the fixed yt-dlp GitHub repositories. */
data class AvailableEngine(
    val id: String,
    val version: String,
    val ejsVersion: String? = null,
    val channel: EngineChannel,
    val gitHead: String? = null,
    val sha256: String? = null,
    internal val artifactUrl: String = "",
    internal val checksumUrl: String = "",
    internal val signatureUrl: String = "",
)

/** An immutable engine slot. The file returned by [EngineUpdateManager.file] never moves. */
data class EngineInstallation(
    val id: String,
    val version: String,
    val ejsVersion: String,
    val channel: EngineChannel,
    val gitHead: String?,
    val sha256: String,
    val healthy: Boolean,
    val bundled: Boolean,
)

/**
 * Manages the bundled and manually staged yt-dlp zipapps.
 *
 * A slot is addressed by its complete SHA-256 and is never overwritten. State is
 * the only mutable pointer, written through Android's crash-safe AtomicFile.
 */
class EngineUpdateManager internal constructor(
    context: Context,
    private val runtime: NativeRuntime,
    private val calls: Call.Factory,
    private val writeDownloadedFile: (File, ByteArray) -> Unit,
) {
    constructor(context: Context, runtime: NativeRuntime = NativeRuntime(context)) : this(
        context,
        runtime,
        defaultClient(),
        ::writeSyncedBytes,
    )

    private val appContext = context.applicationContext
    // This per-instance mutex is sufficient while Hilt supplies the manager as a singleton.
    private val mutex = Mutex()
    private var loadedState: ManagerState? = null
    private var bundledCache: EngineInstallation? = null

    suspend fun bundled(): EngineInstallation = mutex.withLock { ensureBundledLocked() }

    suspend fun active(): EngineInstallation = mutex.withLock {
        val bundled = ensureBundledLocked()
        val state = stateLocked()
        val active = state.activeId?.let { state.installations[it] }
            ?.takeIf { it.healthy && state.healthyIds.contains(it.id) && validSlot(it) }
        if (active != null) return@withLock active
        if (state.activeId != bundled.id) {
            state.activeId = bundled.id
            state.previousId = null
            saveStateLocked(state)
        }
        bundled
    }

    suspend fun check(channel: EngineChannel, force: Boolean = false): AvailableEngine? = mutex.withLock {
        val state = stateLocked()
        val now = System.currentTimeMillis()
        if (now < state.nextAllowedMs) {
            throw EngineUpdateException(
                EngineUpdateCode.RATE_LIMIT,
                ((state.nextAllowedMs - now) / 1000L).coerceAtLeast(1L),
            )
        }
        val cached = state.cached?.takeIf { it.channel == channel }
        if (!force && cached != null && now - state.lastCheckedMs < CHECK_INTERVAL_MS) {
            return@withLock cached.toAvailable().takeIf { !isInstalled(state, it) }
        }

        val endpoint = releaseEndpoint(channel)
        val cachedEtag = state.etag.takeIf { state.etagChannel == channel }
        val result = try {
            fetch(endpoint, API_RESPONSE_BYTES, cachedEtag)
        } catch (failure: EngineUpdateException) {
            if (failure.code == EngineUpdateCode.RATE_LIMIT) {
                state.nextAllowedMs = now + (failure.retryAfterSeconds ?: DEFAULT_RETRY_SECONDS) * 1000L
                saveStateLocked(state)
            }
            throw failure
        }

        val release = if (result.status == HTTP_NOT_MODIFIED) {
            cached ?: throw EngineUpdateException(EngineUpdateCode.NETWORK)
        } else {
            parseRelease(result.body, channel)
        }
        state.lastCheckedMs = now
        state.nextAllowedMs = 0L
        state.etag = if (result.status == HTTP_NOT_MODIFIED) {
            result.etag?.take(MAX_ETAG_CHARS) ?: state.etag
        } else {
            result.etag?.take(MAX_ETAG_CHARS)
        }
        state.etagChannel = channel
        state.cached = release
        saveStateLocked(state)
        release.toAvailable().takeIf { !isInstalled(state, it) }
    }

    suspend fun stage(update: AvailableEngine): EngineInstallation = mutex.withLock {
        val urls = releaseUrls(update)
        ensureBundledLocked()
        val engines = enginesDirectory()
        if (slotDirectories(engines).size >= MAX_INSTALLATIONS) {
            throw EngineUpdateException(EngineUpdateCode.STORAGE)
        }
        val staging = File(engines, ".staging-${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw EngineUpdateException(EngineUpdateCode.STORAGE)
        var candidate: EngineInstallation? = null
        var state: ManagerState? = null
        var createdSlot = false
        var recordAdded = false
        try {
            val artifact = File(staging, YTDLP_FILE_NAME)
            val checksums = downloadBytes(urls.checksum, EngineVerifier.MAX_CHECKSUM_BYTES)
            val signature = downloadBytes(urls.signature, EngineVerifier.MAX_SIGNATURE_BYTES)
            downloadFile(urls.artifact, artifact, EngineVerifier.MAX_ARTIFACT_BYTES.toLong())
            val verified = try {
                EngineVerifier.verify(artifact, checksums, signature)
            } catch (failure: EngineVerificationException) {
                throw verificationFailure(failure)
            }
            validateReleaseBinding(update, verified)

            val loaded = stateLocked()
            state = loaded
            val installation = EngineInstallation(
                id = verified.sha256,
                version = verified.version,
                ejsVersion = verified.ejsVersion,
                channel = channelOf(verified.channel),
                gitHead = verified.gitHead,
                sha256 = verified.sha256,
                healthy = false,
                bundled = false,
            )
            candidate = installation
            createdSlot = materializeSlot(artifact, installation)
            if (loaded.installations[installation.id] == null) {
                loaded.installations[installation.id] = installation
                recordAdded = true
                saveStateLocked(loaded)
            }

            verifyRuntimeCompatibility(installation)
            val healthy = installation.copy(
                healthy = true,
                bundled = loaded.installations[installation.id]?.bundled == true || installation.bundled,
            )
            loaded.installations[healthy.id] = healthy
            loaded.healthyIds += healthy.id
            saveStateLocked(loaded)
            healthy
        } catch (failure: Exception) {
            val failedState = state
            val failedCandidate = candidate
            if (failedState != null && failedCandidate != null) {
                cleanupFailedStage(failedState, failedCandidate, createdSlot, recordAdded)
            }
            throw failure
        } finally {
            deleteOwnedTree(staging)
        }
    }

    suspend fun activate(id: String, probeSource: Source): EngineInstallation = mutex.withLock {
        requireHashId(id)
        val source = normalizedProbeSource(probeSource)
        ensureBundledLocked()
        val state = stateLocked()
        val candidate = state.installations[id]
            ?.takeIf { it.healthy && state.healthyIds.contains(it.id) && validSlot(it) }
            ?: throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        val candidateFile = file(candidate)
        verifyRuntimeCompatibility(candidate)
        val result = try {
            runtime.ytDlp(
                listOf("--dump-single-json", "--skip-download", "--", requireNotNull(source.canonicalUrl)),
                timeoutSeconds = 120L,
                engine = candidateFile,
            )
        } catch (failure: NativeRuntimeException) {
            throw if (failure.code == RuntimeFailureCode.TIMED_OUT) {
                EngineUpdateException(EngineUpdateCode.UNCERTAIN_PROBE)
            } else {
                EngineUpdateException(EngineUpdateCode.PROBE_FAILED)
            }
        }
        if (result.exitCode != 0) throw probeFailure(result.stderr)
        try {
            ExtractorMetadata.parse(result.stdout.trim(), source)
        } catch (_: Exception) {
            throw EngineUpdateException(EngineUpdateCode.PROBE_FAILED)
        }

        val current = state.activeId
        if (current != id) state.previousId = current?.takeIf { healthySlot(state, it) != null }
        state.activeId = id
        saveStateLocked(state)
        candidate
    }

    suspend fun rollback(): EngineInstallation = mutex.withLock {
        val bundled = ensureBundledLocked()
        val state = stateLocked()
        val current = state.activeId
        val target = state.previousId?.let { state.installations[it] }
            ?.takeIf { it.healthy && state.healthyIds.contains(it.id) && validSlot(it) }
            ?: bundled.takeIf { it.id != current }
            ?: throw EngineUpdateException(EngineUpdateCode.NO_PREVIOUS)
        if (current != target.id) state.previousId = current?.takeIf { healthySlot(state, it) != null }
        state.activeId = target.id
        saveStateLocked(state)
        target
    }

    suspend fun installations(): List<EngineInstallation> = mutex.withLock {
        ensureBundledLocked()
        stateLocked().installations.values.filter(::validSlot).sortedBy { it.id }
    }

    fun file(installation: EngineInstallation): File {
        requireHashId(installation.id)
        val root = enginesDirectory()
        val slot = File(root, installation.id).canonicalFile
        if (slot.parentFile != root.canonicalFile || !validSlot(installation)) {
            throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        }
        return File(slot, YTDLP_FILE_NAME)
    }

    suspend fun discardUnhealthyCandidate(id: String): Boolean = mutex.withLock {
        requireHashId(id)
        val state = stateLocked()
        val installation = state.installations[id] ?: return@withLock false
        if (installation.healthy || installation.bundled || state.healthyIds.contains(id) ||
            state.activeId == id || state.previousId == id || bundledCache?.id == id
        ) {
            throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        }
        state.installations.remove(id)
        state.healthyIds.remove(id)
        saveStateLocked(state)
        deleteHashSlot(enginesDirectory(), id)
        true
    }

    private suspend fun ensureBundledLocked(): EngineInstallation {
        bundledCache?.let { cached ->
            if (cached.healthy && cached.bundled && validSlot(cached)) return cached
            bundledCache = null
        }
        val state = stateLocked()

        val staging = File(enginesDirectory(), ".bundled-${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw EngineUpdateException(EngineUpdateCode.STORAGE)
        return try {
            val artifact = File(staging, YTDLP_FILE_NAME)
            copyResource(R.raw.ytdlp, artifact, EngineVerifier.MAX_ARTIFACT_BYTES)
            val checksums = resourceBytes(R.raw.ytdlp_checksums, EngineVerifier.MAX_CHECKSUM_BYTES)
            val signature = resourceBytes(R.raw.ytdlp_checksums_sig, EngineVerifier.MAX_SIGNATURE_BYTES)
            val verified = try {
                EngineVerifier.verify(artifact, checksums, signature)
            } catch (failure: EngineVerificationException) {
                throw verificationFailure(failure)
            }
            val installation = EngineInstallation(
                id = verified.sha256,
                version = verified.version,
                ejsVersion = verified.ejsVersion,
                channel = channelOf(verified.channel),
                gitHead = verified.gitHead,
                sha256 = verified.sha256,
                healthy = false,
                bundled = true,
            )
            materializeSlot(artifact, installation)
            if (!validSlot(installation)) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            val existing = state.installations[installation.id]
            val existingHealthy = existing?.healthy == true && state.healthyIds.contains(installation.id) &&
                validSlot(existing)
            if (!existingHealthy) {
                state.installations[installation.id] = installation
                saveStateLocked(state)
            }
            verifyRuntimeCompatibility(installation)
            val healthy = (existing ?: installation).copy(healthy = true, bundled = true)
            state.installations[healthy.id] = healthy
            state.healthyIds += healthy.id
            if (state.activeId == null) state.activeId = healthy.id
            saveStateLocked(state)
            bundledCache = healthy
            healthy
        } finally {
            deleteOwnedTree(staging)
        }
    }

    private suspend fun verifyRuntimeCompatibility(installation: EngineInstallation) {
        val versions = try {
            runtime.initialize()
            runtime.versions(file(installation))
        } catch (failure: NativeRuntimeException) {
            throw EngineUpdateException(EngineUpdateCode.PROBE_FAILED).also { it.initCause(failure) }
        }
        if (versions["yt-dlp"] != installation.version || versions["ejs"] != installation.ejsVersion) {
            throw EngineUpdateException(EngineUpdateCode.REQUIRES_APP_UPDATE)
        }
        return Unit
    }

    private fun materializeSlot(artifact: File, installation: EngineInstallation): Boolean {
        val root = enginesDirectory()
        requireHashId(installation.id)
        val destination = File(root, installation.id)
        if (isSymbolicLink(destination)) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        if (destination.exists()) {
            if (!validSlot(installation)) {
                throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            }
            return false
        }
        if (slotDirectories(root).size >= MAX_INSTALLATIONS) throw EngineUpdateException(EngineUpdateCode.STORAGE)
        val temporary = File(root, ".slot-${UUID.randomUUID()}")
        if (!temporary.mkdirs()) throw EngineUpdateException(EngineUpdateCode.STORAGE)
        try {
            val target = File(temporary, YTDLP_FILE_NAME)
            copyFile(artifact, target, EngineVerifier.MAX_ARTIFACT_BYTES.toLong())
            if (!Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) ||
                sha256Bounded(target, EngineVerifier.MAX_ARTIFACT_BYTES.toLong()) != installation.sha256
            ) {
                throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            }
            writeSlotMetadata(temporary, installation)
            val created = temporary.renameTo(destination)
            if (!created) {
                if (!validSlot(installation)) {
                    throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
                }
            }
            if (!validSlot(installation)) {
                if (created) deleteHashSlot(root, installation.id)
                throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            }
            return created
        } finally {
            deleteOwnedTree(temporary)
        }
    }

    private fun healthySlot(state: ManagerState, id: String): EngineInstallation? =
        state.installations[id]?.takeIf {
            it.healthy && state.healthyIds.contains(id) && validSlot(it)
        }

    private fun cleanupFailedStage(
        state: ManagerState,
        candidate: EngineInstallation,
        createdSlot: Boolean,
        recordAdded: Boolean,
    ) {
        val existing = state.installations[candidate.id]
        val protected = state.activeId == candidate.id || state.previousId == candidate.id ||
            state.healthyIds.contains(candidate.id) || existing?.healthy == true ||
            existing?.bundled == true || candidate.bundled || bundledCache?.id == candidate.id
        if (protected) return

        if (recordAdded) {
            state.installations.remove(candidate.id)
            state.healthyIds.remove(candidate.id)
            runCatching { saveStateLocked(state) }
        }
        if (createdSlot) runCatching { deleteHashSlot(enginesDirectory(), candidate.id) }
    }

    private fun validSlot(installation: EngineInstallation): Boolean {
        if (installation.id != installation.sha256 || !HASH_PATTERN.matches(installation.id)) return false
        return try {
            val root = enginesDirectory()
            val file = File(File(root, installation.id), YTDLP_FILE_NAME)
            !hasSymlinkComponent(file) && inHashSlot(file, root, installation.id) &&
                Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                file.length() <= EngineVerifier.MAX_ARTIFACT_BYTES.toLong() &&
                sha256Bounded(file, EngineVerifier.MAX_ARTIFACT_BYTES.toLong()) == installation.sha256 &&
                // Recheck after hashing so a replacement cannot turn the checked bytes into another path.
                !hasSymlinkComponent(file) && Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                file.length() <= EngineVerifier.MAX_ARTIFACT_BYTES.toLong()
        } catch (_: Exception) {
            false
        }
    }

    private fun inHashSlot(file: File, root: File, id: String): Boolean = try {
        val slot = file.parentFile
        val canonicalRoot = root.canonicalFile
        val canonicalSlot = slot?.canonicalFile
        val canonicalFile = file.canonicalFile
        canonicalSlot != null && canonicalSlot.name == id &&
            canonicalSlot.parentFile == canonicalRoot &&
            canonicalFile.name == YTDLP_FILE_NAME && canonicalFile.parentFile == canonicalSlot
    } catch (_: Exception) {
        false
    }

    private fun hasSymlinkComponent(file: File): Boolean {
        var current = file.absoluteFile
        while (true) {
            if (app.sourcescribe.core.isUntrustedStorageSymlink(current.toPath())) return true
            current = current.parentFile ?: return false
        }
    }

    private fun writeSlotMetadata(directory: File, installation: EngineInstallation) {
        val file = File(directory, METADATA_FILE_NAME)
        val json = JSONObject()
            .put("id", installation.id)
            .put("version", installation.version)
            .put("ejsVersion", installation.ejsVersion)
            .put("channel", installation.channel.name)
            .put("sha256", installation.sha256)
            .put("bundled", installation.bundled)
        installation.gitHead?.let { json.put("gitHead", it) }
        try {
            FileOutputStream(file).use { output ->
                output.write(json.toString().toByteArray(StandardCharsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
        } catch (_: Exception) {
            throw EngineUpdateException(EngineUpdateCode.STORAGE)
        }
    }

    private fun stateLocked(): ManagerState {
        loadedState?.let { return it }
        val loaded = readState()
        cleanupCrashOrphans(loaded.state, loaded.trusted)
        val state = loaded.state
        loadedState = state
        return state
    }

    private fun readState(): StateLoad {
        val file = stateFile()
        if (!file.isFile) return StateLoad(ManagerState(), trusted = false)
        return try {
            val bytes = AtomicFile(file).openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_STATE_BYTES) throw IOException("state")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            StateLoad(parseState(String(bytes, StandardCharsets.UTF_8)), trusted = true)
        } catch (_: Exception) {
            // AtomicFile restores its backup before openRead; a malformed state is safest as empty state.
            StateLoad(ManagerState(), trusted = false)
        }
    }

    private fun parseState(raw: String): ManagerState {
        val root = JSONObject(boundedJsonText(raw))
        val state = ManagerState(
            activeId = optionalString(root, "active"),
            previousId = optionalString(root, "previous"),
            lastCheckedMs = root.optLong("lastCheckedMs", 0L).coerceAtLeast(0L),
            nextAllowedMs = root.optLong("nextAllowedMs", 0L).coerceAtLeast(0L),
            etag = optionalString(root, "etag")?.takeIf(::safeHeaderValue),
            etagChannel = runCatching { EngineChannel.valueOf(optionalString(root, "etagChannel").orEmpty()) }.getOrNull(),
        )
        val records = root.optJSONArray("installations") ?: JSONArray()
        for (index in 0 until records.length().coerceAtMost(MAX_INSTALLATIONS)) {
            val item = records.optJSONObject(index) ?: continue
            val id = item.optString("id", "")
            if (!HASH_PATTERN.matches(id)) continue
            val version = item.optString("version", "")
            val ejs = item.optString("ejsVersion", "")
            val sha = item.optString("sha256", "")
            if (!VALUE_PATTERN.matches(version) || !VALUE_PATTERN.matches(ejs) || !HASH_PATTERN.matches(sha)) continue
            val channel = runCatching { EngineChannel.valueOf(item.optString("channel")) }.getOrNull() ?: continue
            state.installations[id] = EngineInstallation(
                id, version, ejs, channel, optionalString(item, "gitHead"), sha,
                item.optBoolean("healthy", false), item.optBoolean("bundled", false),
            )
        }
        val healthy = root.optJSONArray("healthy") ?: JSONArray()
        for (index in 0 until healthy.length()) {
            healthy.optString(index).takeIf { HASH_PATTERN.matches(it) }?.let(state.healthyIds::add)
        }
        state.installations.replaceAll { id, item -> item.copy(healthy = item.healthy && state.healthyIds.contains(id)) }
        state.cached = root.optJSONObject("cached")?.let { item ->
            val cachedChannel = runCatching { EngineChannel.valueOf(item.optString("channel")) }.getOrNull()
            val cachedId = item.optString("id", "")
            val cachedVersion = item.optString("version", "")
            val cachedHash = item.optString("sha256", "").takeIf { it.isNotEmpty() }
            if (cachedChannel == null || !TAG_PATTERN.matches(cachedId) || !TAG_PATTERN.matches(cachedVersion) ||
                cachedHash != null && !HASH_PATTERN.matches(cachedHash)
            ) null else CachedRelease(cachedId, cachedVersion, cachedChannel, cachedHash)
        }
        return state
    }

    private fun saveStateLocked(state: ManagerState) {
        val parent = stateFile().parentFile ?: throw EngineUpdateException(EngineUpdateCode.STORAGE)
        if (!parent.exists() && !parent.mkdirs()) throw EngineUpdateException(EngineUpdateCode.STORAGE)
        val records = JSONArray()
        state.installations.values.take(MAX_INSTALLATIONS).forEach { installation ->
            records.put(JSONObject()
                .put("id", installation.id)
                .put("version", installation.version)
                .put("ejsVersion", installation.ejsVersion)
                .put("channel", installation.channel.name)
                .put("sha256", installation.sha256)
                .put("healthy", installation.healthy)
                .put("bundled", installation.bundled)
                .apply { installation.gitHead?.let { put("gitHead", it) } })
        }
        val healthy = JSONArray()
        state.healthyIds.filter { state.installations.containsKey(it) }.forEach(healthy::put)
        val root = JSONObject()
            .put("active", state.activeId ?: JSONObject.NULL)
            .put("previous", state.previousId ?: JSONObject.NULL)
            .put("healthy", healthy)
            .put("installations", records)
            .put("lastCheckedMs", state.lastCheckedMs)
            .put("nextAllowedMs", state.nextAllowedMs)
        state.etag?.let { root.put("etag", it) }
        state.etagChannel?.let { root.put("etagChannel", it.name) }
        state.cached?.let {
            root.put("cached", JSONObject().put("id", it.id).put("version", it.version).put("channel", it.channel.name))
            it.sha256?.let { hash -> root.getJSONObject("cached").put("sha256", hash) }
        }
        val bytes = root.toString().toByteArray(StandardCharsets.UTF_8)
        var output: FileOutputStream? = null
        val atomic = AtomicFile(stateFile())
        try {
            output = atomic.startWrite()
            output.write(bytes)
            output.flush()
            output.fd.sync()
            atomic.finishWrite(output)
            output = null
        } catch (_: Exception) {
            output?.let { runCatching { atomic.failWrite(it) } }
            throw EngineUpdateException(EngineUpdateCode.STORAGE)
        }
    }

    private fun parseRelease(body: ByteArray, channel: EngineChannel): CachedRelease {
        try {
            val root = JSONObject(boundedJsonText(String(body, StandardCharsets.UTF_8)))
            val tag = root.optString("tag_name", "")
            if (!TAG_PATTERN.matches(tag)) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            val assets = root.optJSONArray("assets") ?: throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            val names = HashSet<String>()
            for (index in 0 until assets.length()) {
                val name = assets.optJSONObject(index)?.optString("name", "") ?: continue
                if (!names.add(name)) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            }
            if (!REQUIRED_ASSETS.all(names::contains)) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            val artifact = (0 until assets.length()).asSequence()
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name", "") == YTDLP_FILE_NAME }
            val digest = artifact?.optString("digest", "").orEmpty()
                .removePrefix("sha256:")
                .takeIf { it.isNotEmpty() }
            if (digest != null && !HASH_PATTERN.matches(digest)) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            return CachedRelease(tag, tag, channel, digest)
        } catch (failure: EngineUpdateException) {
            throw failure
        } catch (_: Exception) {
            throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        }
    }

    private fun validateReleaseBinding(update: AvailableEngine, verified: app.sourcescribe.core.VerifiedEngine) {
        if (channelOf(verified.channel) != update.channel) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        if (update.version.matches(VERSION_TAG_PATTERN) && verified.version != update.version) {
            throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        }
        if (update.ejsVersion != null && update.ejsVersion != verified.ejsVersion) {
            throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        }
        update.sha256?.let { expectedHash ->
            if (!HASH_PATTERN.matches(expectedHash) || expectedHash != verified.sha256) {
                throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
            }
        }
        update.gitHead?.let { if (it != verified.gitHead) throw EngineUpdateException(EngineUpdateCode.VERIFICATION) }
    }

    private fun isInstalled(state: ManagerState, available: AvailableEngine): Boolean =
        state.installations.values.any { installation ->
            validSlot(installation) && installation.healthy && state.healthyIds.contains(installation.id) &&
                installation.channel == available.channel &&
                (available.sha256?.let { it == installation.sha256 } ?: installation.version == available.version)
        }

    private fun releaseUrls(update: AvailableEngine): ReleaseUrls {
        if (!TAG_PATTERN.matches(update.id) || !TAG_PATTERN.matches(update.version)) {
            throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        }
        val repo = repository(update.channel)
        val tag = update.id
        val base = "https://github.com/$repo/releases/download/$tag"
        val expected = ReleaseUrls("$base/$YTDLP_FILE_NAME", "$base/$CHECKSUM_FILE_NAME", "$base/$SIGNATURE_FILE_NAME")
        if (update.artifactUrl.isNotEmpty() && update.artifactUrl != expected.artifact ||
            update.checksumUrl.isNotEmpty() && update.checksumUrl != expected.checksum ||
            update.signatureUrl.isNotEmpty() && update.signatureUrl != expected.signature) {
            throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
        }
        return expected
    }

    private suspend fun downloadBytes(url: String, maxBytes: Int): ByteArray =
        withContext(Dispatchers.IO) { fetch(url, maxBytes, null).body }

    private suspend fun downloadFile(url: String, destination: File, maxBytes: Long) = withContext(Dispatchers.IO) {
        val result = fetch(url, maxBytes.toInt(), null)
        try {
            writeDownloadedFile(destination, result.body)
        } catch (_: Exception) {
            throw EngineUpdateException(EngineUpdateCode.STORAGE)
        }
    }

    private fun fetch(url: String, maxBytes: Int, etag: String?): HttpResult {
        var next = fixedUri(url)
        repeat(MAX_REDIRECTS + 1) { redirect ->
            val request = Request.Builder().url(next.toString()).apply {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "SourceScribe")
                if (redirect == 0) etag?.let { header("If-None-Match", it) }
            }.build()
            val response = try {
                calls.newCall(request).execute()
            } catch (_: IOException) {
                throw EngineUpdateException(EngineUpdateCode.NETWORK)
            }
            response.use {
                if (it.code == HTTP_TOO_MANY_REQUESTS) {
                    val retry = it.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, MAX_RETRY_SECONDS)
                    throw EngineUpdateException(EngineUpdateCode.RATE_LIMIT, retry)
                }
                when (classifyHttpResponse(it.code)) {
                    EngineHttpResponseKind.NOT_MODIFIED ->
                        return HttpResult(it.code, ByteArray(0), it.header("ETag"))
                    EngineHttpResponseKind.REDIRECT -> {
                        val location = it.header("Location") ?: throw EngineUpdateException(EngineUpdateCode.NETWORK)
                        next = try { fixedUri(next.resolve(location).toString()) }
                        catch (failure: EngineUpdateException) { throw failure }
                        catch (_: Exception) { throw EngineUpdateException(EngineUpdateCode.NETWORK) }
                        return@use
                    }
                    EngineHttpResponseKind.SUCCESS -> {
                        val body = it.body
                        if (body.contentLength() > maxBytes) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
                        return HttpResult(it.code, readBounded(body.byteStream(), maxBytes), it.header("ETag"))
                    }
                    EngineHttpResponseKind.OTHER -> throw EngineUpdateException(EngineUpdateCode.NETWORK)
                }
            }
        }
        throw EngineUpdateException(EngineUpdateCode.NETWORK)
    }

    private fun readBounded(input: java.io.InputStream, maxBytes: Int): ByteArray {
        input.use {
            val output = ByteArrayOutputStream(minOf(maxBytes, READ_BUFFER_BYTES))
            val buffer = ByteArray(READ_BUFFER_BYTES)
            var total = 0
            val started = System.nanoTime()
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (total > maxBytes - count) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
                output.write(buffer, 0, count)
                total += count
                val expectedNanos = total.toLong() * 1_000_000_000L / DOWNLOAD_RATE_BYTES_PER_SECOND
                val remainingMillis = (expectedNanos - (System.nanoTime() - started)) / 1_000_000L
                if (remainingMillis > 0L) {
                    try { Thread.sleep(remainingMillis.coerceAtMost(1_000L)) }
                    catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw EngineUpdateException(EngineUpdateCode.NETWORK)
                    }
                }
            }
            return output.toByteArray()
        }
    }

    private fun normalizedProbeSource(source: Source): Source {
        val canonicalUrl = source.canonicalUrl
        if (source.kind != SourceKind.YOUTUBE || canonicalUrl == null) {
            throw EngineUpdateException(EngineUpdateCode.PROBE_FAILED)
        }
        return try {
            SourceResolver.youtube(canonicalUrl).also { resolved ->
                if (resolved.id != source.id || resolved.videoId != source.videoId) {
                    throw EngineUpdateException(EngineUpdateCode.PROBE_FAILED)
                }
            }
        } catch (failure: EngineUpdateException) {
            throw failure
        } catch (_: Exception) {
            throw EngineUpdateException(EngineUpdateCode.PROBE_FAILED)
        }
    }

    private fun probeFailure(stderr: String): EngineUpdateException {
        val lower = stderr.lowercase(Locale.ROOT)
        return when {
            "429" in lower || "too many requests" in lower -> EngineUpdateException(EngineUpdateCode.RATE_LIMIT)
            listOf(
                "network", "timed out", "timeout", "sign in", "po token", "challenge", "connection",
                "unable to download", "could not resolve", "temporary failure", "http error 5", "ssl", "dns",
            ).any(lower::contains) ->
                EngineUpdateException(EngineUpdateCode.UNCERTAIN_PROBE)
            else -> EngineUpdateException(EngineUpdateCode.PROBE_FAILED)
        }
    }

    private fun copyResource(id: Int, destination: File, maxBytes: Int) {
        try {
            appContext.resources.openRawResource(id).use { input -> copyStream(input, destination, maxBytes.toLong()) }
        } catch (failure: EngineUpdateException) {
            throw failure
        } catch (_: Exception) {
            throw EngineUpdateException(EngineUpdateCode.STORAGE)
        }
    }

    private fun resourceBytes(id: Int, maxBytes: Int): ByteArray {
        return try {
            appContext.resources.openRawResource(id).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (total > maxBytes - count) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
                    output.write(buffer, 0, count)
                    total += count
                }
                output.toByteArray()
            }
        } catch (failure: EngineUpdateException) {
            throw failure
        } catch (_: Exception) {
            throw EngineUpdateException(EngineUpdateCode.STORAGE)
        }
    }

    private fun copyFile(source: File, destination: File, maxBytes: Long) {
        try {
            source.inputStream().use { input -> copyStream(input, destination, maxBytes) }
        } catch (failure: EngineUpdateException) {
            throw failure
        } catch (_: Exception) {
            throw EngineUpdateException(EngineUpdateCode.STORAGE)
        }
    }

    private fun sha256Bounded(file: File, maxBytes: Long): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    if (total > maxBytes - count) return null
                    digest.update(buffer, 0, count)
                    total += count
                }
            }
            digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        } catch (_: Exception) {
            null
        }
    }

    private fun copyStream(input: java.io.InputStream, destination: File, maxBytes: Long) {
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(READ_BUFFER_BYTES)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (total > maxBytes - count) throw EngineUpdateException(EngineUpdateCode.STORAGE)
                output.write(buffer, 0, count)
                total += count
            }
            output.flush()
            output.fd.sync()
        }
    }

    private fun fixedUri(value: String): URI {
        val uri = try { URI(value) } catch (_: Exception) { throw EngineUpdateException(EngineUpdateCode.NETWORK) }
        val host = uri.host?.lowercase(Locale.ROOT)
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1 || host !in ALLOWED_HOSTS) {
            throw EngineUpdateException(EngineUpdateCode.NETWORK)
        }
        return uri
    }

    private fun enginesDirectory(): File {
        val directory = File(appContext.noBackupFilesDir, ENGINES_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) throw EngineUpdateException(EngineUpdateCode.STORAGE)
        if (isSymbolicLink(directory) || !directory.isDirectory) throw EngineUpdateException(EngineUpdateCode.STORAGE)
        return directory
    }

    private fun stateFile(): File = File(enginesDirectory(), STATE_FILE_NAME)

    private fun slotDirectories(root: File): List<File> = root.listFiles()
        ?.filter { !isSymbolicLink(it) && it.isDirectory && HASH_PATTERN.matches(it.name) }
        .orEmpty()

    private fun cleanupCrashOrphans(state: ManagerState, trustedState: Boolean) {
        val root = enginesDirectory()
        root.listFiles()?.forEach { child ->
            if (isOwnedTemporaryDirectory(child)) deleteOwnedTree(child)
        }
        if (!trustedState || hasSymlinkComponent(root)) return

        val protectedIds = buildSet {
            addAll(state.installations.keys)
            addAll(state.healthyIds)
            state.activeId?.let { add(it) }
            state.previousId?.let { add(it) }
        }
        root.listFiles()?.forEach { child ->
            if (HASH_PATTERN.matches(child.name) && child.name !in protectedIds) {
                deleteHashSlot(root, child.name)
            }
        }
    }

    private fun deleteOwnedTree(root: File) {
        if (!isOwnedTemporaryDirectory(root)) return
        deleteTree(root)
    }

    private fun deleteHashSlot(root: File, id: String) {
        if (!HASH_PATTERN.matches(id) || isSymbolicLink(root) || hasSymlinkComponent(root)) return
        val slot = File(root, id)
        if (slot.parentFile?.canonicalFile != root.canonicalFile) return
        deleteTree(slot)
    }

    private fun deleteTree(root: File) {
        if (isSymbolicLink(root)) {
            root.delete()
            return
        }
        if (root.isDirectory) root.listFiles()?.forEach(::deleteTree)
        root.delete()
    }

    private fun isOwnedTemporaryDirectory(file: File): Boolean =
        !isSymbolicLink(file) && TEMP_DIRECTORY_PATTERN.matches(file.name)

    private fun requireHashId(id: String) {
        if (!HASH_PATTERN.matches(id)) throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
    }

    private fun optionalString(json: JSONObject, name: String): String? = json.opt(name) as? String

    private fun safeHeaderValue(value: String): Boolean =
        value.length <= MAX_ETAG_CHARS && value.all { it.code in 0x20..0x7e }

    private fun isSymbolicLink(file: File): Boolean = Files.isSymbolicLink(file.toPath())

    private fun verificationFailure(failure: EngineVerificationException): EngineUpdateException =
        if (failure.code == app.sourcescribe.core.EngineVerificationCode.REQUIRES_APP_UPDATE) {
            EngineUpdateException(EngineUpdateCode.REQUIRES_APP_UPDATE)
        } else {
            EngineUpdateException(EngineUpdateCode.VERIFICATION)
        }

    private fun channelOf(value: String): EngineChannel = when (value.lowercase(Locale.ROOT)) {
        "stable" -> EngineChannel.STABLE
        "nightly" -> EngineChannel.NIGHTLY
        else -> throw EngineUpdateException(EngineUpdateCode.VERIFICATION)
    }

    private fun releaseEndpoint(channel: EngineChannel): String =
        "https://api.github.com/repos/${repository(channel)}/releases/latest"

    private fun repository(channel: EngineChannel): String = when (channel) {
        EngineChannel.STABLE -> "yt-dlp/yt-dlp"
        EngineChannel.NIGHTLY -> "yt-dlp/yt-dlp-nightly-builds"
    }

    private data class ReleaseUrls(val artifact: String, val checksum: String, val signature: String)

    private data class HttpResult(val status: Int, val body: ByteArray, val etag: String?)

    private data class CachedRelease(
        val id: String,
        val version: String,
        val channel: EngineChannel,
        val sha256: String? = null,
    ) {
        fun toAvailable(): AvailableEngine {
            val base = "https://github.com/${if (channel == EngineChannel.STABLE) "yt-dlp/yt-dlp" else "yt-dlp/yt-dlp-nightly-builds"}/releases/download/$id"
            return AvailableEngine(
                id = id,
                version = version,
                channel = channel,
                sha256 = sha256,
                artifactUrl = "$base/$YTDLP_FILE_NAME",
                checksumUrl = "$base/$CHECKSUM_FILE_NAME",
                signatureUrl = "$base/$SIGNATURE_FILE_NAME",
            )
        }
    }

    private class ManagerState(
        var activeId: String? = null,
        var previousId: String? = null,
        val healthyIds: MutableSet<String> = linkedSetOf(),
        val installations: LinkedHashMap<String, EngineInstallation> = linkedMapOf(),
        var lastCheckedMs: Long = 0L,
        var nextAllowedMs: Long = 0L,
        var etag: String? = null,
        var etagChannel: EngineChannel? = null,
        var cached: CachedRelease? = null,
    )

    private data class StateLoad(
        val state: ManagerState,
        val trusted: Boolean,
    )

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()

        fun writeSyncedBytes(destination: File, bytes: ByteArray) {
            FileOutputStream(destination).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
        }

        const val ENGINES_DIRECTORY = "engines"
        const val STATE_FILE_NAME = "state.json"
        const val METADATA_FILE_NAME = "metadata.json"
        const val YTDLP_FILE_NAME = "yt-dlp"
        const val CHECKSUM_FILE_NAME = "SHA2-256SUMS"
        const val SIGNATURE_FILE_NAME = "SHA2-256SUMS.sig"
        const val MAX_INSTALLATIONS = 5
        const val MAX_REDIRECTS = 3
        const val MAX_STATE_BYTES = 512 * 1024
        const val MAX_ETAG_CHARS = 256
        const val API_RESPONSE_BYTES = 256 * 1024
        const val READ_BUFFER_BYTES = 16 * 1024
        const val DOWNLOAD_RATE_BYTES_PER_SECOND = 64 * 1024
        const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        const val DEFAULT_RETRY_SECONDS = 3600L
        const val MAX_RETRY_SECONDS = 86400L
        const val HTTP_NOT_MODIFIED = 304
        const val HTTP_TOO_MANY_REQUESTS = 429
        val HASH_PATTERN = Regex("[0-9a-f]{64}")
        val VALUE_PATTERN = Regex("[A-Za-z0-9._+~-]{1,64}")
        val TAG_PATTERN = Regex("[0-9A-Za-z._-]{1,80}")
        val TEMP_DIRECTORY_PATTERN = Regex("\\.(?:staging|slot|bundled)-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        val VERSION_TAG_PATTERN = Regex("\\d{4}\\.\\d{2}\\.\\d{2}(?:\\.\\d+)?")
        val REQUIRED_ASSETS = setOf(YTDLP_FILE_NAME, CHECKSUM_FILE_NAME, SIGNATURE_FILE_NAME)
        val ALLOWED_HOSTS = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
    }
}
