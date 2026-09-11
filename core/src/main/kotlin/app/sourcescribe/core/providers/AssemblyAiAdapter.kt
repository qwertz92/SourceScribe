package app.sourcescribe.core.providers

import app.sourcescribe.core.parseBounded

import app.sourcescribe.core.PollResult
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderAdapter
import app.sourcescribe.core.ProviderCapabilities
import app.sourcescribe.core.ProviderError
import app.sourcescribe.core.ProviderErrorCode
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.ProviderTranscript
import app.sourcescribe.core.Region
import app.sourcescribe.core.RemoteHandle
import app.sourcescribe.core.ResponseSpool
import app.sourcescribe.core.Segment
import app.sourcescribe.core.SubmissionResult
import app.sourcescribe.core.TimeEvidence
import app.sourcescribe.core.TranscriptionRequest
import java.util.UUID
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** AssemblyAI pre-recorded transcription adapter with explicit region/model selection. */
class AssemblyAiAdapter(private val http: ProviderHttp = ProviderHttp()) : ProviderAdapter {
    override val provider: Provider = Provider.ASSEMBLYAI

    override fun capabilities(model: String): ProviderCapabilities {
        val price = when (model) {
            MODEL_U35 -> PRICE_U35_MICRO_USD_PER_HOUR
            MODEL_U2 -> PRICE_U2_MICRO_USD_PER_HOUR
            else -> unsupported()
        }
        return ProviderCapabilities(
            provider = provider,
            model = model,
            maxUploadBytes = MAX_UPLOAD_BYTES,
            automaticLanguage = true,
            wordTimestamps = true,
            segmentTimestamps = true,
            diarization = true,
            contextTerms = true,
            regions = setOf(Region.US, Region.EU),
            pollable = true,
            remoteDeletion = true,
            remoteCancellation = false,
            priceMicrousdPerHour = price,
            priceAsOf = PRICING_DATE,
            pricingSource = PRICING_SOURCE,
        )
    }

    override suspend fun submit(
        request: TranscriptionRequest,
        apiKey: String,
        spool: ResponseSpool,
    ): SubmissionResult {
        val validated = validateSubmission(request, apiKey)
        val baseUrl = baseUrl(validated.region)
        val upload = Request.Builder()
            .url("$baseUrl/v2/upload")
            .header("Authorization", apiKey.trim())
            .header("Content-Type", OCTET_STREAM)
            .post(request.audio.asRequestBody(OCTET_STREAM.toMediaType()))
            .build()
        val uploadRaw = http.perform(upload, mayCharge = false)
        val uploadUrl = parseUploadUrl(uploadRaw)

        val submitBody = buildJsonObject {
            put("audio_url", uploadUrl.toString())
            put("speech_models", buildJsonArray { add(JsonPrimitive(validated.model)) })
            put("punctuate", true)
            put("speaker_labels", request.config.diarization)
            if (request.config.language != null) {
                put("language_code", providerLanguageCode(request.config.language))
            } else {
                put("language_detection", true)
            }
            if (validated.contextTerms.isNotEmpty()) {
                put("keyterms_prompt", buildJsonArray {
                    validated.contextTerms.forEach { add(JsonPrimitive(it)) }
                })
            }
        }.toString().toRequestBody(JSON.toMediaType())

        val submit = Request.Builder()
            .url("$baseUrl/v2/transcript")
            .header("Authorization", apiKey.trim())
            .header("Content-Type", JSON)
            .post(submitBody)
            .build()
        return when (val decoded = decodeResponse(http.perform(submit, mayCharge = true, spool), request)) {
            is DecodedResponse.Waiting -> SubmissionResult.Remote(
                RemoteHandle(provider, validated.region, decoded.id),
            )
            is DecodedResponse.Complete -> SubmissionResult.Direct(decoded.transcript)
        }
    }

