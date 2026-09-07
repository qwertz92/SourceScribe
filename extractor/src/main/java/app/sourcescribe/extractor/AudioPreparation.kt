package app.sourcescribe.extractor

import android.os.Process
import android.system.ErrnoException
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToLong

/** The metadata observed by ffprobe for the first audio stream. */
data class AudioInfo(
    val durationMs: Long,
    val mimeType: String,
    val sampleRate: Int,
    val channels: Int,
)

/** A durable, hashed audio chunk produced by the bounded native runtime. */
data class PreparedAudio(
    val file: File,
    val durationMs: Long,
    val offsetMs: Long,
    val sha256: String,
    val mimeType: String,
)

enum class AudioPreparationCode {
    INVALID_INPUT,
    INPUT_NOT_FILE,
    INVALID_OUTPUT_DIRECTORY,
    OUTPUT_EXISTS,
    OUT_OF_RANGE,
    PROBE_FAILED,
    CONVERSION_FAILED,
    OUTPUT_TOO_LARGE,
    OUTPUT_INVALID,
    STORAGE_FAILED,
}

/**
 * A sanitized failure for local media preparation. Native transcripts and paths
 * are intentionally excluded from the message; callers can expose only [code]
 * and [exitCode] in diagnostics.
 */
class AudioPreparationException(
    val code: AudioPreparationCode,
    val exitCode: Int? = null,
) : IOException(
    buildString {
        append("audio_preparation:")
        append(code.name)
        exitCode?.let {
            append(":exit=")
            append(it)
        }
    },
)

/**
 * Probes and converts app-owned local files using only the bounded NativeRuntime.
 * The source file is never modified, and conversion is committed only after its
 * actual duration, size, MIME, and digest have been verified.
 */
class AudioPreparation(private val runtime: NativeRuntime) {
    /** Reads actual duration and first-audio-stream metadata from ffprobe JSON. */
    suspend fun probe(file: File): AudioInfo {
        val input = requireRegularFile(file)
        val result = runtime.ffprobe(
            listOf(
                "-v",
                "error",
                "-print_format",
                "json",
                "-show_entries",
                "format=duration,format_name:stream=codec_type,codec_name,sample_rate,channels,duration",
                "-select_streams",
                "a:0",
                input.absolutePath,
            ),
            timeoutSeconds = NATIVE_TIMEOUT_SECONDS,
        )
        if (result.exitCode != 0) {
            throw AudioPreparationException(AudioPreparationCode.PROBE_FAILED, result.exitCode)
        }
        return parseProbe(result.stdout)
    }

