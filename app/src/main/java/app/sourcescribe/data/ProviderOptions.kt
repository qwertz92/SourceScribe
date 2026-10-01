package app.sourcescribe.data

import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderError
import app.sourcescribe.core.ProviderRejectionReason
import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.core.providers.OpenAiAdapter

/** The same provider-specific rules apply before acquisition and at submission. */
internal fun providerConfigurationError(config: JobConfig): String? {
    val adapter = when (config.provider) {
        Provider.GROQ -> GroqAdapter()
        Provider.ASSEMBLYAI -> AssemblyAiAdapter()
        Provider.OPENAI -> OpenAiAdapter()
        null -> return "PROVIDER_REQUIRED"
    }
    return try {
        adapter.validateConfiguration(config)
        null
    } catch (failure: ProviderError) {
        when (failure.failure?.reason) {
            ProviderRejectionReason.CONTEXT_TOO_LONG -> "PROVIDER_CONTEXT_TOO_LONG"
            ProviderRejectionReason.TOO_MANY_TERMS -> "PROVIDER_TOO_MANY_TERMS"
            ProviderRejectionReason.TERM_TOO_LONG -> "PROVIDER_TERM_TOO_LONG"
            ProviderRejectionReason.INVALID_LANGUAGE -> "PROVIDER_LANGUAGE_UNSUPPORTED"
            ProviderRejectionReason.INVALID_MODEL -> "PROVIDER_MODEL_UNSUPPORTED"
            ProviderRejectionReason.INVALID_OPTION -> "UNSUPPORTED_OPTION"
            else -> "UNSUPPORTED_OPTION"
        }
    }
}
