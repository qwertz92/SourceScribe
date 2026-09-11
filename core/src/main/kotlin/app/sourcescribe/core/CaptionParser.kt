package app.sourcescribe.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

data class ParsedCaptions(
    val segments: List<Segment>,
    val warnings: List<String>,
    val technicallyComplete: Boolean,
)

class CaptionParseException(val reason: String) : IllegalArgumentException(reason) {
    companion object {
        const val UNSUPPORTED_FORMAT = "UNSUPPORTED_FORMAT"
        const val INPUT_TOO_LARGE = "INPUT_TOO_LARGE"
        const val EMPTY_INPUT = "EMPTY_INPUT"
        const val MALFORMED_INPUT = "MALFORMED_INPUT"
        const val NO_SEGMENTS = "NO_SEGMENTS"
    }
}

/** Parsers for the caption formats returned by the extractor. */
object CaptionParser {
    const val MAX_INPUT_CHARS = 8_000_000
    const val MAX_SEGMENTS = 100_000

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String, format: String): ParsedCaptions {
        val normalizedFormat = format.trim().lowercase(Locale.ROOT).replace('_', '-').replace(' ', '-')
        val parserFormat = when (normalizedFormat) {
            "vtt", "text/vtt" -> "vtt"
            "srt", "text/srt", "application/x-subrip" -> "srt"
            "json", "json3", "youtube-json", "youtube-json3", "application/json", "application/json3" -> "json3"
            else -> null
        }
        if (parserFormat == null) {
            throw CaptionParseException(CaptionParseException.UNSUPPORTED_FORMAT)
        }
        if (raw.length > MAX_INPUT_CHARS) {
            throw CaptionParseException(CaptionParseException.INPUT_TOO_LARGE)
        }
        if (raw.removePrefix("\uFEFF").isBlank()) {
            throw CaptionParseException(CaptionParseException.EMPTY_INPUT)
        }
        return when (parserFormat) {
            "vtt" -> parseVtt(raw)
            "srt" -> parseSrt(raw)
            else -> parseJson3(raw)
        }
    }

    private fun parseVtt(raw: String): ParsedCaptions {
        val lines = lines(raw)
        val header = lines.firstOrNull()?.removePrefix("\uFEFF")
        if (header == null || !header.startsWith("WEBVTT") ||
            (header.length > "WEBVTT".length && !header["WEBVTT".length].isWhitespace())
        ) {
            throw CaptionParseException(CaptionParseException.MALFORMED_INPUT)
        }

        val result = Accumulator()
        var index = 1
        while (index < lines.size) {
            while (index < lines.size && lines[index].isBlank()) index++
            if (index >= lines.size) break

            val line = lines[index]
            val keyword = line.trim().takeWhile { !it.isWhitespace() }.uppercase(Locale.ROOT)
            if (keyword in setOf("NOTE", "STYLE", "REGION")) {
                index++
                while (index < lines.size && lines[index].isNotBlank()) index++
                continue
            }

            val cueStartLine = index + 1
            var timingLine = line
            if (parseTiming(line, Format.VTT) == null) {
                index++
                if (index >= lines.size || lines[index].isBlank()) {
                    result.warn("MALFORMED_CUE_LINE_$cueStartLine")
                    while (index < lines.size && lines[index].isBlank()) index++
                    continue
                }
                timingLine = lines[index]
            }

            val timing = parseTiming(timingLine, Format.VTT)
            if (timing == null) {
                result.warn("INVALID_TIMING_LINE_${index + 1}")
                index = skipMalformedCue(lines, index, Format.VTT)
                continue
            }
            if (timing.unknownSettings) result.warn("UNKNOWN_VTT_SETTING_${index + 1}")
            index++
            val cueLines = ArrayList<String>()
            while (index < lines.size && lines[index].isNotBlank() && !isCueStart(lines, index, Format.VTT)) {
                cueLines += lines[index]
                index++
            }
            val rawText = cueLines.joinToString("\n")
            val speaker = extractVttSpeaker(rawText)
            val text = cleanText(rawText)
            if (text.isBlank()) {
                result.warn("EMPTY_CUE_LINE_$cueStartLine")
            } else if (timing.startMs >= timing.endMs) {
                result.warn("INVALID_TIMING_LINE_$cueStartLine")
            } else {
                result.add(
                    Segment(
                        text = text,
                        startMs = timing.startMs,
                        endMs = timing.endMs,
                        speaker = speaker,
                        timeEvidence = TimeEvidence.CAPTION_CUE,
                    ),
                )
            }
        }
        return result.finish()
    }

    private fun parseSrt(raw: String): ParsedCaptions {
        val lines = lines(raw)
        val result = Accumulator()
        var index = 0
        while (index < lines.size) {
            while (index < lines.size && lines[index].isBlank()) index++
            if (index >= lines.size) break

            val cueStartLine = index + 1
            var timingLine = lines[index]
            if (parseTiming(timingLine, Format.SRT) == null) {
                if (CUE_INDEX.matches(lines[index].trim())) {
                    index++
                    if (index < lines.size) timingLine = lines[index]
                }
            }
            val timing = parseTiming(timingLine, Format.SRT)
            if (timing == null) {
                result.warn("MALFORMED_CUE_LINE_$cueStartLine")
                index = skipMalformedCue(lines, index, Format.SRT)
                continue
            }
            index++
            val cueLines = ArrayList<String>()
            while (index < lines.size && lines[index].isNotBlank() && !isCueStart(lines, index, Format.SRT)) {
                cueLines += lines[index]
                index++
            }
            val text = cleanText(cueLines.joinToString("\n"))
            if (text.isBlank()) {
                result.warn("EMPTY_CUE_LINE_$cueStartLine")
            } else if (timing.startMs >= timing.endMs) {
                result.warn("INVALID_TIMING_LINE_$cueStartLine")
            } else {
                result.add(
                    Segment(
                        text = text,
                        startMs = timing.startMs,
                        endMs = timing.endMs,
                        timeEvidence = TimeEvidence.CAPTION_CUE,
                    ),
                )
            }
        }
        return result.finish()
    }

    private fun parseJson3(raw: String): ParsedCaptions {
        val root = try {
            json.parseBounded(raw)
        } catch (_: Exception) {
            throw CaptionParseException(CaptionParseException.MALFORMED_INPUT)
        }
        val events = (root as? JsonObject)?.get("events") as? JsonArray
            ?: throw CaptionParseException(CaptionParseException.MALFORMED_INPUT)
        val result = Accumulator()
        var previous: JsonEvent? = null
        for ((eventIndex, element) in events.withIndex()) {
            if (eventIndex >= MAX_SEGMENTS) {
                result.warn("EVENT_LIMIT_REACHED")
                break
            }
            val event = element as? JsonObject
            if (event == null) {
                result.warn("MALFORMED_EVENT_$eventIndex")
                previous = null
                continue
            }
            val textElement = event["segs"]
            val appendsToPrevious = jsonLong(event["aAppend"]) == 1L
            val rawText = when (textElement) {
                is JsonArray -> textElement.mapIndexedNotNull { segmentIndex, segment ->
                    val segmentObject = segment as? JsonObject
                    val primitive = segmentObject?.get("utf8") as? JsonPrimitive
                    val text = primitive?.takeIf { it.isString }?.contentOrNull
                    if (segmentObject == null || text == null) {
                        result.warn("MALFORMED_CAPTION_SEGMENT_${eventIndex}_$segmentIndex")
                    }
                    text
                }.joinToString(separator = "")
                null -> ""
                else -> {
                    // Not the provider code of the same name: there a malformed segment list costs the
                    // timestamps, here it costs this cue its text and the cue is dropped below.
                    result.warn("MALFORMED_CAPTION_SEGMENTS_$eventIndex")
                    ""
                }
            }
            val text = cleanText(rawText)
            if (text.isBlank()) {
                if (!appendsToPrevious && textElement is JsonArray && textElement.isNotEmpty()) {
                    result.warn("EMPTY_EVENT_$eventIndex")
                }
                previous = null
                continue
            }

            val start = jsonLong(event["tStartMs"])
            val duration = jsonLong(event["dDurationMs"])
            val end = if (start != null && duration != null && duration > 0) safeAdd(start, duration) else null
            val hasValidTiming = start != null && end != null && start >= 0 && end > start
            if (start == null || duration == null || duration <= 0 || end == null) {
                result.warn("MISSING_OR_INVALID_TIMING_EVENT_$eventIndex")
            }

            val segment = Segment(
                text = text,
                startMs = if (hasValidTiming) start else null,
                endMs = if (hasValidTiming) end else null,
                timeEvidence = if (hasValidTiming) TimeEvidence.CAPTION_CUE else TimeEvidence.UNKNOWN,
            )
            val current = JsonEvent(
                start = start,
                end = end,
                text = text,
                windowId = jsonLong(event["wWinId"]),
                segmentIndex = null,
            )
            val rewrittenIndex = rollupIndex(previous, current, result)
            val segmentIndex = if (rewrittenIndex != null) {
                result.replace(rewrittenIndex, segment)
                rewrittenIndex
            } else {
                result.add(segment)
            }
            previous = current.copy(segmentIndex = segmentIndex)
        }
        return result.finish()
    }

    private fun rollupIndex(previous: JsonEvent?, current: JsonEvent, result: Accumulator): Int? {
        val previousStart = previous?.start
        val previousEnd = previous?.end
        val currentStart = current.start
        if (previous == null || previous.segmentIndex == null || previousStart == null || previousEnd == null ||
            currentStart == null || currentStart >= previousEnd ||
            !current.text.startsWith(previous.text) || current.text.length <= previous.text.length
        ) {
            return null
        }

        val sameWindow = previous.windowId != null && previous.windowId == current.windowId &&
            currentStart == previousStart && current.end != null && current.end > currentStart
        if (sameWindow) return previous.segmentIndex

        result.warn("STRUCTURAL_ROLLUP_UNCERTAIN")
        return null
    }

    private fun parseTiming(line: String, format: Format): Timing? {
        val match = TIMING_LINE.matchEntire(line.trim()) ?: return null
        val start = parseTimestamp(match.groupValues[1], format) ?: return null
        val end = parseTimestamp(match.groupValues[2], format) ?: return null
        val settings = match.groupValues.getOrNull(3).orEmpty().trim()
        if (format == Format.SRT && settings.isNotEmpty()) return null
        if (format == Format.VTT && settings.isNotEmpty()) {
            if (settings.contains("-->")) return null
            val tokens = settings.split(Regex("\\s+"))
            if (tokens.any { !VTT_SETTING.matches(it) }) return null
            val unknown = tokens.any { it.substringBefore(':').lowercase(Locale.ROOT) !in VTT_SETTINGS }
            return Timing(start, end, unknownSettings = unknown)
        }
        return Timing(start, end, unknownSettings = false)
    }

    private fun isCueStart(lines: List<String>, index: Int, format: Format): Boolean {
        if (parseTiming(lines[index], format) != null) return true
        return index + 1 < lines.size &&
            CUE_INDEX.matches(lines[index].trim()) &&
            parseTiming(lines[index + 1], format) != null
    }

    private fun skipMalformedCue(lines: List<String>, start: Int, format: Format): Int {
        var index = start
        while (index < lines.size && lines[index].isNotBlank() && !isCueStart(lines, index, format)) index++
        return index
    }

    private fun parseTimestamp(value: String, format: Format): Long? {
        val pattern = if (format == Format.SRT) SRT_TIMESTAMP else VTT_TIMESTAMP
        val match = pattern.matchEntire(value) ?: return null
        val hours = match.groupValues[1].ifEmpty { "0" }.toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val millis = match.groupValues[4].padEnd(3, '0').toLongOrNull() ?: return null
        if (hours < 0 || minutes !in 0..59 || seconds !in 0..59 || millis !in 0..999) return null
        val totalSeconds = try {
            Math.addExact(
                Math.addExact(Math.multiplyExact(hours, 3600L), Math.multiplyExact(minutes, 60L)),
                seconds,
            )
        } catch (_: ArithmeticException) {
            return null
        }
        return try {
            Math.addExact(Math.multiplyExact(totalSeconds, 1000L), millis)
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun jsonLong(element: JsonElement?): Long? {
        val primitive = element as? JsonPrimitive ?: return null
        return primitive.contentOrNull?.toLongOrNull()?.takeIf { it >= 0 }
    }

    private fun safeAdd(first: Long, second: Long): Long? = try {
        Math.addExact(first, second)
    } catch (_: ArithmeticException) {
        null
    }

    private fun lines(raw: String): List<String> = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')

    private fun extractVttSpeaker(rawText: String): String? {
        val firstLine = rawText.lineSequence().firstOrNull()?.trim() ?: return null
        val match = VTT_SPEAKER.find(firstLine) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
    }

    private fun cleanText(value: String): String = decodeEntities(stripCaptionMarkup(value)).trim()

    private fun stripCaptionMarkup(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        var cachedTagEnd: Int? = null
        var cacheValidUntil = -1
        while (index < value.length) {
            if (value[index] == '<') {
                if (index > cacheValidUntil) {
                    cachedTagEnd = value.indexOf('>', index + 1).takeIf { it >= 0 }
                    cacheValidUntil = cachedTagEnd ?: value.lastIndex
                }
                val close = cachedTagEnd
                if (close == null) {
                    output.append(value, index, value.length)
                    break
                }
                if (close - index <= MAX_TAG_CHARS && CAPTION_TAG.matches(value.substring(index, close + 1))) {
                    index = close + 1
                    continue
                }
            }
            output.append(value[index])
            index++
        }
        return output.toString()
    }

    private fun decodeEntities(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] != '&') {
                output.append(value[index++])
                continue
            }
            val close = findEntityEnd(value, index)
            if (close == null) {
                output.append(value[index++])
                continue
            }
            val entity = value.substring(index + 1, close)
            val decoded = when (entity.lowercase(Locale.ROOT)) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos", "#39" -> "'"
                "nbsp" -> " "
                else -> decodeNumericEntity(entity)
            }
            if (decoded == null) {
                output.append('&')
                index++
            } else {
                output.append(decoded)
                index = close + 1
            }
        }
        return output.toString()
    }

    private fun findEntityEnd(value: String, start: Int): Int? {
        val limit = minOf(value.length, start + MAX_ENTITY_CHARS + 2)
        var index = start + 1
        while (index < limit) {
            if (value[index] == ';') return index
            index++
        }
        return null
    }

    private fun decodeNumericEntity(entity: String): String? {
        val codePoint = when {
            entity.startsWith("#x", ignoreCase = true) -> entity.substring(2).toIntOrNull(16)
            entity.startsWith('#') -> entity.substring(1).toIntOrNull(10)
            else -> null
        } ?: return null
        if (codePoint !in 0..0x10FFFF || codePoint in 0xD800..0xDFFF) return null
        return String(Character.toChars(codePoint))
    }

    private class Accumulator {
        val segments = ArrayList<Segment>()
        private val warnings = Warnings()
        var complete = true

        fun add(segment: Segment): Int? {
            if (segments.size >= MAX_SEGMENTS) {
                warn("SEGMENT_LIMIT_REACHED")
                return null
            } else {
                segments += segment
                return segments.lastIndex
            }
        }

        fun replace(index: Int, segment: Segment) {
            if (index in segments.indices) segments[index] = segment else warn("STRUCTURAL_ROLLUP_UNCERTAIN")
        }

        fun warn(code: String) {
            complete = false
            warnings += code
        }

        fun finish(): ParsedCaptions {
            val recorded = warnings.toList()
            if (segments.isEmpty()) {
                throw CaptionParseException(if (recorded.isEmpty()) CaptionParseException.NO_SEGMENTS else CaptionParseException.MALFORMED_INPUT)
            }
            return ParsedCaptions(segments.toList(), recorded, complete)
        }
    }

    private data class Timing(val startMs: Long, val endMs: Long, val unknownSettings: Boolean)
    private data class JsonEvent(
        val start: Long?,
        val end: Long?,
        val text: String,
        val windowId: Long?,
        val segmentIndex: Int?,
    )
    private enum class Format { SRT, VTT }

    private const val MAX_TAG_CHARS = 256
    private const val MAX_ENTITY_CHARS = 16
    private val CUE_INDEX = Regex("\\d+")
    private val TIMING_LINE = Regex("^\\s*(\\S+)\\s+-->\\s+(\\S+)(?:\\s+(.*))?$")
    private val SRT_TIMESTAMP = Regex("(\\d+):(\\d{2}):(\\d{2})[,.](\\d{1,3})")
    private val VTT_TIMESTAMP = Regex("(?:(\\d+):)?(\\d{2}):(\\d{2})\\.(\\d{1,3})")
    private val VTT_SPEAKER = Regex("^<v(?:\\.[^\\s>]+)?\\s+([^>]+)>")
    private val VTT_SETTING = Regex("[A-Za-z][A-Za-z0-9_-]*:[^\\s]+")
    private val VTT_SETTINGS = setOf("vertical", "line", "position", "size", "align", "region")
    private val CAPTION_TAG = Regex(
        "</?(?:v(?:\\.[^\\s>]+)?(?:\\s+[^>]*)?|c(?:\\.[^\\s>]+)*(?:\\s+[^>]*)?|b|i|u|strong|em|font(?:\\s+[^>]*)?|ruby|rt|lang(?:\\s+[^>]*)?|\\d{2}:\\d{2}(?::\\d{2})?\\.\\d{3})>",
    )
}