    /**
     * Encodes one bounded mono 16 kHz MP3 chunk. [startMs] is the media offset,
     * never a speech timestamp. The requested interval must fit in the source;
     * a small end tolerance covers container timestamp rounding only.
     */
    suspend fun chunk(
        input: File,
        outputDirectory: File,
        index: Int,
        startMs: Long,
        durationMs: Long,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ): PreparedAudio {
        val source = requireRegularFile(input)
        validateChunkRequest(index, startMs, durationMs, maxBytes)
        val inputInfo = probe(source)
        validateRange(inputInfo.durationMs, startMs, durationMs)
        val directory = requireOutputDirectory(outputDirectory)
        val output = File(directory, "audio-$index.mp3")
        if (exists(output)) {
            throw AudioPreparationException(AudioPreparationCode.OUTPUT_EXISTS)
        }

        var temporary: File? = null
        var committed = false
        try {
            temporary = createTemporaryOutput(directory, maxBytes)
            val encoded = runtime.ffmpeg(
                listOf(
                    "-nostdin",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-ss",
                    formatSeconds(startMs),
                    "-t",
                    formatSeconds(durationMs),
                    "-i",
                    source.absolutePath,
                    "-vn",
                    "-map",
                    "0:a:0",
                    "-ac",
                    "1",
                    "-ar",
                    "16000",
                    "-c:a",
                    "libmp3lame",
                    "-b:a",
                    "64k",
                    "-f",
                    "mp3",
                    "-fs",
                    maxBytes.toString(),
                    "-y",
                    temporary.absolutePath,
                ),
                timeoutSeconds = NATIVE_TIMEOUT_SECONDS,
            )
            if (encoded.exitCode != 0) {
                throw AudioPreparationException(AudioPreparationCode.CONVERSION_FAILED, encoded.exitCode)
            }

            val actualBytes = temporary.length()
            if (!temporary.isFile || actualBytes <= 0L) {
                throw AudioPreparationException(AudioPreparationCode.OUTPUT_INVALID)
            }
            if (actualBytes > maxBytes) {
                throw AudioPreparationException(AudioPreparationCode.OUTPUT_TOO_LARGE)
            }

            val outputInfo = probe(temporary)
            if (
                outputInfo.durationMs <= 0L ||
                outputInfo.durationMs < (durationMs - OUTPUT_DURATION_TOLERANCE_MS).coerceAtLeast(1L) ||
                outputInfo.durationMs > durationMs + OUTPUT_DURATION_TOLERANCE_MS ||
                outputInfo.mimeType != OUTPUT_MIME_TYPE ||
                outputInfo.sampleRate != OUTPUT_SAMPLE_RATE ||
                outputInfo.channels != OUTPUT_CHANNELS
            ) {
                throw AudioPreparationException(AudioPreparationCode.OUTPUT_INVALID)
            }
            if (temporary.length() > maxBytes) {
                throw AudioPreparationException(AudioPreparationCode.OUTPUT_TOO_LARGE)
            }

            val digest = sha256(temporary)
            commitAtomically(temporary, output)
            committed = true
            return PreparedAudio(
                file = output,
                durationMs = outputInfo.durationMs,
                offsetMs = startMs,
                sha256 = digest,
                mimeType = outputInfo.mimeType,
            )
        } finally {
            if (!committed) {
                try {
                    temporary?.delete()
                } catch (_: SecurityException) {
                    // The temporary file is private to this invocation; cleanup is best effort.
                }
            }
        }
    }

    private fun requireRegularFile(file: File): File {
        val absolute = file.absoluteFile
        val canonical = try {
            file.canonicalFile
        } catch (_: IOException) {
            throw AudioPreparationException(AudioPreparationCode.INPUT_NOT_FILE)
        } catch (_: SecurityException) {
            throw AudioPreparationException(AudioPreparationCode.INPUT_NOT_FILE)
        }
        if (
            !hasOnlyTrustedSymlinkComponents(absolute) ||
            !canonical.isFile ||
            !canonical.canRead() ||
            canonical.length() <= 0L ||
            !isOwnedByApp(canonical)
        ) {
            throw AudioPreparationException(AudioPreparationCode.INPUT_NOT_FILE)
        }
        return canonical
    }

    private fun hasOnlyTrustedSymlinkComponents(file: File): Boolean = try {
        var current: File? = file.absoluteFile
        while (current != null) {
            if (Files.isSymbolicLink(current.toPath()) && !isAndroidControlledAlias(current)) return false
            current = current.parentFile
        }
        true
    } catch (_: SecurityException) {
        false
    }

    private fun isAndroidControlledAlias(file: File): Boolean {
        val path = file.absolutePath
        // Android exposes app data through these kernel-controlled aliases on some releases.
        return path == ANDROID_LEGACY_DATA_ROOT || ANDROID_USER_ROOT_PATTERN.matches(path)
    }

    private fun validateChunkRequest(index: Int, startMs: Long, durationMs: Long, maxBytes: Long) {
        if (index < 0 || startMs < 0L || durationMs <= 0L || maxBytes <= 0L) {
            throw AudioPreparationException(AudioPreparationCode.INVALID_INPUT)
        }
        if (durationMs > MAX_CHUNK_DURATION_MS || maxBytes > MAX_OUTPUT_BYTES) {
            throw AudioPreparationException(AudioPreparationCode.INVALID_INPUT)
        }
        if (startMs > Long.MAX_VALUE - durationMs) {
            throw AudioPreparationException(AudioPreparationCode.OUT_OF_RANGE)
        }
    }

