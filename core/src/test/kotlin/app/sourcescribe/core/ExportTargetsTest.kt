package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Item 14 of round 25. One folder for everything is the usual case and stays the default; a reader who wants
 * the subtitle files beside the video and the notes in the notes folder names a folder for that one format.
 *
 * The rule is one function because three places ask it: the automatic export after a job, the rescheduling
 * path that records a failed export, and the settings screen that has to say which folder a format writes to.
 */
class ExportTargetsTest {
    private val default = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"
    private val subtitles = "content://com.android.externalstorage.documents/tree/primary%3AMovies"

    @Test
    fun aFormatWithoutAFolderOfItsOwnWritesIntoTheOneDefaultFolder() {
        val config = JobConfig(exportTreeUri = default)
        for (format in ExportFormat.entries) {
            assertEquals(format.name, default, ExportTargets.tree(config, format))
        }
    }

    @Test
    fun aFormatWithItsOwnFolderWritesThereAndLeavesEveryOtherFormatAlone() {
        val config = JobConfig(exportTreeUri = default, exportTreeUris = mapOf(ExportFormat.SRT to subtitles))
        assertEquals(subtitles, ExportTargets.tree(config, ExportFormat.SRT))
        for (format in ExportFormat.entries - ExportFormat.SRT) {
            assertEquals(format.name, default, ExportTargets.tree(config, format))
        }
    }

    @Test
    fun aFormatFolderStandsOnItsOwnWhenNoDefaultFolderWasEverChosen() {
        // Settings can be left without a default folder; a format that names one is still exportable, and
        // every other format is the case `JobCoordinator.runExports` records as PERMISSION_REQUIRED.
        val config = JobConfig(exportTreeUri = null, exportTreeUris = mapOf(ExportFormat.SRT to subtitles))
        assertEquals(subtitles, ExportTargets.tree(config, ExportFormat.SRT))
        assertNull(ExportTargets.tree(config, ExportFormat.MARKDOWN))
        assertNull(ExportTargets.tree(JobConfig(), ExportFormat.MARKDOWN))
    }

    @Test
    fun aStoredFolderThatNamesNothingIsNotAFolder() {
        // A configuration snapshot is read back from a file another version wrote. An empty string there is
        // not a tree URI, and treating it as one would send an export to `"".toUri()` instead of to the
        // default folder the reader actually chose.
        val config = JobConfig(exportTreeUri = default, exportTreeUris = mapOf(ExportFormat.SRT to "   "))
        assertEquals(default, ExportTargets.tree(config, ExportFormat.SRT))
    }
}
