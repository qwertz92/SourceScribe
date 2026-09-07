package app.sourcescribe.data

import app.sourcescribe.extractor.physicalFreeBytes

import android.content.Context
import android.net.Uri
import android.os.Process
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.extractor.AudioPreparation
import app.sourcescribe.extractor.AudioPreparationException
import app.sourcescribe.extractor.AudioInfo
import app.sourcescribe.extractor.NativeRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class AudioImportCode {
    INVALID_INPUT,
    INPUT_UNAVAILABLE,
    STORAGE_LIMIT,
    STORAGE,
    PROBE_FAILED,
    CORRUPT,
}

class AudioImportException(val code: AudioImportCode) : IOException("audio_import:${code.name}")

@Singleton
class AudioImport @Inject constructor(
    @ApplicationContext context: Context,
    private val runtime: NativeRuntime,
    settings: SettingsStore,
    private val dao: SourceScribeDao,
    private val storage: StorageBudget = StorageBudget(context, settings),
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val importsDirectory = File(appContext.noBackupFilesDir, IMPORTS_DIRECTORY)
    private val preparation = AudioPreparation(runtime)
    private val json = Json { encodeDefaults = true }

    suspend fun import(uri: Uri, previewOwner: String? = null): Source {
        validateUri(uri)
        val snapshot = storage.snapshot()
        val quota = snapshot.limitBytes - snapshot.usedBytes - snapshot.reservedBytes - StorageBudget.DATABASE_GROWTH_RESERVE_BYTES
        val disk = physicalFreeBytes(appContext.noBackupFilesDir) - snapshot.reservedBytes - StorageBudget.FREE_SPACE_MARGIN_BYTES - StorageBudget.DATABASE_GROWTH_RESERVE_BYTES - 1
        val available = minOf(quota, disk, 2L * 1024 * 1024 * 1024)
        if (available <= 0) throw AudioImportException(AudioImportCode.STORAGE_LIMIT)
        val knownSize = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it > 0 } else null
        }
        if (knownSize != null && knownSize > available) throw AudioImportException(AudioImportCode.STORAGE_LIMIT)
        val maximum = knownSize ?: available
        return storage.withReservation(maximum) { importBounded(uri, maximum, previewOwner) }
    }

    private suspend fun importBounded(uri: Uri, maximum: Long, previewOwner: String?): Source {
        ensureImportsDirectory()
        val limit = directoryUsage() + maximum
        var temporary: File? = null
        try {
            val copied = copyToTemporary(uri, limit)
            temporary = copied.file
            val probe = probe(copied.file)
            if (probe.durationMs !in 1..MAX_DURATION_MS || !probe.mimeType.startsWith("audio/")) {
                throw AudioImportException(AudioImportCode.PROBE_FAILED)
            }
            val hash = copied.sha256
            val sourceId = "local:$hash"
            val source = Source(
                id = sourceId,
                kind = SourceKind.LOCAL_AUDIO,
                contentHash = hash,
                durationMs = probe.durationMs,
                fileName = copied.fileName,
                mimeType = probe.mimeType,
                fileBytes = copied.bytes,
            )
            val finalFile = File(importsDirectory, "$hash.audio")
            return SourceFiles.mutex.withLock {
                run commit@ {
                val existingRow = dao.source(sourceId)
                if (existingRow != null) {
                    val existing = verifiedSnapshot(existingRow, sourceId, hash)
                        ?: throw AudioImportException(AudioImportCode.CORRUPT)
                    val existingPath = existingRow.importedPath
                    if (existingPath != null) {
                        val existingFile = File(existingPath)
                        if (existingFile.absolutePath != finalFile.absolutePath) {
                            throw AudioImportException(AudioImportCode.CORRUPT)
                        }
                        if (isVerifiedImportFile(existingFile, hash)) return@commit existing
                        if (!isAbsentWithoutSymlink(existingFile)) {
                            throw AudioImportException(AudioImportCode.CORRUPT)
                        }

                        val restored = commitOrReuse(copied.file, finalFile, hash)
                        temporary = null
                        dao.updateSource(existingRow.copy(importedPath = restored.absolutePath))
                        return@commit existing
                    }

                    val restored = commitOrReuse(copied.file, finalFile, hash)
                    temporary = null
                    dao.updateSource(existingRow.copy(importedPath = restored.absolutePath))
                    return@commit existing
                }

                val committedFile = commitOrReuse(copied.file, finalFile, hash)
                temporary = null
                val row = SourceRow(source.id, json.encodeToString(source), copied.fileName, committedFile.absolutePath)
                if (dao.insertSource(row) == 0L) {
                    val concurrent = dao.source(source.id)
                    val recovered = concurrent?.let { verifiedSnapshot(it, source.id, hash) }
                        ?: throw AudioImportException(AudioImportCode.CORRUPT)
                    val concurrentPath = concurrent.importedPath
                    if (concurrentPath == null) {
                        dao.updateSource(concurrent.copy(importedPath = committedFile.absolutePath))
                    } else {
                        val concurrentFile = File(concurrentPath)
                        if (concurrentFile.absolutePath != finalFile.absolutePath ||
                            (!isVerifiedImportFile(concurrentFile, hash) && !isAbsentWithoutSymlink(concurrentFile))
                        ) {
                            throw AudioImportException(AudioImportCode.CORRUPT)
                        }
                        if (isAbsentWithoutSymlink(concurrentFile)) {
                            dao.updateSource(concurrent.copy(importedPath = committedFile.absolutePath))
                        }
                    }
                    return@commit recovered
                }
                source
                }.also { if (previewOwner != null) SourceFiles.reservePreview(it.id, previewOwner) }
            }
        } finally {
            temporary?.let { deleteTemporary(it) }
        }
    }

    private fun validateUri(uri: Uri) {
        if (uri.scheme != "content" || uri.authority.isNullOrBlank()) {
            throw AudioImportException(AudioImportCode.INVALID_INPUT)
        }
    }

    private fun ensureImportsDirectory() {
        try {
            if (Files.isSymbolicLink(importsDirectory.toPath())) {
                throw AudioImportException(AudioImportCode.STORAGE)
            }
            if (!importsDirectory.exists() && !importsDirectory.mkdirs()) {
                throw AudioImportException(AudioImportCode.STORAGE)
            }
            if (!importsDirectory.isDirectory || !isOwned(importsDirectory)) {
                throw AudioImportException(AudioImportCode.STORAGE)
            }
        } catch (exception: AudioImportException) {
            throw exception
        } catch (_: SecurityException) {
            throw AudioImportException(AudioImportCode.STORAGE)
        }
    }

    private suspend fun copyToTemporary(uri: Uri, limit: Long): CopiedAudio = withContext(Dispatchers.IO) {
        val used = directoryUsage()
        checkCapacity(used, 0L, limit)
        val fileName = displayName(uri)
        val temporary = try {
            File.createTempFile(TEMPORARY_PREFIX, TEMPORARY_SUFFIX, importsDirectory)
        } catch (_: IOException) {
            throw AudioImportException(AudioImportCode.STORAGE)
        } catch (_: SecurityException) {
            throw AudioImportException(AudioImportCode.STORAGE)
        }
        try {
            val input = try {
                resolver.openInputStream(uri) ?: throw AudioImportException(AudioImportCode.INPUT_UNAVAILABLE)
            } catch (exception: AudioImportException) {
                throw exception
            } catch (_: SecurityException) {
                throw AudioImportException(AudioImportCode.INPUT_UNAVAILABLE)
            } catch (_: IOException) {
                throw AudioImportException(AudioImportCode.INPUT_UNAVAILABLE)
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            input.use { source ->
                FileOutputStream(temporary).use { target ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        checkCapacity(used, bytes, limit, count.toLong())
                        target.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        bytes += count
                    }
                    target.fd.sync()
                }
            }
            if (bytes <= 0L) throw AudioImportException(AudioImportCode.INPUT_UNAVAILABLE)
            CopiedAudio(temporary, digest.digest().toHex(), bytes, fileName)
        } catch (exception: CancellationException) {
            deleteTemporary(temporary)
            throw exception
        } catch (exception: AudioImportException) {
            deleteTemporary(temporary)
            throw exception
        } catch (_: IOException) {
            deleteTemporary(temporary)
            throw AudioImportException(AudioImportCode.STORAGE)
        } catch (_: SecurityException) {
            deleteTemporary(temporary)
            throw AudioImportException(AudioImportCode.STORAGE)
        }
    }

    private suspend fun probe(file: File): AudioInfo {
        try {
            return preparation.probe(file)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: AudioPreparationException) {
            throw AudioImportException(AudioImportCode.PROBE_FAILED)
        } catch (_: Exception) {
            throw AudioImportException(AudioImportCode.PROBE_FAILED)
        }
    }

    private suspend fun commitOrReuse(temporary: File, destination: File, expectedHash: String): File =
        withContext(Dispatchers.IO) {
            try {
                if (Files.isSymbolicLink(destination.toPath())) {
                    throw AudioImportException(AudioImportCode.CORRUPT)
                }
                if (destination.exists()) {
                    if (isVerifiedImportFile(destination, expectedHash)) {
                        deleteTemporary(temporary)
                        return@withContext destination
                    }
                    throw AudioImportException(AudioImportCode.CORRUPT)
                }
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                destination
            } catch (exception: AudioImportException) {
                throw exception
            } catch (_: FileAlreadyExistsException) {
                if (isVerifiedImportFile(destination, expectedHash)) {
                    deleteTemporary(temporary)
                    destination
                } else {
                    throw AudioImportException(AudioImportCode.CORRUPT)
                }
            } catch (_: AtomicMoveNotSupportedException) {
                throw AudioImportException(AudioImportCode.STORAGE)
            } catch (_: UnsupportedOperationException) {
                throw AudioImportException(AudioImportCode.STORAGE)
            } catch (_: IOException) {
                throw AudioImportException(AudioImportCode.STORAGE)
            } catch (_: SecurityException) {
                throw AudioImportException(AudioImportCode.STORAGE)
            }
        }

    private suspend fun verifiedSnapshot(row: SourceRow, expectedId: String, expectedHash: String): Source? {
        return try {
            json.decodeFromString<Source>(row.snapshot).takeIf {
                it.id == expectedId && it.kind == SourceKind.LOCAL_AUDIO && it.canonicalUrl == null && it.contentHash == expectedHash
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun isVerifiedImportFile(file: File, expectedHash: String): Boolean =
        isOwnedImportFile(file) && sha256(file) == expectedHash

    private fun isAbsentWithoutSymlink(file: File): Boolean {
        val path = file.toPath()
        return !Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
    }

    private suspend fun sha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            Files.newInputStream(file.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: IOException) {
            throw AudioImportException(AudioImportCode.STORAGE)
        } catch (_: SecurityException) {
            throw AudioImportException(AudioImportCode.STORAGE)
        }
        digest.digest().toHex()
    }

    private fun directoryUsage(): Long {
        try {
            val files = importsDirectory.listFiles() ?: throw AudioImportException(AudioImportCode.STORAGE)
            var total = 0L
            for (file in files) {
                if (!file.isFile || !isOwnedImportFile(file)) continue
                val length = file.length()
                if (length < 0L || Long.MAX_VALUE - total < length) throw AudioImportException(AudioImportCode.STORAGE)
                total += length
            }
            return total
        } catch (exception: AudioImportException) {
            throw exception
        } catch (_: SecurityException) {
            throw AudioImportException(AudioImportCode.STORAGE)
        }
    }

    private fun checkCapacity(used: Long, current: Long, limit: Long, addition: Long = 0L) {
        if (current < 0L || addition < 0L || used > limit || current > limit - used || addition > limit - used - current) {
            throw AudioImportException(AudioImportCode.STORAGE_LIMIT)
        }
        try {
            if (physicalFreeBytes(importsDirectory) <= FREE_SPACE_GUARD_BYTES + addition) {
                throw AudioImportException(AudioImportCode.STORAGE)
            }
        } catch (exception: AudioImportException) {
            throw exception
        } catch (_: SecurityException) {
            throw AudioImportException(AudioImportCode.STORAGE)
        }
    }

    private fun displayName(uri: Uri): String {
        val raw = try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        } catch (_: Exception) {
            null
        }
        val sanitized = raw.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .map { if (it.isISOControl() || it == '/' || it == '\\') '_' else it }
            .joinToString("")
            .trim()
            .take(MAX_FILENAME_CHARS)
        return sanitized.ifBlank { DEFAULT_FILENAME }
    }

    private fun isOwnedImportFile(file: File): Boolean {
        return try {
            if (Files.isSymbolicLink(importsDirectory.toPath()) || Files.isSymbolicLink(file.toPath()) ||
                !file.isFile || !isOwned(file)
            ) return false
            file.canonicalFile.parentFile == importsDirectory.canonicalFile
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun isOwned(file: File): Boolean = try {
        if (Files.isSymbolicLink(file.toPath())) return false
        Os.stat(file.canonicalPath).st_uid == Process.myUid()
    } catch (_: ErrnoException) {
        false
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun deleteTemporary(file: File) {
        try {
            file.delete()
        } catch (_: SecurityException) {
            // Best effort for a file created by this invocation.
        }
    }

    private data class CopiedAudio(val file: File, val sha256: String, val bytes: Long, val fileName: String)

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    private companion object {
        const val IMPORTS_DIRECTORY = "imports"
        const val TEMPORARY_PREFIX = "import-"
        const val TEMPORARY_SUFFIX = ".part"
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val FREE_SPACE_GUARD_BYTES = 1L * 1024 * 1024
        const val MAX_DURATION_MS = 10L * 60 * 60 * 1000
        const val MAX_FILENAME_CHARS = 160
        const val DEFAULT_FILENAME = "audio"
        val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}
