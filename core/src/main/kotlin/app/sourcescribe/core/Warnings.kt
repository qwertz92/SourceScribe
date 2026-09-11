package app.sourcescribe.core

/**
 * Warnings are recorded per malformed entry, and one answer may carry hundreds of thousands of entries:
 * an unbounded list therefore grows with the size of the answer instead of with the number of distinct
 * problems, and a single deliberately broken answer could fill the heap of a phone that way. Only
 * [LIMIT] distinct warnings are kept. Once that many are recorded a single [TRUNCATED] marker is added,
 * so a shortened list is never mistaken for the whole picture.
 *
 * Past that limit the first warning of a kind is still kept. Which kinds exist is decided by this
 * program's own code and not by an answer, so their number is small and bounded, while the number of
 * entries an answer can break is neither. Without the exception, sixty-four broken timestamps would push a
 * later missing-text warning out of the list for good and hide a second, different problem behind the
 * marker — and the marker says only that something is missing, not what.
 *
 * The caption parser shares this because it had the same cap without the marker, which left a shortened
 * caption warning list indistinguishable from a complete one.
 */
internal class Warnings {
    private val recorded = LinkedHashSet<String>()
    private val kinds = HashSet<String>()
    private var truncated = false

    operator fun plusAssign(warning: String) {
        if (warning in recorded) return
        val firstOfItsKind = kinds.add(TranscriptWarnings.family(warning))
        if (recorded.size < LIMIT || firstOfItsKind) recorded += warning else truncated = true
    }

    fun toList(): List<String> = if (truncated) recorded.toList() + TRUNCATED else recorded.toList()

    internal companion object {
        const val LIMIT = 64
        const val TRUNCATED = "WARNINGS_TRUNCATED"
    }
}
