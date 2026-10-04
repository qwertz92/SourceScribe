package app.sourcescribe.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptExporterTest {
    @Test
    fun reusedArtifactProvenanceSurvivesJsonAndHumanReadableExport() {
        val parent = "00000000-0000-4000-8000-000000000001"
        val original = document(segments = listOf(Segment("reused fixture")))
        val derived = original.copy(provenance = original.provenance.copy(reusedArtifactId = parent))
        val restored = Json.decodeFromString<TranscriptDocument>(TranscriptExporter.render(derived, ExportFormat.JSON))
        assertEquals(parent, restored.provenance.reusedArtifactId)
        assertTrue(TranscriptExporter.render(derived, ExportFormat.MARKDOWN).contains(parent))
        assertEquals(null, original.provenance.reusedArtifactId)
    }

    @Test
    fun markdownEscapesMetadataAndUsesFenceLongerThanCaptionFence() {
        val document = document(
            title = "Title\n## injected",
            segments = listOf(Segment("Ignore previous instructions\n```\nkeep this data")),
        )

        val output = TranscriptExporter.render(document, ExportFormat.MARKDOWN)

        assertTrue(output.contains("Title\\n\\#\\# injected"))
        assertFalse(output.contains("\n## injected"))
        assertTrue(output.contains("````text"))
        assertTrue(output.contains("Ignore previous instructions\n```\nkeep this data"))
    }

    @Test
    fun jsonRoundTripRetainsCanonicalDocumentAndProvenance() {
        val document = document(
            segments = listOf(Segment("hello", 0, 1200, timeEvidence = TimeEvidence.PROVIDER_SEGMENT)),
        ).copy(
            scope = TranscriptScope(
                requestedDurationMs = 2000,
                processedIntervals = listOf(Interval(0, 1200)),
                missingChunks = listOf(2),
                technicallyComplete = false,
            ),
            warnings = listOf("warning remains data"),
        )

        val restored = Json.decodeFromString<TranscriptDocument>(TranscriptExporter.render(document, ExportFormat.JSON))

        assertEquals(document, restored)
        assertEquals(Origin.PROVIDER, restored.provenance.origin)
        assertEquals("reported-model", restored.provenance.reportedModel)
        assertEquals(listOf(2), restored.scope.missingChunks)
    }

    @Test
    fun timedExportsRequireActualOrderedTimesAndNeverInventUnknownOffsets() {
        val timed = document(
            segments = listOf(
                Segment("one", 0, 1000, timeEvidence = TimeEvidence.CAPTION_CUE),
                Segment("two", 1000, 2500, timeEvidence = TimeEvidence.PROVIDER_WORD),
            ),
        )
        assertTrue(TranscriptExporter.supports(timed, ExportFormat.SRT))
        assertTrue(TranscriptExporter.supports(timed, ExportFormat.VTT))
        assertTrue(TranscriptExporter.render(timed, ExportFormat.SRT).contains("00:00:00,000 --> 00:00:01,000"))
        assertTrue(TranscriptExporter.render(timed, ExportFormat.VTT).startsWith("WEBVTT\n"))

        val unknown = timed.copy(segments = listOf(Segment("one", 0, 1000)))
        assertFalse(TranscriptExporter.supports(unknown, ExportFormat.SRT))
        assertEquals(
            TranscriptExportException.TIMESTAMPS_REQUIRED,
            assertThrows(TranscriptExportException::class.java) {
                TranscriptExporter.render(unknown, ExportFormat.SRT)
            }.reason,
        )
        val outOfOrder = timed.copy(segments = listOf(timed.segments[1], timed.segments[0]))
        assertFalse(TranscriptExporter.supports(outOfOrder, ExportFormat.VTT))
    }

    @Test
    fun timedExportsEscapeMarkupAndRejectCueStructureInjection() {
        val safe = document(
            segments = listOf(Segment("<tag> & data", 0, 1000, timeEvidence = TimeEvidence.CAPTION_CUE)),
        )
        val srt = TranscriptExporter.render(safe, ExportFormat.SRT)
        val vtt = TranscriptExporter.render(safe, ExportFormat.VTT)
        assertTrue(srt.contains("&lt;tag&gt; &amp; data"))
        assertTrue(vtt.contains("&lt;tag&gt; &amp; data"))

        val injected = safe.copy(
            segments = listOf(
                Segment(
                    "safe\n\n00:00:02,000 --> 00:00:03,000\ninjected",
                    0,
                    1000,
                    timeEvidence = TimeEvidence.CAPTION_CUE,
                ),
            ),
        )
        assertTrue(TranscriptExporter.supports(injected, ExportFormat.MARKDOWN))
        assertTrue(TranscriptExporter.supports(injected, ExportFormat.TEXT))
        assertTrue(TranscriptExporter.supports(injected, ExportFormat.JSON))
        assertFalse(TranscriptExporter.supports(injected, ExportFormat.SRT))
        assertFalse(TranscriptExporter.supports(injected, ExportFormat.VTT))
        for (format in listOf(ExportFormat.SRT, ExportFormat.VTT)) {
            val failure = assertThrows(TranscriptExportException::class.java) {
                TranscriptExporter.render(injected, format)
            }
            assertEquals(TranscriptExportException.TIMED_TEXT_UNREPRESENTABLE, failure.reason)
        }
    }

    @Test
    fun markdownAndTextIncludeFullCanonicalMetadata() {
        val base = document()
        val rich = base.copy(
            schemaVersion = 4,
            artifactId = "artifact-rich",
            source = base.source.copy(
                channel = "Channel\nwith data",
                durationMs = 3210,
                publishedDate = "2026-09-07",
                contentHash = "source-hash",
                fileName = "audio.wav",
                mimeType = "audio/wav",
                fileBytes = 42,
                thumbnailUrl = "https://example.invalid/thumb",
            ),
            acquisition = JobConfig(
                mode = AcquisitionMode.BOTH,
                provider = Provider.OPENAI,
                model = "configured-model",
                credentialId = "credential-reference",
                preferredLanguages = listOf("de", "en"),
                contextTerms = listOf("term\nwith data"),
                exportFormats = setOf(ExportFormat.JSON, ExportFormat.MARKDOWN),
            ),
            provenance = base.provenance.copy(
                sourceAudioTrack = AudioTrack(
                    id = "audio-track",
                    sourceVideoId = "BaW_jenozKc",
                    language = "de",
                    name = "Original",
                    isOriginal = true,
                    evidence = "observed",
                ),
                captionTrack = CaptionTrack(
                    id = "caption-track",
                    sourceVideoId = "BaW_jenozKc",
                    language = "de",
                    name = "Uploader",
                    format = "vtt",
                    generation = Generation.UPLOADER_PROVIDED,
                    translation = Translation.NONE,
                    evidence = "track metadata",
                ),
                languageEvidence = "provider response",
                engineVersions = mapOf("normalizer" to "2", "parser" to "1"),
                reportedLanguages = listOf("de", "en"),
            ),
            scope = TranscriptScope(
                requestedDurationMs = 5000,
                processedIntervals = listOf(Interval(0, 1000), Interval(2000, 3000)),
                missingChunks = listOf(3, 5),
                technicallyComplete = false,
            ),
            createdAt = 1_234,
            rawHash = "raw-hash",
            normalizationVersion = "2",
            words = listOf(Segment("word", 0, 100, timeEvidence = TimeEvidence.PROVIDER_WORD)),
        )

        val markdown = TranscriptExporter.render(rich, ExportFormat.MARKDOWN)
        val text = TranscriptExporter.render(rich, ExportFormat.TEXT)
        for (output in listOf(markdown, text)) {
            assertTrue(output.contains("artifact-rich"))
            assertTrue(output.contains("credential-reference"))
            assertTrue(output.contains("audio-track"))
            assertTrue(output.contains("caption-track"))
            assertTrue(output.contains("normalizer=2"))
            assertTrue(output.contains("de, en"))
            assertTrue(output.contains("0..1000"))
            assertTrue(output.contains("2000..3000"))
            assertTrue(output.contains("missingChunks"))
            assertTrue(output.contains("1970-01-01T00:00:01.234Z"))
            assertTrue(output.contains("raw-hash"))
        }
        assertTrue(markdown.contains("Channel\\nwith data"))
        assertTrue(text.contains("Channel\\nwith data"))
    }

    @Test
    fun theProvenanceRecordSaysWhenASourceNamedALanguageItCouldNotCarry() {
        fun exported(track: AudioTrack) = TranscriptExporter.render(
            document().let { it.copy(provenance = it.provenance.copy(sourceAudioTrack = track)) },
            ExportFormat.MARKDOWN,
        )

        val track = AudioTrack("audio-track", "BaW_jenozKc", null, null, null, "observed")
        // Nothing was said, so nothing is claimed.
        assertTrue(exported(track).contains("language=unknown"))
        // Something was said that the record could not carry. Writing `unknown` here would state that the
        // source named no language, which is not what happened, and this file is the provenance record.
        val refused = exported(track.copy(languageRefused = true))
        assertTrue(refused.contains("language=stated-but-unusable"))
        assertFalse(refused.contains("language=unknown"))
    }

    @Test
    fun generatedFilenameUsesSanitizedChannelAndTitleWithoutIdentityMetadata() {
        val document = document(
            sourceId = "youtube:secret-video-id",
            title = "../A:title",
        ).copy(
            artifactId = "private-artifact-id",
            language = "de",
            createdAt = 1_782_000_000_000L,
            source = document(title = "../A:title").source.copy(channel = "My channel/one"),
        )

        val fileName = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN)

        assertEquals("My_channel_one - _A_title.md", fileName)
        assertFalse(fileName, fileName.contains("secret-video-id"))
        assertFalse(fileName, fileName.contains("private-artifact-id"))
        assertFalse(fileName, fileName.contains("de"))
        assertFalse(fileName, fileName.contains("2026"))
        assertFalse(fileName, fileName.contains('/'))
        assertFalse(fileName, fileName.contains('\\'))
    }

    @Test
    fun generatedFilenameOmitsMissingChannelAndFallsBackToImportNameThenTranscript() {
        val imported = document(title = null).copy(
            source = document(title = null).source.copy(fileName = "lecture.wav"),
        )
        val unnamed = document(title = null).copy(
            source = document(title = null).source.copy(fileName = null),
        )

        assertEquals("lecture.wav.txt", TranscriptExporter.fileName(imported, ExportFormat.TEXT))
        assertEquals("transcript.md", TranscriptExporter.fileName(unnamed, ExportFormat.MARKDOWN))
    }

    @Test
    fun readableTitleUsesAvailableBudgetRatherThanTheOldFortyBytePartLimit() {
        val title = "Episode ".repeat(10).trim()
        assertEquals("${title.replace(' ', '_')}.txt", TranscriptExporter.fileName(document(title = title), ExportFormat.TEXT))
    }

    @Test
    fun aVeryLongTitleStaysWithinTheUtf8FilenameBudget() {
        val document = document(title = "T".repeat(400))

        val fileName = TranscriptExporter.fileName(
            document,
            ExportFormat.MARKDOWN,
        )

        assertTrue(fileName, fileName.toByteArray(Charsets.UTF_8).size <= 180)
        assertTrue(fileName, fileName.endsWith(".md"))
        assertTrue(fileName, fileName.startsWith("T".repeat(160)))
    }

    @Test
    fun aChosenNameIsUsedAsChosenAndStillCannotEscapeItsDirectory() {
        val document = document()

        assertEquals(
            "Folge_12_Interview.md",
            TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "Folge 12 Interview"),
        )
        val hostile = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "../../etc/passwd")
        assertFalse(hostile, hostile.contains('/'))
        assertFalse(hostile, hostile.contains(".."))
        assertNull(TranscriptExporter.customStem("   "))
        assertNull(TranscriptExporter.customStem("..."))
        // A blank override falls back to the generated name instead of producing an extension-only file.
        assertTrue(TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "   ").length > 4)
    }

    @Test
    fun collisionSuffixFollowsTheChosenNameAndKeepsItsExtension() {
        val document = document()
        val first = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "Interview")
        val second = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "Interview", collisionIndex = 1)
        val third = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "Interview", collisionIndex = 2)

        assertEquals("Interview.md", first)
        assertEquals("Interview_1.md", second)
        assertEquals("Interview_2.md", third)
    }

    @Test
    fun aPartMadeOnlyOfCharactersANameCannotCarryFallsBackInsteadOfBecomingAnUnderscore() {
        // Those characters are each replaced by an underscore and the runs are then folded into one, so the
        // result is never empty and the emptiness test did not catch it. A retained provider file whose
        // extension was `???` therefore ended in `_` and named no format at all.
        val raw = TranscriptExporter.fileName(document(), ExportFormat.RAW, rawExtension = "???")
        assertTrue(raw, raw.endsWith(".raw"))

        // A name the reader chose that says nothing usable gives way to the generated one rather than
        // becoming a file called `_`.
        val chosen = TranscriptExporter.fileName(document(), ExportFormat.MARKDOWN, override = "///")
        assertFalse(chosen, chosen.startsWith("_."))
        assertTrue(chosen, chosen.endsWith(".md"))
    }

    @Test
    fun windowsDeviceNamesAreNeutralisedEvenWhenSomethingFollowsTheDot() {
        val document = document()
        for (name in listOf("AUX", "aux.notes", "CON.important", "com1.txt", "LPT9.a.b", "nul",
            "CONIN$", "conout$", "aux.", "aux ")) {
            val fileName = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = name)
            assertTrue(fileName, fileName.startsWith("_"))
        }
        // A name that merely begins with those letters is a normal name and stays untouched.
        for (name in listOf("Conference", "Auxiliary talk", "Nullhypothese", "COM10")) {
            val fileName = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = name)
            assertFalse(fileName, fileName.startsWith("_"))
        }
    }

    @Test
    fun nameBudgetsCountBytesAndNeverCutThroughACharacter() {
        val document = document()
        val japanese = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "\u8b1b\u6f14".repeat(200))
        assertTrue(japanese, japanese.toByteArray(Charsets.UTF_8).size <= 180)
        assertFalse(japanese, japanese.contains('\uFFFD'))

        val emoji = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "A" + "\uD83D\uDE00".repeat(200))
        assertTrue(emoji, emoji.toByteArray(Charsets.UTF_8).size <= 180)
        for (index in emoji.indices) {
            if (emoji[index].isHighSurrogate()) {
                assertTrue(emoji, index + 1 < emoji.length && emoji[index + 1].isLowSurrogate())
            }
            assertFalse(emoji, emoji[index].isLowSurrogate() && (index == 0 || !emoji[index - 1].isHighSurrogate()))
        }

        val cyrillic = TranscriptExporter.fileName(document(title = "\u041f\u0440\u0438\u0432\u0435\u0442".repeat(80)), ExportFormat.MARKDOWN)
        assertTrue(cyrillic, cyrillic.toByteArray(Charsets.UTF_8).size <= 180)
        assertTrue(cyrillic, cyrillic.startsWith("\u041f\u0440\u0438\u0432\u0435\u0442".repeat(3)))
        assertFalse(cyrillic, cyrillic.contains("youtube_BaW_jenozKc"))
    }

    @Test
    fun rawIsSeparateAndNeverRenderedFromCanonicalDocument() {
        val document = document()

        assertFalse(TranscriptExporter.supports(document, ExportFormat.RAW))
        assertEquals(
            TranscriptExportException.RAW_REQUIRES_SEPARATE_FILE,
            assertThrows(TranscriptExportException::class.java) {
                TranscriptExporter.render(document, ExportFormat.RAW)
            }.reason,
        )
    }

    @Test
    fun filenameAndCollisionSuffixBudgetsCountUtf8Bytes() {
        for (title in listOf("T".repeat(300), "あ".repeat(300))) {
            for (channel in listOf(null, "錄音".repeat(30))) {
                val base = document(title = title).copy(source = document(title = title).source.copy(channel = channel))
                for (collisionIndex in listOf(0, 1, 99)) {
                    for (override in listOf(null, "あ".repeat(200), "O".repeat(200))) {
                        for ((format, rawExtension, expectedExtension) in listOf(
                            Triple(ExportFormat.MARKDOWN, null, "md"),
                            Triple(ExportFormat.RAW, "字幕テキスト", "字幕テキ"),
                            Triple(ExportFormat.RAW, "json3", "json3"),
                        )) {
                            val name = TranscriptExporter.fileName(
                                base, format, override, collisionIndex, rawExtension,
                            )
                            assertTrue(name, name.toByteArray(Charsets.UTF_8).size <= 180)
                            assertFalse(name, name.contains('\uFFFD'))
                            if (collisionIndex > 0) assertTrue(name, name.substringBeforeLast('.').endsWith("_$collisionIndex"))
                            assertTrue(name, name.endsWith(".$expectedExtension"))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aTimedExportOfAPartialResultSaysSoAndMarksWhatWasNotTranscribed() {
        val partial = document(
            segments = listOf(
                Segment("one", 0, 1000, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
                Segment("three", 2000, 2500, speaker = "A", timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
            ),
        ).copy(
            scope = TranscriptScope(
                requestedDurationMs = 4000,
                // Recorded out of order on purpose: the stretches between them are found either way.
                processedIntervals = listOf(Interval(2000, 3000), Interval(0, 1000)),
                missingChunks = listOf(1, 3),
                technicallyComplete = false,
            ),
        )
        assertEquals(
            "1\n00:00:00,000 --> 00:00:01,000\n$NOTICE\n\n" +
                "2\n00:00:00,000 --> 00:00:01,000\none\n\n" +
                "3\n00:00:01,000 --> 00:00:02,000\n$GAP\n\n" +
                "4\n00:00:02,000 --> 00:00:02,500\nA: three\n\n" +
                "5\n00:00:03,000 --> 00:00:04,000\n$GAP\n\n",
            TranscriptExporter.render(partial, ExportFormat.SRT),
        )
        assertEquals(
            "WEBVTT\n\n" +
                "NOTE\nLimitations: technicallyComplete=false; requestedDurationMs=4000; " +
                "processedIntervals=[2000..3000, 0..1000]; missingChunks=[1, 3]\n\n" +
                "00:00:00.000 --> 00:00:01.000\n$NOTICE\n\n" +
                "00:00:00.000 --> 00:00:01.000\none\n\n" +
                "00:00:01.000 --> 00:00:02.000\n$GAP\n\n" +
                "00:00:02.000 --> 00:00:02.500\nA: three\n\n" +
                "00:00:03.000 --> 00:00:04.000\n$GAP\n\n",
            TranscriptExporter.render(partial, ExportFormat.VTT),
        )
    }

    @Test
    fun aStretchNotTranscribedIsMarkedOnlyWhereNoTranscribedTextRuns() {
        // Chunk 0 is the window 0..1000 and its provider timed the last words to 1150; chunk 1, 1000..2000, is
        // missing. Round 17 found the notice starting at 1000, so a player showed "not transcribed" beside "one".
        val overhang = document(
            segments = listOf(
                Segment("one", 0, 1150, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
                Segment("three", 2000, 3000, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
            ),
        ).copy(
            scope = TranscriptScope(
                requestedDurationMs = 3000,
                processedIntervals = listOf(Interval(0, 1000), Interval(2000, 3000)),
                missingChunks = listOf(1),
                technicallyComplete = false,
            ),
        )
        assertEquals(
            "1\n00:00:00,000 --> 00:00:01,150\n$NOTICE\n\n" +
                "2\n00:00:00,000 --> 00:00:01,150\none\n\n" +
                "3\n00:00:01,150 --> 00:00:02,000\n$GAP\n\n" +
                "4\n00:00:02,000 --> 00:00:03,000\nthree\n\n",
            TranscriptExporter.render(overhang, ExportFormat.SRT),
        )
        // Text running through the whole stretch leaves no notice there, only the one at the start.
        val covered = overhang.copy(segments = listOf(
            Segment("one", 0, 2100, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
            Segment("three", 2000, 3000, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
        ))
        assertEquals(
            "1\n00:00:00,000 --> 00:00:02,100\n$NOTICE\n\n" +
                "2\n00:00:00,000 --> 00:00:02,100\none\n\n" +
                "3\n00:00:02,000 --> 00:00:03,000\nthree\n\n",
            TranscriptExporter.render(covered, ExportFormat.SRT),
        )
        // And a cue inside a stretch splits the notice around it instead of being overlaid by it.
        val inside = overhang.copy(segments = listOf(
            Segment("one", 0, 1000, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
            Segment("stray", 1400, 1600, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
            Segment("three", 2000, 3000, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
        ))
        assertEquals(
            "1\n00:00:00,000 --> 00:00:01,000\n$NOTICE\n\n" +
                "2\n00:00:00,000 --> 00:00:01,000\none\n\n" +
                "3\n00:00:01,000 --> 00:00:01,400\n$GAP\n\n" +
                "4\n00:00:01,400 --> 00:00:01,600\nstray\n\n" +
                "5\n00:00:01,600 --> 00:00:02,000\n$GAP\n\n" +
                "6\n00:00:02,000 --> 00:00:03,000\nthree\n\n",
            TranscriptExporter.render(inside, ExportFormat.SRT),
        )
    }

    @Test
    fun aTimedExportWhoseCompletenessWasNeverConfirmedIsNotPresentedAsWhole() {
        val unconfirmed = document(segments = listOf(Segment("one", 0, 1000, timeEvidence = TimeEvidence.CAPTION_CUE)))
        assertNull(unconfirmed.scope.technicallyComplete)
        assertEquals(
            "1\n00:00:00,000 --> 00:00:01,000\n$NOTICE\n\n2\n00:00:00,000 --> 00:00:01,000\none\n\n",
            TranscriptExporter.render(unconfirmed, ExportFormat.SRT),
        )
        assertEquals(
            "WEBVTT\n\nNOTE\nLimitations: technicallyComplete=unknown; requestedDurationMs=unknown; " +
                "processedIntervals=none; missingChunks=[]\n\n" +
                "00:00:00.000 --> 00:00:01.000\n$NOTICE\n\n00:00:00.000 --> 00:00:01.000\none\n\n",
            TranscriptExporter.render(unconfirmed, ExportFormat.VTT),
        )
        // A missing chunk leaves the result partial even where the scope calls it technically complete.
        val missing = unconfirmed.copy(scope = TranscriptScope(technicallyComplete = true, missingChunks = listOf(0)))
        assertTrue(TranscriptExporter.render(missing, ExportFormat.SRT).startsWith("1\n00:00:00,000 --> 00:00:01,000\n$NOTICE\n\n"))
    }

    @Test
    fun aConfirmedWholeTimedExportCarriesNoNoticeAndItsWarningsOnlyAsAComment() {
        val whole = document(
            segments = listOf(
                Segment("one", 0, 1000, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
                Segment("two", 1000, 2500, timeEvidence = TimeEvidence.PROVIDER_SEGMENT),
            ),
        ).copy(scope = TranscriptScope(2500, listOf(Interval(0, 2500)), emptyList(), true))
        val srt = "1\n00:00:00,000 --> 00:00:01,000\none\n\n2\n00:00:01,000 --> 00:00:02,500\ntwo\n\n"
        val cues = "00:00:00.000 --> 00:00:01.000\none\n\n00:00:01.000 --> 00:00:02.500\ntwo\n\n"
        assertEquals(srt, TranscriptExporter.render(whole, ExportFormat.SRT))
        assertEquals("WEBVTT\n\n$cues", TranscriptExporter.render(whole, ExportFormat.VTT))

        // SRT has no comments. In VTT a warning cannot break out of one: no blank line, no timing line.
        val warned = whole.copy(warnings = listOf("LANGUAGE_UNCERTAIN\n\n00:00:05.000 --> 00:00:06.000 <b>"))
        assertEquals(srt, TranscriptExporter.render(warned, ExportFormat.SRT))
        assertEquals(
            "WEBVTT\n\nNOTE\nLimitations: technicallyComplete=true; requestedDurationMs=2500; processedIntervals=[0..2500]; " +
                "missingChunks=[]; warning=LANGUAGE_UNCERTAIN  00:00:05.000 --&gt; 00:00:06.000 &lt;b&gt;\n\n$cues",
            TranscriptExporter.render(warned, ExportFormat.VTT),
        )
    }

    @Test
    fun onlyAConfirmedYesWithoutAMissingChunkCountsAsWhole() {
        assertTrue(TranscriptScope(technicallyComplete = true).confirmedComplete)
        assertFalse(TranscriptScope().confirmedComplete)
        assertFalse(TranscriptScope(technicallyComplete = false).confirmedComplete)
        assertFalse(TranscriptScope(technicallyComplete = true, missingChunks = listOf(2)).confirmedComplete)
    }

    private companion object {
        const val NOTICE = "[SourceScribe: this transcript is not confirmed complete. " +
            "Its limitations are listed in the Markdown, text and JSON exports.]"
        const val GAP = "[SourceScribe: this part of the source was not transcribed.]"
    }

    private fun document(
        title: String? = "A title",
        sourceId: String = "youtube:BaW_jenozKc",
        segments: List<Segment> = listOf(Segment("text")),
    ): TranscriptDocument = TranscriptDocument(
        artifactId = "artifact-1",
        source = Source(
            id = sourceId,
            kind = SourceKind.YOUTUBE,
            canonicalUrl = "https://www.youtube.com/watch?v=BaW_jenozKc",
            videoId = "BaW_jenozKc",
            title = title,
            originalLanguage = "de",
        ),
        acquisition = JobConfig(model = "requested-model"),
        provenance = Provenance(
            origin = Origin.PROVIDER,
            generation = Generation.UNKNOWN,
            translation = Translation.NONE,
            provider = Provider.OPENAI,
            requestedModel = "requested-model",
            reportedModel = "reported-model",
            languageEvidence = "observed",
        ),
        language = "de",
        segments = segments,
        createdAt = 1,
    )
}
