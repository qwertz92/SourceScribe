package app.sourcescribe.data

import android.os.Build
import android.system.Os
import android.system.OsConstants
import app.sourcescribe.BuildConfig
import app.sourcescribe.core.ArtifactFilesException
import app.sourcescribe.core.CaptionParseException
import app.sourcescribe.core.ProviderErrorCode
import app.sourcescribe.extractor.EngineUpdateCode
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractionFailure
import app.sourcescribe.extractor.RuntimeFailureCode
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val MAX_OUTPUT_BYTES = 128 * 1024
private const val MAX_COUNT_ROWS = 512
private const val MAX_ENGINE_ROWS = 256
private const val MAX_TOKEN_LENGTH = 80
private val ERROR_CODE_TOKEN = Regex("^[A-Z0-9_]{1,80}$")
private val KNOWN_ERROR_CODES = buildSet {
    addAll(
        listOf(
            "AUDIO_DURATION_LIMIT", "AUDIO_LONGER_THAN_LIMIT", "AUDIO_DURATION_UNKNOWN",
            "AUDIO_INPUT_MISSING", "AUDIO_RESOURCE_BUSY", "AUDIO_TRACK_CHANGED",
            "AUDIO_TRACK_MISSING", "DOWNLOADED_AUDIO_INVALID", "ENGINE_NOT_AVAILABLE", "IMPORTED_AUDIO_NOT_FOUND",
            "INTERRUPTED", "NO_ACCEPTABLE_CAPTIONS", "NO_TRANSCRIPT", "PERMISSION_REQUIRED",
            "PREPARED_AUDIO_CHANGED", "PRICE_UNKNOWN", "PROVIDER_RESOURCE_BUSY", "REMOTE_HANDLE_INVALID",
            "REMOTE_NOT_COMPLETE", "REMOTE_RECEIPT_INVALID", "REMOTE_RECEIPT_MISSING", "REMOTE_TIMEOUT",
            "RESPONSE_NOT_READY", "RESPONSE_STORAGE", "SOURCE_ID_MISMATCH", "SOURCE_MISSING", "SOURCE_NOT_FOUND",
            "SOURCE_SNAPSHOT_INVALID", "SOURCE_VALIDATION", "SUBMISSION_BINDING_MISMATCH", "SUBMISSION_CHUNK_MISSING",
            "SUBMISSION_UNCERTAIN", "CANONICAL_ARTIFACT_TOO_LARGE", "RAW_RESPONSE_TOO_LARGE", "NORMALIZED_ARTIFACT_INVALID",
            "ARTIFACT_BINDING_MISMATCH", "RAW_RESPONSE_MISSING", "REMOTE_MAY_CONTINUE", "CHOOSE_AUDIO_TRACK",
            "CHOOSE_CAPTION_TRACK", "CAPTION_TRACK_CHANGED", "INVALID_URL", "INVALID_HOST", "INVALID_PATH",
            "INVALID_QUERY", "AMBIGUOUS_VIDEO", "EXPLICIT_VIDEO_REQUIRED", "INVALID_VIDEO_ID", "INPUT_TOO_LARGE",
            "TOO_MANY_VIDEOS", "INVALID_CAPTION_URL", "LIVE_OR_PLAYLIST_UNSUPPORTED", "INVALID_METADATA",
            "METADATA_TOO_LARGE", "INVALID_DURATION", "LOCAL_PROCESSING_FAILED",
        ),
    )
    addAll(ProviderErrorCode.entries.map { it.name })
    addAll(ProviderErrorCode.entries.map { "CREDENTIAL_${it.name}" })
    addAll(ProviderErrorCode.entries.map { "${it.name}_RAW_SAVED" })
    addAll(ExtractionFailure.entries.map { it.name })
    addAll(RuntimeFailureCode.entries.map { it.name })
    addAll(EngineUpdateCode.entries.map { "ENGINE_${it.name}" })
    addAll(
        listOf(
            ArtifactFilesException.INVALID_ARTIFACT_ID, ArtifactFilesException.INVALID_RAW_EXTENSION,
            ArtifactFilesException.RAW_EXTENSION_WITHOUT_DATA, ArtifactFilesException.RAW_NOT_ALLOWED,
            ArtifactFilesException.RAW_REQUIRED, ArtifactFilesException.CANONICAL_TOO_LARGE,
            ArtifactFilesException.RAW_TOO_LARGE, ArtifactFilesException.RAW_HASH_MISMATCH,
            ArtifactFilesException.CONFLICTING_CONTENT, ArtifactFilesException.NOT_FOUND,
            ArtifactFilesException.INCOMPLETE_ARTIFACT, ArtifactFilesException.MALFORMED_CANONICAL,
            ArtifactFilesException.CORRUPT_CANONICAL, ArtifactFilesException.MALFORMED_METADATA,
            ArtifactFilesException.MALFORMED_ARTIFACT, ArtifactFilesException.CORRUPT_RAW,
            ArtifactFilesException.SYMLINK_NOT_ALLOWED, ArtifactFilesException.PATH_ESCAPE,
            ArtifactFilesException.STORAGE_FAILURE,
        ),
    )
    addAll(
        listOf(
            CaptionParseException.UNSUPPORTED_FORMAT, CaptionParseException.INPUT_TOO_LARGE,
            CaptionParseException.EMPTY_INPUT, CaptionParseException.MALFORMED_INPUT, CaptionParseException.NO_SEGMENTS,
        ),
    )
}

