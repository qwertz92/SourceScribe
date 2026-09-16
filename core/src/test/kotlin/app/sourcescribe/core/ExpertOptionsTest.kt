package app.sourcescribe.core

import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.core.providers.OpenAiAdapter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Items 8 and 9 of round 25. Every switch and field under "Advanced options" is asked one question here —
 * may this option do anything at all in the job the reader is configuring — and the screen shows the answer
 * instead of leaving a control that quietly does nothing.
 *
 * Two different reasons an option can be idle, kept apart because the reader can act on them differently:
 * the chosen acquisition mode never reaches the code that reads it, or the chosen provider and model cannot
 * do it. The first is fixed one control higher on the same screen, the second by choosing another provider.
 */
class ExpertOptionsTest {
    private val capabilities = mapOf<Pair<Provider, String>, ProviderCapabilities>(
        (Provider.GROQ to GroqAdapter.MODEL_TURBO) to GroqAdapter().capabilities(GroqAdapter.MODEL_TURBO),
        (Provider.OPENAI to OpenAiAdapter.MODEL_WHISPER_1) to OpenAiAdapter().capabilities(OpenAiAdapter.MODEL_WHISPER_1),
        (Provider.OPENAI to OpenAiAdapter.MODEL_GPT_TRANSCRIBE) to OpenAiAdapter().capabilities(OpenAiAdapter.MODEL_GPT_TRANSCRIBE),
        (Provider.ASSEMBLYAI to AssemblyAiAdapter.MODEL_U35) to AssemblyAiAdapter().capabilities(AssemblyAiAdapter.MODEL_U35),
    )

    private fun config(mode: AcquisitionMode, pair: Pair<Provider, String>? = null) = JobConfig(
        mode = mode,
        provider = pair?.first,
        model = pair?.second,
    )

    private fun availability(option: ExpertOption, mode: AcquisitionMode, pair: Pair<Provider, String>? = null) =
        ExpertOptions.availability(option, config(mode, pair), pair?.let { capabilities.getValue(it) })

    @Test
    fun theFallbackAfterAFailedCaptionFetchIsOfferedOnlyWhereSomethingReadsIt() {
        // The plan's own example. `AcquisitionPlanner.plan` reads `fallbackOnCaptionError` in exactly one
        // branch: CAPTIONS_THEN_STT with a caption fetch that failed. In STT_ONLY the provider is already the
        // plan, in BOTH speech-to-text runs whatever the captions do, and CAPTIONS_ONLY never reaches a
        // provider at all — yet the switch stood on the screen for STT_ONLY and BOTH and did nothing.
        val groq = Provider.GROQ to GroqAdapter.MODEL_TURBO
        for (mode in AcquisitionMode.entries) {
            assertEquals(
                "fallback on caption error in $mode",
                if (mode == AcquisitionMode.CAPTIONS_THEN_STT) OptionAvailability.AVAILABLE
                else OptionAvailability.POINTLESS_FOR_MODE,
                availability(ExpertOption.FALLBACK_ON_CAPTION_ERROR, mode, groq),
            )
        }
    }

    @Test
    fun anOptionThatOnlySteersACaptionTrackIsIdleWhereNoCaptionTrackIsRead() {
        val captionOptions = listOf(
            ExpertOption.ORIGINAL_LANGUAGE, ExpertOption.UPLOADER_CAPTIONS, ExpertOption.AUTOMATIC_CAPTIONS,
            ExpertOption.TRANSLATED_CAPTIONS, ExpertOption.CAPTION_LANGUAGES,
        )
        for (mode in AcquisitionMode.entries) for (option in captionOptions) {
            assertEquals(
                "$option in $mode",
                if (AcquisitionPlanner.usesCaptions(mode)) OptionAvailability.AVAILABLE
                else OptionAvailability.POINTLESS_FOR_MODE,
                availability(option, mode, Provider.GROQ to GroqAdapter.MODEL_TURBO),
            )
        }
    }

    @Test
    fun whatHappensToTheDownloadedAudioMattersOnlyWhereAudioIsDownloaded() {
        // CAPTIONS_ONLY never runs `Phase.DOWNLOAD_AUDIO`, so a retention setting for a file that is never
        // fetched is a control with nothing behind it.
        for (mode in AcquisitionMode.entries) {
            assertEquals(
                "audio retention in $mode",
                if (AcquisitionPlanner.mayUseSpeechToText(mode)) OptionAvailability.AVAILABLE
                else OptionAvailability.POINTLESS_FOR_MODE,
                availability(ExpertOption.AUDIO_RETENTION, mode, Provider.GROQ to GroqAdapter.MODEL_TURBO),
            )
        }
    }

