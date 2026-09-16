package app.sourcescribe.core

/** One entry of the new-source screen's "Advanced options" block, as [ExpertOptions] judges it. */
enum class ExpertOption {
    ORIGINAL_LANGUAGE,
    UPLOADER_CAPTIONS,
    AUTOMATIC_CAPTIONS,
    TRANSLATED_CAPTIONS,
    CAPTION_LANGUAGES,
    FALLBACK_ON_CAPTION_ERROR,
    STT_LANGUAGE,
    DIARIZATION,
    WORD_TIMESTAMPS,
    SEGMENT_TIMESTAMPS,
    CONTEXT_TERMS,
    AUDIO_RETENTION,
    RETAIN_RAW,
    UNMETERED,
    EXPORT_FORMATS,
}

/** Why an expert option can do nothing in the job as it is currently configured, or that it can. */
enum class OptionAvailability {
    /** Something in the run reads this option. */
    AVAILABLE,

    /** Nothing in the chosen acquisition mode ever reads it; the fix is the mode control on the same screen. */
    POINTLESS_FOR_MODE,

    /** The chosen provider and model cannot do it; the fix is another provider or model. */
    UNSUPPORTED_BY_PROVIDER,

    /** Whether it can be done is not decided yet, because no provider and model are chosen. */
    PROVIDER_NOT_CHOSEN,
}

/**
 * Whether each option under "Advanced options" does anything in the job the reader is configuring.
 *
 * Two of them stood on the screen and were read by nothing: "try speech-to-text after a failed caption fetch"
 * outside CAPTIONS_THEN_STT, which is the only branch of `AcquisitionPlanner.plan` that asks
 * `fallbackOnCaptionError`, and what to do with the downloaded audio in CAPTIONS_ONLY, which downloads none.
 * Four more are decided by the provider, and up to 0.3.0 the screen greyed those out without saying why —
 * a switch that cannot be moved and gives no reason reads as a broken screen rather than as a fact about
 * Groq.
 *
 * The two reasons are kept apart because the reader acts on them differently, and the screen words them
 * differently: a mode the option has nothing to do with is fixed one control higher, an unsupported option
 * by choosing another provider or model. [operable] carries the third rule the screen has had since round
 * 15 — an option that is still switched on stays switchable, because it is the reason the job is refused as
 * `UNSUPPORTED_OPTION` and turning it off is the only fix.
 */
object ExpertOptions {
    fun availability(
        option: ExpertOption,
        config: JobConfig,
        capabilities: ProviderCapabilities?,
    ): OptionAvailability {
        val captions = AcquisitionPlanner.usesCaptions(config.mode)
        val speechToText = AcquisitionPlanner.mayUseSpeechToText(config.mode)
        return when (option) {
            ExpertOption.ORIGINAL_LANGUAGE, ExpertOption.UPLOADER_CAPTIONS, ExpertOption.AUTOMATIC_CAPTIONS,
            ExpertOption.TRANSLATED_CAPTIONS, ExpertOption.CAPTION_LANGUAGES ->
                if (captions) OptionAvailability.AVAILABLE else OptionAvailability.POINTLESS_FOR_MODE

            // The one branch that reads it: captions first, speech-to-text only if fetching them failed.
            ExpertOption.FALLBACK_ON_CAPTION_ERROR ->
                if (config.mode == AcquisitionMode.CAPTIONS_THEN_STT) OptionAvailability.AVAILABLE
                else OptionAvailability.POINTLESS_FOR_MODE

            // Audio is downloaded, prepared and retained only where a provider may be reached.
            ExpertOption.STT_LANGUAGE, ExpertOption.AUDIO_RETENTION ->
                if (speechToText) OptionAvailability.AVAILABLE else OptionAvailability.POINTLESS_FOR_MODE

            ExpertOption.DIARIZATION -> byCapability(speechToText, config, capabilities?.diarization)
            ExpertOption.WORD_TIMESTAMPS -> byCapability(speechToText, config, capabilities?.wordTimestamps)
            ExpertOption.SEGMENT_TIMESTAMPS -> byCapability(speechToText, config, capabilities?.segmentTimestamps)
            ExpertOption.CONTEXT_TERMS -> byCapability(speechToText, config, capabilities?.contextTerms)

            // Raw data is kept for a caption branch as well as for a provider response, every branch uses the
            // network, and every artifact that gets stored can be exported.
            ExpertOption.RETAIN_RAW, ExpertOption.UNMETERED, ExpertOption.EXPORT_FORMATS ->
                OptionAvailability.AVAILABLE
        }
    }

    /**
     * Whether the control may still be worked. [availability] alone would lock an option that is switched on
     * for a model chosen earlier: the preview refuses such a job as `UNSUPPORTED_OPTION`, and the only way
     * out of that is to switch the option off.
     */
    fun operable(option: ExpertOption, config: JobConfig, capabilities: ProviderCapabilities?): Boolean =
        availability(option, config, capabilities) == OptionAvailability.AVAILABLE || asksForSomething(option, config)

    /** True while this option is set to something a provider would be asked for. */
    fun asksForSomething(option: ExpertOption, config: JobConfig): Boolean = when (option) {
        ExpertOption.DIARIZATION -> config.diarization
        ExpertOption.WORD_TIMESTAMPS -> config.wordTimestamps
        ExpertOption.SEGMENT_TIMESTAMPS -> config.segmentTimestamps
        ExpertOption.CONTEXT_TERMS -> config.contextTerms.isNotEmpty()
        else -> false
    }

    private fun byCapability(
        speechToText: Boolean,
        config: JobConfig,
        supported: Boolean?,
    ): OptionAvailability = when {
        !speechToText -> OptionAvailability.POINTLESS_FOR_MODE
        supported == true -> OptionAvailability.AVAILABLE
        // Nothing is chosen that could be unsupported yet. A note reading "not available with" and then
        // nothing would be worse than no note at all.
        config.provider == null || config.model == null -> OptionAvailability.PROVIDER_NOT_CHOSEN
        // Either the capability says no, or this provider and model pair has no readable capability record —
        // and in both cases the thing that cannot do it is the provider and model that are chosen.
        else -> OptionAvailability.UNSUPPORTED_BY_PROVIDER
    }
}