    override fun parseSavedResponse(raw: ByteArray, request: TranscriptionRequest): SubmissionResult {
        validateReplayRequest(request)
        return when (val decoded = decodeResponse(raw, request)) {
            is DecodedResponse.Waiting -> SubmissionResult.Remote(
                RemoteHandle(provider, request.config.region, decoded.id),
            )
            is DecodedResponse.Complete -> SubmissionResult.Direct(decoded.transcript)
        }
    }

    override suspend fun poll(
        handle: RemoteHandle,
        request: TranscriptionRequest,
        apiKey: String,
        spool: ResponseSpool,
    ): PollResult {
        val validated = validatePoll(request, apiKey)
        val id = canonicalUuid(handle.id, ProviderErrorCode.INVALID_INPUT)
        if (handle.provider != provider || handle.region != validated.region) invalidInput()
        val poll = Request.Builder()
            .url("${baseUrl(validated.region)}/v2/transcript/$id")
            .header("Authorization", apiKey.trim())
            .build()
        return when (val decoded = decodeResponse(http.perform(poll, mayCharge = false, spool), request, id)) {
            is DecodedResponse.Waiting -> PollResult.Waiting()
            is DecodedResponse.Complete -> PollResult.Complete(decoded.transcript)
        }
    }

    override suspend fun deleteRemote(handle: RemoteHandle, apiKey: String) {
        validateApiKey(apiKey)
        if (handle.provider != provider) invalidInput()
        val id = canonicalUuid(handle.id, ProviderErrorCode.INVALID_INPUT)
        // AssemblyAI DELETE removes the remote transcript and uploaded media; it is not cancellation or refund.
        val delete = Request.Builder()
            .url("${baseUrl(handle.region)}/v2/transcript/$id")
            .header("Authorization", apiKey.trim())
            .delete()
            .build()
        http.perform(delete, mayCharge = false)
    }

    private fun validateSubmission(request: TranscriptionRequest, apiKey: String): ValidatedRequest {
        val validated = validateConfig(request, apiKey, requireApiKey = true)
        val audio = request.audio
        if (!request.config.uploadApproved) invalidInput()
        if (!audio.exists() || !audio.isFile || !audio.canRead()) invalidInput()
        val bytes = audio.length()
        if (bytes <= 0 || bytes > MAX_UPLOAD_BYTES) invalidInput()
        val mime = request.mimeType.trim().lowercase(Locale.ROOT)
        if (request.mimeType.any { it == '\r' || it == '\n' } ||
            !mime.startsWith(AUDIO_MIME_PREFIX) || mime.length == AUDIO_MIME_PREFIX.length
        ) invalidInput()
        validateChunk(request)
        request.config.maxCostMicrousd?.let { budget ->
            if (budget < 0 || estimatedCostMicrousd(request.durationMs, validated) > budget) invalidInput()
        }
        return validated
    }

    private fun validatePoll(request: TranscriptionRequest, apiKey: String): ValidatedRequest {
        val validated = validateConfig(request, apiKey, requireApiKey = true)
        validateChunk(request)
        return validated
    }

    private fun validateReplayRequest(request: TranscriptionRequest) {
        validateConfig(request, apiKey = null, requireApiKey = false)
        validateChunk(request)
    }

    private fun validateChunk(request: TranscriptionRequest) {
        if (request.durationMs < MIN_DURATION_MS || request.durationMs > MAX_DURATION_MS ||
            request.chunkIndex < 0 || request.chunkStartMs < 0 ||
            request.chunkStartMs > Long.MAX_VALUE - request.durationMs
        ) invalidInput()
        val configuredMaxMs = request.config.maxAudioSeconds
            .coerceAtMost(MAX_DURATION_MS / MILLIS_PER_SECOND)
            .times(MILLIS_PER_SECOND)
        if (request.durationMs > configuredMaxMs) invalidInput()
    }

