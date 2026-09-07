package app.sourcescribe.core.providers

import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderAdapter
import app.sourcescribe.core.ProviderCapabilities
import app.sourcescribe.core.ProviderError
import app.sourcescribe.core.ProviderErrorCode
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.ProviderTranscript
import app.sourcescribe.core.Region
import app.sourcescribe.core.ResponseSpool
import app.sourcescribe.core.SubmissionResult
import app.sourcescribe.core.TranscriptionRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody

class GroqAdapter(private val http: ProviderHttp = ProviderHttp()) : ProviderAdapter {
    override val provider: Provider = Provider.GROQ

    override fun capabilities(model: String): ProviderCapabilities = when (model) {
        MODEL_V3 -> profile(model, PRICE_V3_MICRO_USD_PER_HOUR)
        MODEL_TURBO -> profile(model, PRICE_TURBO_MICRO_USD_PER_HOUR)
        else -> throw ProviderError(ProviderErrorCode.UNSUPPORTED_OPTION)
    }

    override suspend fun submit(
        request: TranscriptionRequest,
        apiKey: String,
        spool: ResponseSpool,
    ): SubmissionResult {
        val model = SyncProviderSupport.model(request.config)
        val capabilities = capabilities(model)
        SyncProviderSupport.validateRequest(
            request,
            provider,
            capabilities,
            model,
            apiKey,
            promptLimited = true,
            minimumDurationMs = MIN_DURATION_MS,
        )
        val response = http.perform(
            Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Accept", "application/json")
                .post(body(request, model))
                .build(),
            mayCharge = true,
            spool = spool,
        )
        return SubmissionResult.Direct(parse(response, request, model))
    }

    override fun parseSavedResponse(raw: ByteArray, request: TranscriptionRequest): SubmissionResult {
        val model = SyncProviderSupport.model(request.config)
        val capabilities = capabilities(model)
        SyncProviderSupport.validateOptions(
            request,
            provider,
            capabilities,
            model,
            promptLimited = true,
            minimumDurationMs = MIN_DURATION_MS,
        )
        return SubmissionResult.Direct(parse(raw, request, model))
    }

    private fun parse(raw: ByteArray, request: TranscriptionRequest, model: String): ProviderTranscript =
        SyncTranscriptParser.parse(raw, request, provider, model)

    private fun body(request: TranscriptionRequest, model: String): RequestBody {
        val config = request.config
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", request.audio.name, request.audio.asRequestBody(
                SyncProviderSupport.normalizedMime(request.mimeType).toMediaTypeOrNull()
                    ?: throw ProviderError(ProviderErrorCode.INVALID_INPUT),
            ))
            .addFormDataPart("model", model)
        SyncProviderSupport.language(config)?.let { builder.addFormDataPart("language", it) }
        SyncProviderSupport.contextText(config.contextTerms)?.let { builder.addFormDataPart("prompt", it) }
        val wantsTimestamps = config.wordTimestamps || config.segmentTimestamps
        builder.addFormDataPart("response_format", if (wantsTimestamps) "verbose_json" else "json")
        if (config.wordTimestamps) builder.addFormDataPart("timestamp_granularities[]", "word")
        if (config.segmentTimestamps) builder.addFormDataPart("timestamp_granularities[]", "segment")
        return builder.build()
    }

    private fun profile(model: String, priceMicrousdPerHour: Long): ProviderCapabilities =
        ProviderCapabilities(
            provider = provider,
            model = model,
            maxUploadBytes = MAX_UPLOAD_BYTES,
            automaticLanguage = true,
            wordTimestamps = true,
            segmentTimestamps = true,
            diarization = false,
            contextTerms = true,
            regions = setOf(Region.US),
            pollable = false,
            remoteDeletion = false,
            priceMicrousdPerHour = priceMicrousdPerHour,
            priceAsOf = PRICE_AS_OF,
            pricingSource = PRICING_SOURCE,
            minimumBilledSeconds = MINIMUM_BILLED_SECONDS,
        )

    companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        const val MODEL_V3 = "whisper-large-v3"
        const val MODEL_TURBO = "whisper-large-v3-turbo"
        const val DEFAULT_MODEL = MODEL_TURBO
        const val MAX_UPLOAD_BYTES = 25_000_000L
        const val PRICE_V3_MICRO_USD_PER_HOUR = 111_000L
        const val PRICE_TURBO_MICRO_USD_PER_HOUR = 40_000L
        const val MINIMUM_BILLED_SECONDS = 10
        // Groq documents a 0.01-second minimum file length for direct transcription uploads.
        const val MIN_DURATION_MS = 10L
        const val PRICE_AS_OF = "2026-09-07"
        const val PRICING_SOURCE = "https://console.groq.com/docs/speech-to-text"
    }
}
