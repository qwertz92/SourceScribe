package app.sourcescribe.core

/**
 * Which folder one export format is written into.
 *
 * One folder for everything is the usual case and stays what `JobConfig.exportTreeUri` holds. A format may
 * name a folder of its own — subtitle files beside the video, notes in the notes folder — and
 * `JobConfig.exportTreeUris` holds those. Three places ask this question: the automatic export after a job,
 * the path that records a failed export with the folder it was meant for, and the settings screen that has
 * to show which folder a format actually writes to, so it is one function rather than three readings of two
 * fields.
 */
object ExportTargets {
    /** The tree URI [format] is exported to, or null when neither a folder of its own nor a default is set. */
    fun tree(config: JobConfig, format: ExportFormat): String? =
        config.exportTreeUris[format]?.takeIf { it.isNotBlank() } ?: config.exportTreeUri?.takeIf { it.isNotBlank() }
}
