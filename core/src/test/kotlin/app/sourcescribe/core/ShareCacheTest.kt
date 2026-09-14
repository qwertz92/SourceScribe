package app.sourcescribe.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ShareCacheTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun aShareOlderThanADayIsDeletedAndAYoungerOneStays() {
        // A share hands another app a content URI that it reads right away. A day later the copy has done its job,
        // and left alone it would keep a whole transcript in the cache until Android clears it.
        val now = 1_757_860_000_000L
        val directory = folder.newFolder(ShareCache.DIRECTORY)
        val old = File(directory, "old.md").apply { writeText("transcript") }
        val young = File(directory, "young.md").apply { writeText("transcript") }
        assertTrue(old.setLastModified(now - ShareCache.MAX_AGE_MS - 60_000L))
        assertTrue(young.setLastModified(now - ShareCache.MAX_AGE_MS + 60_000L))

        assertEquals(1, ShareCache.deleteExpired(directory, now))

        assertFalse(old.exists())
        assertTrue(young.exists())
    }

    @Test fun noDirectoryIsNothingToDelete() {
        assertEquals(0, ShareCache.deleteExpired(File(folder.root, ShareCache.DIRECTORY), 1_757_860_000_000L))
    }
}
