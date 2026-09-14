package app.sourcescribe.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * The cache directory a shared export is written to before another app receives it (defect 42).
 *
 * A share hands the other app a content URI for the file, and that app reads it right away. A day later the copy has
 * done its job; left alone, whole transcripts would stay in the app's cache until Android clears it. The app removes
 * such copies when it starts.
 */
object ShareCache {
    const val DIRECTORY = "shares"
    const val MAX_AGE_MS = 24L * 60L * 60L * 1000L

    /**
     * Deletes the regular files directly in [directory] that were last written more than [MAX_AGE_MS] before [nowMs],
     * and returns how many. A symbolic link or a directory is left alone. A file that cannot be read or removed now
     * stays until a later start.
     */
    fun deleteExpired(directory: File, nowMs: Long): Int {
        // The caller runs this at start-up without a handler of its own, so nothing here may throw.
        val children = try {
            directory.listFiles()
        } catch (_: SecurityException) {
            null
        } ?: return 0
        var deleted = 0
        for (child in children) {
            val path = child.toPath()
            try {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue
                val written = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis()
                if (nowMs - written > MAX_AGE_MS && Files.deleteIfExists(path)) deleted++
            } catch (_: IOException) {
                // Left for a later start.
            } catch (_: SecurityException) {
                // Left for a later start.
            }
        }
        return deleted
    }
}
