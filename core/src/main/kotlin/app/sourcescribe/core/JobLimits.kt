package app.sourcescribe.core

/**
 * The app's own ceiling on how long a piece of audio may be. The preview screen, the settings validator and
 * the job pipeline all read it from here, so a change cannot leave one of them enforcing a different number.
 */
object JobLimits {
    const val MAX_AUDIO_SECONDS = 36_000L
    const val MAX_AUDIO_MINUTES = MAX_AUDIO_SECONDS / 60
}
