package app.sourcescribe.core

import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class ProviderCapabilities(
    val provider: Provider,
    val model: String,
    val maxUploadBytes: Long,
    val automaticLanguage: Boolean,
    val wordTimestamps: Boolean,
    val segmentTimestamps: Boolean,
    val diarization: Boolean,
    val contextTerms: Boolean,
    val regions: Set<Region>,
    val pollable: Boolean,
    val remoteDeletion: Boolean,
    val remoteCancellation: Boolean = false,
    val priceMicrousdPerHour: Long? = null,
    val priceAsOf: String? = null,
    val pricingSource: String? = null,
    val minimumBilledSeconds: Int = 0,
)

data class TranscriptionRequest(
    val audio: File,
    val mimeType: String,
    val config: JobConfig,
    val chunkIndex: Int = 0,
    val chunkStartMs: Long = 0,
    val durationMs: Long,
)

@Serializable
data class ProviderTranscript(
    val segments: List<Segment>,
    val language: String?,
    val requestedModel: String,
    val reportedModel: String? = null,
    val warnings: List<String> = emptyList(),
    val technicallyComplete: Boolean = true,
    val words: List<Segment> = emptyList(),
    val reportedLanguages: List<String> = emptyList(),
)

@Serializable
data class RemoteHandle(val provider: Provider, val region: Region, val id: String)

sealed interface SubmissionResult {
    data class Direct(val transcript: ProviderTranscript) : SubmissionResult
    data class Remote(val handle: RemoteHandle) : SubmissionResult
}

sealed interface PollResult {
    data class Waiting(val retryAfterSeconds: Long = 30) : PollResult
    data class Complete(val transcript: ProviderTranscript) : PollResult
}

enum class ProviderErrorCode {
    AUTHENTICATION, ACCESS_DENIED, INVALID_INPUT, UNSUPPORTED_OPTION, RATE_LIMIT, QUOTA,
    NETWORK, SERVER, SUBMISSION_UNCERTAIN, INVALID_RESPONSE, RESPONSE_STORAGE, REMOTE_FAILED,
}

class ProviderError(
    val code: ProviderErrorCode,
    val retryAfterSeconds: Long? = null,
    val httpStatus: Int? = null,
) : Exception(code.name)

/** Save charge-relevant submission/poll responses before parsing; non-billed upload receipts are transient. */
fun interface ResponseSpool { fun save(bytes: ByteArray) }

interface ProviderAdapter {
    val provider: Provider
    fun capabilities(model: String): ProviderCapabilities
    suspend fun submit(request: TranscriptionRequest, apiKey: String, spool: ResponseSpool): SubmissionResult
    fun parseSavedResponse(raw: ByteArray, request: TranscriptionRequest): SubmissionResult
    suspend fun poll(handle: RemoteHandle, request: TranscriptionRequest, apiKey: String, spool: ResponseSpool): PollResult {
        throw ProviderError(ProviderErrorCode.UNSUPPORTED_OPTION)
    }
    suspend fun deleteRemote(handle: RemoteHandle, apiKey: String) {
        throw ProviderError(ProviderErrorCode.UNSUPPORTED_OPTION)
    }
}
