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

    @Test fun aRefusedLanguageCountsAsALanguageAndNotAsSilence() {
        // `null` reaches this function for two different reasons: the source stated no language, or it
        // stated one the record could not carry. Only the second says the source told two renditions apart,
        // and only a flag beside the value can still say so once the value itself is gone.
        val refused = track("140", language = null, bitrateKbps = 96).copy(languageRefused = true)
        val alsoRefused = track("141", language = null, bitrateKbps = 128).copy(languageRefused = true)
        assertNull(AudioTracks.automatic(listOf(refused, alsoRefused)))
        assertNull(AudioTracks.automatic(listOf(refused, track("142", language = null, bitrateKbps = 128))))
        // Alone it decides nothing between languages, so it is still the recommendation.
        assertEquals("140", AudioTracks.automatic(listOf(refused))?.id)
        assertTrue(AudioTracks.describe(listOf(refused), 60_000).single().recommended)
        // And two renditions that both stated nothing are not a question, as they never were.
        assertEquals(
            "140",
            AudioTracks.automatic(listOf(track("140", language = null), track("141", language = null)))?.id,
        )
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

    @Test fun thePickerListsTheUsefulRenditionsBeforeTheDubbedCrowd() {
        // A dubbed video hands over its languages in extractor order; the recommendation was landing
        // somewhere in the middle of fifteen entries, which is what this order exists to prevent.
        val tracks = listOf(
            track("140-ru", "ru", bitrateKbps = 128),
            track("140-desc", "en", bitrateKbps = 48, audioDescription = true),
            track("140-hi", "hi", bitrateKbps = 128),
            track("251-en", "en", isOriginal = true, bitrateKbps = 160),
            track("140-de", "de", bitrateKbps = 128),
            track("249-en", "en", isOriginal = true, bitrateKbps = 64),
            track("249-drc-en", "en", isOriginal = true, bitrateKbps = 64, drc = true),
        )
        val order = AudioTracks.describe(tracks, 600_000).map { it.track.id }
        // Recommendation, then the rest of the original language plain-before-compressed, then the dubs by
        // language code, then the narration track.
        assertEquals(listOf("249-en", "251-en", "249-drc-en", "140-de", "140-hi", "140-ru", "140-desc"), order)
        assertTrue(AudioTracks.describe(tracks, 600_000).first().recommended)
        // Same list, same order: the picker must not reshuffle itself between two resolves.
        assertEquals(order, AudioTracks.describe(tracks.reversed(), 600_000).map { it.track.id })
    }

    @Test fun aMissingLanguageAndAnUnusableRateSortLastInsteadOfJumpingTheQueue() {
        // Two keys of the reading order that the dubbed-video case never reaches: a rendition the extractor
        // gave no language for, and one whose rate is too low for speech. Both belong behind the usable
        // ones, and a missing language must not be able to collide with a real language code.
        val tracks = listOf(
            track("140-none", null, bitrateKbps = 128),
            track("140-zz", "zz", bitrateKbps = 128),
            track("139-en", "en", bitrateKbps = 8),
            track("140-en", "en", bitrateKbps = 128),
            track("140-de", "de", bitrateKbps = 128),
        )
        val order = AudioTracks.describe(tracks, 600_000).map { it.track.id }
        // No track is recommended here, so the order is language code first, unusable rate and unknown
        // language last. "zz" is a real code and stays ahead of the entry that carries none.
        assertEquals(listOf("140-de", "140-en", "139-en", "140-zz", "140-none"), order)
        assertEquals(order, AudioTracks.describe(tracks.reversed(), 600_000).map { it.track.id })
        // A blank language is as absent as a null one and must not sort as an empty language code.
        assertEquals("140-none", AudioTracks.describe(
            listOf(track("140-none", "   ", bitrateKbps = 128), track("140-de", "de", bitrateKbps = 128)),
            600_000,
        ).last().track.id)
    }

    @Test fun aLanguageThatLooksLikeTheOldSortingPlaceholderStaysAheadOfAMissingOne() {
        // The reading order used to stand a missing language in for the highest character there is. That
        // only works as long as no track ever carries it, and `language` is passed through from the
        // extractor unchecked. This is the one input that told the two versions apart: with the old
        // placeholder the real language and the missing one compared equal and the tie fell to the id,
        // which put the track without a language first.
        val tracks = listOf(
            track("140-none", null, bitrateKbps = 128),
            track("141-max", "\uffff", bitrateKbps = 128),
        )
        val order = AudioTracks.describe(tracks, 600_000).map { it.track.id }
        assertEquals(listOf("141-max", "140-none"), order)
        assertEquals(order, AudioTracks.describe(tracks.reversed(), 600_000).map { it.track.id })
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
