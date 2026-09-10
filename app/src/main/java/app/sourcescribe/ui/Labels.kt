package app.sourcescribe.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.sourcescribe.R
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.AudioRetention
import app.sourcescribe.core.AudioSizeClass
import app.sourcescribe.core.AudioTrackDescription
import app.sourcescribe.core.Branch
import app.sourcescribe.core.CaptionTrack
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.ExportFormat
import app.sourcescribe.core.Generation
import app.sourcescribe.core.Origin
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Provenance
import app.sourcescribe.core.Provider
import app.sourcescribe.core.Region
import app.sourcescribe.core.Translation
import app.sourcescribe.extractor.EngineChannel
import java.util.Locale

internal fun providerName(value: Provider): String = when (value) {
    Provider.ASSEMBLYAI -> "AssemblyAI"
    Provider.OPENAI -> "OpenAI"
    Provider.GROQ -> "Groq"
}

@Composable internal fun regionName(value: Region) =
    stringResource(if (value == Region.EU) R.string.region_eu else R.string.region_us)

@Composable internal fun branchName(value: Branch) =
    stringResource(if (value == Branch.CAPTIONS) R.string.caption_branch else R.string.stt_branch)

@Composable internal fun channelName(value: EngineChannel) =
    stringResource(if (value == EngineChannel.STABLE) R.string.channel_stable else R.string.channel_nightly)

@Composable internal fun formatName(value: ExportFormat) = stringResource(when (value) {
    ExportFormat.MARKDOWN -> R.string.format_markdown
    ExportFormat.TEXT -> R.string.format_text
    ExportFormat.JSON -> R.string.format_json
    ExportFormat.SRT -> R.string.format_srt
    ExportFormat.VTT -> R.string.format_vtt
    ExportFormat.RAW -> R.string.format_raw
})

@Composable internal fun modeName(mode: AcquisitionMode) = stringResource(modeLabel(mode))

internal fun modeLabel(mode: AcquisitionMode) = when (mode) {
    AcquisitionMode.CAPTIONS_ONLY -> R.string.mode_captions_only
    AcquisitionMode.CAPTIONS_THEN_STT -> R.string.mode_captions_then_stt
    AcquisitionMode.STT_ONLY -> R.string.mode_stt_only
    AcquisitionMode.BOTH -> R.string.mode_both
}

@Composable internal fun themeName(theme: String) = stringResource(when (theme) {
    "DARK" -> R.string.theme_dark
    "LIGHT" -> R.string.theme_light
    else -> R.string.theme_system
})

@Composable internal fun languageName(language: String) =
    stringResource(if (language == "en") R.string.language_english else R.string.language_german)

@Composable internal fun audioRetentionName(value: AudioRetention) = stringResource(when (value) {
    AudioRetention.TEMPORARY -> R.string.audio_temporary
    AudioRetention.UNTIL_PERSISTED -> R.string.audio_until_persisted
    AudioRetention.KEEP -> R.string.audio_keep
})

internal fun stateLabel(state: ExecutionState) = when (state) {
    ExecutionState.QUEUED -> R.string.state_queued
    ExecutionState.RUNNING -> R.string.state_running
    ExecutionState.WAITING_NETWORK -> R.string.state_waiting_network
    ExecutionState.WAITING_RATE_LIMIT -> R.string.state_waiting_rate_limit
    ExecutionState.WAITING_USER -> R.string.state_waiting_user
    ExecutionState.WAITING_REMOTE -> R.string.state_waiting_remote
    ExecutionState.SUBMISSION_UNCERTAIN -> R.string.state_submission_uncertain
    ExecutionState.FINISHED -> R.string.state_finished
    ExecutionState.CANCELLED -> R.string.state_cancelled
}

internal fun outcomeLabel(outcome: Outcome) = when (outcome) {
    Outcome.NONE -> R.string.outcome_none
    Outcome.SUCCESS -> R.string.outcome_success
    Outcome.SUCCESS_WITH_WARNINGS -> R.string.outcome_success_with_warnings
    Outcome.PARTIAL_SUCCESS -> R.string.outcome_partial_success
    Outcome.FAILED -> R.string.outcome_failed
    Outcome.CANCELLED -> R.string.outcome_cancelled
}

internal fun phaseLabel(phase: Phase) = when (phase) {
    Phase.RESOLVE -> R.string.phase_resolve
    Phase.FETCH_CAPTIONS -> R.string.phase_fetch_captions
    Phase.DOWNLOAD_AUDIO -> R.string.phase_download_audio
    Phase.PREPARE_AUDIO -> R.string.phase_prepare_audio
    Phase.UPLOAD -> R.string.phase_upload
    Phase.SUBMIT -> R.string.phase_submit
    Phase.RETRIEVE -> R.string.phase_retrieve
    Phase.NORMALIZE -> R.string.phase_normalize
    Phase.PERSIST -> R.string.phase_persist
}

/** Elapsed or media time. Hours are shown as hours instead of a three-digit minute count. */
internal fun duration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