    private fun validateConfig(
        request: TranscriptionRequest,
        apiKey: String?,
        requireApiKey: Boolean,
    ): ValidatedRequest {
        if (request.config.provider != provider) invalidInput()
        val model = request.config.model?.takeIf { it.isNotBlank() } ?: invalidInput()
        if (model !in SUPPORTED_MODELS) unsupported()
        if (requireApiKey) validateApiKey(apiKey ?: "")
        val terms = request.config.contextTerms.map { term ->
            val trimmed = term.trim()
            if (trimmed.isEmpty() || trimmed.split(WHITESPACE).size > MAX_WORDS_PER_TERM) invalidInput()
            trimmed
        }
        val maxTerms = if (model == MODEL_U2) MAX_TERMS_U2 else MAX_TERMS_U35
        if (terms.size > maxTerms) invalidInput()
        if (request.config.maxAudioSeconds <= 0) invalidInput()
        request.config.language?.let { validateLanguage(it, model) }
        return ValidatedRequest(model, request.config.region, terms, request.config.diarization)
    }

    private fun validateLanguage(value: String, model: String) {
        val language = value.trim()
        if (!LANGUAGE_PATTERN.matches(language) || language !in supportedLanguages(model)) invalidInput()
    }

    private fun supportedLanguages(model: String): Set<String> = when (model) {
        MODEL_U35 -> UNIVERSAL_35_LANGUAGES
        MODEL_U2 -> UNIVERSAL_2_LANGUAGES
        else -> unsupported()
    }

    private fun providerLanguageCode(value: String): String =
        value.trim().replace('-', '_').lowercase(Locale.ROOT)

    private fun parseUploadUrl(raw: ByteArray): HttpUrl {
        val objectValue = parseObject(raw)
        val value = requiredString(objectValue, "upload_url")
        val url = try {
            value.toHttpUrl()
        } catch (_: IllegalArgumentException) {
            invalidResponse()
        }
        if (!url.isHttps || url.port != HTTPS_PORT || url.host != CDN_HOST ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null ||
            url.fragment != null || !url.encodedPath.startsWith("/upload/") ||
            url.encodedPath.substringAfterLast('/').isBlank()
        ) invalidResponse()
        // AssemblyAI returns a region-neutral CDN URL; upload and submit remain on the selected fixed API origin.
        return url
    }

    private fun decodeResponse(
        raw: ByteArray,
        request: TranscriptionRequest,
        expectedId: String? = null,
    ): DecodedResponse {
        val objectValue = parseObject(raw)
        val id = canonicalUuid(requiredString(objectValue, "id"), ProviderErrorCode.INVALID_RESPONSE)
        if (expectedId != null && id != expectedId) invalidResponse()
        return when (requiredString(objectValue, "status")) {
            STATUS_QUEUED, STATUS_PROCESSING -> DecodedResponse.Waiting(id)
            STATUS_COMPLETED -> DecodedResponse.Complete(parseTranscript(objectValue, request))
            STATUS_ERROR -> throw ProviderError(ProviderErrorCode.REMOTE_FAILED)
            else -> invalidResponse()
        }
    }

