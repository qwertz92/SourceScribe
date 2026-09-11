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
        // Written out from the four files that record warnings, one entry per family, in the shape the
        // recording line actually produces. That makes this list and the mapping in `TranscriptWarnings`
        // two sources for the same names, which is the point: the test that walks the mapping compares it
        // with itself and cannot see a name misspelt against the parser that writes it. Here a name that
        // exists in only one of the two lists fails.
        //
        // Thirteen of these names appear nowhere in the parsers as a literal, because the line assembles them
        // from pieces — `"MISSING_${"$"}{if (word) "WORD" else "SEGMENT"}_TEXT_${"$"}index"` and its kin. Neither a
        // search nor a reader finds them there, which is exactly why they are spelled out once here.
        val written = listOf(
            // CaptionParser
            "MALFORMED_CUE_LINE_12" to WarningGroup.CAPTION_SOURCE,
            "INVALID_TIMING_LINE_12" to WarningGroup.CAPTION_SOURCE,
            "UNKNOWN_VTT_SETTING_12" to WarningGroup.CAPTION_SOURCE,
            "EMPTY_CUE_LINE_12" to WarningGroup.MISSING_TEXT,
            "EVENT_LIMIT_REACHED" to WarningGroup.RESULT_SHORTENED,
            "MALFORMED_EVENT_3" to WarningGroup.CAPTION_SOURCE,
            "MALFORMED_CAPTION_SEGMENT_3_1" to WarningGroup.MISSING_TEXT,
            "MALFORMED_CAPTION_SEGMENTS_3" to WarningGroup.MISSING_TEXT,
            "EMPTY_EVENT_3" to WarningGroup.MISSING_TEXT,
            "MISSING_OR_INVALID_TIMING_EVENT_3" to WarningGroup.CAPTION_SOURCE,
            "STRUCTURAL_ROLLUP_UNCERTAIN" to WarningGroup.CAPTION_SOURCE,
            "SEGMENT_LIMIT_REACHED" to WarningGroup.RESULT_SHORTENED,

            // SyncTranscriptParser, which serves OpenAI and Groq
            "WORD_LIMIT_REACHED" to WarningGroup.RESULT_SHORTENED,
            "MALFORMED_WORD_5" to WarningGroup.WORD_TIMES,
            "MALFORMED_SEGMENT_5" to WarningGroup.MISSING_TEXT,
            "MISSING_WORD_TEXT_5" to WarningGroup.WORD_TIMES,
            "MISSING_SEGMENT_TEXT_5" to WarningGroup.MISSING_TEXT,
            "INVALID_WORD_TIMESTAMP_5" to WarningGroup.WORD_TIMES,
            "INVALID_SEGMENT_TIMESTAMP_5" to WarningGroup.SEGMENT_TIMES,
            "OUT_OF_RANGE_WORD_TIMESTAMP_5" to WarningGroup.WORD_TIMES,
            "OUT_OF_RANGE_SEGMENT_TIMESTAMP_5" to WarningGroup.SEGMENT_TIMES,
            "NON_MONOTONIC_WORD_TIMESTAMP_5" to WarningGroup.WORD_TIMES,
            "NON_MONOTONIC_SEGMENT_TIMESTAMP_5" to WarningGroup.SEGMENT_TIMES,
            "MISSING_SPEAKER_5" to WarningGroup.SPEAKERS,
            "INVALID_CHUNK_OFFSET_5" to WarningGroup.SEGMENT_TIMES,
            "MALFORMED_DIARIZED_SEGMENTS" to WarningGroup.SPEAKERS,
            "MISSING_DIARIZED_SEGMENTS" to WarningGroup.SPEAKERS,
            "MISSING_DIARIZED_SPEAKER" to WarningGroup.SPEAKERS,
            "MALFORMED_SEGMENTS" to WarningGroup.SEGMENT_TIMES,
            "MISSING_SEGMENT_TIMESTAMPS" to WarningGroup.SEGMENT_TIMES,
            "INCOMPLETE_SEGMENT_TIMESTAMPS" to WarningGroup.SEGMENT_TIMES,
            "MALFORMED_WORDS" to WarningGroup.WORD_TIMES,
            "MISSING_WORD_TIMESTAMPS" to WarningGroup.WORD_TIMES,
            "INCOMPLETE_WORD_TIMESTAMPS" to WarningGroup.WORD_TIMES,
            "LANGUAGE_LIMIT_REACHED" to WarningGroup.PROVIDER_LANGUAGE,
            "MULTIPLE_LANGUAGES" to WarningGroup.PROVIDER_LANGUAGE,
            "REPORTED_MODEL_MALFORMED" to WarningGroup.PROVIDER_MODEL,
            "REPORTED_MODEL_TOO_LONG" to WarningGroup.PROVIDER_MODEL,

            // AssemblyAiAdapter
            "WORD_TIMESTAMPS_MISSING" to WarningGroup.WORD_TIMES,
            "DIARIZATION_MISSING" to WarningGroup.SPEAKERS,
            "SEGMENT_TIMESTAMPS_MISSING" to WarningGroup.SEGMENT_TIMES,
            "WORD_TIMESTAMPS_MALFORMED_9" to WarningGroup.WORD_TIMES,
            "WORD_TIMESTAMPS_MALFORMED_SPEAKER_9" to WarningGroup.WORD_TIMES,
            "WORD_TIMESTAMPS_MALFORMED_OFFSET_9" to WarningGroup.WORD_TIMES,
            "WORD_TIMESTAMPS_OUT_OF_RANGE_9" to WarningGroup.WORD_TIMES,
            "DIARIZATION_MALFORMED_9" to WarningGroup.SPEAKERS,
            "DIARIZATION_MALFORMED_OFFSET_9" to WarningGroup.SPEAKERS,
            "DIARIZATION_SPEAKER_MISSING_9" to WarningGroup.SPEAKERS,
            "DIARIZATION_OUT_OF_RANGE_9" to WarningGroup.SPEAKERS,
            "REPORTED_LANGUAGES_MALFORMED_9" to WarningGroup.PROVIDER_LANGUAGE,

            // SttStep, which puts the sections back together
            "CHUNK_4_RESPONSE_STORAGE" to WarningGroup.SECTION_LOST_STORAGE,
            "CHUNK_4_RAW_RESPONSE_TOO_LARGE" to WarningGroup.SECTION_LOST_STORAGE,
            "CHUNK_4_RAW_RESPONSE_AGGREGATE_TOO_LARGE" to WarningGroup.SECTION_LOST_STORAGE,
            "CHUNK_4_CANONICAL_TRANSCRIPT_AGGREGATE_TOO_LARGE" to WarningGroup.SECTION_LOST_STORAGE,
            "CHUNK_4_REMOTE_NOT_COMPLETE" to WarningGroup.SECTION_LOST_PROVIDER,
            "MISSING_CHUNKS:2,3" to WarningGroup.COVERAGE,
            "AUDIO_INTERVAL_GAP_OR_OVERLAP" to WarningGroup.SECTION_ALIGNMENT,
            "MULTIPLE_REPORTED_MODELS" to WarningGroup.PROVIDER_MODEL,

            // The list's own note about itself, added by `Warnings` rather than by a parser.
            Warnings.TRUNCATED to WarningGroup.MORE_NOTES,
        )

        // Every family has to stand here, or a name could be changed on both sides of the mapping at once
        // and never be held against a parser again.
        assertEquals(
            TranscriptWarnings.FAMILIES.keys,
            written.map { TranscriptWarnings.family(it.first) }.toSet(),
        )
        // The comparison above is between sets, so a name listed twice hides inside it — and is caught only
        // because the list happens to be exactly as long as the mapping. Said outright, it stays caught
        // when a further example is added to the list one day.
        assertEquals(TranscriptWarnings.FAMILIES.size, written.size)
        assertEquals(written.size, written.map { TranscriptWarnings.family(it.first) }.toSet().size)
        // A group added without a code that reaches it would be a sentence nobody can ever see.
        assertEquals(WarningGroup.entries.toSet(), written.map { it.second }.toSet())
        for ((code, group) in written) {
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

    @Test fun everyFamilyThisProgramNamesIsRecognisedInEveryShapeItIsRecordedIn() {
        // One example per group used to stand in for all of them, which left most of these names covered by
        // nothing. Two things this does catch: a name that the family reduction shortens before the lookup
        // ever sees it, which could then never be matched; and a shape a parser writes that the mapping has
        // no answer for. One thing it cannot: a name misspelt against the parser that writes it. Input and
        // expectation both come from the mapping here, so the two agree with each other whatever either of
        // them says. `everyGroupIsReachableFromACodeSomeParserActuallyWrites` is the second source.
        for ((group, families) in TranscriptWarnings.GROUPED_FAMILIES) {
            for (family in families) {
                assertEquals("$family is not its own family name", family, TranscriptWarnings.family(family))
                val shapes = listOf(
                    family,
                    "CHUNK_7_$family",
                    family + "_12",
                    "CHUNK_2_" + family + "_4_9",
                    "$family:2,3",
                )
                for (code in shapes) {
                    assertEquals(code, listOf(group), TranscriptWarnings.summarize(listOf(code)).groups)
                }
            }
        }
    }

    @Test fun everyGroupHasAFamilyAndNoFamilyBelongsToTwoGroups() {
        // The mapping used to be a `when`, where a group without a single code of its own was a line nobody
        // would miss and a name claimed twice was the compiler's business. As data it is neither, so both
        // are asked here: a group with no family can never be shown, and of two groups claiming one name the
        // second silently takes it from the first.
        assertEquals(
            WarningGroup.entries.toSet(),
            TranscriptWarnings.GROUPED_FAMILIES.map { it.first }.toSet(),
        )
        // A group listed with an empty list is present as a key and reachable through nothing, and the
        // comparison above answers only for the keys.
        for ((group, families) in TranscriptWarnings.GROUPED_FAMILIES) {
            assertTrue("$group is listed without a family of its own", families.isNotEmpty())
        }
        assertEquals(
            TranscriptWarnings.GROUPED_FAMILIES.sumOf { it.second.size },
            TranscriptWarnings.FAMILIES.size,
        )
    }

    @Test fun anEmptyListSaysNothing() {
        val summary = TranscriptWarnings.summarize(emptyList())
        assertEquals(emptyList<WarningGroup>(), summary.groups)
        assertEquals(emptyList<String>(), summary.unknown)
    }
}