/** A configured limit, read as a duration rather than a raw minute count. */
@Composable internal fun limitDuration(seconds: Long): String {
    val minutes = (seconds / 60).coerceAtLeast(0)
    return if (minutes >= 60) stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_minutes, minutes)
}

/** Download sizes in decimal units, the way a data plan counts them. */
internal fun byteSize(bytes: Long): String {
    val locale = Locale.getDefault()
    return when {
        bytes >= 1_000_000_000L -> String.format(locale, "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(locale, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format(locale, "%.0f kB", bytes / 1_000.0)
        else -> String.format(locale, "%d B", bytes)
    }
}

@Composable internal fun trackSize(description: AudioTrackDescription): String {
    val bytes = description.bytes ?: return stringResource(R.string.audio_track_size_unknown)
    val text = byteSize(bytes)
    return if (description.bytesEstimated) stringResource(R.string.audio_track_size_approx, text) else text
}

/** The line a reader scans first: language, size, and why this track is the suggested one. */
@Composable internal fun audioTrackTitle(description: AudioTrackDescription): String {
    val parts = mutableListOf<String>()
    parts += languageText(description.track.language)
    if (description.isOriginal == true) parts += stringResource(R.string.audio_track_original)
    parts += trackSize(description)
    when (description.sizeClass) {
        AudioSizeClass.SMALLEST -> parts += stringResource(R.string.audio_track_smallest)
        AudioSizeClass.LARGEST -> parts += stringResource(R.string.audio_track_largest)
        else -> Unit
    }
    if (description.dynamicRangeCompressed) parts += stringResource(R.string.audio_track_drc)
    if (description.audioDescription) parts += stringResource(R.string.audio_track_description)
    return parts.joinToString(" · ")
}

/** The technical line underneath: codec, data rate, channels and the extractor format id. */
@Composable internal fun audioTrackDetail(description: AudioTrackDescription): String {
    val parts = mutableListOf<String>()
    listOfNotNull(description.codecLabel, description.containerLabel).distinct().forEach { parts += it }
    description.bitrateKbps?.let { parts += stringResource(R.string.audio_track_bitrate, it) }
    when (val channels = description.channels) {
        null -> Unit
        1 -> parts += stringResource(R.string.audio_track_mono)
        2 -> parts += stringResource(R.string.audio_track_stereo)
        else -> parts += stringResource(R.string.audio_track_channels, channels)
    }
    parts += stringResource(R.string.audio_track_format, description.track.id)
    return parts.joinToString(" · ")
}

@Composable internal fun audioTrackOption(description: AudioTrackDescription): String {
    val head = if (description.recommended) {
        stringResource(R.string.audio_track_recommended) + " · " + audioTrackTitle(description)
    } else {
        audioTrackTitle(description)
    }
    return head + "\n" + audioTrackDetail(description)
}

@Composable internal fun captionTrackTitle(track: CaptionTrack): String =
    languageText(track.language) + " · " + (track.name ?: generationName(track.generation))

@Composable internal fun captionTrackOption(track: CaptionTrack): String {
    val detail = listOfNotNull(
        generationName(track.generation),
        if (track.translation == Translation.NONE) null else translationName(track.translation),
        track.format.uppercase(Locale.ROOT),
    ).joinToString(" · ")
    return languageText(track.language) + (track.name?.let { " · $it" } ?: "") + "\n" + detail
}

@Composable internal fun languageText(code: String?): String {
    if (code.isNullOrBlank()) return stringResource(R.string.unknown)
    val locale = Locale.forLanguageTag(code)
    val display = locale.getDisplayName(Locale.getDefault())
    return if (display.isBlank() || display.equals(code, ignoreCase = true)) {
        stringResource(R.string.language_value, code)
    } else {
        display
    }
}

@Composable internal fun originName(provenance: Provenance): String = when {
    provenance.origin == Origin.PROVIDER ->
        "${stringResource(R.string.external_stt)} · ${provenance.provider?.let(::providerName) ?: stringResource(R.string.unknown)}"
    provenance.generation == Generation.UPLOADER_PROVIDED -> stringResource(R.string.origin_uploader)
    provenance.generation == Generation.AUTOMATIC -> stringResource(R.string.origin_automatic)
    else -> stringResource(R.string.origin_unknown)
}

@Composable internal fun translationName(value: Translation): String = stringResource(when (value) {
    Translation.NONE -> R.string.translation_none
    Translation.AUTOMATIC -> R.string.translation_automatic
    Translation.UNKNOWN -> R.string.unknown
})

@Composable internal fun generationName(value: Generation): String = stringResource(when (value) {
    Generation.UPLOADER_PROVIDED -> R.string.origin_uploader
    Generation.AUTOMATIC -> R.string.origin_automatic
    Generation.UNKNOWN -> R.string.origin_unknown
})

@Composable internal fun messageText(code: String): String = when (code) {
    "NO_ACCEPTABLE_CAPTIONS" -> stringResource(R.string.no_captions)
    "NO_AUDIO" -> stringResource(R.string.no_audio)
    "CHOOSE_AUDIO_TRACK" -> stringResource(R.string.audio_track_choose_language)
    "AUDIO_TRACK_CHANGED" -> stringResource(R.string.audio_track_changed)
    "IMPORTED_AUDIO_NOT_FOUND" -> stringResource(R.string.reimport_audio)
    "MISSING_RETRY_DATA" -> stringResource(R.string.missing_retry_data)
    "MISSING_RETRY_RESPONSE_SAVED" -> stringResource(R.string.missing_retry_response_saved)
    "JOB_CONFIG_INVALID" -> stringResource(R.string.job_config_invalid)
    "ARTIFACT_FILE_MISSING" -> stringResource(R.string.artifact_file_missing)
    "SCHEDULING_FAILED" -> stringResource(R.string.scheduling_failed)
    "ACTION_BUSY" -> stringResource(R.string.action_busy)
    "CHOOSE_CAPTION_TRACK" -> stringResource(R.string.choose_caption_help)
    "CAPTION_TRACK_CHANGED" -> stringResource(R.string.caption_track_changed)
    "CREDENTIAL_NOT_FOUND", "KEY_NOT_FOUND" -> stringResource(R.string.restore_key)
    "KEY_LOCKED_OR_INVALIDATED", "CREDENTIAL_LOCKED_OR_INVALIDATED", "KEY_CORRUPT", "CREDENTIAL_CORRUPT" ->
        stringResource(R.string.key_unavailable_help)
    "KEY_STORAGE", "CREDENTIAL_STORAGE" -> stringResource(R.string.key_storage_help)
    "REMOTE_DELETE_CONFIRMED" -> stringResource(R.string.remote_delete_confirmed)
    "NO_REMOTE_HANDLE" -> stringResource(R.string.no_remote_handle)
    "DELETE_PENDING" -> stringResource(R.string.delete_pending)
    "NO_MISSING_BRANCH" -> stringResource(R.string.no_missing_branch)
    "JOB_STILL_RUNNING" -> stringResource(R.string.job_still_running)
    "SUBMISSION_UNCERTAIN" ->
        stringResource(R.string.state_submission_uncertain) + " · " + stringResource(R.string.remote_may_continue)
    "EXPORT_PENDING", "EXPORT_WRITING" -> stringResource(R.string.export_pending)
    "EXTERNAL_DOCUMENT_MISSING" -> stringResource(R.string.external_document_missing)
    "EXPORT_INTERRUPTED" -> stringResource(R.string.export_interrupted)
    "EXPORT_FAILED" -> stringResource(R.string.export_failed)
    "EXPORT_SCHEDULING_FAILED" -> stringResource(R.string.export_scheduling_failed)
    "JOBS_CREATED" -> stringResource(R.string.jobs_created)
    "KEY_SAVED" -> stringResource(R.string.key_saved)
    "PRESET_SAVED" -> stringResource(R.string.preset_saved)
    "DEFAULTS_SAVED" -> stringResource(R.string.defaults_saved)
    "FILE_NAME_SAVED" -> stringResource(R.string.file_name_saved)
    "CLIPBOARD_EMPTY" -> stringResource(R.string.clipboard_empty)
    "ENGINE_CURRENT" -> stringResource(R.string.engine_current)
    "ENGINE_ACTIVE" -> stringResource(R.string.engine_active)
    "EXPORT_EXPORTED" -> stringResource(R.string.export_complete)
    "EXPORT_PERMISSION_REQUIRED", "PERMISSION_REQUIRED" -> stringResource(R.string.export_permission_required)
    "NOTIFICATIONS_DENIED" -> stringResource(R.string.notifications_denied)
    "REMOTE_MAY_CONTINUE" -> stringResource(R.string.remote_may_continue)
    "PROVIDER_REQUIRED", "CREDENTIAL_REQUIRED", "UPLOAD_APPROVAL_REQUIRED", "MODEL_REQUIRED" ->
        stringResource(R.string.missing_provider)
    "STORAGE_LIMIT", "AUDIO_IMPORT_STORAGE_LIMIT", "DEVICE_STORAGE_LOW" -> stringResource(R.string.storage_full)
    "BUDGET_EXCEEDED" -> stringResource(R.string.budget_exceeded)
    "BUDGET_INVALID" -> stringResource(R.string.invalid_budget)
    "AUDIO_DURATION_LIMIT" -> stringResource(R.string.invalid_duration)
    "AUDIO_LONGER_THAN_LIMIT", "SOURCE_LONGER_THAN_LIMIT" -> stringResource(R.string.audio_longer_than_limit)
    "AUDIO_DURATION_UNKNOWN" -> stringResource(R.string.audio_duration_unknown)
    "UNSUPPORTED_OPTION", "PROVIDER_CAPABILITY_OR_CREDENTIAL_INVALID" -> stringResource(R.string.unsupported_options)
    "PRICE_UNKNOWN" -> stringResource(R.string.price_unknown)
    else -> stringResource(R.string.operation_failed) + "\n" + stringResource(R.string.error_detail, code)
}
