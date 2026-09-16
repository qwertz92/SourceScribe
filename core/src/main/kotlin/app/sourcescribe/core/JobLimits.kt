package app.sourcescribe.core

/**
 * The app's own ceiling on how long a piece of audio may be. The preview screen, the settings validator and
 * the job pipeline all read it from here, so a change cannot leave one of them enforcing a different number,
 * and the rule stays testable without a device.
 *
 * Since 0.4.0 this is the only length limit there is: the new-source screen no longer asks for one, a job
 * takes its length from the source itself, and `JobConfig.maxAudioSeconds` carries this ceiling as the limit
 * the job was created under. A job created by an older version keeps the lower limit it was started with,
 * because a configuration snapshot is not rewritten afterwards.
 */
object JobLimits {
    const val MAX_AUDIO_SECONDS = 36_000L
    const val MAX_AUDIO_MINUTES = MAX_AUDIO_SECONDS / 60

    /** True when the source is longer than this app processes in one job. An unknown length is not too long. */
    fun exceeds(durationMs: Long?): Boolean = durationMs != null && durationMs > MAX_AUDIO_SECONDS * 1000L
}
