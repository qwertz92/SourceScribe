package app.sourcescribe.core.providers

/**
 * What both readers of the model name a provider reports hold it to: the AssemblyAI adapter and the parser behind
 * OpenAI and Groq. The name is stored with the transcript and shown as the model the provider says it used, so a value
 * past the bound is refused rather than shortened. The two readers must refuse the same values and name the refusal
 * the same way, which two private copies of these values did not ensure (defect 20).
 */
internal object ReportedModel {
    /** The longest name kept, in the units `String.length` counts. */
    const val MAX_LENGTH = 128

    /** The warning for a name that is present but names nothing. */
    const val MALFORMED = "REPORTED_MODEL_MALFORMED"

    /** The warning for a name longer than [MAX_LENGTH]. */
    const val TOO_LONG = "REPORTED_MODEL_TOO_LONG"
}
