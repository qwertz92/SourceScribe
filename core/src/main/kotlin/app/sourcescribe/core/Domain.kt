package app.sourcescribe.core

import kotlinx.serialization.Serializable

@Serializable enum class SourceKind { YOUTUBE, LOCAL_AUDIO }
@Serializable enum class AcquisitionMode { CAPTIONS_ONLY, CAPTIONS_THEN_STT, STT_ONLY, BOTH }
@Serializable enum class Provider { GROQ, OPENAI, ASSEMBLYAI }
@Serializable enum class Region { US, EU }
@Serializable enum class Branch { CAPTIONS, STT }
@Serializable enum class ExecutionState { QUEUED, RUNNING, WAITING_NETWORK, WAITING_RATE_LIMIT, WAITING_USER, WAITING_REMOTE, SUBMISSION_UNCERTAIN, FINISHED, CANCELLED }
@Serializable enum class Phase { RESOLVE, FETCH_CAPTIONS, DOWNLOAD_AUDIO, PREPARE_AUDIO, UPLOAD, SUBMIT, RETRIEVE, NORMALIZE, PERSIST }
@Serializable enum class Outcome { NONE, SUCCESS, SUCCESS_WITH_WARNINGS, PARTIAL_SUCCESS, FAILED, CANCELLED }
@Serializable enum class ExportState { NOT_REQUESTED, PENDING, WRITING, EXPORTED, PERMISSION_REQUIRED, FAILED }
@Serializable enum class ExportFormat { MARKDOWN, TEXT, JSON, SRT, VTT, RAW }
@Serializable enum class AudioRetention { TEMPORARY, UNTIL_PERSISTED, KEEP }
@Serializable enum class NetworkPolicy { ANY, UNMETERED }
@Serializable enum class Generation { UPLOADER_PROVIDED, AUTOMATIC, UNKNOWN }
@Serializable enum class Translation { NONE, AUTOMATIC, UNKNOWN }
@Serializable enum class Origin { YOUTUBE, PROVIDER }
@Serializable enum class TimeEvidence { CAPTION_CUE, PROVIDER_WORD, PROVIDER_SEGMENT, UNKNOWN }

@Serializable
data class Source(
    val id: String,
    val kind: SourceKind,
    val canonicalUrl: String? = null,
    val videoId: String? = null,
    val contentHash: String? = null,
    val title: String? = null,
    val channel: String? = null,
    val durationMs: Long? = null,
    val publishedDate: String? = null,
    val thumbnailUrl: String? = null,
    val originalLanguage: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileBytes: Long? = null,
)

@Serializable
data class CaptionTrack(
    val id: String,
    val sourceVideoId: String,
    val language: String,
    val name: String?,
    val format: String,
    val generation: Generation,
    val translation: Translation,
    val evidence: String,
)

@Serializable
data class AudioTrack(
    val id: String,
    val sourceVideoId: String,
    val language: String?,
    val name: String?,
    val isOriginal: Boolean?,
    val evidence: String,
)

@Serializable
data class JobConfig(
    val mode: AcquisitionMode = AcquisitionMode.CAPTIONS_THEN_STT,
    val provider: Provider? = null,
    val model: String? = null,
    val region: Region = Region.US,
    val credentialId: String? = null,
    val preferredLanguages: List<String> = listOf("de", "en"),
    val preferOriginalLanguage: Boolean = true,
    val allowUploaderCaptions: Boolean = true,
    val allowAutomaticCaptions: Boolean = true,
    val allowTranslatedCaptions: Boolean = false,
    val captionTrackId: String? = null,
    val audioTrackId: String? = null,
    val fallbackOnCaptionError: Boolean = false,
    val language: String? = null,
    val diarization: Boolean = false,
    val wordTimestamps: Boolean = false,
    val segmentTimestamps: Boolean = true,
    val contextTerms: List<String> = emptyList(),
    val exportFormats: Set<ExportFormat> = setOf(ExportFormat.MARKDOWN),
    val exportTreeUri: String? = null,
    val retainRaw: Boolean = false,
    val audioRetention: AudioRetention = AudioRetention.UNTIL_PERSISTED,
    val networkPolicy: NetworkPolicy = NetworkPolicy.ANY,
    val uploadApproved: Boolean = false,
    val maxAudioSeconds: Long = 3600,
    val maxCostMicrousd: Long? = null,
)

@Serializable
data class AppSettings(
    val defaults: JobConfig = JobConfig(),
    val presets: Map<String, JobConfig> = emptyMap(),
    val parallelJobs: Int = 2,
    val storageLimitBytes: Long = 2L * 1024 * 1024 * 1024,
    val theme: String = "SYSTEM",
)

@Serializable
data class Provenance(
    val origin: Origin,
    val generation: Generation = Generation.UNKNOWN,
    val translation: Translation = Translation.UNKNOWN,
    val provider: Provider? = null,
    val requestedModel: String? = null,
    val reportedModel: String? = null,
    val sourceAudioTrack: AudioTrack? = null,
    val captionTrack: CaptionTrack? = null,
    val languageEvidence: String? = null,
    val engineVersions: Map<String, String> = emptyMap(),
    val reportedLanguages: List<String> = emptyList(),
)

@Serializable
data class Segment(
    val text: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val speaker: String? = null,
    val timeEvidence: TimeEvidence = TimeEvidence.UNKNOWN,
    val chunkIndex: Int? = null,
)

@Serializable data class Interval(val startMs: Long, val endMs: Long)
@Serializable
data class TranscriptScope(
    val requestedDurationMs: Long? = null,
    val processedIntervals: List<Interval> = emptyList(),
    val missingChunks: List<Int> = emptyList(),
    val technicallyComplete: Boolean? = null,
)

@Serializable
data class TranscriptDocument(
    val schemaVersion: Int = 1,
    val artifactId: String,
    val source: Source,
    val acquisition: JobConfig,
    val provenance: Provenance,
    val language: String? = null,
    val scope: TranscriptScope = TranscriptScope(),
    val segments: List<Segment>,
    val warnings: List<String> = emptyList(),
    val createdAt: Long,
    val rawHash: String? = null,
    val normalizationVersion: String = "1",
    val words: List<Segment> = emptyList(),
) {
    val text: String get() = segments.joinToString("\n") { it.text }
}
