package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.AppSettings
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStoreTest {
    @Test
    fun aStoreReadsAndWritesTheSettingsInTheFilesOfItsOwnContext() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val appSettings = File(base.filesDir, "datastore/settings.preferences_pb")
        fun stamp() = appSettings.takeIf(File::exists)?.let { it.lastModified() to it.length() }
        val before = stamp()
        val roots = List(2) { File(base.cacheDir, "settings-store-${UUID.randomUUID()}") }
        try {
            val changed = AppSettings().parallelJobs + 1
            SettingsStore(FilesContext(base, roots[0])).update { it.copy(parallelJobs = changed) }

            // Another store on the same files sees the change, and one on other files does not.
            assertEquals(changed, SettingsStore(FilesContext(base, roots[0])).settings.first().parallelJobs)
            assertEquals(AppSettings().parallelJobs, SettingsStore(FilesContext(base, roots[1])).settings.first().parallelJobs)
            assertTrue(File(roots[0], "files/datastore/settings.preferences_pb").isFile)
            // The delegate this replaced wrote the app's own settings whenever the app had used it first.
            assertEquals(before, stamp())
        } finally {
            roots.forEach { it.deleteRecursively() }
        }
    }

    private class FilesContext(base: Context, root: File) : ContextWrapper(base) {
        private val files = File(root, "files")

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = files
    }
}
