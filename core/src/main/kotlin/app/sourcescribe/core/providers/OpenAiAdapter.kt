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

class OpenAiAdapter(private val http: ProviderHttp = ProviderHttp()) : ProviderAdapter {
    override val provider: Provider = Provider.OPENAI

    override fun capabilities(model: String): ProviderCapabilities = when (model) {
        MODEL_GPT_TRANSCRIBE -> profile(model, false, false, false, true, PRICE_GPT_TRANSCRIBE_MICRO_USD_PER_HOUR)
        MODEL_WHISPER_1 -> profile(model, true, true, false, true, PRICE_WHISPER_MICRO_USD_PER_HOUR)
        MODEL_GPT_4O_TRANSCRIBE_DIARIZE -> profile(model, false, true, true, false, null)
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
            promptLimited = model == MODEL_WHISPER_1,
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
            promptLimited = model == MODEL_WHISPER_1,
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
        when (model) {
            MODEL_GPT_TRANSCRIBE -> {
                SyncProviderSupport.contextKeywords(config.contextTerms)
                    .forEach { builder.addFormDataPart("keywords[]", it) }
                builder.addFormDataPart("response_format", "json")
            }
            MODEL_WHISPER_1 -> {
                SyncProviderSupport.contextText(config.contextTerms)?.let { builder.addFormDataPart("prompt", it) }
                val wantsTimestamps = config.wordTimestamps || config.segmentTimestamps
                builder.addFormDataPart("response_format", if (wantsTimestamps) "verbose_json" else "json")
                if (config.wordTimestamps) builder.addFormDataPart("timestamp_granularities[]", "word")
                if (config.segmentTimestamps) builder.addFormDataPart("timestamp_granularities[]", "segment")
            }
            MODEL_GPT_4O_TRANSCRIBE_DIARIZE -> {
                builder.addFormDataPart("response_format", "diarized_json")
                if (request.durationMs > DIARIZATION_AUTO_CHUNKING_MS) {
                    builder.addFormDataPart("chunking_strategy", "auto")
                }
            }
            else -> throw ProviderError(ProviderErrorCode.UNSUPPORTED_OPTION)
        }
        return builder.build()
    }

    private fun profile(
        model: String,
        wordTimestamps: Boolean,
        segmentTimestamps: Boolean,
        diarization: Boolean,
        contextTerms: Boolean,
        priceMicrousdPerHour: Long?,
    ): ProviderCapabilities = ProviderCapabilities(
        provider = provider,
        model = model,
        maxUploadBytes = MAX_UPLOAD_BYTES,
        automaticLanguage = true,
        wordTimestamps = wordTimestamps,
        segmentTimestamps = segmentTimestamps,
        diarization = diarization,
        contextTerms = contextTerms,
        regions = setOf(Region.US),
        pollable = false,
        remoteDeletion = false,
        priceMicrousdPerHour = priceMicrousdPerHour,
        priceAsOf = PRICE_AS_OF,
        pricingSource = PRICING_SOURCE,
    )

    companion object {
        const val ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        const val MODEL_GPT_TRANSCRIBE = "gpt-transcribe"
        const val MODEL_WHISPER_1 = "whisper-1"
        const val MODEL_GPT_4O_TRANSCRIBE_DIARIZE = "gpt-4o-transcribe-diarize"
        const val DEFAULT_MODEL = MODEL_GPT_TRANSCRIBE
        const val MAX_UPLOAD_BYTES = 25_000_000L
        const val PRICE_GPT_TRANSCRIBE_MICRO_USD_PER_HOUR = 270_000L
        const val PRICE_WHISPER_MICRO_USD_PER_HOUR = 360_000L
        const val DIARIZATION_AUTO_CHUNKING_MS = 30_000L
        const val PRICE_AS_OF = "2026-09-07"
        const val PRICING_SOURCE = "https://developers.openai.com/api/docs/guides/speech-to-text"
    }
}
