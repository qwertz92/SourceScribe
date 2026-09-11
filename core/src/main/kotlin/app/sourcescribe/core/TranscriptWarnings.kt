package app.sourcescribe.core

/**
 * What a warning says about the finished transcript, independent of which parser recorded it and where.
 * The order of the entries is the order they are shown in: what is missing from the result first, what is
 * uncertain about it next, what the provider said about itself last. [SECTION_ALIGNMENT] sits on the seam,
 * as the first of the uncertain ones, because it can mean a missing stretch but does not establish one.
 */
enum class WarningGroup {
    COVERAGE,
    SECTION_LOST_STORAGE,
    SECTION_LOST_PROVIDER,
    RESULT_SHORTENED,
    MISSING_TEXT,
    SECTION_ALIGNMENT,
    SEGMENT_TIMES,
    WORD_TIMES,
    SPEAKERS,
    PROVIDER_LANGUAGE,
    PROVIDER_MODEL,
    CAPTION_SOURCE,
    MORE_NOTES,
}

/**
 * The parsers record one warning per affected entry, and every warning of a provider answer is then
 * prefixed with the chunk it came from, so a ten-hour job can carry a few thousand of them. None of that
 * answers the question a reader has, which is what is wrong with the transcript in front of them.
 *
 * A code is therefore reduced to the family it belongs to — the chunk prefix and the position inside the
 * chunk are dropped — and families that say the same thing about the result share one group. A code that
 * matches no group is reported back unchanged rather than dropped, so a code added later shows up as an
 * unexplained code instead of silently disappearing.
 */
object TranscriptWarnings {
    /** The groups present, in display order, and every code that matched none of them, in first-seen order. */
    data class Summary(val groups: List<WarningGroup>, val unknown: List<String>)

    fun summarize(warnings: List<String>): Summary {
        val groups = LinkedHashSet<WarningGroup>()
        val unknown = LinkedHashSet<String>()
        for (warning in warnings) {
            // A code that is nothing but a chunk prefix leaves no family behind. It still has to be
            // reported, so it falls back to what was actually recorded rather than being dropped.
            val family = family(warning).ifEmpty { warning }
            if (family.isBlank()) continue
            val group = group(family)
            if (group == null) unknown += family else groups += group
        }
        return Summary(groups.sortedBy { it.ordinal }, unknown.toList())
    }

    /**
     * True for the shape every recorded warning code has: capitals, digits and underscores, optionally
     * followed by a colon and the section numbers it names. Anything else in the list is text somebody put
     * there for a reader, and text must survive this untouched rather than be cut at its first colon.
     */
    fun looksLikeCode(value: String): Boolean {
        val colon = value.indexOf(':')
        val head = if (colon < 0) value.length else colon
        if (head == 0) return false
        for (index in 0 until head) {
            val character = value[index]
            if (character !in 'A'..'Z' && character !in '0'..'9' && character != '_') return false
        }
        for (index in head + 1 until value.length) {
            val character = value[index]
            if (character !in '0'..'9' && character != ',') return false
        }
        return true
    }

    /**
     * `CHUNK_3_MALFORMED_SEGMENT_12_4` and `MISSING_CHUNKS:2,3` both name one family. A trailing number is
     * the only thing stripped, repeatedly, because a caption warning can carry two of them, one for the
     * event and one for the segment inside it.
     *
     * A word in front of that number, as in `WORD_TIMESTAMPS_MALFORMED_OFFSET_17`, stays part of the family
     * name. Stripping it would have to guess where the name ends, and the guess took the last word of every
     * family that happens to end in the same word: `MISSING_SPEAKER_7` became `MISSING` and
     * `INVALID_CHUNK_OFFSET_5` became `INVALID_CHUNK`, so neither could ever be recognised.
     *
     * Counted out by hand rather than matched by a pattern. A pattern is what produced that guess, because
     * a regular expression takes the leftmost position a match can start at, not the shortest one. It also
     * keeps this cheap enough for [Warnings] to ask for a family on every warning it records.
     */
    internal fun family(code: String): String {
        if (!looksLikeCode(code)) return code
        var value = code.substringBefore(':')
        if (value.startsWith(CHUNK_PREFIX)) {
            var end = CHUNK_PREFIX.length
            while (end < value.length && value[end] in '0'..'9') end++
            // Only a prefix that really carries a number names a chunk, so `CHUNK_LIKE_NAME` keeps its name.
            if (end > CHUNK_PREFIX.length && end < value.length && value[end] == '_') value = value.substring(end + 1)
        }
        while (true) {
            var end = value.length
            while (end > 0 && value[end - 1] in '0'..'9') end--
            if (end == value.length || end == 0 || value[end - 1] != '_') return value
            value = value.substring(0, end - 1)
        }
    }

    private fun group(family: String): WarningGroup? = FAMILIES[family]
        // A refusal the provider gave for one section is built from its error code, so it is matched by its
        // prefix instead of being listed value by value.
        ?: if (family.startsWith(RESPONSE_PREFIX)) WarningGroup.SECTION_LOST_PROVIDER else null

