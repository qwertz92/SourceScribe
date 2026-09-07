package app.sourcescribe.extractor

import app.sourcescribe.core.CaptionTrack
import app.sourcescribe.core.ExtractorMetadata
import app.sourcescribe.core.InvalidSource
import app.sourcescribe.core.ResolvedSource
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.sourcescribe.core.Generation

enum class ExtractionFailure { NETWORK, RATE_LIMIT, SOURCE_UNAVAILABLE, CHALLENGE_REQUIRED, NO_CAPTIONS, INVALID_RESPONSE, STORAGE, NATIVE }
class ExtractionException(val failure: ExtractionFailure, val retryAfterSeconds: Long? = null) : IOException(failure.name)

class ExtractorEngine(private val runtime: NativeRuntime) {
    suspend fun resolve(source: Source, engine: File? = null): ResolvedSource {
        val url = requireNotNull(source.canonicalUrl)
        SourceResolver.requireMatchingVideo(SourceResolver.youtube(url), source.videoId)
        val result = runtime.ytDlp(listOf("--dump-single-json", "--skip-download", "--", url), engine = engine)
        requireSuccessful(result)
        return ExtractorMetadata.parse(result.stdout.trim(), source)
    }

    suspend fun caption(resolved: ResolvedSource, track: CaptionTrack, engine: File, directory: File): ByteArray = withContext(Dispatchers.IO) {
        SourceResolver.requireMatchingVideo(resolved.source, track.sourceVideoId)
        if (track !in resolved.captions || track.generation == Generation.UNKNOWN ||
            !Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}").matches(track.language) ||
            track.format !in setOf("json3", "vtt", "srt")) throw InvalidSource("CAPTION_TRACK_CHANGED")
        val info = captionDownloadInfo(resolved, track)
        if (!directory.isDirectory || Files.isSymbolicLink(directory.toPath())) throw ExtractionException(ExtractionFailure.STORAGE)
        val temporary = File(directory, "caption-${UUID.randomUUID()}")
        if (!temporary.mkdir()) throw ExtractionException(ExtractionFailure.STORAGE)
        try {
            val infoFile = File(temporary, "selected-caption.json").also { it.writeText(info.toString()) }
            val languagePattern = "^" + track.language.map { if (it.isLetterOrDigit() || it == '_') it.toString() else "\\$it" }.joinToString("") + "$"
            val result = coroutineScope {
                val download = async {
                    runtime.ytDlp(listOf(
                        "--load-info-json", infoFile.absolutePath, "--ignore-no-formats-error",
                        "--no-simulate", "--skip-download", "--no-part", "--no-continue", "--no-overwrites",
                        if (track.generation == Generation.UPLOADER_PROVIDED) "--write-subs" else "--write-auto-subs",
                        if (track.generation == Generation.UPLOADER_PROVIDED) "--no-write-auto-subs" else "--no-write-subs",
                        "--sub-langs", languagePattern, "--sub-format", track.format,
                        "--max-filesize", MAX_CAPTION_BYTES.toString(),
                        "--print", "video:SS_SOURCE_ID=%(id)s",
                        "--output", "subtitle:${File(temporary, "%(id)s").absolutePath}",
                        "--output", "default:${File(temporary, "%(id)s").absolutePath}",
                    ), timeoutSeconds = 180, engine = engine)
                }
                while (!download.isCompleted) {
                    if (!captionDirectoryWithinLimit(temporary)) {
                        download.cancel()
                        throw ExtractionException(ExtractionFailure.STORAGE)
                    }
                    delay(100)
                }
                download.await()
            }
            requireSuccessful(result)
            val observed = result.stdout.lineSequence().filter { it.startsWith("SS_SOURCE_ID=") }.map { it.removePrefix("SS_SOURCE_ID=").trim() }.toList()
            if (observed.size != 1) throw InvalidSource("SOURCE_ID_MISMATCH")
            SourceResolver.requireMatchingVideo(resolved.source, observed.single())
            val expected = File(temporary, "${resolved.source.videoId}.${track.language}.${track.format}")
            if (!captionDirectoryWithinLimit(temporary) || Files.isSymbolicLink(expected.toPath()) ||
                !expected.isFile || expected.length() !in 1..MAX_CAPTION_BYTES.toLong()) throw ExtractionException(ExtractionFailure.INVALID_RESPONSE)
            expected.inputStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() > MAX_CAPTION_BYTES - count) throw ExtractionException(ExtractionFailure.INVALID_RESPONSE)
                    output.write(buffer, 0, count)
                }
                output.toByteArray().also { if (it.isEmpty()) throw ExtractionException(ExtractionFailure.INVALID_RESPONSE) }
            }
        } finally {
            // Only the UUID directory created by this invocation is removed; never follow links.
            Files.walk(temporary.toPath()).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private fun captionDirectoryWithinLimit(directory: File): Boolean {
        var total = 0L
        var entries = 0
        Files.walk(directory.toPath()).use { paths ->
            val iterator = paths.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (++entries > 1024 || Files.isSymbolicLink(path)) return false
                if (Files.isRegularFile(path)) {
                    total += Files.size(path)
                    if (total > MAX_CAPTION_BYTES || physicalFreeBytes(directory) < 8L * 1024 * 1024) return false
                }
            }
        }
        return true
    }

    suspend fun downloadAudio(source: Source, formatId: String, directory: File, maxBytes: Long, engine: File? = null): File {
        require(Regex("[A-Za-z0-9_.-]{1,80}").matches(formatId))
        require(maxBytes in 1..(2L * 1024 * 1024 * 1024))
        val refreshed = resolve(source, engine)
        if (refreshed.audio.none { it.id == formatId }) throw InvalidSource("AUDIO_TRACK_CHANGED")
        if (!directory.exists() && !directory.mkdirs()) throw ExtractionException(ExtractionFailure.STORAGE)
        if (physicalFreeBytes(directory) < minOf(maxBytes, 32L * 1024 * 1024)) throw ExtractionException(ExtractionFailure.STORAGE)
        val output = File(directory, "audio.downloading")
        if (output.exists() && !output.delete()) throw ExtractionException(ExtractionFailure.STORAGE)
        val result = try {
            coroutineScope {
                val download = async {
                    runtime.ytDlp(listOf(
                        "--format", formatId, "--max-filesize", maxBytes.toString(), "--no-part", "--no-continue",
                        "--no-overwrites", "--output", output.absolutePath,
                        "--print", "after_move:SS_SOURCE_ID=%(id)s", "--", requireNotNull(source.canonicalUrl),
                    ), timeoutSeconds = 480, engine = engine)
                }
                while (!download.isCompleted) {
                    if (output.length() > maxBytes || physicalFreeBytes(directory) < 8L * 1024 * 1024) {
                        download.cancel()
                        throw ExtractionException(ExtractionFailure.STORAGE)
                    }
                    delay(100)
                }
                download.await()
            }
        } catch (failure: Exception) {
            output.delete()
            throw failure
        }
        return validateAudioDownload(source, output, maxBytes, result)
    }

    /** A failed native command or source/size check must never leave reusable audio behind. */
    internal fun validateAudioDownload(source: Source, output: File, maxBytes: Long, result: RuntimeOutput): File = try {
        requireSuccessful(result)
        val observed = result.stdout.lineSequence().filter { it.startsWith("SS_SOURCE_ID=") }.map { it.removePrefix("SS_SOURCE_ID=").trim() }.toList()
        if (observed.size != 1) throw InvalidSource("SOURCE_ID_MISMATCH")
        SourceResolver.requireMatchingVideo(source, observed.single())
        if (!output.isFile || output.length() <= 0 || output.length() > maxBytes) throw ExtractionException(ExtractionFailure.INVALID_RESPONSE)
        output
    } catch (failure: Exception) {
        output.delete()
        throw failure
    }

    private fun requireSuccessful(output: RuntimeOutput) {
        if (output.exitCode == 0) return
        val error = output.stderr.lowercase()
        val failure = when {
            "429" in error || "too many requests" in error -> ExtractionFailure.RATE_LIMIT
            "sign in" in error || "po token" in error || "challenge" in error -> ExtractionFailure.CHALLENGE_REQUIRED
            "private" in error || "unavailable" in error || "removed" in error || "403" in error -> ExtractionFailure.SOURCE_UNAVAILABLE
            "timed out" in error || "network" in error || "resolve" in error || "no address associated" in error -> ExtractionFailure.NETWORK
            else -> ExtractionFailure.NATIVE
        }
        throw ExtractionException(failure)
    }

    private companion object { const val MAX_CAPTION_BYTES = 4 * 1024 * 1024 }
}