    private fun parseTranscript(objectValue: JsonObject, request: TranscriptionRequest): ProviderTranscript {
        val text = requiredString(objectValue, "text")
        val model = request.config.model?.takeIf { it in SUPPORTED_MODELS } ?: invalidResponse()
        val warnings = Warnings()
        val reportedLanguages = parseReportedLanguages(objectValue, warnings)
        val language = reportedLanguages.firstOrNull()
        val reportedModel = parseReportedModel(objectValue, warnings)
        val parsedWords = parseWords(objectValue, warnings, request.config.diarization)
        val parsedUtterances = parseUtterances(objectValue, warnings, request.config.diarization)
        val words = toWordSegments(parsedWords.entries, request, warnings)
        val utterances = toUtteranceSegments(parsedUtterances.entries, request, warnings)

        var technicallyComplete = !parsedWords.malformed && !parsedUtterances.malformed
        if (words.size != parsedWords.entries.size) technicallyComplete = false
        if (utterances.size != parsedUtterances.entries.size) technicallyComplete = false
        if (request.config.wordTimestamps && words.isEmpty()) {
            warnings += WARNING_WORD_TIMESTAMPS_MISSING
            technicallyComplete = false
        }
        if (request.config.diarization &&
            (utterances.isEmpty() || parsedUtterances.missingSpeakers)
        ) {
            warnings += WARNING_DIARIZATION_MISSING
            technicallyComplete = false
        }
        if (request.config.segmentTimestamps && utterances.isEmpty()) {
            warnings += WARNING_SEGMENT_TIMESTAMPS_MISSING
            technicallyComplete = false
        }

        // Keep the complete provider text in segments. Word-level evidence lives in the
        // dedicated words field so requesting both granularities cannot discard either one.
        val segments = if (utterances.isNotEmpty() &&
            !parsedUtterances.malformed &&
            utterances.size == parsedUtterances.entries.size
        ) {
            utterances
        } else {
            listOf(fullTextSegment(text, words, request))
        }
        return ProviderTranscript(
            segments = segments,
            language = language,
            requestedModel = model,
            reportedModel = reportedModel,
            warnings = warnings.toList(),
            technicallyComplete = technicallyComplete,
            words = words,
            reportedLanguages = reportedLanguages,
        )
    }

    private fun parseWords(
        objectValue: JsonObject,
        warnings: Warnings,
        speakerRequired: Boolean,
    ): ParsedWords {
        val value = objectValue["words"] ?: return ParsedWords(emptyList(), malformed = false)
        if (value is JsonNull) return ParsedWords(emptyList(), malformed = false)
        val array = value as? JsonArray ?: run {
            warnings += WARNING_WORD_TIMESTAMPS_MALFORMED
            return ParsedWords(emptyList(), malformed = true)
        }
        var malformed = false
        var previousStart = -1L
        val entries = ArrayList<WordEvidence>(array.size)
        array.forEachIndexed { index, element ->
            val word = element as? JsonObject
            if (word == null) {
                warnings += "${WARNING_WORD_TIMESTAMPS_MALFORMED}_$index"
                malformed = true
                return@forEachIndexed
            }
            val text = optionalEntryText(word, "text")
            val start = optionalEntryTimestamp(word, "start")
            val end = optionalEntryTimestamp(word, "end")
            if (text == null || start == null || end == null || end <= start || start < previousStart) {
                warnings += "${WARNING_WORD_TIMESTAMPS_MALFORMED}_$index"
                malformed = true
                return@forEachIndexed
            }
            val parsedSpeaker = parseSpeaker(word, "speaker")
            if (parsedSpeaker.malformed && speakerRequired) {
                warnings += "${WARNING_WORD_TIMESTAMPS_MALFORMED}_SPEAKER_$index"
                malformed = true
            }
            val parsed = WordEvidence(text, start, end, parsedSpeaker.value)
            entries += parsed
            previousStart = parsed.start
        }
        return ParsedWords(entries, malformed)
    }

