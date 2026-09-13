package app.sourcescribe

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.core.*
import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.data.CredentialInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewRulesTest {
    @Test
    fun deliberateStartBindsApprovalToModeCredentialProviderAndRegionWithoutChangingTheDraft() {
        val key = CredentialInfo("selected-key", Provider.GROQ, Region.US)
        val draft = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = key.provider, region = key.region,
            credentialId = key.id, model = GroqAdapter.MODEL_TURBO, maxCostMicrousd = 123,
            audioTrackId = "chosen-audio", uploadApproved = false)
        for (mode in AcquisitionMode.entries) {
            val selected = draft.copy(mode = mode)
            assertEquals(selected.copy(uploadApproved = mode != AcquisitionMode.CAPTIONS_ONLY),
                MainViewModel.configurationForStart(selected, listOf(key)))
        }
        val staleApproval = draft.copy(uploadApproved = true)
        for (keys in listOf(emptyList(), listOf(key.copy(id = "other-key")),
            listOf(key.copy(provider = Provider.OPENAI)), listOf(key.copy(region = Region.EU)))) {
            assertEquals(draft, MainViewModel.configurationForStart(staleApproval, keys))
        }
        assertEquals(false, draft.uploadApproved)
    }

    @Test
    fun aSourceLongerThanThisJobAllowsIsNotPriced() {
        val config = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = Provider.GROQ,
            model = GroqAdapter.MODEL_TURBO, maxAudioSeconds = 3_600L)

        // Two hours under a one-hour limit: far inside the app's ten-hour ceiling, and still a source this
        // job cannot run. Round 12 consulted the ceiling alone, so the cost row offered a figure in
        // dollars for exactly this case while the warning below it said the run would be refused.
        assertTrue(MainViewModel.sourceTooLong(7_200_000L, config))
        assertEquals(false, MainViewModel.sourceTooLong(3_600_000L, config))

        // An unusable job limit is not judged as a length problem — but the ceiling still is.
        val unusableLimit = config.copy(maxAudioSeconds = 0L)
        assertEquals(false, MainViewModel.sourceTooLong(7_200_000L, unusableLimit))
        assertTrue(MainViewModel.sourceTooLong(JobLimits.MAX_AUDIO_SECONDS * 1000L + 1, unusableLimit))
        assertEquals(false, MainViewModel.sourceTooLong(JobLimits.MAX_AUDIO_SECONDS * 1000L, unusableLimit))

        // A source whose length nobody knows is not too long. It has no price either, for another reason.
        assertEquals(false, MainViewModel.sourceTooLong(null, config))
    }

    @Test
    fun aTermListTheProviderRefusesIsNamedAsAnErrorAndNotPriced() {
        val config = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = Provider.ASSEMBLYAI,
            model = AssemblyAiAdapter.MODEL_U35, credentialId = "c", uploadApproved = true,
            maxAudioSeconds = 3_600L)

        // Both provider paths refuse a list over a single blank entry, so a blank one among real terms is
        // not a smaller prompt — it is a job that cannot start. DEFECTS 31 had the preview silent about
        // exactly this while the submission would answer INVALID_INPUT.
        assertEquals(null, MainViewModel.configError(config))
        assertEquals("CONTEXT_TERM_BLANK", MainViewModel.configError(config.copy(contextTerms = listOf(""))))
        assertEquals(
            "CONTEXT_TERM_BLANK",
            MainViewModel.configError(config.copy(contextTerms = listOf("Kubernetes", "  "))),
        )
        assertEquals(null, MainViewModel.configError(config.copy(contextTerms = listOf("Kubernetes"))))

        // And the cost row says nothing rather than quoting one, the way it says nothing for a source past
        // the limit. A figure in dollars above a line that refuses the run is the defect round 13 closed.
        val oneBlank = config.copy(contextTerms = listOf(""))
        assertEquals(null, MainViewModel.estimatedCostMicrousd(oneBlank, 3_600_000L))
        val mixed = config.copy(contextTerms = listOf("Kubernetes", ""))
        assertEquals(null, MainViewModel.estimatedCostMicrousd(mixed, 3_600_000L))
        // 260 004, not 260 000: the shown figure is the sum over the six chunks an hour is submitted in,
        // and each one rounds up on its own. The same hour through the per-chunk formula is 260 000 exactly,
        // which is why the two numbers differ by four and neither is a typo.
        assertEquals(
            260_004L,
            MainViewModel.estimatedCostMicrousd(config.copy(contextTerms = listOf("Kubernetes")), 3_600_000L),
        )

        // What a job gets is what Start makes of the preview, and Start drops blank entries. A list stored with
        // one, from before the field dropped them itself, showed as an empty field while the job stayed refused,
        // and nothing on the screen said where the blank entry was (round 17). Refused as it stands, it is neither
        // refused nor unpriced once Start has made the job of it.
        val key = CredentialInfo("c", Provider.ASSEMBLYAI, config.region)
        val started = MainViewModel.configurationForStart(oneBlank, listOf(key))
        assertEquals(emptyList<String>(), started.contextTerms)
        assertEquals(null, MainViewModel.configError(started))
        assertNotEquals(null, MainViewModel.estimatedCostMicrousd(started, 3_600_000L))
        assertEquals(listOf("Kubernetes"), MainViewModel.configurationForStart(mixed, listOf(key)).contextTerms)
    }

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
            model = GroqAdapter.MODEL_TURBO, credentialId = key.id, uploadApproved = false))
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

    @Test
    fun everyErrorThePreviewCanShowHasItsHeightReserved() {
        val source = SourceResolver.youtube("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        val id = requireNotNull(source.videoId)
        val caption = CaptionTrack("en", id, "en", null, "json3", Generation.UPLOADER_PROVIDED, Translation.NONE, "fixture")
        val audio = listOf(AudioTrack("a", id, "en", null, null, "fixture"), AudioTrack("b", id, "de", null, null, "fixture"))
        val sources = listOf(emptyList(), listOf(caption)).flatMap { captions ->
            listOf(emptyList(), audio.take(1), audio).map { ResolvedSource(source, captions, it, emptyMap()) }
        }
        val options = listOf<(JobConfig) -> JobConfig>(
            { it }, { it.copy(diarization = true) }, { it.copy(wordTimestamps = true) }, { it.copy(segmentTimestamps = true) },
            { it.copy(contextTerms = listOf("Kubernetes")) }, { it.copy(contextTerms = listOf("Kubernetes", "")) },
            { it.copy(maxCostMicrousd = -1L) }, { it.copy(maxCostMicrousd = 1L) }, { it.copy(maxAudioSeconds = 0L) },
        )
        // Every mode, provider, model, region and source shape against each option, with and without a key.
        val seen = mutableSetOf<String>()
        for (mode in AcquisitionMode.entries) for (provider in listOf(null) + Provider.entries) {
            val models = listOf(null) + (provider?.let { MainViewModel.models(it) } ?: emptyList())
            for (model in models) for (region in Region.entries) for (option in options) for (resolved in sources) {
                val key = CredentialInfo("fixture-key", provider ?: Provider.GROQ, region)
                val config = option(JobConfig(mode = mode, provider = provider, model = model, region = region, credentialId = key.id))
                for (keys in listOf(emptyList(), listOf(key))) {
                    MainViewModel.previewError(SourcePreview(resolved, config, null), keys)?.let { seen += it }
                }
            }
        }
        assertEquals(
            "The preview card reserves room for the texts in PREVIEW_ERRORS_SHOWN_AS_TEXT. A code it can show " +
                "that the list lacks is still shown in full, but it moves the card when it appears. Add it there.",
            emptySet<String>(),
            seen - MainViewModel.PREVIEW_ERRORS_SHOWN_AS_TEXT.toSet() - "SOURCE_LONGER_THAN_LIMIT",
        )
        // The other direction, over the whole list: the grid has to reach every code it names, or the assertion
        // above is about nothing, and a code no preview can return reserves room for a text that never appears.
        // A hand-picked subset stood here until round 16, and one of the three codes it left out,
        // UPLOAD_APPROVAL_REQUIRED, turned out to be unreachable from the preview.
        assertEquals("Codes the grid failed to reach", emptyList<String>(),
            MainViewModel.PREVIEW_ERRORS_SHOWN_AS_TEXT.filterNot { it in seen })
    }

    @Test
    fun aDraftChangeMovesTheEpochOfEveryTypedSettingItChangesButTheOneBeingTyped() {
        val before = JobConfig()
        for (setting in TypedSetting.entries) {
            // Exhaustive, so a setting added later cannot pass here without a change of its own.
            val after = when (setting) {
                TypedSetting.AUDIO_LIMIT -> before.copy(maxAudioSeconds = 120)
                TypedSetting.BUDGET -> before.copy(maxCostMicrousd = 1)
                TypedSetting.CAPTION_LANGUAGES -> before.copy(preferredLanguages = listOf("de"))
                TypedSetting.STT_LANGUAGE -> before.copy(language = "de")
                TypedSetting.CONTEXT_TERMS -> before.copy(contextTerms = listOf("Kubernetes"))
            }
            assertNotEquals(setting.name, setting.of(before), setting.of(after))
            val edits = DraftEdits()
            val fromOutside = edits.after(before, after, typed = null)
            val typed = edits.after(before, after, typed = setting)
            for (other in TypedSetting.entries) {
                assertEquals("$setting set from outside, epoch of $other moved", other == setting,
                    fromOutside.epoch(other) != edits.epoch(other))
                assertEquals("$setting typed, epoch of $other", edits.epoch(other), typed.epoch(other))
            }
            // Set back to where it started, the epoch does not return with it: text typed under the first
            // epoch must not become current again over a value it was never typed against.
            val back = fromOutside.after(after, before, typed = null)
            assertNotEquals(edits.epoch(setting), back.epoch(setting))
            assertNotEquals(fromOutside.epoch(setting), back.epoch(setting))
        }
        // A change to nothing a field shows moves nothing, so a half-typed entry survives a switch being flipped.
        val edits = DraftEdits()
        assertEquals(edits, edits.after(JobConfig(), JobConfig(retainRaw = true, diarization = true), typed = null))
        // Every view model draws its own session, so an epoch saved before the process died is not current after.
        assertNotEquals(DraftEdits().epoch(TypedSetting.BUDGET), DraftEdits().epoch(TypedSetting.BUDGET))
    }
}
