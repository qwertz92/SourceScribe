package app.sourcescribe.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider

enum class EngineVerificationCode {
    SIGNATURE,
    HASH,
    ARCHIVE,
    COMPATIBILITY,
    REQUIRES_APP_UPDATE,
}

class EngineVerificationException(
    val code: EngineVerificationCode,
    detail: String,
    cause: Throwable? = null,
) : Exception("${code.name}: $detail", cause)

data class VerifiedEngine(
    val sha256: String,
    val version: String,
    val ejsVersion: String,
    val channel: String,
    val gitHead: String?,
)

/**
 * Verifies the signed upstream checksum manifest before inspecting a yt-dlp
 * zipapp. Inspection only reads bounded entries; it never extracts or runs
 * archive content. Runtime/Python compatibility remains the manager's check.
 */
object EngineVerifier {
    const val MAX_CHECKSUM_BYTES = 64 * 1024
    const val MAX_SIGNATURE_BYTES = 32 * 1024
    const val MAX_ARTIFACT_BYTES = 16 * 1024 * 1024
    const val MAX_ARCHIVE_ENTRIES = 10_000
    const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    const val MAX_TOTAL_UNPACKED_BYTES = 64 * 1024 * 1024

    private const val KEY_RESOURCE = "/yt-dlp-release-key.asc"
    private const val EXPECTED_FINGERPRINT = "AC0CBBE6848D6A873464AF4E57CF65933B5A7581"
    private const val ASSET_NAME = "yt-dlp"
    private const val VERSION_PATH = "yt_dlp/version.py"
    private const val EJS_VERSION_PATH = "yt_dlp_ejs/_version.py"
    private const val ZIP_EOCD_SIGNATURE = 0x06054b50L
    private const val ZIP_CENTRAL_SIGNATURE = 0x02014b50L
    private const val ZIP_EOCD_BYTES = 22
    private const val ZIP_CENTRAL_BYTES = 46
    private const val ZIP_MAX_COMMENT_BYTES = 65_535
    private const val IO_BUFFER_BYTES = 32 * 1024
    private const val UNIX_OS = 3
    private const val UNIX_SYMLINK_MODE = 0xA000L

    private val trustedFingerprint = hexBytes(EXPECTED_FINGERPRINT)
    private val checksumLine = Regex("^([0-9A-Fa-f]{64})[ \\t]{2,}([^\\r\\n]+)$")
    private val assignmentValue = Regex("[A-Za-z0-9][A-Za-z0-9._+~-]{0,63}")
    private val gitHeadValue = Regex("[0-9A-Fa-f]{40}")

    /** Verifies the fixed-key signature, signed hash manifest, archive, and metadata. */
    fun verify(artifact: File, checksums: ByteArray, signature: ByteArray): VerifiedEngine {
        if (checksums.size > MAX_CHECKSUM_BYTES) {
            fail(EngineVerificationCode.HASH, "checksum manifest exceeds limit")
        }
        if (signature.size > MAX_SIGNATURE_BYTES) {
            fail(EngineVerificationCode.SIGNATURE, "signature exceeds limit")
        }

        // Do not parse or use a checksum until its detached signature is trusted.
        verifyDetachedSignature(checksums, signature)
        val expectedHash = parseExpectedHash(checksums)
        val initialHash = hashArtifact(artifact)
        if (initialHash != expectedHash) {
            fail(EngineVerificationCode.HASH, "artifact hash mismatch")
        }

        val metadata = inspectArchive(artifact)

        // A staged file must not change between hashing and inspection.
        val finalHash = hashArtifact(artifact)
        if (finalHash != initialHash) {
            fail(EngineVerificationCode.HASH, "artifact changed during inspection")
        }
        return VerifiedEngine(finalHash, metadata.version, metadata.ejsVersion, metadata.channel, metadata.gitHead)
    }