    private fun parseUtterances(
        objectValue: JsonObject,
        warnings: Warnings,
        speakerRequired: Boolean,
    ): ParsedUtterances {
        val value = objectValue["utterances"] ?: return ParsedUtterances(emptyList(), false, false)
        if (value is JsonNull) return ParsedUtterances(emptyList(), false, false)
        val array = value as? JsonArray ?: run {
            warnings += WARNING_DIARIZATION_MALFORMED
            return ParsedUtterances(emptyList(), malformed = true, missingSpeakers = false)
        }
        var malformed = false
        var missingSpeakers = false
        var previousStart = -1L
        val entries = ArrayList<UtteranceEvidence>(array.size)
        array.forEachIndexed { index, element ->
            val utterance = element as? JsonObject
            if (utterance == null) {
                warnings += "${WARNING_DIARIZATION_MALFORMED}_$index"
                malformed = true
                return@forEachIndexed
            }
            val text = optionalEntryText(utterance, "text")
            val start = optionalEntryTimestamp(utterance, "start")
            val end = optionalEntryTimestamp(utterance, "end")
            if (text == null || start == null || end == null || end <= start || start < previousStart) {
                warnings += "${WARNING_DIARIZATION_MALFORMED}_$index"
                malformed = true
                return@forEachIndexed
            }
            val parsedSpeaker = parseSpeaker(utterance, "speaker")
            if (speakerRequired && (parsedSpeaker.value == null || parsedSpeaker.malformed)) {
                warnings += "${WARNING_DIARIZATION_SPEAKER_MISSING}_$index"
                missingSpeakers = true
            }
            if (speakerRequired && parsedSpeaker.malformed) malformed = true
            val parsed = UtteranceEvidence(text, start, end, parsedSpeaker.value)
            entries += parsed
            previousStart = parsed.start
        }
        return ParsedUtterances(entries, malformed, missingSpeakers)
    }

    private fun parseObject(raw: ByteArray): JsonObject {
        if (raw.isEmpty() || raw.size > MAX_RESPONSE_BYTES) invalidResponse()
        return try {
            JSON_PARSER.parseBounded(raw.toString(Charsets.UTF_8)) as? JsonObject ?: invalidResponse()
        } catch (failure: ProviderError) {
            throw failure
        } catch (_: Exception) {
            invalidResponse()
        }
    }

    private fun requiredString(objectValue: JsonObject, key: String): String {
        val value = objectValue[key] as? JsonPrimitive ?: invalidResponse()
        if (!value.isString || value.content.isBlank()) invalidResponse()
        return value.content
    }

    private fun optionalEntryText(objectValue: JsonObject, key: String): String? {
        val primitive = objectValue[key] as? JsonPrimitive ?: return null
        return primitive.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    }

    private fun optionalEntryTimestamp(objectValue: JsonObject, key: String): Long? {
        val primitive = objectValue[key] as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.content.toLongOrNull()?.takeIf { it >= 0 }
    }

    private fun parseSpeaker(objectValue: JsonObject, key: String): ParsedSpeaker {
        val value = objectValue[key] ?: return ParsedSpeaker(null, malformed = false)
        if (value is JsonNull) return ParsedSpeaker(null, malformed = false)
        val primitive = value as? JsonPrimitive ?: return ParsedSpeaker(null, malformed = true)
        if (!primitive.isString) return ParsedSpeaker(null, malformed = true)
        return ParsedSpeaker(primitive.content.takeIf { it.isNotBlank() }, malformed = false)
    }

    private fun toWordSegments(
        entries: List<WordEvidence>,
        request: TranscriptionRequest,
        warnings: Warnings,
    ): List<Segment> = entries.mapIndexedNotNull { index, word ->
        // AssemblyAI times are chunk-relative milliseconds: validate their raw extent
        // first, then apply the chunk offset only to retained provider evidence.
        if (word.start > request.durationMs || word.end > request.durationMs) {
            warnings += "${WARNING_WORD_TIMESTAMPS_OUT_OF_RANGE}_$index"
            null
        } else {
            val start = offsetTimestampOrNull(word.start, request.chunkStartMs)
            val end = offsetTimestampOrNull(word.end, request.chunkStartMs)
            if (start == null || end == null || end <= start) {
                warnings += "${WARNING_WORD_TIMESTAMPS_MALFORMED}_OFFSET_$index"
                null
            } else {
                Segment(
                    text = word.text,
                    startMs = start,
                    endMs = end,
                    speaker = scopeSpeaker(word.speaker, request.chunkIndex),
                    timeEvidence = TimeEvidence.PROVIDER_WORD,
                    chunkIndex = request.chunkIndex,
                )
            }
        }
    }

