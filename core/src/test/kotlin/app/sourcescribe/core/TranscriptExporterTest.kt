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
    fun generatedFilenamesLeadWithTheTitleAndStillCarryIdentity() {
        val document = document(
            sourceId = "youtube:abc/../CON",
            title = "title leads the name",
        ).copy(
            artifactId = "artifact/with\\separators",
            language = "de\n..",
            provenance = Provenance(
                origin = Origin.YOUTUBE,
                requestedModel = "model/with:*?",
            ),
        )

        val fileName = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN)

        assertTrue(fileName, fileName.endsWith(".md"))
        assertTrue(fileName, fileName.startsWith("title_leads_the_name-"))
        assertTrue(fileName, fileName.contains("youtube_abc"))
        assertTrue(fileName, fileName.contains("1970-01-01"))
        assertFalse(fileName, fileName.contains('/'))
        assertFalse(fileName, fileName.contains('\\'))
        assertFalse(fileName, fileName.contains(".."))
        assertTrue(fileName, fileName.length <= 180)
        assertNotEquals("title_leads_the_name.md", fileName)
    }

    @Test
    fun theIdentityInAGeneratedNameIsADigestOfAStatedWidth() {
        // Written down because the width is a bound the name depends on, not a formatting choice: the
        // identity is what keeps two runs of one source apart once day, language and source id match.
        val first = document()
        val name = TranscriptExporter.fileName(first, ExportFormat.MARKDOWN)
        val identity = name.removeSuffix(".md").substringAfterLast('-')
        assertEquals(name, TranscriptExporter.SHORT_ID_BYTES * 2, identity.length)
        assertTrue(name, identity.all { it in "0123456789abcdef" })

        // A digest and not a prefix: two ids that share everything but their last character still differ.
        assertNotEquals(
            TranscriptExporter.fileName(first.copy(artifactId = "artifact-1-a"), ExportFormat.MARKDOWN),
            TranscriptExporter.fileName(first.copy(artifactId = "artifact-1-b"), ExportFormat.MARKDOWN),
        )

        // The discriminator that separates two exports of one document is hashed to the same width.
        val discriminated = TranscriptExporter.fileName(
            first,
            ExportFormat.MARKDOWN,
            override = "Interview",
            discriminator = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
        )
        assertEquals(
            discriminated,
            TranscriptExporter.SHORT_ID_BYTES * 2,
            discriminated.removeSuffix(".md").substringAfterLast('-').length,
        )
    }

    @Test
    fun twoArtifactsOfTheSameSourceNeverShareAGeneratedName() {
        val first = document()
        val second = first.copy(artifactId = "artifact-2")

        assertNotEquals(
            TranscriptExporter.fileName(first, ExportFormat.MARKDOWN),
            TranscriptExporter.fileName(second, ExportFormat.MARKDOWN),
        )
    }

    @Test
    fun aVeryLongTitleYieldsSpaceInsteadOfTruncatingIdentityOrDiscriminator() {
        val document = document(title = "T".repeat(400))

        val fileName = TranscriptExporter.fileName(
            document,
            ExportFormat.MARKDOWN,
            discriminator = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
        )

        assertTrue(fileName, fileName.length <= 180)
        assertTrue(fileName, fileName.contains("youtube_BaW_jenozKc"))
        assertTrue(fileName, fileName.endsWith(".md"))
        assertTrue(fileName, fileName.startsWith("T".repeat(40)))
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
    fun aRepeatedExportOfAChosenNameGetsItsOwnFile() {
        val document = document()
        val first = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "Interview")
        val second = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "Interview",
            discriminator = "export-2")
        val third = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN, override = "Interview",
            discriminator = "export-3")

        assertEquals("Interview.md", first)
        assertNotEquals(first, second)
        assertNotEquals(second, third)
        assertTrue(second, second.startsWith("Interview-"))
        assertTrue(second, second.endsWith(".md"))
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
        assertTrue(cyrillic, cyrillic.contains("youtube_BaW_jenozKc"))
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
    fun noPartOfANameMeasuredInCharactersPushesTheIdentityOutOfIt() {
        // The budget left for the readable head was a byte limit minus the *character* count of the part that
        // must survive, and a language or a source id made of three-byte characters makes those two differ by
        // up to 52. This passed before that arithmetic was corrected, which is the finding: the head is
        // capped at 40 bytes of its own and an overridden name at 160, so the caps kept every reachable name
        // inside the limit while the arithmetic did not. The grid below is the widest set of parts the name
        // builder accepts, so it pins the property to the arithmetic — and raising the title's share of the
        // name stays the one-line change it looks like.
        val plain = "youtube:BaW_jenozKc"
        for (title in listOf("T".repeat(300), "あ".repeat(300))) {
            for (sourceId in listOf(plain, "あ".repeat(40), "録音".repeat(30) + ".m4a")) {
                for (language in listOf(null, "de")) {
                    for (originalLanguage in listOf(null, "de", "あ".repeat(40))) {
                        val base = document(title = title, sourceId = sourceId)
                        val wide = base.copy(
                            language = language,
                            source = base.source.copy(originalLanguage = originalLanguage),
                        )
                        for (discriminator in listOf(null, "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")) {
                            for (override in listOf(null, "あ".repeat(200), "O".repeat(200))) {
                                for ((format, rawExtension) in listOf(
                                    ExportFormat.MARKDOWN to null,
                                    ExportFormat.RAW to "字幕テキスト",
                                    ExportFormat.RAW to "json3",
                                )) {
                                    val name = TranscriptExporter.fileName(
                                        wide, format, override, discriminator, rawExtension,
                                    )
                                    assertTrue(name, name.toByteArray(Charsets.UTF_8).size <= 180)
                                    // A name the reader chose ends where the reader ended it, unless a
                                    // discriminator was appended; every generated name ends in the digest.
                                    if (override != null && discriminator == null) continue
                                    val identity = name.substringBeforeLast('.').substringAfterLast('-')
                                    assertEquals(name, TranscriptExporter.SHORT_ID_BYTES * 2, identity.length)
                                    assertTrue(name, identity.all { it in "0123456789abcdef" })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun document(
        title: String = "A title",
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
