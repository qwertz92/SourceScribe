package app.sourcescribe.core

/**
 * The app's own ceiling on how long a piece of audio may be, together with the arithmetic around it. The
 * preview screen, the settings validator and the job pipeline all read it from here, so a change cannot
 * leave one of them enforcing a different number, and every rule stays testable without a device.
 */
object JobLimits {
    const val MAX_AUDIO_SECONDS = 36_000L
    const val MAX_AUDIO_MINUTES = MAX_AUDIO_SECONDS / 60

    /**
     * True when the source is longer than the limit this job would carry. A limit outside the allowed range
     * is deliberately not judged here: `configError` refuses it as an invalid entry, and reporting it as a
     * length problem would name the wrong cause and offer the wrong remedy.
     */
    fun exceeds(durationMs: Long?, maxAudioSeconds: Long): Boolean =
        durationMs != null && maxAudioSeconds in 1..MAX_AUDIO_SECONDS &&
            durationMs > maxAudioSeconds * 1000L

    /**
     * The smallest allowed limit that would admit this source, rounded up to whole minutes and padded a
     * little. Null only when no allowed limit admits it, because the source is past the app's own ceiling.
     */
    fun suggestedSeconds(durationMs: Long): Long? {
        val needed = ((durationMs + 59_999) / 60_000) * 60
        if (needed > MAX_AUDIO_SECONDS) return null
        return minOf(needed + 5 * 60, MAX_AUDIO_SECONDS)
    }
}