    private fun toUtteranceSegments(
        entries: List<UtteranceEvidence>,
        request: TranscriptionRequest,
        warnings: Warnings,
    ): List<Segment> = entries.mapIndexedNotNull { index, utterance ->
        // Utterance times follow the same chunk-relative millisecond rule as words.
        if (utterance.start > request.durationMs || utterance.end > request.durationMs) {
            warnings += "${WARNING_DIARIZATION_OUT_OF_RANGE}_$index"
            null
        } else {
            val start = offsetTimestampOrNull(utterance.start, request.chunkStartMs)
            val end = offsetTimestampOrNull(utterance.end, request.chunkStartMs)
            if (start == null || end == null || end <= start) {
                warnings += "${WARNING_DIARIZATION_MALFORMED}_OFFSET_$index"
                null
            } else {
                Segment(
                    text = utterance.text,
                    startMs = start,
                    endMs = end,
                    speaker = scopeSpeaker(utterance.speaker, request.chunkIndex),
                    timeEvidence = TimeEvidence.PROVIDER_SEGMENT,
                    chunkIndex = request.chunkIndex,
                )
            }
        }
    }

    private fun fullTextSegment(
        text: String,
        words: List<Segment>,
        request: TranscriptionRequest,
    ): Segment {
        val useWordExtent = !request.config.segmentTimestamps
        val timedWords = words.filter {
            it.startMs != null && it.endMs != null && it.timeEvidence == TimeEvidence.PROVIDER_WORD
        }
        return Segment(
            text = text,
            startMs = timedWords.firstOrNull()?.startMs?.takeIf { useWordExtent },
            endMs = timedWords.lastOrNull()?.endMs?.takeIf { useWordExtent },
            speaker = timedWords.mapNotNull { it.speaker }.distinct().singleOrNull(),
            timeEvidence = if (useWordExtent && timedWords.isNotEmpty()) {
                TimeEvidence.PROVIDER_WORD
            } else {
                TimeEvidence.UNKNOWN
            },
            chunkIndex = request.chunkIndex,
        )
    }

    /**
     * A language tag in the shapes a provider actually reports: `en`, `en_us`, `zh-CN`. The value is stored
     * with the transcript and shown as its language, so it is bounded here instead of being taken at
     * whatever length and character set an answer happens to carry.
     */
    private val reportedLanguage = Regex("[A-Za-z0-9]{1,8}([_-][A-Za-z0-9]{1,8}){0,3}")

