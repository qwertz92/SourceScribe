package app.sourcescribe.core

/**
 * Warnings are recorded per malformed entry, and one answer may carry hundreds of thousands of entries:
 * an unbounded list therefore grows with the size of the answer instead of with the number of distinct
 * problems, and a single deliberately broken answer could fill the heap of a phone that way. Only
 * [LIMIT] distinct warnings are kept. Once that many are recorded a single [TRUNCATED] marker is added,
 * so a shortened list is never mistaken for the whole picture.
 *
 * Past that limit the first warning of a kind is still kept. Without the exception, sixty-four broken
 * timestamps would push a later missing-text warning out of the list for good and hide a second, different
 * problem behind the marker — and the marker says only that something is missing, not what.
 *
 * That exception needs the number of kinds to stay small. It does, because [TranscriptWarnings] names them
 * and a test holds that count below [KIND_LIMIT] — but that is a fact about the rest of the program, not
 * about this class, and a caller that built a code out of something an answer said would hand it as many
 * kinds as the answer cares to invent. Every one of them would be a first of its kind and enter the list,
 * which is the heap the limit above exists to protect. [KIND_LIMIT] therefore ends the exception outright,
 * so no caller can grow the list past [LIMIT] plus [KIND_LIMIT] entries however it names its codes.
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
        val full = recorded.size >= LIMIT
        // Below the limit every warning is recorded, so `kinds` cannot outgrow `recorded` there; past it
        // this is what stops both of them, because the kind is not even remembered once the ceiling is met.
        if (full && kinds.size >= KIND_LIMIT) {
            truncated = true
            return
        }
        val firstOfItsKind = kinds.add(TranscriptWarnings.family(warning))
        if (!full || firstOfItsKind) recorded += warning else truncated = true
    }

    fun toList(): List<String> = if (truncated) recorded.toList() + TRUNCATED else recorded.toList()

    internal companion object {
        const val LIMIT = 64

        /**
         * Room for every family this program names, the ones it builds from a provider error code included,
         * and room again for that many on top. A ceiling rather than a working limit: no answer to a single
         * section can name this many different problems, and a test fails if the program ever grows towards
         * it, so reaching it means a code was named after something in an answer.
         */
        const val KIND_LIMIT = 160
        const val TRUNCATED = "WARNINGS_TRUNCATED"
    }
}
