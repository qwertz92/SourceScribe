package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test fun ambiguousAudioRequiresChoice() {
        val tracks = listOf(AudioTrack("140", "BaW_jenozKc", "de", null, null, "fixture"), AudioTrack("140-1", "BaW_jenozKc", "en", null, null, "fixture"))
        val resolved = ResolvedSource(source, emptyList(), tracks, emptyMap())
        assertNull(TrackSelection.audio(resolved, null))
        assertEquals("140-1", TrackSelection.audio(resolved, "140-1")?.id)
    }
}
