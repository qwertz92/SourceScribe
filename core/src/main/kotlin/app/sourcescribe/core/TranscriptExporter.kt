package app.sourcescribe.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

class TranscriptExportException(val reason: String) : IllegalArgumentException(reason) {
    companion object {
        const val RAW_REQUIRES_SEPARATE_FILE = "RAW_REQUIRES_SEPARATE_FILE"
        const val TIMESTAMPS_REQUIRED = "TIMESTAMPS_REQUIRED"
        const val TIMED_TEXT_UNREPRESENTABLE = "TIMED_TEXT_UNREPRESENTABLE"
    }
}

object TranscriptExporter {
    private const val MAX_FILENAME_CHARS = 180
    private const val MAX_FILENAME_PART_CHARS = 40
    /** Leaves room for the longest extension a provider raw payload can carry. */
    private const val MAX_FILENAME_STEM_CHARS = 160
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = true
    }
    private val BLANK_CUE_LINE = Regex("\\n[\\t ]*\\n")

    fun render(document: TranscriptDocument, format: ExportFormat): String = when (format) {
        ExportFormat.MARKDOWN -> renderMarkdown(document)
        ExportFormat.TEXT -> renderText(document)
        ExportFormat.JSON -> json.encodeToString(document)
        ExportFormat.SRT -> renderTimed(document, format)
        ExportFormat.VTT -> renderTimed(document, format)
        ExportFormat.RAW -> throw TranscriptExportException(TranscriptExportException.RAW_REQUIRES_SEPARATE_FILE)
    }

    fun extension(format: ExportFormat): String = when (format) {
        ExportFormat.MARKDOWN -> "md"
        ExportFormat.TEXT -> "txt"
        ExportFormat.JSON -> "json"
        ExportFormat.SRT -> "srt"
        ExportFormat.VTT -> "vtt"
        ExportFormat.RAW -> "raw"
    }

    /**
     * The generated file stem: readable title first, then the parts that keep two runs of the same
     * source apart. The identity part is never truncated; only the title yields when the budget runs out.
     */
    fun generatedStem(document: TranscriptDocument, discriminator: String? = null): String {
        val title = safePart(
            document.source.title ?: document.source.fileName ?: "transcript",
            "transcript",
        )
        val identity = identitySuffix(document) +
            (discriminator?.let(::shortId)?.let { "-$it" } ?: "")
        return compose(title, identity)
    }

    /** A user-chosen stem, sanitised. Provenance stays inside the document, so the name is the reader's. */
    fun customStem(value: String): String? =
        safePart(value, "", MAX_FILENAME_STEM_CHARS).takeIf { it.isNotBlank() }

    fun fileName(
        document: TranscriptDocument,
        format: ExportFormat,
        override: String? = null,
        discriminator: String? = null,
        rawExtension: String? = null,
    ): String {
        // A chosen name is used as given; a repeated export is separated by the storage layer.
        val stem = override?.let(::customStem) ?: generatedStem(document, discriminator)
        val suffix = ".${rawExtension?.let { safePart(it, "raw", 12) } ?: extension(format)}"
        return stem.take(MAX_FILENAME_CHARS - suffix.length).trimEnd('.', ' ', '-', '_') + suffix
    }

    private fun identitySuffix(document: TranscriptDocument): String {
        val source = safePart(document.source.id, "source")
        val language = safePart(document.language ?: document.source.originalLanguage ?: "lang-unknown", "lang-unknown")
        val date = createdAtUtc(document.createdAt).take(10)
        return listOf(date, language, source, shortId(document.artifactId)).joinToString("-")
    }

    /**
     * Enough of an identifier to separate runs without turning the name into an identifier.
     * A digest rather than a prefix, so two ids that merely share their opening characters still differ.
     */
    private fun shortId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return buildString(8) { for (index in 0 until 4) append("%02x".format(digest[index])) }
    }

    /** Joins a readable head with a suffix that must survive, trimming only the head. */
    private fun compose(head: String, suffix: String): String {
        val tail = "-$suffix"
        val budget = (MAX_FILENAME_STEM_CHARS - tail.length).coerceAtLeast(1)
        val shortened = head.take(budget).trimEnd('.', ' ', '-', '_').ifBlank { "transcript" }
        return shortened + tail
    }

    fun supports(document: TranscriptDocument, format: ExportFormat): Boolean = when (format) {
        ExportFormat.MARKDOWN, ExportFormat.TEXT, ExportFormat.JSON -> true
        ExportFormat.RAW -> false
        ExportFormat.SRT, ExportFormat.VTT -> timedSegments(document) != null
    }

    private fun renderMarkdown(document: TranscriptDocument): String {
        val title = markdownValue(document.source.title ?: "Transcript")
        val metadata = buildString {
            append("# ").append(title).append("\n\n")
            metadataFields(document).forEach { field ->
                append("- **").append(field.label).append(":** ")
                    .append(markdownValue(field.value)).append("\n")
            }
        }
        val body = markdownBody(document)
        val fence = fenceFor(body)
        return buildString {
            append(metadata)
            append("\n## Transcript data\n\n")
            append(fence).append("text\n")
            append(body)
            append("\n").append(fence).append("\n")
        }
    }

    private fun renderText(document: TranscriptDocument): String = buildString {
        metadataFields(document).forEach { field ->
            append(field.label).append(": ").append(textValue(field.value)).append("\n")
        }
        append("Transcript data:\n\n")
        val content = normalizeLineBreaks(document.text)
        append(content)
        if (!content.endsWith('\n')) append('\n')
    }

    private fun renderTimed(document: TranscriptDocument, format: ExportFormat): String {
        val segments = timedSegments(document)
            ?: throw TranscriptExportException(
                if (hasUnrepresentableTimedText(document)) {
                    TranscriptExportException.TIMED_TEXT_UNREPRESENTABLE
                } else {
                    TranscriptExportException.TIMESTAMPS_REQUIRED
                },
            )
        return buildString {
            if (format == ExportFormat.VTT) append("WEBVTT\n\n")
            segments.forEachIndexed { index, segment ->
                if (format == ExportFormat.SRT) append(index + 1).append("\n")
                append(timestamp(segment.startMs!!, format == ExportFormat.SRT))
                    .append(" --> ")
                    .append(timestamp(segment.endMs!!, format == ExportFormat.SRT))
                    .append("\n")
                val speaker = segment.speaker?.let(::singleLineTimedValue)?.takeIf { it.isNotBlank() }
                if (speaker != null) append(speaker).append(": ")
                append(timedText(segment.text)!!).append("\n\n")
            }
        }
    }

    private fun hasUnrepresentableTimedText(document: TranscriptDocument): Boolean =
        document.segments.any { it.text.isNotBlank() && timedText(it.text) == null }

    private fun timedSegments(document: TranscriptDocument): List<Segment>? {
        val segments = document.segments.filter { it.text.isNotBlank() }
        if (segments.isEmpty()) return null
        var previousStart = -1L
        var previousEnd = -1L
        for (segment in segments) {
            val start = segment.startMs ?: return null
            val end = segment.endMs ?: return null
            if (segment.timeEvidence == TimeEvidence.UNKNOWN || start < 0 || end <= start) return null
            if (start < previousStart || (start == previousStart && end < previousEnd)) return null
            if (timedText(segment.text) == null) return null
            previousStart = start
            previousEnd = end
        }
        return segments
    }

    private fun timestamp(milliseconds: Long, comma: Boolean): String {
        val hours = milliseconds / 3_600_000
        val minutes = (milliseconds / 60_000) % 60
        val seconds = (milliseconds / 1_000) % 60
        val millis = milliseconds % 1_000
        val separator = if (comma) ',' else '.'
        return buildString {
            append(hours.toString().padStart(2, '0'))
            append(':').append(minutes.toString().padStart(2, '0'))
            append(':').append(seconds.toString().padStart(2, '0'))
            append(separator).append(millis.toString().padStart(3, '0'))
        }
    }

    private fun markdownBody(document: TranscriptDocument): String = document.segments
        .filter { it.text.isNotBlank() }
        .joinToString("\n\n") { segment ->
            val prefix = buildString {
                val start = segment.startMs
                val end = segment.endMs
                if (start != null && end != null && start >= 0 && end > start && segment.timeEvidence != TimeEvidence.UNKNOWN) {
                    append('[').append(timestamp(start, false)).append(" - ").append(timestamp(end, false)).append("] ")
                }
                segment.speaker?.let(::singleLineValue)?.takeIf { it.isNotBlank() }?.let {
                    append(it).append(": ")
                }
            }
            prefix + normalizeLineBreaks(segment.text)
        }

    private data class MetadataField(val label: String, val value: String?)

    private fun metadataFields(document: TranscriptDocument): List<MetadataField> {
        val source = document.source
        val config = document.acquisition
        val provenance = document.provenance
        val scope = document.scope
        return listOf(
            MetadataField("Title", source.title),
            MetadataField("Source", source.canonicalUrl ?: source.id),
            MetadataField("Source ID", source.id),
            MetadataField("Language", document.language),
            MetadataField("Provenance", provenanceSummary(document)),
            MetadataField("Requested model", provenance.requestedModel),
            MetadataField("Reported model", provenance.reportedModel),
            MetadataField("Limitations", limitations(document)),
            MetadataField("Schema version", document.schemaVersion.toString()),
            MetadataField("Artifact ID", document.artifactId),
            MetadataField("Video ID", source.videoId),
            MetadataField("Source kind", source.kind.name),
            MetadataField("Channel", source.channel),
            MetadataField("Duration (ms)", source.durationMs?.toString()),
            MetadataField("Published date", source.publishedDate),
            MetadataField("Original language", source.originalLanguage),
            MetadataField("Source content hash", source.contentHash),
            MetadataField("Source file name", source.fileName),
            MetadataField("Source MIME type", source.mimeType),
            MetadataField("Source file bytes", source.fileBytes?.toString()),
            MetadataField("Thumbnail URL", source.thumbnailUrl),
            MetadataField("Reported languages", listValue(provenance.reportedLanguages)),
            MetadataField("Acquisition mode", config.mode.name),
            MetadataField("Acquisition provider", config.provider?.name),
            MetadataField("Configured model", config.model),
            MetadataField("Region", config.region.name),
            MetadataField("Credential reference", config.credentialId),
            MetadataField("Preferred languages", listValue(config.preferredLanguages)),
            MetadataField("Prefer original language", config.preferOriginalLanguage.toString()),
            MetadataField("Allow uploader captions", config.allowUploaderCaptions.toString()),
            MetadataField("Allow automatic captions", config.allowAutomaticCaptions.toString()),
            MetadataField("Allow translated captions", config.allowTranslatedCaptions.toString()),
            MetadataField("Caption track ID", config.captionTrackId),
            MetadataField("Audio track ID", config.audioTrackId),
            MetadataField("Fallback on caption error", config.fallbackOnCaptionError.toString()),
            MetadataField("Requested language", config.language),
            MetadataField("Diarization", config.diarization.toString()),
            MetadataField("Word timestamps", config.wordTimestamps.toString()),
            MetadataField("Segment timestamps", config.segmentTimestamps.toString()),
            MetadataField("Context terms", listValue(config.contextTerms)),
            MetadataField("Export formats", listValue(config.exportFormats.sortedBy { it.name })),
            MetadataField("Export tree URI", config.exportTreeUri),
            MetadataField("Retain raw", config.retainRaw.toString()),
            MetadataField("Audio retention", config.audioRetention.name),
            MetadataField("Network policy", config.networkPolicy.name),
            MetadataField("Upload approved", config.uploadApproved.toString()),
            MetadataField("Maximum audio seconds", config.maxAudioSeconds.toString()),
            MetadataField("Maximum cost (micro USD)", config.maxCostMicrousd?.toString()),
            MetadataField("Provenance origin", provenance.origin.name),
            MetadataField("Generation", provenance.generation.name),
            MetadataField("Translation", provenance.translation.name),
            MetadataField("Provider", provenance.provider?.name),
            MetadataField("Requested model", provenance.requestedModel),
            MetadataField("Reported model", provenance.reportedModel),
            MetadataField("Language evidence", provenance.languageEvidence),
            MetadataField("Reused artifact", provenance.reusedArtifactId),
            MetadataField("Source audio track", audioTrackValue(provenance.sourceAudioTrack)),
            MetadataField("Caption track", captionTrackValue(provenance.captionTrack)),
            MetadataField("Engine versions", mapValue(provenance.engineVersions)),
            MetadataField("Scope requested duration (ms)", scope.requestedDurationMs?.toString()),
            MetadataField("Scope processed intervals", intervalsValue(scope.processedIntervals)),
            MetadataField("Scope missing chunks", listValue(scope.missingChunks)),
            MetadataField("Scope technically complete", scope.technicallyComplete?.toString()),
            MetadataField("Created at (Unix ms)", document.createdAt.toString()),
            MetadataField("Created at (UTC)", createdAtUtc(document.createdAt)),
            MetadataField("Raw hash", document.rawHash),
            MetadataField("Normalization version", document.normalizationVersion),
            MetadataField("Segment count", document.segments.size.toString()),
            MetadataField("Word count", document.words.size.toString()),
        )
    }

    private fun provenanceSummary(document: TranscriptDocument): String = listOf(
        "origin=${document.provenance.origin.name.lowercase(Locale.ROOT)}",
        "generation=${document.provenance.generation.name.lowercase(Locale.ROOT)}",
        "translation=${document.provenance.translation.name.lowercase(Locale.ROOT)}",
        "provider=${document.provenance.provider?.name?.lowercase(Locale.ROOT) ?: "unknown"}",
        "languageEvidence=${document.provenance.languageEvidence ?: "unknown"}",
    ).joinToString(", ")

    private fun limitations(document: TranscriptDocument): String {
        val scope = document.scope
        val values = ArrayList<String>()
        values += "technicallyComplete=${scope.technicallyComplete?.toString() ?: "unknown"}"
        values += "requestedDurationMs=${scope.requestedDurationMs?.toString() ?: "unknown"}"
        values += "processedIntervals=${intervalsValue(scope.processedIntervals)}"
        values += "missingChunks=${listValue(scope.missingChunks)}"
        document.warnings.forEach { values += "warning=$it" }
        return values.joinToString("; ").ifBlank { "none reported" }
    }

    private fun listValue(values: Iterable<Any?>): String {
        val rendered = values.map { it?.toString() ?: "unknown" }
        return rendered.joinToString(", ", prefix = "[", postfix = "]").ifBlank { "none" }
    }

    private fun intervalsValue(intervals: List<Interval>): String =
        if (intervals.isEmpty()) "none" else intervals.joinToString(", ", prefix = "[", postfix = "]") {
            "${it.startMs}..${it.endMs}"
        }

    private fun mapValue(values: Map<String, String>): String =
        if (values.isEmpty()) "none" else values.entries.sortedBy { it.key }.joinToString(", ", prefix = "{", postfix = "}") {
            "${it.key}=${it.value}"
        }

    private fun audioTrackValue(track: AudioTrack?): String? = track?.let {
        listOf(
            "id=${it.id}",
            "sourceVideoId=${it.sourceVideoId}",
            "language=${it.language ?: "unknown"}",
            "name=${it.name ?: "unknown"}",
            "isOriginal=${it.isOriginal?.toString() ?: "unknown"}",
            "evidence=${it.evidence}",
        ).joinToString(", ")
    }

    private fun captionTrackValue(track: CaptionTrack?): String? = track?.let {
        listOf(
            "id=${it.id}",
            "sourceVideoId=${it.sourceVideoId}",
            "language=${it.language}",
            "name=${it.name ?: "unknown"}",
            "format=${it.format}",
            "generation=${it.generation.name}",
            "translation=${it.translation.name}",
            "evidence=${it.evidence}",
        ).joinToString(", ")
    }

    private fun createdAtUtc(createdAt: Long): String = Instant.ofEpochMilli(createdAt).toString()

    private fun markdownValue(value: String?): String = buildString {
        for (character in normalizeLineBreaks(value ?: "unknown")) {
            when {
                character == '\n' -> append("\\n")
                character.code < 0x20 || character.code in 0x7F..0x9F ->
                    append("\\u").append(character.code.toString(16).padStart(4, '0').uppercase(Locale.ROOT))
                character in "\\`*_[]()<>#|~" -> append('\\').append(character)
                else -> append(character)
            }
        }
    }

    private fun textValue(value: String?): String = buildString {
        for (character in normalizeLineBreaks(value ?: "unknown")) {
            when {
                character == '\n' -> append("\\n")
                character.code < 0x20 || character.code in 0x7F..0x9F ->
                    append("\\u").append(character.code.toString(16).padStart(4, '0').uppercase(Locale.ROOT))
                else -> append(character)
            }
        }
    }

    private fun singleLineValue(value: String): String = textValue(value).replace("\\n", " ")

    private fun singleLineTimedValue(value: String): String =
        escapeTimedData(normalizeLineBreaks(value).replace('\n', ' '))

    private fun timedText(value: String): String? {
        val normalized = normalizeLineBreaks(value)
        if (normalized.isBlank() || normalized.firstOrNull() == '\n' || normalized.lastOrNull() == '\n' ||
            BLANK_CUE_LINE.containsMatchIn(normalized) || normalized.contains("-->")
        ) {
            return null
        }
        return escapeTimedData(normalized)
    }

    private fun escapeTimedData(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun normalizeLineBreaks(value: String): String = value.replace("\r\n", "\n").replace('\r', '\n')

    private fun fenceFor(value: String): String {
        val longest = Regex("`+").findAll(value).maxOfOrNull { it.value.length } ?: 0
        return "`".repeat(maxOf(3, longest + 1))
    }

    private fun safePart(value: String, fallback: String, maxChars: Int = MAX_FILENAME_PART_CHARS): String {
        val cleaned = buildString {
            for (character in value.trim()) {
                when {
                    character.code < 0x20 || character.code in 0x7F..0x9F -> append('_')
                    character.isWhitespace() -> append('_')
                    character in "<>:\"/\\|?*" -> append('_')
                    else -> append(character)
                }
            }
        }.trim('.', ' ')
            .replace(Regex("_+"), "_")
            .replace(Regex("\\.{2,}"), "_")
            .take(maxChars)
            .trimEnd('.', ' ')
        if (cleaned.isEmpty()) return fallback
        val upper = cleaned.uppercase(Locale.ROOT)
        val reserved = upper in setOf("CON", "PRN", "AUX", "NUL") ||
            (upper.length == 4 && upper.substring(0, 3) in setOf("COM", "LPT") && upper[3].isDigit())
        return if (reserved) "_$cleaned" else cleaned
    }

}
