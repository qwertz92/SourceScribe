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
