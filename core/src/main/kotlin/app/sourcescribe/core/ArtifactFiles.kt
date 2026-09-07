package app.sourcescribe.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArtifactFilesException(
    val reason: String,
    detail: String,
    cause: Throwable? = null,
) : IOException("$reason: $detail", cause) {
    companion object {
        const val INVALID_ARTIFACT_ID = "INVALID_ARTIFACT_ID"
        const val INVALID_ID = INVALID_ARTIFACT_ID
        const val INVALID_RAW_EXTENSION = "INVALID_RAW_EXTENSION"
        const val RAW_EXTENSION_WITHOUT_DATA = "RAW_EXTENSION_WITHOUT_DATA"
        const val RAW_NOT_ALLOWED = "RAW_NOT_ALLOWED"
        const val RAW_NOT_RETAINED = RAW_NOT_ALLOWED
        const val RAW_REQUIRED = "RAW_REQUIRED"
        const val CANONICAL_TOO_LARGE = "CANONICAL_TOO_LARGE"
        const val RAW_TOO_LARGE = "RAW_TOO_LARGE"
        const val RAW_HASH_MISMATCH = "RAW_HASH_MISMATCH"
        const val CONFLICTING_CONTENT = "CONFLICTING_CONTENT"
        const val NOT_FOUND = "NOT_FOUND"
        const val INCOMPLETE_ARTIFACT = "INCOMPLETE_ARTIFACT"
        const val MALFORMED_CANONICAL = "MALFORMED_CANONICAL"
        const val CORRUPT_CANONICAL = "CORRUPT_CANONICAL"
        const val CORRUPTED_CANONICAL = CORRUPT_CANONICAL
        const val MALFORMED_METADATA = "MALFORMED_METADATA"
        const val MALFORMED_ARTIFACT = "MALFORMED_ARTIFACT"
        const val CORRUPT_RAW = "CORRUPT_RAW"
        const val SYMLINK_NOT_ALLOWED = "SYMLINK_NOT_ALLOWED"
        const val PATH_ESCAPE = "PATH_ESCAPE"
        const val STORAGE_FAILURE = "STORAGE_FAILURE"
    }
}

data class StoredArtifact(
    val artifactId: String,
    val sha256: String,
    val bytes: Long,
    val rawExtension: String? = null,
)

/**
 * Stores immutable, finalized transcript files below the app-private root.
 *
 * Every file is fsynced before its same-filesystem atomic rename. Java's
 * portable Android APIs do not expose directory fsync, so directory metadata
 * durability is not claimed here; the commit marker still publishes last and
 * does not make filesystem and Room operations one transaction.
 */
class ArtifactFiles(private val root: File) {
    companion object {
        const val MAX_CANONICAL_BYTES = 32 * 1024 * 1024
        const val MAX_RAW_BYTES = 16 * 1024 * 1024

        private const val CANONICAL_NAME = "transcript.json"
        private const val COMMIT_NAME = ".commit.json"
        private const val RAW_PREFIX = "raw."
        private const val MAX_METADATA_BYTES = 16 * 1024
        private val ARTIFACT_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val RAW_EXTENSIONS = setOf("srt", "vtt", "json3", "json", "txt")
    }

    @Serializable
    private data class CommitMetadata(
        val schemaVersion: Int = 1,
        val artifactId: String,
        val sha256: String,
        val bytes: Long,
        val rawExtension: String? = null,
        val rawSha256: String? = null,
        val rawBytes: Long? = null,
    )

    private data class Finalized(
        val document: TranscriptDocument,
        val metadata: CommitMetadata,
    )

