package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.AppSettings
import app.sourcescribe.core.ExportFormat
import app.sourcescribe.core.ExportTargets
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun aKeytermSetAndAFolderPerFormatSurviveBeingWrittenAndReadBack() = runBlocking {
        // Items 25/13 and 25/14. Both are new fields on stored records, and the settings file refuses a
        // record it considers invalid rather than quietly correcting it, so the values a screen can produce
        // have to come back out of it unchanged — and a value no screen can produce has to be refused.
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "settings-store-${UUID.randomUUID()}")
        val folder = "content://com.android.externalstorage.documents/tree/primary%3AMovies"
        try {
            val store = SettingsStore(FilesContext(base, root))
            store.update {
                it.copy(
                    keytermSets = mapOf("technical" to listOf("Kubernetes", "etcd")),
                    defaults = it.defaults.copy(exportTreeUris = mapOf(ExportFormat.SRT to folder)),
                )
            }
            val read = SettingsStore(FilesContext(base, root)).settings.first()
            assertEquals(mapOf("technical" to listOf("Kubernetes", "etcd")), read.keytermSets)
            assertEquals(folder, ExportTargets.tree(read.defaults, ExportFormat.SRT))
            assertEquals(null, ExportTargets.tree(read.defaults, ExportFormat.MARKDOWN))

            // A set with a blank term would be a saved job refusal: every provider turns the whole list down
            // over one. A folder that is not a document tree is not a folder.
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { store.update { it.copy(keytermSets = mapOf("broken" to listOf("Kubernetes", " "))) } }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    store.update { it.copy(defaults = it.defaults.copy(
                        exportTreeUris = mapOf(ExportFormat.SRT to "/storage/emulated/0/Movies"))) }
                }
            }
            // And the refused writes changed nothing.
            assertEquals(mapOf("technical" to listOf("Kubernetes", "etcd")),
                SettingsStore(FilesContext(base, root)).settings.first().keytermSets)
        } finally {
            root.deleteRecursively()
        }
    }

    private class FilesContext(base: Context, root: File) : ContextWrapper(base) {
        private val files = File(root, "files")

        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = files
    }
}
