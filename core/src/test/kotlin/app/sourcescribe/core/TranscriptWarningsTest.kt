package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptWarningsTest {
    @Test fun theChunkAndThePositionInsideItAreNotPartOfWhatAWarningSays() {
        assertEquals("WORD_TIMESTAMPS_MALFORMED", TranscriptWarnings.family("CHUNK_3_WORD_TIMESTAMPS_MALFORMED_OFFSET_17"))
        assertEquals("WORD_TIMESTAMPS_MALFORMED", TranscriptWarnings.family("WORD_TIMESTAMPS_MALFORMED_SPEAKER_4"))
        assertEquals("DIARIZATION_SPEAKER_MISSING", TranscriptWarnings.family("DIARIZATION_SPEAKER_MISSING_3"))
        // A caption warning can carry two positions, the event and the segment inside it.
        assertEquals("MALFORMED_SEGMENT", TranscriptWarnings.family("MALFORMED_SEGMENT_12_4"))
        // The missing sections are listed after a colon rather than an underscore.
        assertEquals("MISSING_CHUNKS", TranscriptWarnings.family("MISSING_CHUNKS:2,3,4"))
        assertEquals("RESPONSE_QUOTA", TranscriptWarnings.family("CHUNK_11_RESPONSE_QUOTA"))
        // Only a chunk prefix that really carries a number is a chunk prefix.
        assertEquals("CHUNK_LIKE_NAME", TranscriptWarnings.family("CHUNK_LIKE_NAME"))
    }

    @Test fun thousandsOfEntriesBecomeTheFewThingsTheyActuallySay() {
        // What a ten-hour job can hand over: the same two problems per chunk, once per broken entry.
        val warnings = (0 until 60).flatMap { chunk ->
            (0 until 40).flatMap { index ->
                listOf(
                    "CHUNK_${chunk}_WORD_TIMESTAMPS_MALFORMED_$index",
                    "CHUNK_${chunk}_DIARIZATION_SPEAKER_MISSING_$index",
                )
            }
        }
        assertEquals(4_800, warnings.size)
        val summary = TranscriptWarnings.summarize(warnings)
        assertEquals(listOf(WarningGroup.WORD_TIMES, WarningGroup.SPEAKERS), summary.groups)
        assertEquals(emptyList<String>(), summary.unknown)
    }

    @Test fun whatIsMissingFromTheResultIsSaidBeforeWhatIsMerelyUncertain() {
        // Deliberately handed over in the least helpful order to show the display order does not follow it.
        val summary = TranscriptWarnings.summarize(listOf(
            "WARNINGS_TRUNCATED",
            "MULTIPLE_REPORTED_MODELS",
            "WORD_TIMESTAMPS_MISSING",
            "CHUNK_2_RESPONSE_STORAGE",
            "MISSING_CHUNKS:2",
            "CHUNK_5_REMOTE_NOT_COMPLETE",
        ))
        assertEquals(
            listOf(
                WarningGroup.COVERAGE,
                WarningGroup.SECTION_LOST_STORAGE,
                WarningGroup.SECTION_LOST_PROVIDER,
                WarningGroup.WORD_TIMES,
                WarningGroup.PROVIDER_MODEL,
                WarningGroup.MORE_NOTES,
            ),
            summary.groups,
        )
    }

    @Test fun aCodeNoOneKnowsIsHandedBackInsteadOfDroppedSilently() {
        val summary = TranscriptWarnings.summarize(listOf(
            "CHUNK_1_SOMETHING_INVENTED_LATER_7", "WORD_TIMESTAMPS_MISSING", "SOMETHING_INVENTED_LATER_9",
        ))
        assertEquals(listOf(WarningGroup.WORD_TIMES), summary.groups)
        // The same unknown family twice, from two different chunks, is one thing to report.
        assertEquals(listOf("SOMETHING_INVENTED_LATER"), summary.unknown)
    }

    @Test fun everyRefusalTheProviderGivesForOneSectionCountsAsALostSection() {
        // The code is built from the provider error enum, so the mapping has to hold for all of its values
        // rather than for the handful somebody thought of.
        for (code in ProviderErrorCode.entries) {
            val summary = TranscriptWarnings.summarize(listOf("CHUNK_0_RESPONSE_${code.name}"))
            assertEquals("$code should be reported as a lost section",
                listOf(WarningGroup.SECTION_LOST_PROVIDER), summary.groups)
            assertTrue("$code should not be reported as unexplained", summary.unknown.isEmpty())
        }
    }

    @Test fun aSentenceSomebodyWroteForAReaderIsNotTakenApartLikeACode() {
        // The fixture used for looking at the app by hand puts whole sentences in this list. Treating one
        // as a code would cut it at its first colon and hand back a fragment.
        val note = "SYNTHETISCHE UI-PR\u00dcFDATEI: nur f\u00fcr manuelle Viewer-/ADB-Pr\u00fcfung."
        assertEquals(note, TranscriptWarnings.family(note))
        val summary = TranscriptWarnings.summarize(listOf(note, "WORD_TIMESTAMPS_MISSING"))
        assertEquals(listOf(WarningGroup.WORD_TIMES), summary.groups)
        assertEquals(listOf(note), summary.unknown)
        assertFalse(TranscriptWarnings.looksLikeCode(note))
        assertTrue(TranscriptWarnings.looksLikeCode("MISSING_CHUNKS:2,3"))
        assertTrue(TranscriptWarnings.looksLikeCode("CHUNK_3_WORD_TIMESTAMPS_MALFORMED_17"))
    }

    @Test fun anEmptyListSaysNothing() {
        val summary = TranscriptWarnings.summarize(emptyList())
        assertEquals(emptyList<WarningGroup>(), summary.groups)
        assertEquals(emptyList<String>(), summary.unknown)
    }
}