    /** Internal seam for hostile archive tests which deliberately bypass signature responsibility. */
    internal fun inspectArchive(artifact: File): EngineArchiveMetadata {
        val bytes = readArchiveBytes(artifact)
        val prefixBytes = inspectCentralDirectory(bytes)
        // Android ZipFile rejects Python zipapps with a shebang. Inspect a temporary
        // ZIP view; the signed original remains the only persisted/executed artifact.
        var inspectionFile = artifact
        try {
            if (prefixBytes != 0) {
                val prefix = "#!/usr/bin/env python3\n".toByteArray(StandardCharsets.US_ASCII)
                if (prefixBytes != prefix.size || !bytes.copyOfRange(0, prefixBytes).contentEquals(prefix)) {
                    fail(EngineVerificationCode.REQUIRES_APP_UPDATE, "unsupported zipapp prefix")
                }
                inspectionFile = File.createTempFile(".engine-inspect-", ".zip", artifact.absoluteFile.parentFile)
                inspectionFile.outputStream().use { it.write(bytes, prefixBytes, bytes.size - prefixBytes) }
            }
            ZipFile(inspectionFile).use { zip ->
                if (zip.size() > MAX_ARCHIVE_ENTRIES) {
                    fail(EngineVerificationCode.ARCHIVE, "archive entry count exceeds limit")
                }

                val entries = zip.entries()
                val names = HashSet<String>()
                val metadataBytes = HashMap<String, ByteArray>()
                var entryCount = 0
                var totalDeclared = 0L
                var totalActual = 0L
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    entryCount++
                    if (entryCount > MAX_ARCHIVE_ENTRIES || !names.add(entry.name)) {
                        fail(EngineVerificationCode.ARCHIVE, "duplicate or excessive archive entries")
                    }
                    validateEntryName(entry)

                    val declared = entry.size
                    if (declared < 0L || declared > MAX_ENTRY_BYTES ||
                        totalDeclared > MAX_TOTAL_UNPACKED_BYTES - declared
                    ) {
                        fail(EngineVerificationCode.ARCHIVE, "archive declared size exceeds limit")
                    }
                    totalDeclared += declared

                    val method = entry.method
                    if (method != ZipEntry.STORED && method != ZipEntry.DEFLATED) {
                        fail(EngineVerificationCode.ARCHIVE, "unsupported archive compression method")
                    }
                    if (entry.compressedSize < 0L) {
                        fail(EngineVerificationCode.ARCHIVE, "unknown archive compressed size")
                    }

                    val capture = entry.name == VERSION_PATH || entry.name == EJS_VERSION_PATH
                    val output = if (capture) ByteArrayOutputStream(declared.toInt()) else null
                    var actual = 0L
                    zip.getInputStream(entry).use { input ->
                        val buffer = ByteArray(IO_BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            val count = read.toLong()
                            if (actual > MAX_ENTRY_BYTES - count || totalActual > MAX_TOTAL_UNPACKED_BYTES - count) {
                                fail(EngineVerificationCode.ARCHIVE, "archive decompression exceeds limit")
                            }
                            actual += count
                            totalActual += count
                            if (output != null) output.write(buffer, 0, read)
                        }
                    }
                    if (actual != declared) {
                        fail(EngineVerificationCode.ARCHIVE, "archive size differs from declaration")
                    }
                    if (output != null) metadataBytes[entry.name] = output.toByteArray()
                }

                if (entryCount != zip.size()) {
                    fail(EngineVerificationCode.ARCHIVE, "archive entry count changed while reading")
                }
                return parseMetadata(metadataBytes)
            }
        } catch (failure: EngineVerificationException) {
            throw failure
        } catch (failure: IOException) {
            fail(EngineVerificationCode.ARCHIVE, "archive could not be inspected", failure)
        } catch (failure: SecurityException) {
            fail(EngineVerificationCode.ARCHIVE, "archive could not be inspected", failure)
        } finally {
            if (inspectionFile != artifact) {
                try {
                    if (inspectionFile.exists() && !inspectionFile.delete()) {
                        fail(EngineVerificationCode.ARCHIVE, "temporary inspection view could not be removed")
                    }
                } catch (failure: SecurityException) {
                    fail(EngineVerificationCode.ARCHIVE, "temporary inspection view cleanup denied", failure)
                }
            }
        }
    }

    private fun verifyDetachedSignature(payload: ByteArray, encodedSignature: ByteArray) {
        val provider = BouncyCastleProvider()
        try {
            val key = trustedPublicKey(provider)
            val decoder = PGPUtil.getDecoderStream(ByteArrayInputStream(encodedSignature))
            val factory = PGPObjectFactory(decoder, JcaKeyFingerprintCalculator().setProvider(provider))
            factory.setThrowForUnknownCriticalPackets(true)
            val first = factory.nextObject()
            if (first !is PGPSignatureList || first.size() != 1 || factory.nextObject() != null) {
                fail(EngineVerificationCode.SIGNATURE, "signature must contain exactly one detached signature")
            }

            val pgpSignature = first.get(0)
            val issuer = pgpSignature.hashedSubPackets?.issuerFingerprint?.fingerprint
            if (pgpSignature.version != 4 ||
                pgpSignature.signatureType != org.bouncycastle.openpgp.PGPSignature.BINARY_DOCUMENT ||
                pgpSignature.keyAlgorithm != PublicKeyAlgorithmTags.RSA_GENERAL ||
                pgpSignature.hashAlgorithm !in setOf(
                    HashAlgorithmTags.SHA256,
                    HashAlgorithmTags.SHA384,
                    HashAlgorithmTags.SHA512,
                ) ||
                pgpSignature.keyID != key.keyID ||
                issuer == null || !MessageDigest.isEqual(issuer, trustedFingerprint)
            ) {
                fail(EngineVerificationCode.SIGNATURE, "signature metadata is not trusted")
            }

            pgpSignature.init(JcaPGPContentVerifierBuilderProvider().setProvider(provider), key)
            pgpSignature.update(payload)
            if (!pgpSignature.verify()) {
                fail(EngineVerificationCode.SIGNATURE, "signature verification failed")
            }
        } catch (failure: EngineVerificationException) {
            throw failure
        } catch (failure: Exception) {
            fail(EngineVerificationCode.SIGNATURE, "signature is malformed or unverifiable", failure)
        }
    }

    private fun trustedPublicKey(provider: BouncyCastleProvider): PGPPublicKey {
        val input = EngineVerifier::class.java.getResourceAsStream(KEY_RESOURCE)
            ?: fail(EngineVerificationCode.SIGNATURE, "trusted key resource is missing")
        input.use { stream ->
            val decoder = PGPUtil.getDecoderStream(stream)
            val rings = PGPPublicKeyRingCollection(
                decoder,
                JcaKeyFingerprintCalculator().setProvider(provider),
            )
            val matches = ArrayList<PGPPublicKey>()
            val ringIterator = rings.getKeyRings()
            while (ringIterator.hasNext()) {
                val keyIterator = ringIterator.next().getPublicKeys()
                while (keyIterator.hasNext()) {
                    val key = keyIterator.next()
                    if (MessageDigest.isEqual(key.fingerprint, trustedFingerprint)) matches += key
                }
            }
            if (matches.size != 1) {
                fail(EngineVerificationCode.SIGNATURE, "trusted key fingerprint is not exact")
            }
            val key = matches.single()
            if (key.algorithm != PublicKeyAlgorithmTags.RSA_GENERAL || key.bitStrength < 4096) {
                fail(EngineVerificationCode.SIGNATURE, "trusted key is not the expected RSA key")
            }
            return key
        }
    }

    private fun parseExpectedHash(checksums: ByteArray): String {
        val text = decodeUtf8(checksums, EngineVerificationCode.HASH)
        val names = HashSet<String>()
        var expected: String? = null
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val match = checksumLine.matchEntire(line)
                ?: fail(EngineVerificationCode.HASH, "checksum manifest line is malformed")
            val name = match.groupValues[2]
            if (name.any { it.code !in 0x21..0x7E } || !names.add(name)) {
                fail(EngineVerificationCode.HASH, "checksum manifest name is malformed or duplicated")
            }
            if (name == ASSET_NAME) {
                if (expected != null) fail(EngineVerificationCode.HASH, "checksum for artifact is duplicated")
                expected = match.groupValues[1].lowercase(Locale.ROOT)
            }
        }
        return expected ?: fail(EngineVerificationCode.HASH, "checksum for artifact is missing")
    }

    private fun hashArtifact(artifact: File): String {
        if (!artifact.isFile) fail(EngineVerificationCode.HASH, "artifact is not a regular file")
        val digest = try {
            MessageDigest.getInstance("SHA-256", BouncyCastleProvider())
        } catch (failure: Exception) {
            fail(EngineVerificationCode.HASH, "SHA-256 is unavailable", failure)
        }
        var total = 0L
        try {
            artifact.inputStream().use { input ->
                val buffer = ByteArray(IO_BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    val count = read.toLong()
                    if (total > MAX_ARTIFACT_BYTES - count) {
                        fail(EngineVerificationCode.HASH, "artifact exceeds size limit")
                    }
                    digest.update(buffer, 0, read)
                    total += count
                }
            }
        } catch (failure: EngineVerificationException) {
            throw failure
        } catch (failure: IOException) {
            fail(EngineVerificationCode.HASH, "artifact could not be read", failure)
        } catch (failure: SecurityException) {
            fail(EngineVerificationCode.HASH, "artifact could not be read", failure)
        }
        if (total == 0L) fail(EngineVerificationCode.HASH, "artifact is empty")
        return digest.digest().hex()
    }

    private fun readArchiveBytes(artifact: File): ByteArray {
        if (!artifact.isFile) fail(EngineVerificationCode.ARCHIVE, "artifact is not a regular file")
        val declared = artifact.length()
        if (declared <= 0L || declared > MAX_ARTIFACT_BYTES) {
            fail(EngineVerificationCode.ARCHIVE, "archive exceeds size limit")
        }
        val output = ByteArrayOutputStream(declared.toInt())
        try {
            artifact.inputStream().use { input ->
                val buffer = ByteArray(IO_BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    val count = read.toLong()
                    if (total > MAX_ARTIFACT_BYTES - count) {
                        fail(EngineVerificationCode.ARCHIVE, "archive exceeds size limit")
                    }
                    output.write(buffer, 0, read)
                    total += count
                }
            }
        } catch (failure: EngineVerificationException) {
            throw failure
        } catch (failure: IOException) {
            fail(EngineVerificationCode.ARCHIVE, "archive could not be read", failure)
        } catch (failure: SecurityException) {
            fail(EngineVerificationCode.ARCHIVE, "archive could not be read", failure)
        }
        return output.toByteArray()
    }

    private fun inspectCentralDirectory(bytes: ByteArray): Int {
        if (bytes.size < ZIP_EOCD_BYTES) fail(EngineVerificationCode.ARCHIVE, "archive is truncated")
        val minimum = maxOf(0, bytes.size - ZIP_EOCD_BYTES - ZIP_MAX_COMMENT_BYTES)
        val eocd = (bytes.size - ZIP_EOCD_BYTES downTo minimum).firstOrNull { readUnsignedInt(bytes, it) == ZIP_EOCD_SIGNATURE }
            ?: fail(EngineVerificationCode.ARCHIVE, "archive end record is missing")
        val commentLength = readUnsignedShort(bytes, eocd + 20)
        if (eocd + ZIP_EOCD_BYTES + commentLength != bytes.size) {
            fail(EngineVerificationCode.ARCHIVE, "archive has trailing data")
        }
        val entries = readUnsignedShort(bytes, eocd + 10)
        val entriesOnDisk = readUnsignedShort(bytes, eocd + 8)
        val centralSize = readUnsignedInt(bytes, eocd + 12)
        val centralOffset = readUnsignedInt(bytes, eocd + 16)
        if (readUnsignedShort(bytes, eocd + 4) != 0 || readUnsignedShort(bytes, eocd + 6) != 0 ||
            entriesOnDisk != entries ||
            entries == 0xffff || centralSize == 0xffffffffL || centralOffset == 0xffffffffL ||
            entries > MAX_ARCHIVE_ENTRIES
        ) {
            fail(EngineVerificationCode.ARCHIVE, "unsupported archive layout")
        }
        val centralStart = eocd.toLong() - centralSize
        if (centralStart < 0L || centralStart < centralOffset || centralStart + centralSize != eocd.toLong()) {
            fail(EngineVerificationCode.ARCHIVE, "archive central directory is invalid")
        }
        var position = centralStart.toInt()
        val end = eocd
        repeat(entries) {
            if (position < 0 || position > end - ZIP_CENTRAL_BYTES || readUnsignedInt(bytes, position) != ZIP_CENTRAL_SIGNATURE) {
                fail(EngineVerificationCode.ARCHIVE, "archive central entry is malformed")
            }
            val nameLength = readUnsignedShort(bytes, position + 28)
            val extraLength = readUnsignedShort(bytes, position + 30)
            val entryCommentLength = readUnsignedShort(bytes, position + 32)
            val recordLength = ZIP_CENTRAL_BYTES + nameLength + extraLength + entryCommentLength
            if (recordLength < ZIP_CENTRAL_BYTES || recordLength > end - position) {
                fail(EngineVerificationCode.ARCHIVE, "archive central entry is truncated")
            }
            val madeBy = readUnsignedShort(bytes, position + 4)
            val externalAttributes = readUnsignedInt(bytes, position + 38)
            val unixMode = externalAttributes ushr 16
            val unixSymlink = (madeBy ushr 8) == UNIX_OS && (unixMode and 0xF000L) == UNIX_SYMLINK_MODE
            val highSymlink = (externalAttributes and 0xF0000000L) == 0xA0000000L
            if (unixSymlink || highSymlink) {
                fail(EngineVerificationCode.ARCHIVE, "archive symlink entry is not allowed")
            }
            position += recordLength
        }
        if (position != end) fail(EngineVerificationCode.ARCHIVE, "archive central entry count is invalid")
        return (centralStart - centralOffset).toInt()
    }

    private fun validateEntryName(entry: ZipEntry) {
        val name = entry.name
        if (name.isEmpty() || name.any { it == '\u0000' || it == '\\' || it.code < 0x20 || it.code == 0x7F }) {
            fail(EngineVerificationCode.ARCHIVE, "archive entry name is invalid")
        }
        if (name.startsWith('/') || name.matches(Regex("^[A-Za-z]:.*"))) {
            fail(EngineVerificationCode.ARCHIVE, "archive entry path is absolute")
        }
        val parts = name.split('/')
        if (parts.any { it == ".." || it == "." || it.isEmpty() && it != parts.last() } ||
            name.endsWith('/') && !entry.isDirectory
        ) {
            fail(EngineVerificationCode.ARCHIVE, "archive entry path traverses directories")
        }
    }

    private fun parseMetadata(entries: Map<String, ByteArray>): EngineArchiveMetadata {
        val versionSource = entries[VERSION_PATH]
            ?: fail(EngineVerificationCode.COMPATIBILITY, "yt-dlp version metadata is missing")
        val ejsSource = entries[EJS_VERSION_PATH]
            ?: fail(EngineVerificationCode.REQUIRES_APP_UPDATE, "yt-dlp EJS metadata is missing")
        val versionText = decodeUtf8(versionSource, EngineVerificationCode.COMPATIBILITY)
        val ejsText = decodeUtf8(ejsSource, EngineVerificationCode.COMPATIBILITY)
        val version = requireAssignment(versionText, "__version__")
        val channel = requireAssignment(versionText, "CHANNEL")
        if (channel !in setOf("stable", "nightly")) {
            fail(EngineVerificationCode.COMPATIBILITY, "engine channel is unsupported")
        }
        val gitHead = if (assignmentExists(versionText, "RELEASE_GIT_HEAD")) {
            val value = requireAssignment(versionText, "RELEASE_GIT_HEAD")
            if (!gitHeadValue.matches(value)) {
                fail(EngineVerificationCode.COMPATIBILITY, "release git head is malformed")
            }
            value
        } else {
            null
        }
        val ejsVersion = Regex("""(?m)^[ \t]*__version__[ \t]*=[ \t]*version[ \t]*=[ \t]*(['"])([A-Za-z0-9._+~-]{1,64})\1[ \t]*(?:#.*)?$""")
            .findAll(ejsText).map { it.groupValues[2] }.toList().let { values ->
                if (values.size != 1) fail(EngineVerificationCode.COMPATIBILITY, "EJS version metadata is malformed")
                values.single()
            }
        if (!assignmentValue.matches(version) || !assignmentValue.matches(channel) || !assignmentValue.matches(ejsVersion)) {
            fail(EngineVerificationCode.COMPATIBILITY, "engine metadata value is malformed")
        }
        return EngineArchiveMetadata(version, ejsVersion, channel, gitHead)
    }

    private fun requireAssignment(source: String, name: String): String {
        val regex = Regex("""(?m)^[ \t]*${Regex.escape(name)}[ \t]*=[ \t]*(['"])([A-Za-z0-9._+~-]{1,64})\1[ \t]*(?:#.*)?$""")
        val values = regex.findAll(source).map { it.groupValues[2] }.toList()
        if (values.size != 1) fail(EngineVerificationCode.COMPATIBILITY, "engine metadata assignment is missing or duplicated")
        return values.single()
    }

    private fun assignmentExists(source: String, name: String): Boolean =
        Regex("""(?m)^[ \t]*${Regex.escape(name)}[ \t]*=""").containsMatchIn(source)

    private fun decodeUtf8(bytes: ByteArray, code: EngineVerificationCode): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (failure: CharacterCodingException) {
        fail(code, "metadata is not valid UTF-8", failure)
    }

    private fun ByteArray.hex(): String = buildString(size * 2) {
        for (byte in this@hex) {
            val value = byte.toInt() and 0xff
            append("0123456789abcdef"[value ushr 4])
            append("0123456789abcdef"[value and 0x0f])
        }
    }

    private fun hexBytes(value: String): ByteArray = ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

    private fun readUnsignedShort(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 2 > bytes.size) fail(EngineVerificationCode.ARCHIVE, "archive record is truncated")
        return (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) fail(EngineVerificationCode.ARCHIVE, "archive record is truncated")
        return (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)
    }

    private fun fail(code: EngineVerificationCode, detail: String, cause: Throwable? = null): Nothing =
        throw EngineVerificationException(code, detail, cause)
}

internal data class EngineArchiveMetadata(
    val version: String,
    val ejsVersion: String,
    val channel: String,
    val gitHead: String?,
)