    private val rootPath = root.toPath().toAbsolutePath().normalize()
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    // ponytail: one process-local lock keeps publication races simple; cross-process coordination belongs to JobCoordinator.
    @Synchronized
    fun write(document: TranscriptDocument, raw: ByteArray? = null, rawExtension: String? = null): StoredArtifact = storage {
        val artifactId = validateArtifactId(document.artifactId)
        val rawHash = raw?.let(::sha256)
        validateRawArguments(document, raw, rawExtension, rawHash)
        val canonical = encodeCanonical(document)
        if (canonical.size > MAX_CANONICAL_BYTES) {
            fail(ArtifactFilesException.CANONICAL_TOO_LARGE, "canonical transcript exceeds $MAX_CANONICAL_BYTES bytes")
        }
        val expectedHash = sha256(canonical)
        val existingDirectory = artifactDirectory(artifactId, create = false)
        val hasCommit = directoryExists(existingDirectory) && entryExists(existingDirectory.resolve(COMMIT_NAME))
        if (raw == null && document.acquisition.retainRaw && !hasCommit) {
            fail(ArtifactFilesException.RAW_REQUIRED, "raw data is required when retention is enabled")
        }
        val directory = artifactDirectory(artifactId, create = true)
        validateEntries(directory, expectedRawExtension = null, finalized = false)

        val commit = directory.resolve(COMMIT_NAME)
        if (entryExists(commit)) {
            val existingCanonical = readRegular(directory.resolve(CANONICAL_NAME), MAX_CANONICAL_BYTES, ArtifactFilesException.CANONICAL_TOO_LARGE)
            if (sha256(existingCanonical) != expectedHash) {
                fail(ArtifactFilesException.CONFLICTING_CONTENT, "artifact $artifactId is immutable")
            }
            val finalized = loadFinalized(directory, artifactId)
            requireSameRawBinding(finalized.metadata, raw, rawExtension, rawHash)
            return@storage storedArtifact(finalized)
        }

        val canonicalPath = directory.resolve(CANONICAL_NAME)
        if (entryExists(canonicalPath)) {
            val existingCanonical = readRegular(canonicalPath, MAX_CANONICAL_BYTES, ArtifactFilesException.CANONICAL_TOO_LARGE)
            if (sha256(existingCanonical) != expectedHash) {
                fail(ArtifactFilesException.CONFLICTING_CONTENT, "artifact $artifactId is immutable")
            }
            decodeCanonical(existingCanonical, artifactId)
        } else {
            atomicWrite(canonicalPath, canonical)
        }

        if (raw != null) {
            writeOrVerifyRaw(directory, rawExtension!!, raw)
        }
        val rawEntries = rawEntries(directory)
        if (raw == null && rawEntries.isNotEmpty()) {
            fail(ArtifactFilesException.MALFORMED_ARTIFACT, "uncommitted raw data has no controlled extension metadata")
        }
        if (raw != null && rawEntries.any { it.fileName.toString() != rawName(rawExtension!!) }) {
            fail(ArtifactFilesException.MALFORMED_ARTIFACT, "unexpected raw file in artifact $artifactId")
        }
        validateEntries(directory, expectedRawExtension = rawExtension, finalized = false)

        val metadata = CommitMetadata(
            artifactId = artifactId,
            sha256 = expectedHash,
            bytes = canonical.size.toLong(),
            rawExtension = rawExtension,
            rawSha256 = rawHash,
            rawBytes = raw?.size?.toLong(),
        )
        atomicWrite(commit, encodeMetadata(metadata))
        return@storage storedArtifact(loadFinalized(directory, artifactId))
    }

    @Synchronized
    fun read(id: String): TranscriptDocument = storage {
        val artifactId = validateArtifactId(id)
        val directory = artifactDirectory(artifactId, create = false)
        if (!directoryExists(directory)) fail(ArtifactFilesException.NOT_FOUND, "artifact $artifactId does not exist")
        loadFinalized(directory, artifactId).document
    }

    @Synchronized
    fun canonicalFile(id: String): File = storage {
        val artifactId = validateArtifactId(id)
        val path = rootPathForArtifact(artifactId, create = false).resolve(CANONICAL_NAME)
        rejectSymlink(path)
        ensureWithinRoot(path)
        path.toFile()
    }

    @Synchronized
    fun rawFile(id: String): File? = storage {
        val artifactId = validateArtifactId(id)
        val directory = artifactDirectory(artifactId, create = false)
        if (!directoryExists(directory)) return@storage null
        val finalized = loadFinalized(directory, artifactId)
        val extension = finalized.metadata.rawExtension ?: return@storage null
        directory.resolve(rawName(extension)).toFile()
    }

    @Synchronized
    fun recoverable(): List<StoredArtifact> = storage {
        ensureRoot(create = false)
        if (!rootExists()) return@storage emptyList()
        val artifacts = ArrayList<StoredArtifact>()
        for (directory in entries(rootPath)) {
            rejectSymlink(directory)
            if (!directoryExists(directory)) {
                fail(ArtifactFilesException.MALFORMED_ARTIFACT, "non-directory entry in artifact root")
            }
            val artifactId = directory.fileName.toString()
            if (!ARTIFACT_ID.matches(artifactId)) {
                fail(ArtifactFilesException.MALFORMED_ARTIFACT, "invalid artifact directory name")
            }
            validateEntries(directory, expectedRawExtension = null, finalized = false)
            if (!entryExists(directory.resolve(COMMIT_NAME))) continue
            artifacts += storedArtifact(loadFinalized(directory, artifactId))
        }
        artifacts.sortedBy { it.artifactId }
    }

