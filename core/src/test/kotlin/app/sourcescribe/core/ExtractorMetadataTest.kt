package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorMetadataTest {
    private val source = SourceResolver.youtube("https://youtu.be/BaW_jenozKc")

    @Test fun wrongIdentityAndLiveSourceStopBeforeUse() {
        for (raw in listOf("{\"id\":\"abcdefghijk\"}", "{\"id\":\"BaW_jenozKc\",\"live_status\":\"is_live\"}")) {
            assertThrows(InvalidSource::class.java) { ExtractorMetadata.parse(raw, source) }
        }
    }

    @Test fun provenanceAndTranslationsStaySeparate() {
        val resolved = ExtractorMetadata.parse("""{"id":"BaW_jenozKc","language":"de","subtitles":{"de":[{"ext":"vtt","url":"https://www.youtube.com/api/timedtext?lang=de"}]},"automatic_captions":{"en":[{"ext":"json3","url":"https://www.youtube.com/api/timedtext?lang=de&tlang=en"}]}}""", source)
        assertNull(resolved.source.durationMs)
        assertEquals(Generation.UPLOADER_PROVIDED, resolved.captions.first().generation)
        assertEquals(Translation.AUTOMATIC, resolved.captions.last().translation)
        assertEquals(1, TrackSelection.captions(resolved, JobConfig()).size)
        assertEquals(2, TrackSelection.captions(resolved, JobConfig(allowTranslatedCaptions = true)).size)
        assertThrows(InvalidSource::class.java) { ExtractorMetadata.requireCaptionUrl("https://evil.test/api/timedtext") }
    }

    @Test fun youtubeHlsCaptionsAreAcceptedWithExactEndpointAndMarkedAssembly() {
        val url = "https://manifest.googlevideo.com/api/manifest/hls_timedtext_playlist/opaque-fixture"
        val resolved = ExtractorMetadata.parse("""{"id":"BaW_jenozKc","subtitles":{"en":[{"ext":"vtt","url":"$url"}]}}""", source)
        assertEquals(1, resolved.captions.size)
        org.junit.Assert.assertTrue(resolved.captions.single().evidence.contains("hls-vtt-assembled"))
        for (bad in listOf(url.replace("manifest.googlevideo.com", "evil.test"), url.replace("hls_timedtext_playlist", "video"), "$url#fragment", url.replace("https:", "http:"))) {
            assertThrows(InvalidSource::class.java) { ExtractorMetadata.requireCaptionUrl(bad) }
        }
    }

    @Test fun directCaptionFormatWinsOverEarlierHlsVariantLikeYtDlp() {
        val resolved = ExtractorMetadata.parse("""{"id":"BaW_jenozKc","subtitles":{"en":[{"ext":"vtt","url":"https://manifest.googlevideo.com/api/manifest/hls_timedtext_playlist/fixture"},{"ext":"vtt","url":"https://www.youtube.com/api/timedtext?lang=en"}]}}""", source)
        assertEquals("https://www.youtube.com/api/timedtext?lang=en", resolved.captionUrls.values.single())
    }

    @Test fun subtitleLanguageCannotAddRegexSelectionsOrPaths() {
        for (language in listOf("en,.*", "../en", "en/../../a", "en$")) {
            val resolved = ExtractorMetadata.parse("""{"id":"BaW_jenozKc","subtitles":{"$language":[{"ext":"vtt","url":"https://www.youtube.com/api/timedtext?lang=en"}]}}""", source)
            assertEquals(0, resolved.captions.size)
        }
    }

    @Test fun audioFormatsAreReadWithTheirTechnicalFactsAndRoles() {
        val raw = """{"id":"BaW_jenozKc","formats":[
            {"format_id":"137","vcodec":"avc1.640028","acodec":"none","ext":"mp4"},
            {"format_id":"18","vcodec":"avc1.42001E","acodec":"mp4a.40.2","ext":"mp4"},
            {"format_id":"251","vcodec":"none","acodec":"opus","ext":"webm","abr":105.2,"filesize":15728640,
             "asr":48000,"audio_channels":2,"language":"en","language_preference":10},
            {"format_id":"251-drc","vcodec":"none","acodec":"opus","ext":"webm","tbr":105.2,
             "filesize_approx":15728000,"language":"en","language_preference":10},
            {"format_id":"140","vcodec":"none","acodec":"mp4a.40.2","ext":"m4a","abr":129.4,"filesize":0,
             "language":"de","language_preference":5},
            {"format_id":"233-desc","vcodec":"none","acodec":"mp4a.40.2","ext":"m4a","abr":48,
             "language":"en","language_preference":-10}
        ]}"""
        val audio = ExtractorMetadata.parse(raw, source).audio.associateBy { it.id }
        assertEquals(setOf("251", "251-drc", "140", "233-desc"), audio.keys)

        val opus = audio.getValue("251")
        assertEquals("opus", opus.codec)
        assertEquals("webm", opus.container)
        assertEquals(105, opus.bitrateKbps)
        assertEquals(15_728_640L, opus.bytes)
        assertEquals(false, opus.bytesEstimated)
        assertEquals(48_000, opus.sampleRateHz)
        assertEquals(2, opus.channels)
        assertEquals(true, opus.isOriginal)
        assertEquals(false, opus.audioDescription)
        assertEquals(false, opus.dynamicRangeCompressed)
        assertEquals("Opus", AudioTracks.describe(listOf(opus), 1_120_000).single().codecLabel)

        val compressed = audio.getValue("251-drc")
        assertEquals(true, compressed.dynamicRangeCompressed)
        assertEquals(105, compressed.bitrateKbps)
        assertEquals(15_728_000L, compressed.bytes)
        assertEquals(true, compressed.bytesEstimated)

        // A reported size of zero is not a size, and language_preference 5 marks a dubbed track, not the original.
        val dubbed = audio.getValue("140")
        assertNull(dubbed.bytes)
        assertEquals(false, dubbed.isOriginal)
        assertNull(dubbed.sampleRateHz)
        assertNull(dubbed.channels)

        val narration = audio.getValue("233-desc")
        assertEquals(true, narration.audioDescription)
        assertEquals(false, narration.isOriginal)

        // Provenance names the fields this entry carried and stays silent about the ones it did not.
        assertTrue(narration.evidence.contains("language_preference"))
        assertFalse(narration.evidence.contains("filesize"))
        assertTrue(opus.evidence.contains("audio_channels"))
        assertFalse(opus.evidence.contains("tbr"))

        assertEquals("251", TrackSelection.audio(ExtractorMetadata.parse(raw, source), null)?.id)
    }

    @Test fun provenanceDoesNotNameAFieldThatWasWrittenAsNull() {
        // yt-dlp writes a key it has no answer for as JSON null. That is not a field the entry carried,
        // and an evidence line that named it would overstate what backed the record.
        val raw = """{"id":"BaW_jenozKc","formats":[{"format_id":"140","vcodec":"none","acodec":"mp4a.40.2",
            "ext":"m4a","abr":null,"asr":null,"filesize":null,"audio_channels":2}]}"""
        val track = ExtractorMetadata.parse(raw, source).audio.single()
        assertNull(track.bitrateKbps)
        assertNull(track.sampleRateHz)
        assertNull(track.bytes)
        assertEquals("yt-dlp:formats.acodec,vcodec,ext,audio_channels", track.evidence)
    }

    @Test fun absentLanguagePreferenceFallsBackOnlyToTheParenthesisedNote() {
        fun note(value: String) = ExtractorMetadata.parse(
            """{"id":"BaW_jenozKc","formats":[{"format_id":"140","vcodec":"none","acodec":"mp4a.40.2","format_note":"$value"}]}""",
            source,
        ).audio.single().isOriginal
        assertEquals(true, note("English (original)"))
        assertNull(note("not original, re-encoded"))
        assertNull(note("low"))
    }

    @Test fun ambiguousAudioRequiresChoice() {
        val tracks = listOf(AudioTrack("140", "BaW_jenozKc", "de", null, null, "fixture"), AudioTrack("140-1", "BaW_jenozKc", "en", null, null, "fixture"))
        val resolved = ResolvedSource(source, emptyList(), tracks, emptyMap())
        assertNull(TrackSelection.audio(resolved, null))
        assertEquals("140-1", TrackSelection.audio(resolved, "140-1")?.id)
    }
}
