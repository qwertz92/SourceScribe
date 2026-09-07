package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.Provider
import app.sourcescribe.core.Region
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialStoreTest {
    private lateinit var context: Context
    private lateinit var testRoot: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testRoot = File(context.cacheDir, "credential-store-test-${UUID.randomUUID()}")
        check(testRoot.mkdirs())
    }

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
    }

    @Test
    fun roundTripListsMetadataAndDoesNotStorePlaintextKey() {
        val store = store()
        val apiKey = "test-secret-value"

        val id = store.save(Provider.GROQ, Region.US, apiKey)

        assertEquals(apiKey, store.read(id, Provider.GROQ, Region.US))
        assertEquals(listOf(CredentialInfo(id, Provider.GROQ, Region.US)), store.list())
        val blob = credentialFile(id).readBytes()
        assertFalse(blob.contains(apiKey.toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun eachSaveUsesAUniqueNonce() {
        val store = store()

        val first = store.save(Provider.OPENAI, Region.EU, "same-secret")
        val second = store.save(Provider.OPENAI, Region.EU, "same-secret")

        assertFalse(iv(first).contentEquals(iv(second)))
    }

    @Test
    fun replaceKeepsIdAndReadsNewKey() {
        val store = store()
        val id = store.save(Provider.GROQ, Region.US, "old-secret")

        store.replace(id, Provider.GROQ, Region.US, "new-secret")

        assertEquals("new-secret", store.read(id, Provider.GROQ, Region.US))
        assertEquals(listOf(CredentialInfo(id, Provider.GROQ, Region.US)), store.list())
    }

    @Test
    fun replaceScopeMismatchLeavesOldKey() {
        val store = store()
        val id = store.save(Provider.GROQ, Region.US, "old-secret")

        expectCode(CredentialException.Code.CORRUPT) {
            store.replace(id, Provider.OPENAI, Region.US, "new-secret")
        }
        expectCode(CredentialException.Code.CORRUPT) {
            store.replace(id, Provider.GROQ, Region.EU, "new-secret")
        }

        assertEquals("old-secret", store.read(id, Provider.GROQ, Region.US))
    }

    @Test
    fun malformedReplacementKeyLeavesOldKey() {
        val store = store()
        val id = store.save(Provider.GROQ, Region.US, "old-secret")

        expectCode(CredentialException.Code.INVALID_INPUT) {
            store.replace(id, Provider.GROQ, Region.US, "bad\nsecret")
        }

        assertEquals("old-secret", store.read(id, Provider.GROQ, Region.US))
    }

    @Test
    fun restoreCreatesMissingReferenceWithRequestedId() {
        val store = store()
        val id = UUID.randomUUID().toString()

        store.restore(id, Provider.GROQ, Region.US, "restored-secret")

        assertEquals("restored-secret", store.read(id, Provider.GROQ, Region.US))
        assertEquals(listOf(CredentialInfo(id, Provider.GROQ, Region.US)), store.list())
    }

    @Test
    fun restoreDoesNotOverwriteExistingReference() {
        val store = store()
        val id = store.save(Provider.GROQ, Region.US, "old-secret")

        expectCode(CredentialException.Code.INVALID_INPUT) {
            store.restore(id, Provider.GROQ, Region.US, "new-secret")
        }

        assertEquals("old-secret", store.read(id, Provider.GROQ, Region.US))
    }

    @Test
    fun restoreScopeMismatchLeavesExistingReferenceUntouched() {
        val store = store()
        val id = store.save(Provider.GROQ, Region.US, "old-secret")

        expectCode(CredentialException.Code.CORRUPT) {
            store.restore(id, Provider.OPENAI, Region.US, "new-secret")
        }
        expectCode(CredentialException.Code.CORRUPT) {
            store.restore(id, Provider.GROQ, Region.EU, "new-secret")
        }

        assertEquals("old-secret", store.read(id, Provider.GROQ, Region.US))
    }

    @Test
    fun restoreDoesNotOverwriteCorruptReference() {
        val store = store()
        val id = store.save(Provider.GROQ, Region.US, "old-secret")
        credentialFile(id).writeBytes(byteArrayOf(1, 2, 3))
        val corruptBlob = credentialFile(id).readBytes()

        expectCode(CredentialException.Code.CORRUPT) {
            store.restore(id, Provider.GROQ, Region.US, "new-secret")
        }

        assertEquals(corruptBlob.toList(), credentialFile(id).readBytes().toList())
    }

    @Test
    fun wrongProviderAndRegionAreRejectedByAad() {
        val store = store()
        val id = store.save(Provider.GROQ, Region.US, "test-secret")

        expectCode(CredentialException.Code.CORRUPT) {
            store.read(id, Provider.OPENAI, Region.US)
        }
        expectCode(CredentialException.Code.CORRUPT) {
            store.read(id, Provider.GROQ, Region.EU)
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val store = store()
        val id = store.save(Provider.ASSEMBLYAI, Region.EU, "test-secret")
        val blob = credentialFile(id).readBytes()
        blob[blob.lastIndex] = (blob[blob.lastIndex].toInt() xor 1).toByte()
        credentialFile(id).writeBytes(blob)

        expectCode(CredentialException.Code.CORRUPT) {
            store.read(id, Provider.ASSEMBLYAI, Region.EU)
        }
    }

    @Test
    fun deleteRemovesCredential() {
        val store = store()
        val id = store.save(Provider.GROQ, Region.US, "test-secret")

        store.delete(id)

        assertEquals(emptyList<CredentialInfo>(), store.list())
        expectCode(CredentialException.Code.NOT_FOUND) {
            store.read(id, Provider.GROQ, Region.US)
        }
    }

    private fun expectCode(code: CredentialException.Code, operation: () -> Unit) {
        try {
            operation()
            fail("Expected ${code.name}")
        } catch (error: CredentialException) {
            assertEquals(code, error.code)
        }
    }

    private fun iv(id: String): ByteArray {
        DataInputStream(ByteArrayInputStream(credentialFile(id).readBytes())).use { input ->
            input.readInt()
            input.readInt()
            val idLength = input.readInt()
            input.skipBytes(idLength)
            input.readInt()
            input.readInt()
            assertEquals(12, input.readInt())
            return ByteArray(12).also(input::readFully)
        }
    }

    private fun store() = CredentialStore(IsolatedStorageContext(context, testRoot))

    private fun credentialDirectory(): File = testRoot.resolve("credentials")

    private fun credentialFile(id: String): File = credentialDirectory().resolve("$id.cred")

    private fun ByteArray.contains(needle: ByteArray): Boolean =
        indices.any { start ->
            start + needle.size <= size && needle.indices.all { offset -> this[start + offset] == needle[offset] }
        }

    private class IsolatedStorageContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getFilesDir(): File = root
    }
}
