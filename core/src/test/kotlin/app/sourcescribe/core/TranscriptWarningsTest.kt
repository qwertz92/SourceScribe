package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptWarningsTest {
    @Test fun theChunkAndThePositionInsideItAreNotPartOfWhatAWarningSays() {
        assertEquals("WORD_TIMESTAMPS_MALFORMED", TranscriptWarnings.family("CHUNK_3_WORD_TIMESTAMPS_MALFORMED_17"))
        assertEquals("DIARIZATION_SPEAKER_MISSING", TranscriptWarnings.family("DIARIZATION_SPEAKER_MISSING_3"))
        // A caption warning can carry two positions, the event and the segment inside it.
        assertEquals("MALFORMED_CAPTION_SEGMENT", TranscriptWarnings.family("MALFORMED_CAPTION_SEGMENT_12_4"))
        // The missing sections are listed after a colon rather than an underscore.
        assertEquals("MISSING_CHUNKS", TranscriptWarnings.family("MISSING_CHUNKS:2,3,4"))
        assertEquals("RESPONSE_QUOTA", TranscriptWarnings.family("CHUNK_11_RESPONSE_QUOTA"))
        // Only a chunk prefix that really carries a number is a chunk prefix.
        assertEquals("CHUNK_LIKE_NAME", TranscriptWarnings.family("CHUNK_LIKE_NAME"))
    }

    @Test fun aWordInFrontOfTheIndexBelongsToTheFamilyAndIsNotStrippedWithIt() {
        // A rule that stripped this word had to guess where the family name ends, and the guess took the
        // last word of every family that ends in the same word. Both halves of that are pinned here.
        assertEquals("WORD_TIMESTAMPS_MALFORMED_SPEAKER", TranscriptWarnings.family("WORD_TIMESTAMPS_MALFORMED_SPEAKER_4"))
        assertEquals("WORD_TIMESTAMPS_MALFORMED_OFFSET", TranscriptWarnings.family("CHUNK_3_WORD_TIMESTAMPS_MALFORMED_OFFSET_17"))
        assertEquals("DIARIZATION_MALFORMED_OFFSET", TranscriptWarnings.family("DIARIZATION_MALFORMED_OFFSET_2"))
        assertEquals("MISSING_SPEAKER", TranscriptWarnings.family("MISSING_SPEAKER_7"))
        assertEquals("INVALID_CHUNK_OFFSET", TranscriptWarnings.family("INVALID_CHUNK_OFFSET_5"))
        // Which is what those two codes are for: a speaker the provider left out, and a section whose
        // timestamps could not be moved onto the whole recording.
        assertEquals(listOf(WarningGroup.SPEAKERS),
            TranscriptWarnings.summarize(listOf("CHUNK_1_MISSING_SPEAKER_7")).groups)
        assertEquals(listOf(WarningGroup.SEGMENT_TIMES),
            TranscriptWarnings.summarize(listOf("INVALID_CHUNK_OFFSET_5")).groups)
        // The three codes that carry such a word are checked through the summary too, not only here: a
        // family name that survives the shortening is worth nothing if no group claims it.
        for (code in listOf("WORD_TIMESTAMPS_MALFORMED_SPEAKER_4", "CHUNK_3_WORD_TIMESTAMPS_MALFORMED_OFFSET_17")) {
            assertEquals(code, listOf(WarningGroup.WORD_TIMES), TranscriptWarnings.summarize(listOf(code)).groups)
        }
        assertEquals(listOf(WarningGroup.SPEAKERS),
            TranscriptWarnings.summarize(listOf("DIARIZATION_MALFORMED_OFFSET_2")).groups)
    }

    @Test fun everyGroupIsReachableFromACodeSomeParserActuallyWrites() {
        val examples = mapOf(
            WarningGroup.COVERAGE to "MISSING_CHUNKS:2,3",
            WarningGroup.SECTION_ALIGNMENT to "AUDIO_INTERVAL_GAP_OR_OVERLAP",
            WarningGroup.SECTION_LOST_STORAGE to "CHUNK_2_RESPONSE_STORAGE",
            WarningGroup.SECTION_LOST_PROVIDER to "CHUNK_5_REMOTE_NOT_COMPLETE",
            WarningGroup.RESULT_SHORTENED to "EVENT_LIMIT_REACHED",
            WarningGroup.MISSING_TEXT to "MALFORMED_CAPTION_SEGMENT_3_1",
            WarningGroup.SEGMENT_TIMES to "CHUNK_1_INVALID_SEGMENT_TIMESTAMP_4",
            WarningGroup.WORD_TIMES to "CHUNK_1_WORD_TIMESTAMPS_MALFORMED_SPEAKER_4",
            WarningGroup.SPEAKERS to "CHUNK_1_DIARIZATION_MALFORMED_OFFSET_4",
            WarningGroup.PROVIDER_LANGUAGE to "MULTIPLE_LANGUAGES",
            WarningGroup.PROVIDER_MODEL to "MULTIPLE_REPORTED_MODELS",
            WarningGroup.CAPTION_SOURCE to "MALFORMED_CUE_LINE_12",
            WarningGroup.MORE_NOTES to "WARNINGS_TRUNCATED",
        )
        // A group added without a code that reaches it would be a sentence nobody can ever see.
        assertEquals(WarningGroup.entries.toSet(), examples.keys)
        for ((group, code) in examples) {
            val summary = TranscriptWarnings.summarize(listOf(code))
            assertEquals("$code should be reported as $group", listOf(group), summary.groups)
            assertTrue("$code should not be reported as unexplained", summary.unknown.isEmpty())
        }
    }

    @Test fun anEntryDroppedWholeIsReportedAsMissingTextAndNotAsAMissingTimestamp() {
        // Both parsers skip the entry entirely, so its text never reaches the transcript. The timestamp
        // families keep their text and lose only the time, which is a different thing to tell a reader.
        for (code in listOf("CHUNK_1_MALFORMED_SEGMENT_4", "MALFORMED_CAPTION_SEGMENT_3_1", "MALFORMED_CAPTION_SEGMENTS_3")) {
            assertEquals(code, listOf(WarningGroup.MISSING_TEXT), TranscriptWarnings.summarize(listOf(code)).groups)
        }
        // The provider code of the same name means the timestamp list was unusable, not that text is gone.
        assertEquals(listOf(WarningGroup.SEGMENT_TIMES), TranscriptWarnings.summarize(listOf("MALFORMED_SEGMENTS")).groups)
        // The word counterparts of both codes drop an entry from the word list, which carries the times per
        // word and not the text a reader sees. Reporting them as missing text would name the wrong loss.
        for (code in listOf("CHUNK_1_MALFORMED_WORD_4", "CHUNK_1_MISSING_WORD_TEXT_4")) {
            assertEquals(code, listOf(WarningGroup.WORD_TIMES), TranscriptWarnings.summarize(listOf(code)).groups)
        }
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
            "AUDIO_INTERVAL_GAP_OR_OVERLAP",
            "MISSING_CHUNKS:2",
            "CHUNK_5_REMOTE_NOT_COMPLETE",
        ))
        assertEquals(
            listOf(
                WarningGroup.COVERAGE,
                WarningGroup.SECTION_LOST_STORAGE,
                WarningGroup.SECTION_LOST_PROVIDER,
                WarningGroup.SECTION_ALIGNMENT,
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

    @Test fun anEntryThatLeavesNoFamilyBehindIsStillReported() {
        // A code that is nothing but a chunk prefix reduces to the empty string. Skipping it would drop a
        // recorded warning without a trace, which is exactly what this summary promises not to do.
        val summary = TranscriptWarnings.summarize(listOf("CHUNK_2_"))
        assertEquals(emptyList<WarningGroup>(), summary.groups)
        assertEquals(listOf("CHUNK_2_"), summary.unknown)
        // An entry that carries no characters has nothing to report either way.
        assertEquals(emptyList<String>(), TranscriptWarnings.summarize(listOf("", "   ")).unknown)
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
        val note = "SYNTHETISCHE UI-PRÜFDATEI: nur für manuelle Viewer-/ADB-Prüfung."
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