@Singleton
class Diagnostics @Inject constructor(
    private val dao: SourceScribeDao,
    private val engines: EngineUpdateManager,
) {
    suspend fun build(): String {
        val attempts = dao.observeAttempts().first()
        val installedEngines = try {
            engines.installations().map { DiagnosticEngine(it.id, it.version) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val pageSize = try {
            Os.sysconf(OsConstants._SC_PAGESIZE).takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
        return buildDiagnosticsText(
            buildVersion = BuildConfig.VERSION_NAME,
            androidApi = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            pageSizeBytes = pageSize,
            attempts = attempts,
            installedEngines = installedEngines,
        )
    }
}

internal data class DiagnosticEngine(val id: String, val version: String)

internal fun buildDiagnosticsText(
    buildVersion: String,
    androidApi: Int,
    abis: List<String>,
    pageSizeBytes: Long?,
    attempts: List<AttemptRow>,
    installedEngines: List<DiagnosticEngine>?,
): String {
    val output = buildString {
        appendLine("SourceScribe diagnostics")
        appendLine("app_version=${safeToken(buildVersion)}")
        appendLine("android_api=$androidApi")
        appendLine("abi=${abis.take(8).joinToString(",") { safeToken(it) }}")
        appendLine("page_size_bytes=${pageSizeBytes ?: "UNKNOWN"}")

        if (installedEngines == null) {
            appendLine("engine_status=NOT_PROBED")
        } else {
            installedEngines.asSequence().take(MAX_ENGINE_ROWS).forEachIndexed { index, engine ->
                appendLine("engine_${index}_id=${safeToken(engine.id)}")
                appendLine("engine_${index}_version=${safeToken(engine.version)}")
            }
        }

        appendCounts("attempt_state", attempts.map { it.state.name })
        appendCounts("attempt_phase", attempts.map { it.phase.name })
        appendCounts("error_code", attempts.mapNotNull { safeErrorCode(it.error) })
    }.trimEnd() + "\n"

    val outputBytes = output.toByteArray(StandardCharsets.UTF_8)
    check(outputBytes.size <= MAX_OUTPUT_BYTES) { "diagnostics output exceeds 128 KiB" }
    return output
}

private fun StringBuilder.appendCounts(prefix: String, values: Iterable<String>) {
    val counts = values.groupingBy { it }.eachCount().toSortedMap()
    if (counts.isEmpty()) {
        appendLine("${prefix}_NONE=0")
    } else {
        counts.asSequence().take(MAX_COUNT_ROWS).forEach { (name, count) ->
            appendLine("${prefix}_${name}=$count")
        }
    }
}

internal fun safeErrorCode(raw: String?): String? =
    raw?.trim()?.let { value ->
        val candidate = value.takeWhile { it in 'A'..'Z' || it in '0'..'9' || it == '_' }
        if (!ERROR_CODE_TOKEN.matches(candidate)) "OTHER"
        else candidate.takeIf { it in KNOWN_ERROR_CODES } ?: "OTHER"
    }

private fun safeToken(value: String): String = buildString {
    value.take(MAX_TOKEN_LENGTH).forEach { character ->
        append(
            when {
                character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9' -> character
                character == '_' || character == '-' || character == '.' -> character
                else -> '_'
            },
        )
    }
    if (isEmpty()) append("UNKNOWN")
}