    /**
     * Every family this program names outright, with the group it belongs to. Data rather than a `when`, for
     * two reasons. The names become countable, which is what lets [Warnings] bound the number of kinds it
     * keeps against the number that can actually arise instead of against a convention its callers follow.
     * And a test can drive each single name through [summarize], where before one example per group stood in
     * for all of them and most of these names were covered by nothing at all.
     */
    internal val GROUPED_FAMILIES: List<Pair<WarningGroup, List<String>>> = listOf(
        WarningGroup.COVERAGE to listOf("MISSING_CHUNKS"),

        // These look like a storage problem and are not one: every place that records them drops the whole
        // section from the transcript in the same step, so they belong with what is missing, not with what
        // could not be filed away.
        WarningGroup.SECTION_LOST_STORAGE to listOf(
            "RESPONSE_STORAGE", "RAW_RESPONSE_TOO_LARGE", "RAW_RESPONSE_AGGREGATE_TOO_LARGE",
            "CANONICAL_TRANSCRIPT_AGGREGATE_TOO_LARGE",
        ),

        WarningGroup.SECTION_LOST_PROVIDER to listOf("REMOTE_NOT_COMPLETE"),

        WarningGroup.RESULT_SHORTENED to listOf(
            "WORD_LIMIT_REACHED", "SEGMENT_LIMIT_REACHED", "EVENT_LIMIT_REACHED",
        ),

        // An entry that could not be read at all is skipped whole, so its text never reaches the transcript.
        // That is a missing passage and not a missing timestamp, which is what SEGMENT_TIMES is for.
        WarningGroup.MISSING_TEXT to listOf(
            "MALFORMED_SEGMENT", "MALFORMED_CAPTION_SEGMENT", "MALFORMED_CAPTION_SEGMENTS",
            "MISSING_SEGMENT_TEXT", "EMPTY_CUE_LINE", "EMPTY_EVENT",
        ),

        // The check behind this one fires for a gap and for an overlap alike, and cannot tell them apart.
        // Saying a part is missing would claim more than was established.
        WarningGroup.SECTION_ALIGNMENT to listOf("AUDIO_INTERVAL_GAP_OR_OVERLAP"),

        // Every family here keeps its text and loses only its place on the timeline. `INVALID_CHUNK_OFFSET`
        // belongs with them: the entry is still written, its start and end are dropped.
        WarningGroup.SEGMENT_TIMES to listOf(
            "SEGMENT_TIMESTAMPS_MISSING", "MALFORMED_SEGMENTS", "INVALID_SEGMENT_TIMESTAMP",
            "OUT_OF_RANGE_SEGMENT_TIMESTAMP", "NON_MONOTONIC_SEGMENT_TIMESTAMP",
            "MISSING_SEGMENT_TIMESTAMPS", "INCOMPLETE_SEGMENT_TIMESTAMPS", "INVALID_CHUNK_OFFSET",
        ),

        // `MALFORMED_WORD` and `MISSING_WORD_TEXT` cost the word list an entry, and that list carries the
        // times per word; the text a reader sees comes from the segments. So they belong here rather than
        // with the missing passages, unlike their segment counterparts above.
        WarningGroup.WORD_TIMES to listOf(
            "WORD_TIMESTAMPS_MISSING", "WORD_TIMESTAMPS_MALFORMED", "WORD_TIMESTAMPS_MALFORMED_SPEAKER",
            "WORD_TIMESTAMPS_MALFORMED_OFFSET", "WORD_TIMESTAMPS_OUT_OF_RANGE", "MALFORMED_WORD",
            "MISSING_WORD_TEXT", "INVALID_WORD_TIMESTAMP", "OUT_OF_RANGE_WORD_TIMESTAMP",
            "NON_MONOTONIC_WORD_TIMESTAMP", "MALFORMED_WORDS", "MISSING_WORD_TIMESTAMPS",
            "INCOMPLETE_WORD_TIMESTAMPS",
        ),

        WarningGroup.SPEAKERS to listOf(
            "DIARIZATION_MISSING", "DIARIZATION_MALFORMED", "DIARIZATION_MALFORMED_OFFSET",
            "DIARIZATION_OUT_OF_RANGE", "DIARIZATION_SPEAKER_MISSING", "MALFORMED_DIARIZED_SEGMENTS",
            "MISSING_DIARIZED_SEGMENTS", "MISSING_DIARIZED_SPEAKER", "MISSING_SPEAKER",
        ),

        WarningGroup.PROVIDER_LANGUAGE to listOf(
            "REPORTED_LANGUAGES_MALFORMED", "MULTIPLE_LANGUAGES", "LANGUAGE_LIMIT_REACHED",
        ),

        WarningGroup.PROVIDER_MODEL to listOf(
            "REPORTED_MODEL_MALFORMED", "REPORTED_MODEL_TOO_LONG", "MULTIPLE_REPORTED_MODELS",
        ),

        WarningGroup.CAPTION_SOURCE to listOf(
            "MALFORMED_CUE_LINE", "INVALID_TIMING_LINE", "UNKNOWN_VTT_SETTING", "MALFORMED_EVENT",
            "MISSING_OR_INVALID_TIMING_EVENT", "STRUCTURAL_ROLLUP_UNCERTAIN",
        ),

        WarningGroup.MORE_NOTES to listOf(Warnings.TRUNCATED),
    )

    /**
     * [GROUPED_FAMILIES] by name. Two groups claiming one name would leave no trace here, the later one
     * simply taking the family from the earlier, so a test compares this size against the number listed.
     */
    internal val FAMILIES: Map<String, WarningGroup> =
        GROUPED_FAMILIES.flatMap { (group, families) -> families.map { it to group } }.toMap()

    private const val CHUNK_PREFIX = "CHUNK_"
    private const val RESPONSE_PREFIX = "RESPONSE_"

    /** The families built from a provider error code instead of being listed, one per [ProviderErrorCode]. */
    internal val PREFIXED_FAMILY_COUNT = ProviderErrorCode.entries.size
}
