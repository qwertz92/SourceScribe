package app.sourcescribe

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.core.*
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.data.CredentialInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewRulesTest {
    @Test
    fun damagedStoredConfigurationHasNoFallbackProviderOrDefaults() {
        for (raw in listOf("", "{", "{\"mode\":\"BROKEN\"}", "{\"provider\":\"UNKNOWN\"}")) {
            assertNull(app.sourcescribe.data.decodeStoredJobConfig(raw))
        }
        val config = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = Provider.GROQ,
            model = GroqAdapter.MODEL_TURBO, maxCostMicrousd = 123)
        assertEquals(config, app.sourcescribe.data.decodeStoredJobConfig(kotlinx.serialization.json.Json.encodeToString(config)))
    }

    @Test
    fun clipboardPartsPreserveEveryCharacterIncludingBoundaryEmoji() {
        val text = "a".repeat(63_999) + "\uD83D\uDE00" + "b".repeat(130_000)
        val ranges = MainViewModel.clipboardRanges(text)
        assertEquals(text, ranges.joinToString("") { text.substring(it) })
        assertTrue(ranges.all { it.count() <= 64_000 })
        assertTrue(ranges.none { text[it.last].isHighSurrogate() || text[it.first].isLowSurrogate() })
        assertTrue(MainViewModel.clipboardRanges("").isEmpty())
    }

    @Test
    fun startRequiresAvailableCaptionsAndExplicitAmbiguousAudioChoice() {
        val source = SourceResolver.youtube("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        val videoId = requireNotNull(source.videoId)
        val resolved = ResolvedSource(source, emptyList(), listOf(
            AudioTrack("a", videoId, "en", null, null, "fixture"),
            AudioTrack("b", videoId, "de", null, null, "fixture"),
        ), emptyMap())
        val captionOnly = SourcePreview(resolved, JobConfig(mode = AcquisitionMode.CAPTIONS_ONLY), null)
        assertEquals("NO_ACCEPTABLE_CAPTIONS", MainViewModel.previewError(captionOnly, emptyList()))
        // Caption-first remains available without permission to upload.
        assertNull(MainViewModel.previewError(captionOnly.copy(config = JobConfig()), emptyList()))
        val key = CredentialInfo("fixture-key", Provider.GROQ, Region.US)
        val stt = captionOnly.copy(config = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = key.provider,
            model = GroqAdapter.MODEL_TURBO, credentialId = key.id, uploadApproved = true))
        assertEquals("CHOOSE_AUDIO_TRACK", MainViewModel.previewError(stt, listOf(key)))
        assertEquals("CREDENTIAL_REQUIRED", MainViewModel.previewError(stt, emptyList()))
        assertNull(MainViewModel.previewError(stt.copy(config = stt.config.copy(audioTrackId = "b")), listOf(key)))
    }
    @Test
    fun availableCaptionsDoNotRequireFallbackAudioSelection() {
        val source = SourceResolver.youtube("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        val id = requireNotNull(source.videoId)
        val caption = CaptionTrack("en", id, "en", null, "json3", Generation.UPLOADER_PROVIDED, Translation.NONE, "fixture")
        val resolved = ResolvedSource(source, listOf(caption), listOf(
            AudioTrack("a", id, "en", null, null, "fixture"), AudioTrack("b", id, "de", null, null, "fixture")), emptyMap())
        val key = CredentialInfo("fixture-key", Provider.GROQ, Region.US)
        val config = JobConfig(mode = AcquisitionMode.CAPTIONS_THEN_STT, provider = key.provider,
            credentialId = key.id, model = GroqAdapter.MODEL_TURBO, uploadApproved = true, captionTrackId = caption.id)
        assertNull(MainViewModel.previewError(SourcePreview(resolved, config, null), listOf(key)))
    }

}
