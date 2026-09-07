package app.sourcescribe.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import android.util.AtomicFile
import app.sourcescribe.core.Provider
import app.sourcescribe.core.Region
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.InvalidKeyException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CredentialInfo(
    val id: String,
    val provider: Provider,
    val region: Region,
)

class CredentialException(val code: Code) : Exception("Credential operation failed: ${code.name}") {
    enum class Code {
        NOT_FOUND,
        LOCKED_OR_INVALIDATED,
        CORRUPT,
        INVALID_INPUT,
        STORAGE,
    }
}

class CredentialStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)

    fun save(provider: Provider, region: Region, apiKey: String): String = synchronized(PROCESS_LOCK) {
        val keyBytes = validateApiKey(apiKey)
        val id = UUID.randomUUID().toString()
        try {
            ensureDirectory()
            val encrypted = encrypt(keyBytes, id, provider, region)
            writeBlob(
                AtomicFile(fileFor(id)),
                encodeBlob(id, provider, region, encrypted.iv, encrypted.ciphertext),
            )
            id
        } finally {
            keyBytes.fill(0)
        }
    }

    fun replace(id: String, provider: Provider, region: Region, apiKey: String): Unit = synchronized(PROCESS_LOCK) {
        val canonicalId = canonicalId(id)
        val keyBytes = validateApiKey(apiKey)
        try {
            val stored = parseBlob(readBlob(AtomicFile(fileFor(canonicalId))))
            if (stored.id != canonicalId || stored.provider != provider || stored.region != region) {
                throw CredentialException(CredentialException.Code.CORRUPT)
            }
            val encrypted = encrypt(keyBytes, canonicalId, provider, region)
            writeBlob(
                AtomicFile(fileFor(canonicalId)),
                encodeBlob(canonicalId, provider, region, encrypted.iv, encrypted.ciphertext),
            )
        } finally {
            keyBytes.fill(0)
        }
    }

    fun restore(id: String, provider: Provider, region: Region, apiKey: String): Unit = synchronized(PROCESS_LOCK) {
        val canonicalId = canonicalId(id)
        val keyBytes = validateApiKey(apiKey)
        try {
            ensureDirectory()
            val file = AtomicFile(fileFor(canonicalId))
            if (hasStoredFile(file)) {
                val stored = parseBlob(readBlob(file))
                if (stored.id != canonicalId || stored.provider != provider || stored.region != region) {
                    throw CredentialException(CredentialException.Code.CORRUPT)
                }
                throw CredentialException(CredentialException.Code.INVALID_INPUT)
            }
            val encrypted = encrypt(keyBytes, canonicalId, provider, region)
            writeBlob(
                file,
                encodeBlob(canonicalId, provider, region, encrypted.iv, encrypted.ciphertext),
            )
        } finally {
            keyBytes.fill(0)
        }
    }

    fun read(id: String, provider: Provider, region: Region): String = synchronized(PROCESS_LOCK) {
        val canonicalId = canonicalId(id)
        val stored = parseBlob(readBlob(AtomicFile(fileFor(canonicalId))))
        if (stored.id != canonicalId || stored.provider != provider || stored.region != region) {
            throw CredentialException(CredentialException.Code.CORRUPT)
        }
        val plaintext = decrypt(stored, canonicalId, provider, region)
        try {
            String(plaintext, StandardCharsets.UTF_8)
        } finally {
            plaintext.fill(0)
        }
    }

    fun delete(id: String) = synchronized(PROCESS_LOCK) {
        val canonicalId = canonicalId(id)
        ensureDirectory()
        val file = AtomicFile(fileFor(canonicalId))
        if (!hasStoredFile(file)) throw CredentialException(CredentialException.Code.NOT_FOUND)
        try {
            file.delete()
        } catch (_: SecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        } catch (_: RuntimeException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
    }

    fun list(): List<CredentialInfo> = synchronized(PROCESS_LOCK) {
        try {
            if (!directory.exists()) return@synchronized emptyList()
            if (!directory.isDirectory) throw CredentialException(CredentialException.Code.STORAGE)
            val files = directory.listFiles() ?: throw CredentialException(CredentialException.Code.STORAGE)
            files.asSequence()
                .mapNotNull { file ->
                    when {
                        file.name.endsWith(FILE_SUFFIX) -> file.name.removeSuffix(FILE_SUFFIX)
                        file.name.endsWith("$FILE_SUFFIX.bak") -> file.name.removeSuffix("$FILE_SUFFIX.bak")
                        else -> null
                    }
                }
                .distinct()
                .map { id ->
                    val canonicalId = try {
                        canonicalId(id)
                    } catch (_: CredentialException) {
                        throw CredentialException(CredentialException.Code.CORRUPT)
                    }
                    val stored = parseBlob(readBlob(AtomicFile(fileFor(canonicalId))))
                    if (stored.id != canonicalId) throw CredentialException(CredentialException.Code.CORRUPT)
                    CredentialInfo(canonicalId, stored.provider, stored.region)
                }
                .sortedBy(CredentialInfo::id)
                .toList()
        } catch (_: SecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
    }

    private fun fileFor(id: String): File = File(directory, "$id$FILE_SUFFIX")

    private fun ensureDirectory() {
        try {
            if (directory.exists()) {
                if (!directory.isDirectory) throw CredentialException(CredentialException.Code.STORAGE)
                return
            }
            if (!directory.mkdirs() && !directory.isDirectory) {
                throw CredentialException(CredentialException.Code.STORAGE)
            }
        } catch (_: SecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
    }

    private fun hasStoredFile(file: AtomicFile): Boolean {
        return try {
            val base = file.baseFile
            base.isFile || File("${base.path}.bak").isFile
        } catch (_: SecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
    }

    private fun validateApiKey(apiKey: String): ByteArray {
        if (apiKey.isBlank() || apiKey.any { Character.isISOControl(it) }) {
            throw CredentialException(CredentialException.Code.INVALID_INPUT)
        }
        val bytes = apiKey.toByteArray(StandardCharsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MAX_KEY_BYTES) {
            throw CredentialException(CredentialException.Code.INVALID_INPUT)
        }
        return bytes
    }

    private fun canonicalId(id: String): String {
        if (!UUID_PATTERN.matches(id)) {
            throw CredentialException(CredentialException.Code.INVALID_INPUT)
        }
        return try {
            val uuid = UUID.fromString(id)
            if (uuid.toString() != id) throw CredentialException(CredentialException.Code.INVALID_INPUT)
            id
        } catch (_: IllegalArgumentException) {
            throw CredentialException(CredentialException.Code.INVALID_INPUT)
        }
    }

    private fun encrypt(keyBytes: ByteArray, id: String, provider: Provider, region: Region): EncryptedCredential {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key(createIfMissing = true))
            cipher.updateAAD(aad(id, provider, region))
            val iv = cipher.iv
            if (iv.size != IV_BYTES) throw CredentialException(CredentialException.Code.STORAGE)
            return EncryptedCredential(iv, cipher.doFinal(keyBytes))
        } catch (_: UserNotAuthenticatedException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: UnrecoverableKeyException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: ProviderException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: InvalidKeyException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: GeneralSecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
    }

    private fun decrypt(stored: StoredCredential, id: String, provider: Provider, region: Region): ByteArray {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(createIfMissing = false), GCMParameterSpec(TAG_BITS, stored.iv))
            cipher.updateAAD(aad(id, provider, region))
            return cipher.doFinal(stored.ciphertext)
        } catch (_: AEADBadTagException) {
            throw CredentialException(CredentialException.Code.CORRUPT)
        } catch (_: BadPaddingException) {
            throw CredentialException(CredentialException.Code.CORRUPT)
        } catch (_: IllegalBlockSizeException) {
            throw CredentialException(CredentialException.Code.CORRUPT)
        } catch (_: UserNotAuthenticatedException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: UnrecoverableKeyException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: ProviderException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: InvalidKeyException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: GeneralSecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
    }

    private fun key(createIfMissing: Boolean): SecretKey {
        val store = try {
            KeyStore.getInstance(KEYSTORE).also { it.load(null) }
        } catch (_: GeneralSecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        } catch (_: IOException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
        try {
            if (store.containsAlias(KEY_ALIAS)) {
                val existing = store.getKey(KEY_ALIAS, null)
                if (existing is SecretKey) return existing
                throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
            }
            if (!createIfMissing) throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: UserNotAuthenticatedException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: UnrecoverableKeyException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: ProviderException) {
            throw CredentialException(CredentialException.Code.LOCKED_OR_INVALIDATED)
        } catch (_: KeyStoreException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        } catch (_: GeneralSecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
        return try {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(KEY_SIZE_BITS)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .setUserAuthenticationRequired(false)
                        .build(),
                )
                generateKey()
            }
        } catch (_: GeneralSecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        } catch (_: ProviderException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
    }

    private fun aad(id: String, provider: Provider, region: Region): ByteArray =
        "$AAD_PREFIX\u0000$id\u0000${provider.name}\u0000${region.name}".toByteArray(StandardCharsets.UTF_8)

    private fun encodeBlob(
        id: String,
        provider: Provider,
        region: Region,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val output = ByteArrayOutputStream(MAX_BLOB_BYTES)
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(FORMAT_VERSION)
            data.writeInt(id.length)
            data.write(id.toByteArray(StandardCharsets.UTF_8))
            data.writeInt(provider.ordinal)
            data.writeInt(region.ordinal)
            data.writeInt(iv.size)
            data.write(iv)
            data.writeInt(ciphertext.size)
            data.write(ciphertext)
        }
        return output.toByteArray()
    }

    private fun parseBlob(bytes: ByteArray): StoredCredential {
        if (bytes.isEmpty() || bytes.size > MAX_BLOB_BYTES) throw CredentialException(CredentialException.Code.CORRUPT)
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) corrupt()
                val idLength = input.readInt()
                if (idLength != UUID_LENGTH) corrupt()
                val idBytes = ByteArray(idLength)
                input.readFully(idBytes)
                val id = String(idBytes, StandardCharsets.UTF_8)
                if (runCatching { canonicalId(id) }.getOrNull() == null) corrupt()
                val provider = Provider.values().getOrNull(input.readInt()) ?: corrupt()
                val region = Region.values().getOrNull(input.readInt()) ?: corrupt()
                val ivLength = input.readInt()
                if (ivLength != IV_BYTES) corrupt()
                val iv = ByteArray(ivLength)
                input.readFully(iv)
                val ciphertextLength = input.readInt()
                if (ciphertextLength !in TAG_BYTES..MAX_CIPHERTEXT_BYTES) corrupt()
                val ciphertext = ByteArray(ciphertextLength)
                input.readFully(ciphertext)
                if (input.read() != -1) corrupt()
                return StoredCredential(id, provider, region, iv, ciphertext)
            }
        } catch (_: CredentialException) {
            throw CredentialException(CredentialException.Code.CORRUPT)
        } catch (_: EOFException) {
            throw CredentialException(CredentialException.Code.CORRUPT)
        } catch (_: IOException) {
            throw CredentialException(CredentialException.Code.CORRUPT)
        } catch (_: RuntimeException) {
            throw CredentialException(CredentialException.Code.CORRUPT)
        }
    }

    private fun readBlob(file: AtomicFile): ByteArray {
        if (!hasStoredFile(file)) throw CredentialException(CredentialException.Code.NOT_FOUND)
        try {
            file.openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(READ_BUFFER_BYTES)
                var size = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    size += count
                    if (size > MAX_BLOB_BYTES) throw CredentialException(CredentialException.Code.CORRUPT)
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            }
        } catch (_: FileNotFoundException) {
            throw CredentialException(CredentialException.Code.NOT_FOUND)
        } catch (_: CredentialException) {
            throw CredentialException(CredentialException.Code.CORRUPT)
        } catch (_: IOException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        } catch (_: SecurityException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        } catch (_: RuntimeException) {
            throw CredentialException(CredentialException.Code.STORAGE)
        }
    }

    private fun writeBlob(file: AtomicFile, bytes: ByteArray) {
        var output: FileOutputStream? = null
        try {
            output = file.startWrite()
            output.write(bytes)
            output.flush()
            file.finishWrite(output)
            output = null
        } catch (_: IOException) {
            output?.let { failWrite(file, it) }
            throw CredentialException(CredentialException.Code.STORAGE)
        } catch (_: SecurityException) {
            output?.let { failWrite(file, it) }
            throw CredentialException(CredentialException.Code.STORAGE)
        } catch (_: RuntimeException) {
            output?.let { failWrite(file, it) }
            throw CredentialException(CredentialException.Code.STORAGE)
        }
    }

    private fun failWrite(file: AtomicFile, output: FileOutputStream) {
        try {
            file.failWrite(output)
        } catch (_: IOException) {
            // Keep the typed storage result from the original write failure.
        }
    }

    private fun corrupt(): Nothing = throw CredentialException(CredentialException.Code.CORRUPT)

    private data class EncryptedCredential(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    private data class StoredCredential(
        val id: String,
        val provider: Provider,
        val region: Region,
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    private companion object {
        const val DIRECTORY_NAME = "credentials"
        const val FILE_SUFFIX = ".cred"
        const val KEY_ALIAS = "sourcescribe.credentials.v1"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AAD_PREFIX = "SourceScribe credential v1"
        const val MAGIC = 0x53534331
        const val FORMAT_VERSION = 1
        const val KEY_SIZE_BITS = 256
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val IV_BYTES = 12
        const val UUID_LENGTH = 36
        const val MAX_KEY_BYTES = 4096
        const val MAX_CIPHERTEXT_BYTES = MAX_KEY_BYTES + TAG_BYTES
        const val MAX_BLOB_BYTES = 16 * 1024
        const val READ_BUFFER_BYTES = 4096
        val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        val PROCESS_LOCK = Any()
    }
}