    private fun validateRange(sourceDurationMs: Long, startMs: Long, durationMs: Long) {
        if (sourceDurationMs <= 0L || startMs >= sourceDurationMs) {
            throw AudioPreparationException(AudioPreparationCode.OUT_OF_RANGE)
        }
        val requestedEnd = startMs + durationMs
        val allowedEnd = if (sourceDurationMs > Long.MAX_VALUE - INPUT_END_TOLERANCE_MS) {
            Long.MAX_VALUE
        } else {
            sourceDurationMs + INPUT_END_TOLERANCE_MS
        }
        if (requestedEnd > allowedEnd) {
            throw AudioPreparationException(AudioPreparationCode.OUT_OF_RANGE)
        }
    }

    private fun requireOutputDirectory(directory: File): File {
        val canonical = try {
            if (!directory.exists() && !directory.mkdirs()) {
                throw AudioPreparationException(AudioPreparationCode.INVALID_OUTPUT_DIRECTORY)
            }
            directory.canonicalFile
        } catch (exception: AudioPreparationException) {
            throw exception
        } catch (_: IOException) {
            throw AudioPreparationException(AudioPreparationCode.INVALID_OUTPUT_DIRECTORY)
        } catch (_: SecurityException) {
            throw AudioPreparationException(AudioPreparationCode.INVALID_OUTPUT_DIRECTORY)
        }
        if (!canonical.isDirectory || !canonical.canWrite() || !isOwnedByApp(canonical)) {
            throw AudioPreparationException(AudioPreparationCode.INVALID_OUTPUT_DIRECTORY)
        }
        return canonical
    }

    private fun exists(file: File): Boolean = try {
        file.exists()
    } catch (_: SecurityException) {
        throw AudioPreparationException(AudioPreparationCode.INVALID_OUTPUT_DIRECTORY)
    }

    private fun createTemporaryOutput(directory: File, maxBytes: Long): File {
        return try {
            if (physicalFreeBytes(directory) < maxBytes) {
                throw AudioPreparationException(AudioPreparationCode.STORAGE_FAILED)
            }
            File.createTempFile("audio-prep-", ".tmp", directory)
        } catch (exception: AudioPreparationException) {
            throw exception
        } catch (_: IOException) {
            throw AudioPreparationException(AudioPreparationCode.STORAGE_FAILED)
        } catch (_: SecurityException) {
            throw AudioPreparationException(AudioPreparationCode.STORAGE_FAILED)
        }
    }

    private suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            file.inputStream().use { input ->
                val buffer = ByteArray(HASH_BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
        } catch (_: IOException) {
            throw AudioPreparationException(AudioPreparationCode.STORAGE_FAILED)
        } catch (_: SecurityException) {
            throw AudioPreparationException(AudioPreparationCode.STORAGE_FAILED)
        }
        digest.digest().toHex()
    }

    private suspend fun commitAtomically(temporary: File, output: File) = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(temporary, true).use { stream -> stream.fd.sync() }
            if (exists(output)) {
                throw AudioPreparationException(AudioPreparationCode.OUTPUT_EXISTS)
            }
            Files.move(
                temporary.toPath(),
                output.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (exception: AudioPreparationException) {
            throw exception
        } catch (_: AtomicMoveNotSupportedException) {
            throw AudioPreparationException(AudioPreparationCode.STORAGE_FAILED)
        } catch (_: UnsupportedOperationException) {
            throw AudioPreparationException(AudioPreparationCode.STORAGE_FAILED)
        } catch (_: IOException) {
            throw AudioPreparationException(AudioPreparationCode.STORAGE_FAILED)
        } catch (_: SecurityException) {
            throw AudioPreparationException(AudioPreparationCode.STORAGE_FAILED)
        }
    }

