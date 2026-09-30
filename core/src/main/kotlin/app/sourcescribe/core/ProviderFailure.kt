package app.sourcescribe.core

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderOperation { UPLOAD, SUBMIT, RETRIEVE, DELETE }

@Serializable
enum class ProviderRejectionReason {
    FILE_TOO_LARGE,
    UNSUPPORTED_MEDIA,
    INVALID_MODEL,
    INVALID_LANGUAGE,
    INVALID_OPTION,
    INVALID_AUDIO,
    CONTEXT_TOO_LONG,
    TOO_MANY_TERMS,
    TERM_TOO_LONG,
    UNKNOWN,
}

@Serializable
data class ProviderFailure(
    val httpStatus: Int? = null,
    val operation: ProviderOperation? = null,
    val reason: ProviderRejectionReason? = null,
)