    @Test
    fun anOptionTheChosenModelCannotDoSaysThatRatherThanStandingThereGrey() {
        // Groq's Whisper turbo: keyterms yes, speakers no, word times no. OpenAI's whisper-1: word and
        // segment times yes, speakers no. AssemblyAI's Universal 3.5: all four. The numbers come from the
        // adapters themselves, so a capability that changes there changes this expectation with it.
        val cases = listOf(
            Provider.GROQ to GroqAdapter.MODEL_TURBO,
            Provider.OPENAI to OpenAiAdapter.MODEL_WHISPER_1,
            Provider.OPENAI to OpenAiAdapter.MODEL_GPT_TRANSCRIBE,
            Provider.ASSEMBLYAI to AssemblyAiAdapter.MODEL_U35,
        )
        val asked = mapOf(
            ExpertOption.DIARIZATION to { it: ProviderCapabilities -> it.diarization },
            ExpertOption.WORD_TIMESTAMPS to { it: ProviderCapabilities -> it.wordTimestamps },
            ExpertOption.SEGMENT_TIMESTAMPS to { it: ProviderCapabilities -> it.segmentTimestamps },
            ExpertOption.CONTEXT_TERMS to { it: ProviderCapabilities -> it.contextTerms },
        )
        for (pair in cases) for ((option, supported) in asked) {
            val capability = capabilities.getValue(pair)
            assertEquals(
                "$option with ${pair.first} ${pair.second}",
                if (supported(capability)) OptionAvailability.AVAILABLE else OptionAvailability.UNSUPPORTED_BY_PROVIDER,
                availability(option, AcquisitionMode.STT_ONLY, pair),
            )
            // And in a mode that never reaches a provider the reason is the mode, not the provider: telling
            // the reader that Groq cannot do speakers while the job is caption-only would send them to the
            // wrong control.
            assertEquals(
                "$option in CAPTIONS_ONLY",
                OptionAvailability.POINTLESS_FOR_MODE,
                availability(option, AcquisitionMode.CAPTIONS_ONLY, pair),
            )
        }
    }

    @Test
    fun withNoProviderChosenYetTheAnswerNamesThatAndNotAProvider() {
        // A note reading "not available with " and nothing after it is worse than no note. Until a provider
        // and a model are chosen the four capability options have nobody to be unsupported by.
        for (option in listOf(ExpertOption.DIARIZATION, ExpertOption.WORD_TIMESTAMPS,
            ExpertOption.SEGMENT_TIMESTAMPS, ExpertOption.CONTEXT_TERMS)) {
            assertEquals(option.name, OptionAvailability.PROVIDER_NOT_CHOSEN,
                availability(option, AcquisitionMode.STT_ONLY, null))
        }
        // A provider whose model this app cannot read capabilities for is a different case: something is
        // chosen, and it is the thing that cannot do it.
        val unreadable = JobConfig(mode = AcquisitionMode.STT_ONLY, provider = Provider.GROQ, model = "no-such-model")
        assertEquals(OptionAvailability.UNSUPPORTED_BY_PROVIDER,
            ExpertOptions.availability(ExpertOption.DIARIZATION, unreadable, null))
    }

    @Test
    fun theThreeOptionsThatAlwaysApplyAreNeverGreyedOut() {
        // Raw data is kept for a caption branch as well as for a provider response, every branch uses the
        // network, and every finished artifact can be exported.
        for (mode in AcquisitionMode.entries) for (option in listOf(ExpertOption.RETAIN_RAW,
            ExpertOption.UNMETERED, ExpertOption.EXPORT_FORMATS)) {
            assertEquals("$option in $mode", OptionAvailability.AVAILABLE, availability(option, mode, null))
        }
    }

    @Test
    fun anOptionThatIsStillSetStaysOperableSoItCanBeTurnedOff() {
        // The rule the screen has carried since round 15, now in one place. A switch that was turned on for a
        // model chosen earlier keeps the job refused as UNSUPPORTED_OPTION until it is turned off, so greying
        // it out on the capability alone locks the reader out of the only fix.
        val turbo = Provider.GROQ to GroqAdapter.MODEL_TURBO
        val capability = capabilities.getValue(turbo)
        val plain = config(AcquisitionMode.STT_ONLY, turbo)
        // Groq's Whisper turbo returns no speaker labels.
        assertEquals(false, ExpertOptions.operable(ExpertOption.DIARIZATION, plain, capability))
        assertEquals(true, ExpertOptions.operable(ExpertOption.DIARIZATION, plain.copy(diarization = true), capability))
        // OpenAI's gpt-transcribe returns no timestamps of either kind.
        val transcribe = Provider.OPENAI to OpenAiAdapter.MODEL_GPT_TRANSCRIBE
        val noTimes = capabilities.getValue(transcribe)
        val timed = config(AcquisitionMode.STT_ONLY, transcribe)
        assertEquals(false, ExpertOptions.operable(ExpertOption.WORD_TIMESTAMPS, timed, noTimes))
        assertEquals(true, ExpertOptions.operable(ExpertOption.WORD_TIMESTAMPS, timed.copy(wordTimestamps = true), noTimes))
        // Segment timestamps are on in a fresh `JobConfig`, so choosing this model is exactly the case the
        // exception exists for: the switch is set, the provider cannot do it, and the preview refuses the
        // job until it is off. `MainViewModel.modelDefaults` clears it when the model is chosen on this
        // screen; a draft from a preset or a re-prepared job can still arrive with it on.
        assertEquals(true, ExpertOptions.operable(ExpertOption.SEGMENT_TIMESTAMPS, timed, noTimes))
        assertEquals(true, ExpertOptions.asksForSomething(ExpertOption.SEGMENT_TIMESTAMPS, timed))
        assertEquals(false, ExpertOptions.operable(ExpertOption.SEGMENT_TIMESTAMPS, timed.copy(segmentTimestamps = false), noTimes))
        // Keyterms Groq does support, so the field is operable whether or not it holds anything.
        assertEquals(true, ExpertOptions.operable(ExpertOption.CONTEXT_TERMS, plain, capability))
        assertEquals(true, ExpertOptions.operable(ExpertOption.CONTEXT_TERMS,
            timed.copy(contextTerms = listOf("Kubernetes")), noTimes))
        // And an option the provider supports needs no exception at all.
        assertEquals(true, ExpertOptions.operable(ExpertOption.SEGMENT_TIMESTAMPS, plain, capability))
    }
}
