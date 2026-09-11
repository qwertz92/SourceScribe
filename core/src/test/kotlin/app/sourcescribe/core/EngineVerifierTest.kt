package app.sourcescribe.core

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class EngineVerifierTest {
    @Test
    fun verifiesOfficialFixtureAndMetadata() {
        val artifact = File(requireNotNull(System.getProperty("sourcescribe.engineFixture")))
        val verified = EngineVerifier.verify(artifact, resource("SHA2-256SUMS"), resource("SHA2-256SUMS.sig"))

        assertEquals("1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6", verified.sha256)
        assertEquals("2026.08.19", verified.version)
        assertEquals("0.8.0", verified.ejsVersion)
        assertEquals("stable", verified.channel)
        assertEquals("594bd50c2c78ac432f81600d309fdc4e0a92d82c", verified.gitHead)
    }

    @Test
    fun modifiedSignedManifestFailsBeforeHashUse() {
        val checksums = resource("SHA2-256SUMS").also { it[0] = (it[0].toInt() xor 1).toByte() }
        val failure = assertThrows(EngineVerificationException::class.java) {
            EngineVerifier.verify(File("missing-engine"), checksums, resource("SHA2-256SUMS.sig"))
        }
        assertEquals(EngineVerificationCode.SIGNATURE, failure.code)
    }

    @Test
    fun duplicateDetachedSignaturesAreRejected() {
        val signature = resource("SHA2-256SUMS.sig")
        val failure = assertThrows(EngineVerificationException::class.java) {
            EngineVerifier.verify(
                File("missing-engine"),
                resource("SHA2-256SUMS"),
                signature + signature,
            )
        }
        assertEquals(EngineVerificationCode.SIGNATURE, failure.code)
    }

    @Test
    fun inputLimitsAreEnforcedBeforeCryptographicWork() {
        val checksumFailure = assertThrows(EngineVerificationException::class.java) {
            EngineVerifier.verify(
                File("missing-engine"),
                ByteArray(EngineVerifier.MAX_CHECKSUM_BYTES + 1),
                ByteArray(0),
            )
        }
        assertEquals(EngineVerificationCode.HASH, checksumFailure.code)

        val signatureFailure = assertThrows(EngineVerificationException::class.java) {
            EngineVerifier.verify(
                File("missing-engine"),
                ByteArray(0),
                ByteArray(EngineVerifier.MAX_SIGNATURE_BYTES + 1),
            )
        }
        assertEquals(EngineVerificationCode.SIGNATURE, signatureFailure.code)
    }

    @Test
    fun safeZipappInspectionExtractsOnlyFixedMetadata() {
        val archive = zipFile(
            VERSION_PATH to versionSource(),
            EJS_VERSION_PATH to ejsSource(),
            "yt_dlp/unused.py" to "# data\n".toByteArray(),
        )
        try {
            val metadata = EngineVerifier.inspectArchive(archive)
            assertEquals("2026.08.19", metadata.version)
            assertEquals("0.8.0", metadata.ejsVersion)
            assertEquals("stable", metadata.channel)
            assertEquals("594bd50c2c78ac432f81600d309fdc4e0a92d82c", metadata.gitHead)
        } finally {
            assertTrue(archive.delete())
        }
    }

    @Test
    fun missingEjsMetadataRequiresAppUpdate() {
        val archive = zipFile(VERSION_PATH to versionSource())
        try {
            val failure = assertThrows(EngineVerificationException::class.java) {
                EngineVerifier.inspectArchive(archive)
            }
            assertEquals(EngineVerificationCode.REQUIRES_APP_UPDATE, failure.code)
        } finally {
            assertTrue(archive.delete())
        }
    }

    @Test
    fun unsupportedChannelAndUnsafePathAreRejected() {
        val badChannel = zipFile(
            VERSION_PATH to String(versionSource(), Charsets.UTF_8).replace("'stable'", "'master'").toByteArray(),
            EJS_VERSION_PATH to ejsSource(),
        )
        try {
            val channelFailure = assertThrows(EngineVerificationException::class.java) {
                EngineVerifier.inspectArchive(badChannel)
            }
            assertEquals(EngineVerificationCode.COMPATIBILITY, channelFailure.code)
        } finally {
            assertTrue(badChannel.delete())
        }

        val unsafePath = zipFile(
            VERSION_PATH to versionSource(),
            EJS_VERSION_PATH to ejsSource(),
            "../outside" to byteArrayOf(1),
        )
        try {
            val pathFailure = assertThrows(EngineVerificationException::class.java) {
                EngineVerifier.inspectArchive(unsafePath)
            }
            assertEquals(EngineVerificationCode.ARCHIVE, pathFailure.code)
        } finally {
            assertTrue(unsafePath.delete())
        }
    }

    @Test
    fun oversizedEntriesAreRejected() {
        val oversized = zipFile(
            VERSION_PATH to versionSource(),
            EJS_VERSION_PATH to ejsSource(),
            "too-large.bin" to ByteArray(EngineVerifier.MAX_ENTRY_BYTES + 1),
        )
        try {
            val sizeFailure = assertThrows(EngineVerificationException::class.java) {
                EngineVerifier.inspectArchive(oversized)
            }
            assertEquals(EngineVerificationCode.ARCHIVE, sizeFailure.code)
        } finally {
            assertTrue(oversized.delete())
        }
    }

    @Test
    fun anArchiveOfTooManyEntriesIsRejectedBeforeAnyOfThemIsRead() {
        // Until round 11 nothing exercised this limit, or the one below it. Both are the part of the
        // signed-update path that stops an archive from being enormous in the two ways a small file can be:
        // by holding a great many members, or by unpacking to far more than it weighs. That the mechanism
        // works was never in doubt and never shown either, which are different things.
        //
        // The inputs are built from the production numbers on purpose. What is asked here is whether the
        // check fires at the size the program actually enforces; whether that size is the intended one is
        // asked in `StatedNumbersTest`, where the number is written out.
        val entries = Array(EngineVerifier.MAX_ARCHIVE_ENTRIES + 1) { "filler-$it.bin" to ByteArray(1) }
        val crowded = zipFile(VERSION_PATH to versionSource(), EJS_VERSION_PATH to ejsSource(), *entries)
        try {
            val failure = assertThrows(EngineVerificationException::class.java) {
                EngineVerifier.inspectArchive(crowded)
            }
            assertEquals(EngineVerificationCode.ARCHIVE, failure.code)
            // The name of this test is a claim, and it is the one the negative control had to settle: the
            // count is read from the archive's end record and refused there, before a single entry is
            // opened. Two later guards repeat it while the entries are walked; disabling only those two
            // leaves this test green, which is how the end-record check was found to be the one that fires.
            assertEquals("ARCHIVE: unsupported archive layout", failure.message)
        } finally {
            assertTrue(crowded.delete())
        }
    }

    @Test
    fun anArchiveThatUnpacksPastTheTotalIsRejectedEvenWhenEveryEntryFits() {
        // Each member stays inside the per-entry limit, so only the running total can catch this one. Nine
        // of them at eight mebibytes is seventy-two, against a ceiling of sixty-four. The bytes are zeros
        // and therefore compress to almost nothing, which is the whole point of the attack being guarded.
        val filler = ByteArray(EngineVerifier.MAX_ENTRY_BYTES)
        val entries = Array(9) { "part-$it.bin" to filler }
        val inflated = zipFile(VERSION_PATH to versionSource(), EJS_VERSION_PATH to ejsSource(), *entries)
        try {
            val failure = assertThrows(EngineVerificationException::class.java) {
                EngineVerifier.inspectArchive(inflated)
            }
            assertEquals(EngineVerificationCode.ARCHIVE, failure.code)
        } finally {
            assertTrue(inflated.delete())
        }
    }

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/engine/$name")).use { it.readBytes() }

    private fun versionSource(): ByteArray = """
        # generated test metadata
        __version__ = '2026.08.19'
        RELEASE_GIT_HEAD = '594bd50c2c78ac432f81600d309fdc4e0a92d82c'
        CHANNEL = 'stable'
    """.trimIndent().toByteArray()

    private fun ejsSource(): ByteArray = """
        __version__ = version = '0.8.0'
    """.trimIndent().toByteArray()

    private fun zipFile(vararg entries: Pair<String, ByteArray>): File {
        val file = File.createTempFile("sourcescribe-engine-", ".zip")
        FileOutputStream(file).use { output ->
            output.write("#!/usr/bin/env python3\n".toByteArray())
            ZipOutputStream(output).use { zip ->
                for ((name, bytes) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
        return file
    }

    private companion object {
        const val VERSION_PATH = "yt_dlp/version.py"
        const val EJS_VERSION_PATH = "yt_dlp_ejs/_version.py"
    }
}