    private fun isOwnedByApp(file: File): Boolean = try {
        Os.stat(file.absolutePath).st_uid == Process.myUid()
    } catch (_: ErrnoException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun parseProbe(stdout: String): AudioInfo {
        try {
            val root = JSONObject(stdout)
            val format = root.optJSONObject("format")
            val streams = root.optJSONArray("streams")
                ?: throw AudioPreparationException(AudioPreparationCode.PROBE_FAILED)
            var audioStream: JSONObject? = null
            for (position in 0 until streams.length()) {
                val candidate = streams.optJSONObject(position) ?: continue
                if (candidate.optString("codec_type", "") == "audio") {
                    audioStream = candidate
                    break
                }
            }
            val stream = audioStream ?: throw AudioPreparationException(AudioPreparationCode.PROBE_FAILED)
            val durationMs = firstDurationMillis(
                stream.optString("duration", ""),
                format?.optString("duration", "").orEmpty(),
            )
            val sampleRate = stream.optString("sample_rate", "").toIntOrNull()
                ?: throw AudioPreparationException(AudioPreparationCode.PROBE_FAILED)
            val channels = stream.optInt("channels", 0)
            if (sampleRate !in 1..MAX_SAMPLE_RATE || channels !in 1..MAX_CHANNELS) {
                throw AudioPreparationException(AudioPreparationCode.PROBE_FAILED)
            }
            val formatName = format?.optString("format_name", "").orEmpty()
                .substringBefore(',')
                .trim()
            val codecName = stream.optString("codec_name", "")
            return AudioInfo(
                durationMs = durationMs,
                mimeType = mimeType(formatName, codecName),
                sampleRate = sampleRate,
                channels = channels,
            )
        } catch (exception: AudioPreparationException) {
            throw exception
        } catch (_: Exception) {
            throw AudioPreparationException(AudioPreparationCode.PROBE_FAILED)
        }
    }

    private fun firstDurationMillis(vararg values: String): Long {
        for (value in values) {
            val seconds = value.toDoubleOrNull() ?: continue
            if (!seconds.isFinite() || seconds <= 0.0 || seconds > Long.MAX_VALUE.toDouble() / 1000.0) {
                continue
            }
            val millis = (seconds * 1000.0).roundToLong()
            if (millis > 0L) return millis
        }
        throw AudioPreparationException(AudioPreparationCode.PROBE_FAILED)
    }

    private fun mimeType(formatName: String, codecName: String): String {
        return when (formatName.lowercase(Locale.ROOT)) {
            "mp3" -> "audio/mpeg"
            "wav", "wave" -> "audio/wav"
            "m4a", "mov", "mp4" -> "audio/mp4"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            "webm" -> "audio/webm"
            else -> when (codecName.lowercase(Locale.ROOT)) {
                "mp3" -> "audio/mpeg"
                "aac" -> "audio/aac"
                "opus" -> "audio/opus"
                "flac" -> "audio/flac"
                else -> "application/octet-stream"
            }
        }
    }

    private fun formatSeconds(milliseconds: Long): String =
        String.format(Locale.ROOT, "%.3f", milliseconds / MILLIS_PER_SECOND.toDouble())

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    private companion object {
        const val NATIVE_TIMEOUT_SECONDS = 480L
        const val DEFAULT_MAX_BYTES = 24_000_000L
        const val MAX_OUTPUT_BYTES = 24_000_000L
        const val MAX_CHUNK_DURATION_MS = 600_000L
        // Container and MP3 encoder timestamps can differ by a small bounded amount.
        const val INPUT_END_TOLERANCE_MS = 100L
        const val OUTPUT_DURATION_TOLERANCE_MS = 250L
        const val OUTPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_CHANNELS = 1
        const val MAX_SAMPLE_RATE = 384_000
        const val MAX_CHANNELS = 64
        const val HASH_BUFFER_BYTES = 64 * 1024
        const val MILLIS_PER_SECOND = 1_000L
        const val ANDROID_LEGACY_DATA_ROOT = "/data/data"
        val ANDROID_USER_ROOT_PATTERN = Regex("/data/user(?:_de)?/[0-9]+")
        val HEX_DIGITS = "0123456789abcdef".toCharArray()
        const val OUTPUT_MIME_TYPE = "audio/mpeg"
    }
}
