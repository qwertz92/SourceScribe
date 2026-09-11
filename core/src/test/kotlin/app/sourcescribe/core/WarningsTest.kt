package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WarningsTest {
    @Test fun theSameProblemIsRecordedOnceHoweverOftenItOccurs() {
        val warnings = Warnings()
        repeat(1_000) { warnings += "MALFORMED_SEGMENT" }
        assertEquals(listOf("MALFORMED_SEGMENT"), warnings.toList())
    }

    @Test fun aRunawayAnswerCannotGrowTheListWithIt() {
        // The parsers record one warning per malformed entry, so an answer carrying a million broken
        // entries used to produce a million distinct strings on a phone. The list is capped instead, and
        // says that it is capped, so a shortened list is never read as the complete picture.
        val warnings = Warnings()
        repeat(1_000_000) { warnings += "MALFORMED_SEGMENT_$it" }
        val recorded = warnings.toList()
        assertEquals(Warnings.LIMIT + 1, recorded.size)
        assertEquals("MALFORMED_SEGMENT_0", recorded.first())
        assertEquals(Warnings.TRUNCATED, recorded.last())
        // What is kept is the beginning, which is where a reader looks for the first thing that went wrong.
        assertTrue(recorded.contains("MALFORMED_SEGMENT_${Warnings.LIMIT - 1}"))
        assertFalse(recorded.contains("MALFORMED_SEGMENT_${Warnings.LIMIT}"))
    }

    @Test fun aSecondKindOfProblemIsNotPushedOutByTheFirstOne() {
        // The cap counts entries, and one broken section can fill it on its own. If the cap also dropped the
        // first warning of a kind nobody has reported yet, a second, different problem would vanish behind
        // the marker, which says only that something is missing and not what.
        val warnings = Warnings()
        repeat(Warnings.LIMIT) { warnings += "INVALID_TIMING_LINE_$it" }
        warnings += "EMPTY_CUE_LINE_999"
        assertTrue(warnings.toList().toString(), warnings.toList().contains("EMPTY_CUE_LINE_999"))
        // Nothing was dropped to make room for it, so there is nothing to mark as shortened yet.
        assertFalse(warnings.toList().contains(Warnings.TRUNCATED))

        // A further entry of a kind already reported is exactly what the cap is for.
        warnings += "INVALID_TIMING_LINE_${Warnings.LIMIT}"
        val recorded = warnings.toList()
        assertFalse(recorded.contains("INVALID_TIMING_LINE_${Warnings.LIMIT}"))
        assertEquals(Warnings.TRUNCATED, recorded.last())

        // What the reader ends up being told: both problems, not only the one that filled the list.
        assertEquals(
            listOf(WarningGroup.MISSING_TEXT, WarningGroup.CAPTION_SOURCE, WarningGroup.MORE_NOTES),
            TranscriptWarnings.summarize(recorded).groups,
        )
    }

    @Test fun theListKeepsTheOrderTheProblemsWereFoundIn() {
        // The order is the order of discovery, not the alphabet: a reader looks for the first thing that
        // went wrong. Without a case whose insertion order differs from its sorted order, a later switch
        // to a sorted collection would pass unnoticed.
        val warnings = Warnings()
        warnings += "ZEBRA_MISSING"
        warnings += "APPLE_MALFORMED"
        warnings += "MIDDLE_UNCERTAIN"
        assertEquals(listOf("ZEBRA_MISSING", "APPLE_MALFORMED", "MIDDLE_UNCERTAIN"), warnings.toList())
    }

    @Test fun aListThatFitsCarriesNoTruncationMarker() {
        val warnings = Warnings()
        repeat(Warnings.LIMIT) { warnings += "MALFORMED_SEGMENT_$it" }
        val recorded = warnings.toList()
        assertEquals(Warnings.LIMIT, recorded.size)
        assertFalse(recorded.contains(Warnings.TRUNCATED))
        // A repeat of something already recorded does not push the list over its own edge.
        warnings += "MALFORMED_SEGMENT_0"
        assertEquals(recorded, warnings.toList())
    }
}
