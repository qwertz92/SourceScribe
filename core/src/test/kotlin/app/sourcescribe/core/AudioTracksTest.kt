package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTracksTest {
    private fun track(
        id: String,
        language: String? = "en",
        isOriginal: Boolean? = null,
        bitrateKbps: Int? = null,
        bytes: Long? = null,
        drc: Boolean = false,
        audioDescription: Boolean = false,
    ) = AudioTrack(id, "BaW_jenozKc", language, null, isOriginal, "fixture",
        bitrateKbps = bitrateKbps, bytes = bytes, dynamicRangeCompressed = drc, audioDescription = audioDescription)

    @Test fun narrationOfThePictureIsNeverChosenWithoutTheReader() {
        val description = track("140-desc", isOriginal = false, bitrateKbps = 48, audioDescription = true)
        assertNull(AudioTracks.automatic(listOf(description)))
        assertNull(AudioTracks.automatic(listOf(description, track("139-desc", bitrateKbps = 64, audioDescription = true))))
        val spoken = track("140", isOriginal = true, bitrateKbps = 128)
        assertEquals("140", AudioTracks.automatic(listOf(description, spoken))?.id)
        assertTrue(AudioTracks.describe(listOf(description, spoken), 60_000).single { it.recommended }.track.id == "140")
    }

    @Test fun severalSpokenLanguagesStayTheReadersDecision() {
        assertNull(AudioTracks.automatic(listOf(track("140", "de", bitrateKbps = 128), track("141", "en", bitrateKbps = 128))))
        // Regional variants of one language are the same content decision, so they do not need a question.
        assertEquals("140", AudioTracks.automatic(listOf(track("140", "en-US", bitrateKbps = 96), track("141", "en-GB", bitrateKbps = 128)))?.id)
        // A marked original ends the question even when other languages exist.
        assertEquals("141", AudioTracks.automatic(listOf(track("140", "de", bitrateKbps = 96), track("141", "en", isOriginal = true, bitrateKbps = 128)))?.id)
    }

    @Test fun theRecommendationAvoidsCompressionAndUnusablyLowRatesBeforeItSaves() {
        assertEquals("140", AudioTracks.automatic(listOf(track("139-drc", bitrateKbps = 48, drc = true), track("140", bitrateKbps = 256)))?.id)
        assertEquals("140", AudioTracks.automatic(listOf(track("249", bitrateKbps = 8), track("140", bitrateKbps = 256)))?.id)
        assertEquals("249", AudioTracks.automatic(listOf(track("251", bitrateKbps = 160), track("249", bitrateKbps = 64)))?.id)
        // Same rendition list, same pick: a re-resolve must not rebind a running job to another track.
        val list = listOf(track("251", bitrateKbps = 160), track("250", bitrateKbps = 96), track("249", bitrateKbps = 64))
        assertEquals(AudioTracks.automatic(list)?.id, AudioTracks.automatic(list.reversed())?.id)
    }

    @Test fun sizesAreEstimatedWithoutInventingZeroOrLosingTheRemainder() {
        assertEquals(2_000L, AudioTracks.estimateBytes(64, 250))
        assertEquals(16_000L, AudioTracks.estimateBytes(128, 1_000))
        assertEquals(23_984L, AudioTracks.estimateBytes(64, 2_998))
        val short = AudioTracks.describe(listOf(track("140", bitrateKbps = 128)), 500).single()
        assertTrue(short.bytesEstimated)
        assertTrue((short.bytes ?: 0L) > 0L)
        val unknown = AudioTracks.describe(listOf(track("140", bitrateKbps = 128)), null).single()
        assertNull(unknown.bytes)
        val exact = AudioTracks.describe(listOf(track("140", bitrateKbps = 128, bytes = 4_242L)), 60_000).single()
        assertEquals(4_242L, exact.bytes)
        assertFalse(exact.bytesEstimated)
    }

    @Test fun sizeClassesOnlySeparateWhatIsActuallyDifferent() {
        val described = AudioTracks.describe(listOf(
            track("249", bitrateKbps = 64), track("250", bitrateKbps = 96), track("251", bitrateKbps = 160),
        ), 60_000).associateBy { it.track.id }
        assertEquals(AudioSizeClass.SMALLEST, described.getValue("249").sizeClass)
        assertEquals(AudioSizeClass.MEDIUM, described.getValue("250").sizeClass)
        assertEquals(AudioSizeClass.LARGEST, described.getValue("251").sizeClass)
        for (equal in AudioTracks.describe(listOf(track("140", bitrateKbps = 128), track("141", bitrateKbps = 128)), 60_000)) {
            assertEquals(AudioSizeClass.UNKNOWN, equal.sizeClass)
        }
        assertEquals(AudioSizeClass.UNKNOWN, AudioTracks.describe(listOf(track("140")), 60_000).single().sizeClass)
        assertEquals(emptyList<AudioTrackDescription>(), AudioTracks.describe(emptyList(), 60_000))
        assertNull(AudioTracks.automatic(emptyList()))
    }
}