    @Synchronized
    fun delete(id: String): Unit = storage {
        val artifactId = validateArtifactId(id)
        val directory = artifactDirectory(artifactId, create = false)
        if (!directoryExists(directory)) return@storage

        val commit = directory.resolve(COMMIT_NAME)
        val metadata = if (entryExists(commit)) readMetadata(commit) else null
        validateEntries(directory, metadata?.rawExtension, finalized = metadata != null)
        for (entry in entries(directory)) {
            rejectSymlink(entry)
            if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                fail(ArtifactFilesException.MALFORMED_ARTIFACT, "non-regular file in artifact directory")
            }
        }
        for (entry in entries(directory)) {
            Files.deleteIfExists(entry)
        }
        Files.deleteIfExists(directory)
    }

    private fun validateRawArguments(
        document: TranscriptDocument,
        raw: ByteArray?,
        rawExtension: String?,
        rawHash: String?,
    ) {
        if (raw == null && rawExtension != null) {
            fail(ArtifactFilesException.RAW_EXTENSION_WITHOUT_DATA, "rawExtension requires raw bytes")
        }
        if (raw != null && !document.acquisition.retainRaw) {
            fail(ArtifactFilesException.RAW_NOT_ALLOWED, "raw retention is disabled for this document")
        }
        if (raw != null) {
            validateRawExtension(rawExtension!!)
            if (raw.size > MAX_RAW_BYTES) {
                fail(ArtifactFilesException.RAW_TOO_LARGE, "raw data exceeds $MAX_RAW_BYTES bytes")
            }
            if (document.rawHash != null && document.rawHash != rawHash) {
                fail(ArtifactFilesException.RAW_HASH_MISMATCH, "document rawHash does not match supplied raw data")
            }
        }
    }

    private fun encodeCanonical(document: TranscriptDocument): ByteArray = try {
        json.encodeToString(document).toByteArray(StandardCharsets.UTF_8)
    } catch (failure: SerializationException) {
        fail(ArtifactFilesException.MALFORMED_CANONICAL, "document could not be serialized", failure)
    }

    private fun encodeMetadata(metadata: CommitMetadata): ByteArray = try {
        json.encodeToString(metadata).toByteArray(StandardCharsets.UTF_8)
    } catch (failure: SerializationException) {
        fail(ArtifactFilesException.STORAGE_FAILURE, "commit metadata could not be serialized", failure)
    }

    private fun loadFinalized(directory: Path, artifactId: String): Finalized {
        val commit = directory.resolve(COMMIT_NAME)
        if (!entryExists(commit)) fail(ArtifactFilesException.INCOMPLETE_ARTIFACT, "artifact $artifactId is not finalized")
        val metadata = readMetadata(commit)
        if (metadata.artifactId != artifactId) {
            fail(ArtifactFilesException.MALFORMED_METADATA, "commit metadata identity does not match directory")
        }
        validateEntries(directory, metadata.rawExtension, finalized = true)

        val canonicalPath = directory.resolve(CANONICAL_NAME)
        if (!entryExists(canonicalPath)) {
            fail(ArtifactFilesException.CORRUPT_CANONICAL, "finalized artifact has no canonical transcript")
        }
        val canonical = readRegular(canonicalPath, MAX_CANONICAL_BYTES, ArtifactFilesException.CANONICAL_TOO_LARGE)
        if (canonical.size.toLong() != metadata.bytes || sha256(canonical) != metadata.sha256) {
            fail(ArtifactFilesException.CORRUPT_CANONICAL, "canonical transcript hash or size mismatch")
        }
        val document = decodeCanonical(canonical, artifactId)
        if (metadata.rawExtension != null) {
            if (!document.acquisition.retainRaw) {
                fail(ArtifactFilesException.CORRUPT_RAW, "raw metadata conflicts with document retention policy")
            }
            val rawPath = directory.resolve(rawName(metadata.rawExtension))
            if (!entryExists(rawPath)) {
                fail(ArtifactFilesException.CORRUPT_RAW, "raw metadata points to a missing file")
            }
            readRegular(
                rawPath,
                MAX_RAW_BYTES,
                ArtifactFilesException.RAW_TOO_LARGE,
            ).also { raw ->
                if (raw.size.toLong() != metadata.rawBytes || sha256(raw) != metadata.rawSha256) {
                    fail(ArtifactFilesException.CORRUPT_RAW, "raw data hash or size mismatch")
                }
            }
        }
        return Finalized(document, metadata)
    }

    private fun decodeCanonical(bytes: ByteArray, artifactId: String): TranscriptDocument {
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (failure: CharacterCodingException) {
            fail(ArtifactFilesException.MALFORMED_CANONICAL, "canonical transcript is not valid UTF-8", failure)
        }
        val document = try {
            json.decodeFromString<TranscriptDocument>(text)
        } catch (failure: SerializationException) {
            fail(ArtifactFilesException.MALFORMED_CANONICAL, "canonical transcript is not valid JSON", failure)
        } catch (failure: IllegalArgumentException) {
            fail(ArtifactFilesException.MALFORMED_CANONICAL, "canonical transcript is not valid JSON", failure)
        }
        if (document.artifactId != artifactId) {
            fail(ArtifactFilesException.CORRUPT_CANONICAL, "document identity does not match directory")
        }
        return document
    }

    private fun readMetadata(path: Path): CommitMetadata {
        val bytes = readRegular(path, MAX_METADATA_BYTES, ArtifactFilesException.MALFORMED_METADATA)
        val metadata = try {
            json.decodeFromString<CommitMetadata>(decodeUtf8(bytes))
        } catch (failure: SerializationException) {
            fail(ArtifactFilesException.MALFORMED_METADATA, "commit metadata is not valid JSON", failure)
        } catch (failure: IllegalArgumentException) {
            fail(ArtifactFilesException.MALFORMED_METADATA, "commit metadata is not valid JSON", failure)
        }
        val rawHash = metadata.rawSha256
        val rawBytes = metadata.rawBytes
        if (metadata.schemaVersion != 1 || !ARTIFACT_ID.matches(metadata.artifactId) ||
            !SHA256.matches(metadata.sha256) || metadata.bytes !in 0..MAX_CANONICAL_BYTES.toLong() ||
            (metadata.rawExtension == null && (rawHash != null || rawBytes != null)) ||
            (metadata.rawExtension != null &&
                (rawHash == null || rawBytes == null || !SHA256.matches(rawHash) ||
                    rawBytes !in 0..MAX_RAW_BYTES.toLong()))
        ) {
            fail(ArtifactFilesException.MALFORMED_METADATA, "commit metadata contains invalid values")
        }
        metadata.rawExtension?.let(::validateRawExtension)
        return metadata
    }

    private fun decodeUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (failure: CharacterCodingException) {
        fail(ArtifactFilesException.MALFORMED_METADATA, "commit metadata is not valid UTF-8", failure)
    }

    private fun writeOrVerifyRaw(directory: Path, extension: String, raw: ByteArray) {
        val path = directory.resolve(rawName(extension))
        if (entryExists(path)) {
            val existing = readRegular(path, MAX_RAW_BYTES, ArtifactFilesException.RAW_TOO_LARGE)
            if (!existing.contentEquals(raw)) {
                fail(ArtifactFilesException.CONFLICTING_CONTENT, "raw data is immutable")
            }
        } else {
            atomicWrite(path, raw)
        }
    }

    private fun requireSameRawBinding(
        metadata: CommitMetadata,
        raw: ByteArray?,
        rawExtension: String?,
        rawHash: String?,
    ) {
        if (metadata.rawExtension != rawExtension ||
            (metadata.rawExtension != null &&
                (raw == null || metadata.rawSha256 != rawHash || metadata.rawBytes != raw.size.toLong()))
        ) {
            fail(ArtifactFilesException.CONFLICTING_CONTENT, "finalized raw data binding is immutable")
        }
    }

    private fun rawEntries(directory: Path): List<Path> = entries(directory).filter {
        val name = it.fileName.toString()
        name.startsWith(RAW_PREFIX)
    }

    private fun validateEntries(directory: Path, expectedRawExtension: String?, finalized: Boolean) {
        val expectedRawName = expectedRawExtension?.let(::rawName)
        for (entry in entries(directory)) {
            rejectSymlink(entry)
            if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                fail(ArtifactFilesException.MALFORMED_ARTIFACT, "non-regular entry in artifact directory")
            }
            val name = entry.fileName.toString()
            when {
                name == CANONICAL_NAME || name == COMMIT_NAME || isTemporaryName(name) -> Unit
                expectedRawName != null && name == expectedRawName -> Unit
                !finalized && name.startsWith(RAW_PREFIX) && name.removePrefix(RAW_PREFIX) in RAW_EXTENSIONS -> Unit
                else -> fail(ArtifactFilesException.MALFORMED_ARTIFACT, "unexpected file in artifact directory")
            }
        }
    }

    private fun atomicWrite(path: Path, content: ByteArray) {
        val directory = path.parent ?: fail(ArtifactFilesException.PATH_ESCAPE, "artifact file has no parent")
        rejectSymlink(path)
        ensureWithinRoot(path)
        ensureNoSymlinkComponents(directory)
        if (entryExists(path)) fail(ArtifactFilesException.CONFLICTING_CONTENT, "refusing to replace an existing file")

        var temporary: Path? = null
        try {
            val temp = Files.createTempFile(directory, ".artifact-", ".tmp")
            temporary = temp
            rejectSymlink(temp)
            FileOutputStream(temp.toFile()).use { output ->
                output.write(content)
                output.flush()
                output.fd.sync()
            }
            if (entryExists(path)) fail(ArtifactFilesException.CONFLICTING_CONTENT, "refusing to replace an existing file")
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE)
            temporary = null
        } catch (failure: ArtifactFilesException) {
            throw failure
        } catch (failure: AtomicMoveNotSupportedException) {
            fail(ArtifactFilesException.STORAGE_FAILURE, "atomic rename is not supported", failure)
        } catch (failure: IOException) {
            fail(ArtifactFilesException.STORAGE_FAILURE, "atomic artifact write failed", failure)
        } finally {
            temporary?.let {
                try {
                    Files.deleteIfExists(it)
                } catch (_: IOException) {
                    // The temporary file is ours; leaving it is recoverable and preserves data.
                }
            }
        }
    }

    private fun readRegular(path: Path, maxBytes: Int, tooLargeReason: String): ByteArray {
        rejectSymlink(path)
        if (!entryExists(path)) fail(ArtifactFilesException.NOT_FOUND, "required artifact file is missing")
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            fail(ArtifactFilesException.MALFORMED_ARTIFACT, "artifact entry is not a regular file")
        }
        val declaredSize = try {
            Files.size(path)
        } catch (failure: IOException) {
            fail(ArtifactFilesException.STORAGE_FAILURE, "artifact file size could not be read", failure)
        }
        if (declaredSize > maxBytes) fail(tooLargeReason, "artifact file exceeds $maxBytes bytes")
        val bounded = ByteArray(declaredSize.toInt())
        val buffer = ByteArray(minOf(32 * 1024, maxBytes))
        var total = 0
        try {
            Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    if (read > bounded.size - total) {
                        fail(ArtifactFilesException.STORAGE_FAILURE, "artifact file grew while reading")
                    }
                    System.arraycopy(buffer, 0, bounded, total, read)
                    total += read
                }
            }
        } catch (failure: ArtifactFilesException) {
            throw failure
        } catch (failure: IOException) {
            fail(ArtifactFilesException.STORAGE_FAILURE, "artifact file could not be read", failure)
        }
        if (total.toLong() != declaredSize) {
            fail(ArtifactFilesException.STORAGE_FAILURE, "artifact file changed while reading")
        }
        return bounded
    }

    private fun artifactDirectory(artifactId: String, create: Boolean): Path {
        val path = rootPathForArtifact(artifactId, create)
        if (create && !directoryExists(path)) {
            try {
                Files.createDirectory(path)
            } catch (failure: IOException) {
                fail(ArtifactFilesException.STORAGE_FAILURE, "artifact directory could not be created", failure)
            }
            ensureNoSymlinkComponents(path)
        }
        if (directoryExists(path)) return path
        if (create) fail(ArtifactFilesException.STORAGE_FAILURE, "artifact directory is unavailable")
        return path
    }

    private fun rootPathForArtifact(artifactId: String, create: Boolean): Path {
        validateArtifactId(artifactId)
        ensureRoot(create)
        val path = rootPath.resolve(artifactId).normalize()
        rejectSymlink(path)
        ensureWithinRoot(path)
        if (entryExists(path) && !directoryExists(path)) {
            fail(ArtifactFilesException.MALFORMED_ARTIFACT, "artifact path is not a directory")
        }
        return path
    }

    private fun ensureRoot(create: Boolean) {
        ensureNoSymlinkComponents(rootPath)
        if (!rootExists()) {
            if (!create) return
            try {
                Files.createDirectories(rootPath)
            } catch (failure: IOException) {
                fail(ArtifactFilesException.STORAGE_FAILURE, "artifact root could not be created", failure)
            }
            ensureNoSymlinkComponents(rootPath)
        }
        if (!directoryExists(rootPath)) {
            fail(ArtifactFilesException.STORAGE_FAILURE, "artifact root is not a directory")
        }
        ensureWithinRoot(rootPath)
    }

    private fun ensureWithinRoot(path: Path) {
        val canonicalRoot = try {
            rootPath.toFile().canonicalFile.toPath()
        } catch (failure: IOException) {
            fail(ArtifactFilesException.PATH_ESCAPE, "artifact root could not be canonicalized", failure)
        }
        val canonicalPath = try {
            path.toFile().canonicalFile.toPath()
        } catch (failure: IOException) {
            fail(ArtifactFilesException.PATH_ESCAPE, "artifact path could not be canonicalized", failure)
        }
        if (!canonicalPath.startsWith(canonicalRoot)) {
            fail(ArtifactFilesException.PATH_ESCAPE, "artifact path escapes root")
        }
    }

    private fun ensureNoSymlinkComponents(path: Path) {
        var current = path.root ?: return
        for (part in path) {
            current = current.resolve(part)
            if (Files.isSymbolicLink(current)) {
                fail(ArtifactFilesException.SYMLINK_NOT_ALLOWED, "symlink in artifact path")
            }
        }
    }

    private fun rejectSymlink(path: Path) {
        if (Files.isSymbolicLink(path)) {
            fail(ArtifactFilesException.SYMLINK_NOT_ALLOWED, "symlink is not an artifact file")
        }
    }

    private fun rootExists(): Boolean = Files.exists(rootPath, LinkOption.NOFOLLOW_LINKS)

    private fun directoryExists(path: Path): Boolean = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)

    private fun entryExists(path: Path): Boolean = Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)

    private fun entries(directory: Path): List<Path> {
        val result = ArrayList<Path>()
        try {
            Files.newDirectoryStream(directory).use { stream: DirectoryStream<Path> ->
                for (entry in stream) result.add(entry)
            }
        } catch (failure: IOException) {
            fail(ArtifactFilesException.STORAGE_FAILURE, "artifact directory could not be listed", failure)
        }
        return result
    }

    private fun validateArtifactId(value: String): String {
        if (!ARTIFACT_ID.matches(value)) {
            fail(ArtifactFilesException.INVALID_ARTIFACT_ID, "artifact id must be a lowercase canonical UUID")
        }
        return value
    }

    private fun validateRawExtension(value: String) {
        if (value !in RAW_EXTENSIONS) {
            fail(ArtifactFilesException.INVALID_RAW_EXTENSION, "raw extension is not whitelisted")
        }
    }

    private fun rawName(extension: String): String {
        validateRawExtension(extension)
        return RAW_PREFIX + extension
    }

    private fun isTemporaryName(name: String): Boolean = name.endsWith(".tmp")

    private fun storedArtifact(finalized: Finalized): StoredArtifact = StoredArtifact(
        artifactId = finalized.metadata.artifactId,
        sha256 = finalized.metadata.sha256,
        bytes = finalized.metadata.bytes,
        rawExtension = finalized.metadata.rawExtension,
    )

    private fun sha256(bytes: ByteArray): String = buildString(64) {
        for (byte in MessageDigest.getInstance("SHA-256").digest(bytes)) {
            val value = byte.toInt() and 0xff
            append("0123456789abcdef"[value ushr 4])
            append("0123456789abcdef"[value and 0x0f])
        }
    }

    private fun <T> storage(action: () -> T): T = try {
        action()
    } catch (failure: ArtifactFilesException) {
        throw failure
    } catch (failure: SecurityException) {
        throw ArtifactFilesException(ArtifactFilesException.STORAGE_FAILURE, "artifact storage access was denied", failure)
    } catch (failure: IOException) {
        throw ArtifactFilesException(ArtifactFilesException.STORAGE_FAILURE, "artifact storage operation failed", failure)
    }

    private fun fail(reason: String, detail: String, cause: Throwable? = null): Nothing =
        throw ArtifactFilesException(reason, detail, cause)
}