    private fun parseReportedLanguages(
        objectValue: JsonObject,
        warnings: Warnings,
    ): List<String> {
        val result = ArrayList<String>()
        val direct = optionalMetadataString(objectValue, "language_code", warnings)
        when {
            direct == null -> Unit
            reportedLanguage.matches(direct) -> result += direct
            else -> warnings += WARNING_REPORTED_LANGUAGES_MALFORMED
        }
        val values = objectValue["language_codes"]
        when {
            values == null || values is JsonNull -> Unit
            values is JsonArray -> values.forEachIndexed { index, value ->
                val language = (value as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
                    ?.takeIf { reportedLanguage.matches(it) }
                if (language == null) warnings += "${WARNING_REPORTED_LANGUAGES_MALFORMED}_$index"
                else result += language
            }
            else -> warnings += WARNING_REPORTED_LANGUAGES_MALFORMED
        }
        return result.distinct()
    }

    private fun parseReportedModel(objectValue: JsonObject, warnings: Warnings): String? =
        optionalMetadataString(objectValue, "speech_model_used", warnings, WARNING_REPORTED_MODEL_MALFORMED)

    private fun optionalMetadataString(
        objectValue: JsonObject,
        key: String,
        warnings: Warnings,
        warning: String = WARNING_REPORTED_LANGUAGES_MALFORMED,
    ): String? {
        val value = objectValue[key] ?: return null
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive
        val result = primitive?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        if (result == null) warnings += warning
        return result
    }

    private fun offsetTimestampOrNull(timestamp: Long, offset: Long): Long? =
        if (timestamp >= 0 && offset >= 0 && timestamp <= Long.MAX_VALUE - offset) {
            timestamp + offset
        } else {
            null
        }

    private fun scopeSpeaker(speaker: String?, chunkIndex: Int): String? =
        speaker?.takeIf { it.isNotBlank() }?.let { "chunk-$chunkIndex:$it" }

    private fun canonicalUuid(value: String, code: ProviderErrorCode): String {
        if (!UUID_PATTERN.matches(value)) throw ProviderError(code)
        return try {
            UUID.fromString(value).toString()
        } catch (_: IllegalArgumentException) {
            throw ProviderError(code)
        }
    }

    private fun estimatedCostMicrousd(durationMs: Long, request: ValidatedRequest): Long {
        val hourly = when (request.model) {
            MODEL_U35 -> PRICE_U35_MICRO_USD_PER_HOUR
            MODEL_U2 -> PRICE_U2_MICRO_USD_PER_HOUR
            else -> unsupported()
        } + if (request.diarization) SPEAKER_LABELS_MICRO_USD_PER_HOUR else 0
        val withTerms = if (request.model == MODEL_U35 && request.contextTerms.isNotEmpty()) {
            hourly + KEYTERMS_U35_MICRO_USD_PER_HOUR
        } else hourly
        return (durationMs * withTerms + MILLIS_PER_HOUR - 1) / MILLIS_PER_HOUR
    }

    private fun validateApiKey(apiKey: String) {
        if (apiKey.isBlank() || apiKey.any { it == '\r' || it == '\n' }) invalidInput()
    }

    private fun baseUrl(region: Region): String = when (region) {
        Region.US -> US_BASE_URL
        Region.EU -> EU_BASE_URL
    }

    private fun invalidInput(): Nothing = throw ProviderError(ProviderErrorCode.INVALID_INPUT)
    private fun invalidResponse(): Nothing = throw ProviderError(ProviderErrorCode.INVALID_RESPONSE)
    private fun unsupported(): Nothing = throw ProviderError(ProviderErrorCode.UNSUPPORTED_OPTION)

    private data class ValidatedRequest(
        val model: String,
        val region: Region,
        val contextTerms: List<String>,
        val diarization: Boolean,
    )

    private sealed interface DecodedResponse {
        data class Waiting(val id: String) : DecodedResponse
        data class Complete(val transcript: ProviderTranscript) : DecodedResponse
    }

    private data class WordEvidence(val text: String, val start: Long, val end: Long, val speaker: String?)
    private data class UtteranceEvidence(val text: String, val start: Long, val end: Long, val speaker: String?)
    private data class ParsedWords(val entries: List<WordEvidence>, val malformed: Boolean)
    private data class ParsedUtterances(
        val entries: List<UtteranceEvidence>,
        val malformed: Boolean,
        val missingSpeakers: Boolean,
    )
    private data class ParsedSpeaker(val value: String?, val malformed: Boolean)

    companion object {
        const val MODEL_U35 = "universal-3-5-pro"
        const val MODEL_U2 = "universal-2"
        const val MAX_UPLOAD_BYTES = 2_200_000_000L
        const val MAX_DURATION_MS = 36_000_000L
        const val MIN_DURATION_MS = 160L
        const val PRICE_U35_MICRO_USD_PER_HOUR = 210_000L
        const val PRICE_U2_MICRO_USD_PER_HOUR = 150_000L
        const val SPEAKER_LABELS_MICRO_USD_PER_HOUR = 20_000L
        const val KEYTERMS_U35_MICRO_USD_PER_HOUR = 50_000L
        const val PRICING_DATE = "2026-09-07"
        const val PRICING_SOURCE = "https://www.assemblyai.com/pricing/"

        private const val US_BASE_URL = "https://api.assemblyai.com"
        private const val EU_BASE_URL = "https://api.eu.assemblyai.com"
        private const val CDN_HOST = "cdn.assemblyai.com"
        private const val HTTPS_PORT = 443
        private const val OCTET_STREAM = "application/octet-stream"
        private const val AUDIO_MIME_PREFIX = "audio/"
        private const val JSON = "application/json"
        private const val STATUS_QUEUED = "queued"
        private const val STATUS_PROCESSING = "processing"
        private const val STATUS_COMPLETED = "completed"
        private const val STATUS_ERROR = "error"
        private const val MAX_WORDS_PER_TERM = 6
        private const val MAX_TERMS_U2 = 200
        private const val MAX_TERMS_U35 = 1000
        private const val MILLIS_PER_SECOND = 1000L
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
        private const val WARNING_WORD_TIMESTAMPS_MISSING = "WORD_TIMESTAMPS_MISSING"
        private const val WARNING_WORD_TIMESTAMPS_MALFORMED = "WORD_TIMESTAMPS_MALFORMED"
        private const val WARNING_WORD_TIMESTAMPS_OUT_OF_RANGE = "WORD_TIMESTAMPS_OUT_OF_RANGE"
        private const val WARNING_DIARIZATION_MISSING = "DIARIZATION_MISSING"
        private const val WARNING_DIARIZATION_MALFORMED = "DIARIZATION_MALFORMED"
        private const val WARNING_DIARIZATION_OUT_OF_RANGE = "DIARIZATION_OUT_OF_RANGE"
        private const val WARNING_DIARIZATION_SPEAKER_MISSING = "DIARIZATION_SPEAKER_MISSING"
        private const val WARNING_SEGMENT_TIMESTAMPS_MISSING = "SEGMENT_TIMESTAMPS_MISSING"
        private const val WARNING_REPORTED_LANGUAGES_MALFORMED = "REPORTED_LANGUAGES_MALFORMED"
        private const val WARNING_REPORTED_MODEL_MALFORMED = "REPORTED_MODEL_MALFORMED"
        private val SUPPORTED_MODELS = setOf(MODEL_U35, MODEL_U2)
        private val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
        private val LANGUAGE_PATTERN = Regex("^[a-z]{2,3}(-[A-Z]{2})?$")
        private val WHITESPACE = Regex("\\s+")
        private val UNIVERSAL_35_LANGUAGES = setOf(
            "en", "es", "fr", "de", "it", "pt", "ar", "da", "nl", "fi",
            "he", "hi", "ja", "zh", "no", "sv", "tr", "vi",
        )
        private val UNIVERSAL_2_LANGUAGES = setOf(
            "en", "en-AU", "en-UK", "en-US", "es", "fr", "de", "it", "pt", "nl",
            "hi", "ja", "zh", "fi", "ko", "pl", "ru", "tr", "uk", "vi", "af", "sq",
            "am", "ar", "hy", "as", "az", "ba", "eu", "be", "bn", "bs", "br", "bg",
            "my", "ca", "hr", "cs", "da", "et", "fo", "gl", "ka", "el", "gu", "ht",
            "ha", "haw", "he", "hu", "is", "id", "jw", "kn", "kk", "km", "lo", "la",
            "lv", "ln", "lt", "lb", "mk", "mg", "ms", "ml", "mt", "mi", "mr", "mn",
            "ne", "no", "nn", "oc", "pa", "ps", "fa", "ro", "sa", "sr", "sn", "sd",
            "si", "sk", "sl", "so", "su", "sw", "sv", "tl", "tg", "ta", "tt", "te",
            "th", "bo", "tk", "ur", "uz", "cy", "yi", "yo",
        )
        private val JSON_PARSER = Json { ignoreUnknownKeys = true }
    }
}
