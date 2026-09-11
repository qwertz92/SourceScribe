package app.sourcescribe.core.providers

import app.sourcescribe.core.parseBounded

import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderCapabilities
import app.sourcescribe.core.ProviderError
import app.sourcescribe.core.ProviderErrorCode
import app.sourcescribe.core.ProviderTranscript
import app.sourcescribe.core.Segment
import app.sourcescribe.core.TimeEvidence
import app.sourcescribe.core.TranscriptionRequest
import app.sourcescribe.core.Warnings
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Shared request validation and response parsing for direct transcription APIs. */
internal object SyncProviderSupport {
    const val MAX_UPLOAD_BYTES = 25_000_000L
    const val MAX_PROMPT_BYTES = 224

    fun model(config: JobConfig): String = config.model?.takeIf { it.isNotBlank() }
        ?: throw ProviderError(ProviderErrorCode.INVALID_INPUT)

    fun validateRequest(
        request: TranscriptionRequest,
        expectedProvider: Provider,
        capabilities: ProviderCapabilities,
        model: String,
        apiKey: String,
        promptLimited: Boolean = false,
        minimumDurationMs: Long = 1L,
    ) {
        if (request.config.provider != expectedProvider) invalidInput()
        if (!request.config.uploadApproved) invalidInput()
        validateOptions(request, expectedProvider, capabilities, model, promptLimited, minimumDurationMs)
        if (apiKey.isBlank() || apiKey.any { it == '\r' || it == '\n' }) invalidInput()
        if (!request.audio.isFile || !request.audio.canRead()) invalidInput()
        val bytes = request.audio.length()
        if (bytes <= 0 || bytes > capabilities.maxUploadBytes) invalidInput()
        val mime = request.mimeType.trim().lowercase(Locale.ROOT)
        if (request.mimeType.any { it == '\r' || it == '\n' } ||
            !mime.startsWith("audio/") || mime.length == "audio/".length
        ) invalidInput()
        if (request.durationMs <= 0 || request.chunkStartMs < 0 || request.chunkIndex < 0) invalidInput()
        if (request.chunkStartMs > Long.MAX_VALUE - request.durationMs) invalidInput()
    }

    fun validateOptions(
        request: TranscriptionRequest,
        expectedProvider: Provider,
        capabilities: ProviderCapabilities,
        model: String,
        promptLimited: Boolean = false,
        minimumDurationMs: Long = 1L,
    ) {
        if (request.config.provider != expectedProvider) invalidInput()
        if (request.config.region !in capabilities.regions) unsupported()
        if (model.isBlank()) unsupported()
        if (request.config.language != null && normalizeLanguage(request.config.language) == null) invalidInput()
        if (request.config.language == null && !capabilities.automaticLanguage) unsupported()
        if (request.config.wordTimestamps && !capabilities.wordTimestamps) unsupported()
        if (request.config.segmentTimestamps && !capabilities.segmentTimestamps) unsupported()
        if (request.config.diarization && !capabilities.diarization) unsupported()
        if (request.config.contextTerms.any { it.isBlank() }) invalidInput()
        if (request.config.contextTerms.any { it.isNotBlank() } && !capabilities.contextTerms) unsupported()
        if (promptLimited && request.config.contextTerms.isNotEmpty()) contextText(request.config.contextTerms)
        if (minimumDurationMs <= 0 || request.config.maxAudioSeconds <= 0 || request.durationMs < minimumDurationMs ||
            request.chunkStartMs < 0 || request.chunkIndex < 0
        ) invalidInput()
        val maxDurationMs = if (request.config.maxAudioSeconds > Long.MAX_VALUE / 1000L) {
            Long.MAX_VALUE
        } else {
            request.config.maxAudioSeconds * 1000L
        }
        if (request.durationMs > maxDurationMs) invalidInput()
        if (request.chunkStartMs > Long.MAX_VALUE - request.durationMs) invalidInput()
        request.config.maxCostMicrousd?.let { budget ->
            if (budget < 0) invalidInput()
            val hourly = capabilities.priceMicrousdPerHour ?: unsupported()
            val billedMs = maxOf(request.durationMs, capabilities.minimumBilledSeconds * 1000L)
            val cost = if (hourly == 0L || billedMs > Long.MAX_VALUE / hourly) {
                Long.MAX_VALUE
            } else {
                val product = billedMs * hourly
                product / 3_600_000L + if (product % 3_600_000L == 0L) 0L else 1L
            }
            if (cost > budget) invalidInput()
        }
    }

    fun language(config: JobConfig): String? = config.language?.let(::normalizeLanguage)

