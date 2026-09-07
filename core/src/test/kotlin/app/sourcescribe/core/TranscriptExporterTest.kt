package app.sourcescribe.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptExporterTest {
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
    fun filenamesContainIdentityAndRejectWindowsTraversalInputs() {
        val document = document(
            sourceId = "youtube:abc/../CON",
            title = "title does not define identity",
        ).copy(
            artifactId = "artifact/with\\separators",
            language = "de\n..",
            provenance = Provenance(
                origin = Origin.YOUTUBE,
                requestedModel = "model/with:*?",
            ),
        )

        val fileName = TranscriptExporter.fileName(document, ExportFormat.MARKDOWN)

        assertTrue(fileName.endsWith(".md"))
        assertTrue(fileName.contains("youtube_abc"))
        assertTrue(fileName.contains("model_with"))
        assertFalse(fileName.contains('/'))
        assertFalse(fileName.contains('\\'))
        assertFalse(fileName.contains(".."))
        assertTrue(fileName.length <= 180)
        assertNotEquals("title_does_not_define_identity.md", fileName)
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