/** A minimal, exact track recipe. No webpage_url: yt-dlp would otherwise re-extract on error. */
internal fun captionDownloadInfo(resolved: ResolvedSource, track: CaptionTrack): JSONObject {
    val url = resolved.captionUrls[track.id] ?: throw InvalidSource("CAPTION_TRACK_CHANGED")
    ExtractorMetadata.requireCaptionUrl(url)
    val uri = URI(url)
    val parameters = ExtractorMetadata.captionParameters(url)
    if (parameters["v"]?.let { it != resolved.source.videoId } == true) throw InvalidSource("SOURCE_ID_MISMATCH")
    val language = parameters["tlang"] ?: parameters["lang"]
    if (language != null && !language.equals(track.language.removeSuffix("-orig"), ignoreCase = true)) throw InvalidSource("CAPTION_TRACK_CHANGED")
    val subtitle = JSONObject().put("url", url).put("ext", track.format)
        .put("protocol", if (uri.host == "manifest.googlevideo.com") "m3u8_native" else "https")
    return JSONObject().put("id", requireNotNull(resolved.source.videoId)).put("title", "SourceScribe")
        .put(if (track.generation == Generation.UPLOADER_PROVIDED) "subtitles" else "automatic_captions",
            JSONObject().put(track.language, JSONArray().put(subtitle)))
}