    fun contextText(terms: List<String>): String? {
        val result = StringBuilder()
        var byteCount = 0
        for (term in terms) {
            val value = term.trim()
            if (value.isEmpty()) continue
            val separatorBytes = if (result.isEmpty()) 0 else 2
            val valueBytes = value.toByteArray(StandardCharsets.UTF_8).size
            if (valueBytes > MAX_PROMPT_BYTES - byteCount - separatorBytes) invalidInput()
            if (separatorBytes != 0) result.append(", ")
            result.append(value)
            byteCount += separatorBytes + valueBytes
        }
        val text = result.toString().trim().takeIf { it.isNotEmpty() } ?: return null
        // Conservative UTF-8 byte budget for the documented 224-token prompt limit; this is not a tokenizer.
        return text
    }

    fun contextKeywords(terms: List<String>): List<String> = terms
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun normalizedMime(mimeType: String): String = mimeType.trim()

    private fun normalizeLanguage(language: String): String? {
        val value = language.trim()
        return if (value.length == 2 && value.all { it in 'a'..'z' || it in 'A'..'Z' }) {
            value.lowercase(Locale.ROOT)
        } else {
            null
        }
    }

    private fun invalidInput(): Nothing = throw ProviderError(ProviderErrorCode.INVALID_INPUT)

    private fun unsupported(): Nothing = throw ProviderError(ProviderErrorCode.UNSUPPORTED_OPTION)
}

