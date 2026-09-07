package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.SourceKind
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioImportTest {
    private lateinit var context: Context
    private lateinit var database: SourceScribeDatabase
    private lateinit var testRoot: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        testRoot = File(context.cacheDir, "audio-import-test-${UUID.randomUUID()}")
        check(testRoot.mkdirs())
    }

    @After
    fun tearDown() {
        database.close()
        testRoot.deleteRecursively()
    }

    @Test
    fun rejectsFileUrisBeforeOpeningAnything() = runBlocking {
        expectCode(AudioImportCode.INVALID_INPUT) {
            importer().import(Uri.fromFile(File(context.cacheDir, "secret.wav")))
        }
    }

    @Test
    fun rejectsContentUrisWithoutAuthority() = runBlocking {
        expectCode(AudioImportCode.INVALID_INPUT) {
            importer().import("content:///audio/1".toUri())
        }
    }

    @Test
    fun importsFixtureWithVerifiedMetadataHashAndPrivateSnapshot() = runBlocking {
        val source = importer().import(AudioImportFixtureProvider.WAV_URI)
        val row = requireNotNull(database.records().source(source.id))
        val imported = requireNotNull(row.importedPath).let(::File)

        assertEquals("local:${AudioImportFixtureProvider.WAV_SHA256}", source.id)
        assertEquals(SourceKind.LOCAL_AUDIO, source.kind)
        assertEquals(AudioImportFixtureProvider.WAV_SHA256, source.contentHash)
        assertEquals(AudioImportFixtureProvider.WAV_BYTES.size.toLong(), source.fileBytes)
        assertEquals(100L, source.durationMs)
        assertTrue(source.mimeType.orEmpty().startsWith("audio/"))
        assertEquals(AudioImportFixtureProvider.WAV_SHA256, sha256(imported))
        assertEquals(AudioImportFixtureProvider.WAV_BYTES.size.toLong(), imported.length())
        assertFalse(row.snapshot.contains(AudioImportFixtureProvider.WAV_URI.toString()))
    }

    @Test
    fun repeatedImportDeduplicatesWithoutOverwritingVerifiedFile() = runBlocking {
        val first = importer().import(AudioImportFixtureProvider.WAV_URI)
        val imported = requireNotNull(database.records().source(first.id)?.importedPath).let(::File)
        val modifiedAt = imported.lastModified()

        val second = importer().import(AudioImportFixtureProvider.WAV_URI)

        assertEquals(first, second)
        assertEquals(modifiedAt, imported.lastModified())
        assertEquals(1, database.records().observeSources().first().size)
    }

    @Test
    fun reimportRestoresPathAfterRetentionClearsIt() = runBlocking {
        val first = importer().import(AudioImportFixtureProvider.WAV_URI)
        val row = requireNotNull(database.records().source(first.id))
        val previous = requireNotNull(row.importedPath).let(::File)
        assertTrue(previous.delete())
        database.records().updateSource(row.copy(importedPath = null))

        val restored = importer().import(AudioImportFixtureProvider.WAV_URI)
        val restoredRow = requireNotNull(database.records().source(first.id))
        val restoredFile = requireNotNull(restoredRow.importedPath).let(::File)

        assertEquals(first, restored)
        assertTrue(restoredFile.isFile)
        assertEquals(AudioImportFixtureProvider.WAV_SHA256, sha256(restoredFile))
        assertEquals(AudioImportFixtureProvider.WAV_BYTES.size.toLong(), restoredFile.length())
    }

    @Test
    fun reimportRestoresMissingExpectedFileWithoutClearingDatabasePath() = runBlocking {
        val first = importer().import(AudioImportFixtureProvider.WAV_URI)
        val row = requireNotNull(database.records().source(first.id))
        val missing = requireNotNull(row.importedPath).let(::File)
        assertTrue(missing.delete())

        val restored = importer().import(AudioImportFixtureProvider.WAV_URI)
        val restoredRow = requireNotNull(database.records().source(first.id))
        val restoredFile = requireNotNull(restoredRow.importedPath).let(::File)

        assertEquals(first, restored)
        assertEquals(missing.absolutePath, restoredFile.absolutePath)
        assertEquals(AudioImportFixtureProvider.WAV_SHA256, sha256(restoredFile))
    }

    @Test
    fun mismatchedSnapshotFailsClosedAfterRetentionClearsPath() = runBlocking {
        val first = importer().import(AudioImportFixtureProvider.WAV_URI)
        val row = requireNotNull(database.records().source(first.id))
        val previous = requireNotNull(row.importedPath).let(::File)
        assertTrue(previous.delete())
        val brokenSnapshot = Json.encodeToString(first.copy(contentHash = "0".repeat(64)))
        database.records().updateSource(row.copy(snapshot = brokenSnapshot, importedPath = null))

        expectCode(AudioImportCode.CORRUPT) {
            importer().import(AudioImportFixtureProvider.WAV_URI)
        }

        assertTrue(testRoot.resolve("imports").listFiles().orEmpty().none { it.name.endsWith(".part") || it.name.endsWith(".audio") })
    }

    @Test
    fun corruptExistingFileRejectsReimportWithoutReplacingIt() = runBlocking {
        val first = importer().import(AudioImportFixtureProvider.WAV_URI)
        val row = requireNotNull(database.records().source(first.id))
        val imported = requireNotNull(row.importedPath).let(::File)
        imported.writeText("corrupt existing audio")

        expectCode(AudioImportCode.CORRUPT) {
            importer().import(AudioImportFixtureProvider.WAV_URI)
        }

        assertEquals(row.importedPath, database.records().source(first.id)?.importedPath)
        assertEquals("corrupt existing audio", imported.readText())
        assertTrue(testRoot.resolve("imports").listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun nonAudioFixtureFailsBeforeRoomPersistenceAndCleansTemporaryFile() = runBlocking {
        expectCode(AudioImportCode.PROBE_FAILED) {
            importer().import(AudioImportFixtureProvider.TEXT_URI)
        }

        assertTrue(database.records().observeSources().first().isEmpty())
        val imports = testRoot.resolve("imports")
        assertTrue(imports.listFiles().orEmpty().none { it.name.endsWith(".part") || it.name.endsWith(".audio") })
    }

    private fun importer() = AudioImport(
        IsolatedStorageContext(context, testRoot),
        NativeRuntime(context),
        SettingsStore(context),
        database.records(),
    )

    private suspend fun expectCode(code: AudioImportCode, operation: suspend () -> Unit) {
        try {
            operation()
            fail("Expected ${code.name}")
        } catch (error: AudioImportException) {
            assertEquals(code, error.code)
        }
    }

    private class IsolatedStorageContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getNoBackupFilesDir(): File = root
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