/** Parses the bounded JSON returned by synchronous Groq/OpenAI transcription calls. */
object SyncTranscriptParser {
    private const val MAX_RESPONSE_BYTES = 16 * 1024 * 1024
    private const val MAX_SEGMENTS = 100_000
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        raw: ByteArray,
        request: TranscriptionRequest,
        provider: Provider,
        model: String,
    ): ProviderTranscript {
        if (provider != Provider.GROQ && provider != Provider.OPENAI) invalidResponse()
        if (raw.isEmpty() || raw.size > MAX_RESPONSE_BYTES) invalidResponse()
        val root = try {
            json.parseBounded(raw.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            invalidResponse()
        }
        val objectRoot = root as? JsonObject ?: invalidResponse()
        val text = string(objectRoot["text"])
        if (text == null || text.isBlank()) invalidResponse()

        val warnings = Warnings()
        val rawModelReported = string(objectRoot["model"])
        if (rawModelReported != null && rawModelReported.length > MAX_REPORTED_MODEL_LENGTH) {
            warnings += "REPORTED_MODEL_TOO_LONG"
        }
        val modelReported = rawModelReported
            ?.takeIf { it.isNotBlank() && it.length <= MAX_REPORTED_MODEL_LENGTH }
        val (reportedLanguages, language) = responseLanguages(objectRoot, warnings)
        val parsed = when {
            model == "gpt-transcribe" -> ParsedParts(
                segments = listOf(Segment(text = text, chunkIndex = request.chunkIndex)),
                words = emptyList(),
            )
            model == "gpt-4o-transcribe-diarize" -> ParsedParts(
                segments = parseDiarizedSegments(
                    objectRoot["segments"],
                    request,
                    warnings,
                ),
                words = emptyList(),
            )
            else -> parseTimestampedSegments(objectRoot, request, warnings)
        }

        val technicallyComplete = completeness(objectRoot, model, request, warnings)
        return ProviderTranscript(
            segments = if (parsed.segments.isEmpty()) listOf(Segment(text = text, chunkIndex = request.chunkIndex)) else parsed.segments,
            language = language,
            requestedModel = model,
            reportedModel = modelReported,
            warnings = warnings.toList(),
            technicallyComplete = technicallyComplete,
            words = parsed.words,
            reportedLanguages = reportedLanguages,
        )
    }

    private fun parseTimestampedSegments(
        root: JsonObject,
        request: TranscriptionRequest,
        warnings: Warnings,
    ): ParsedParts {
        val rawSegments = root["segments"] as? JsonArray
        val rawWords = root["words"] as? JsonArray
        val segments = rawSegments?.let { parseEntries(it, request, TimeEvidence.PROVIDER_SEGMENT, null, warnings) }
            .orEmpty()
        val words = rawWords?.let { parseEntries(it, request, TimeEvidence.PROVIDER_WORD, "word", warnings) }
            .orEmpty()

        return ParsedParts(segments = segments, words = words)
    }

    private data class ParsedParts(val segments: List<Segment>, val words: List<Segment>)

    private fun parseDiarizedSegments(
        element: JsonElement?,
        request: TranscriptionRequest,
        warnings: Warnings,
    ): List<Segment> {
        val rawSegments = element as? JsonArray ?: return emptyList()
        return parseEntries(rawSegments, request, TimeEvidence.PROVIDER_SEGMENT, "speaker", warnings)
    }

    private fun parseEntries(
        entries: JsonArray,
        request: TranscriptionRequest,
        evidence: TimeEvidence,
        field: String?,
        warnings: Warnings,
    ): List<Segment> {
        val result = ArrayList<Segment>()
        var previousStart: Long? = null
        if (entries.size > MAX_SEGMENTS) {
            warnings += if (evidence == TimeEvidence.PROVIDER_WORD) {
                "WORD_LIMIT_REACHED"
            } else {
                "SEGMENT_LIMIT_REACHED"
            }
        }
        for ((index, element) in entries.take(MAX_SEGMENTS).withIndex()) {
            val objectEntry = element as? JsonObject
            if (objectEntry == null) {
                warnings += "MALFORMED_${if (evidence == TimeEvidence.PROVIDER_WORD) "WORD" else "SEGMENT"}_$index"
                continue
            }
            val text = string(objectEntry["text"])?.takeIf { it.isNotBlank() }
                ?: if (field == "word") {
                    string(objectEntry["word"])?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            if (text == null) {
                warnings += "MISSING_${if (evidence == TimeEvidence.PROVIDER_WORD) "WORD" else "SEGMENT"}_TEXT_$index"
                continue
            }
            val start = timestamp(objectEntry["start"])
            val end = timestamp(objectEntry["end"])
            val hasValidExtent = start != null && end != null && end > start
            val withinDuration = end?.let { hasValidExtent && it <= request.durationMs } ?: false
            val monotonic = if (start == null) {
                true
            } else {
                previousStart?.let { start >= it } ?: true
            }
            if (start != null) previousStart = start
            val validTime = hasValidExtent && withinDuration && monotonic
            if (!validTime) {
                warnings += "INVALID_${if (evidence == TimeEvidence.PROVIDER_WORD) "WORD" else "SEGMENT"}_TIMESTAMP_$index"
            }
            if (hasValidExtent && !withinDuration) {
                warnings += "OUT_OF_RANGE_${if (evidence == TimeEvidence.PROVIDER_WORD) "WORD" else "SEGMENT"}_TIMESTAMP_$index"
            }
            if (!monotonic) {
                warnings += "NON_MONOTONIC_${if (evidence == TimeEvidence.PROVIDER_WORD) "WORD" else "SEGMENT"}_TIMESTAMP_$index"
            }
            val scopedSpeaker = if (field == "speaker") {
                val speaker = string(objectEntry["speaker"])
                if (speaker == null || speaker.isBlank()) {
                    warnings += "MISSING_SPEAKER_$index"
                    null
                } else {
                    "chunk-${request.chunkIndex}:$speaker"
                }
            } else {
                null
            }
            val shiftedStart = if (validTime) offset(start, request.chunkStartMs) else null
            val shiftedEnd = if (validTime) offset(end, request.chunkStartMs) else null
            val hasShiftedTime = shiftedStart != null && shiftedEnd != null && shiftedEnd > shiftedStart
            if (validTime && !hasShiftedTime) warnings += "INVALID_CHUNK_OFFSET_$index"
            result += Segment(
                text = text,
                startMs = if (hasShiftedTime) shiftedStart else null,
                endMs = if (hasShiftedTime) shiftedEnd else null,
                speaker = scopedSpeaker,
                timeEvidence = if (hasShiftedTime) evidence else TimeEvidence.UNKNOWN,
                chunkIndex = request.chunkIndex,
            )
        }
        return result
    }

    private fun completeness(
        root: JsonObject,
        model: String,
        request: TranscriptionRequest,
        warnings: Warnings,
    ): Boolean {
        var complete = true
        if (model == "gpt-transcribe") return true

        if (model == "gpt-4o-transcribe-diarize") {
            val segments = root["segments"] as? JsonArray
            if (root.containsKey("segments") && root["segments"] !is JsonArray && root["segments"] !is JsonNull) {
                warnings += "MALFORMED_DIARIZED_SEGMENTS"
                complete = false
            } else if (segments == null || segments.isEmpty()) {
                warnings += "MISSING_DIARIZED_SEGMENTS"
                complete = false
            }
            if (segments != null) {
                if (segments.any { string((it as? JsonObject)?.get("speaker")).isNullOrBlank() }) {
                    warnings += "MISSING_DIARIZED_SPEAKER"
                    complete = false
                }
            }
            if (segments != null && segments.size > MAX_SEGMENTS) complete = false
            if (segments != null && !validTimestampStream(segments, request, allowWordField = false)) complete = false
            return complete
        }

        if (request.config.segmentTimestamps) {
            val segments = root["segments"] as? JsonArray
            if (root.containsKey("segments") && root["segments"] !is JsonArray && root["segments"] !is JsonNull) {
                warnings += "MALFORMED_SEGMENTS"
                complete = false
            } else if (segments == null || segments.isEmpty()) {
                warnings += "MISSING_SEGMENT_TIMESTAMPS"
                complete = false
            } else if (!validTimestampStream(segments, request, allowWordField = false)) {
                warnings += "INCOMPLETE_SEGMENT_TIMESTAMPS"
                complete = false
            }
        }
        if (request.config.wordTimestamps) {
            val words = root["words"] as? JsonArray
            if (root.containsKey("words") && root["words"] !is JsonArray && root["words"] !is JsonNull) {
                warnings += "MALFORMED_WORDS"
                complete = false
            } else if (words == null || words.isEmpty()) {
                warnings += "MISSING_WORD_TIMESTAMPS"
                complete = false
            } else if (!validTimestampStream(words, request, allowWordField = true)) {
                warnings += "INCOMPLETE_WORD_TIMESTAMPS"
                complete = false
            }
        }
        val returnedSegments = root["segments"] as? JsonArray
        if (root.containsKey("segments") && root["segments"] !is JsonArray && root["segments"] !is JsonNull) complete = false
        if (returnedSegments != null && returnedSegments.size > MAX_SEGMENTS) complete = false
        if (returnedSegments != null && !validTimestampStream(returnedSegments, request, allowWordField = false)) complete = false
        val returnedWords = root["words"] as? JsonArray
        if (root.containsKey("words") && root["words"] !is JsonArray && root["words"] !is JsonNull) complete = false
        if (returnedWords != null && returnedWords.size > MAX_SEGMENTS) complete = false
        if (returnedWords != null && !validTimestampStream(returnedWords, request, allowWordField = true)) complete = false
        return complete
    }

    private fun validTimestampStream(
        entries: JsonArray,
        request: TranscriptionRequest,
        allowWordField: Boolean,
    ): Boolean {
        var previousStart: Long? = null
        for (element in entries) {
            val objectEntry = element as? JsonObject ?: return false
            val start = timestamp(objectEntry["start"])
            val end = timestamp(objectEntry["end"])
            val text = string(objectEntry["text"])?.takeIf { it.isNotBlank() }
                ?: if (allowWordField) string(objectEntry["word"])?.takeIf { it.isNotBlank() } else null
            val outOfOrder = if (start == null) false else previousStart?.let { start < it } ?: false
            if (start == null || end == null || end <= start || end > request.durationMs ||
                text == null || outOfOrder
            ) return false
            previousStart = start
        }
        return true
    }

    private fun responseLanguages(root: JsonObject, warnings: Warnings): Pair<List<String>, String?> {
        val values = LinkedHashSet<String>()
        var validObserved = 0
        fun add(value: String?) {
            if (value != null && value.length == 2 && value.all { it in 'a'..'z' || it in 'A'..'Z' }) {
                validObserved++
                if (values.size < MAX_REPORTED_LANGUAGES) values += value.lowercase(Locale.ROOT)
            }
        }
        fun addObserved(element: JsonElement?) {
            when (element) {
                is JsonArray -> element.forEach {
                    add(languageCode(it))
                }
                else -> add(languageCode(element))
            }
        }
        addObserved(root["language"])
        addObserved(root["languages"])
        if (validObserved > MAX_REPORTED_LANGUAGES) warnings += "LANGUAGE_LIMIT_REACHED"
        val reported = values.toList()
        if (reported.size > 1) warnings += "MULTIPLE_LANGUAGES"
        return reported to reported.singleOrNull()
    }

    private fun string(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun languageCode(element: JsonElement?): String? = when (element) {
        is JsonObject -> string(element["code"])
        else -> string(element)
    }

    private fun timestamp(element: JsonElement?): Long? {
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        val seconds = primitive.content.toDoubleOrNull() ?: return null
        if (!seconds.isFinite() || seconds < 0.0) return null
        val millis = seconds * 1000.0
        if (!millis.isFinite() || millis > Long.MAX_VALUE.toDouble()) return null
        return millis.roundToLong()
    }

    private fun offset(timestampMs: Long, chunkStartMs: Long): Long? =
        if (timestampMs <= Long.MAX_VALUE - chunkStartMs) timestampMs + chunkStartMs else null

    private fun invalidResponse(): Nothing = throw ProviderError(ProviderErrorCode.INVALID_RESPONSE)

    private const val MAX_REPORTED_LANGUAGES = 16
    private const val MAX_REPORTED_MODEL_LENGTH = 128
}
