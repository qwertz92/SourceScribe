package app.sourcescribe.data

import app.sourcescribe.extractor.physicalFreeBytes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A measured app-private storage snapshot. */
data class StorageSnapshot(
    val usedBytes: Long,
    val reservedBytes: Long,
    val limitBytes: Long,
)

/** Stable failures from the app-private storage ceiling and maintenance boundary. */
class StorageBudgetException(
    val reason: String,
    detail: String,
    cause: Throwable? = null,
) : IOException("storage_budget:$reason: $detail", cause) {
    companion object {
        const val STORAGE_LIMIT = "STORAGE_LIMIT"
        const val DEVICE_STORAGE_LOW = "DEVICE_STORAGE_LOW"
        const val STORAGE_SCAN_FAILED = "STORAGE_SCAN_FAILED"
        const val INVALID_ATTEMPT_ID = "INVALID_ATTEMPT_ID"
        const val CLEANUP_FAILED = "CLEANUP_FAILED"
    }
}

/**
 * Limits app-managed user data before a caller starts a write-heavy operation.
 *
 * The quota covers all regular files below [Context.noBackupFilesDir],
 * [Context.filesDir], and [Context.cacheDir], except the known bundled/runtime
 * directories below `noBackupFilesDir`: `sourcescribe-runtime`, `engines`, and
 * `youtubedl-android`. Those directories contain executable/runtime material,
 * not user audio or transcript data, and are deliberately outside the user's
 * configurable storage limit. `databasesDir` is also outside the scan; Room's
 * small metadata growth is covered by [DATABASE_GROWTH_RESERVE_BYTES]. Imported
 * audio, attempt downloads/response spools, retained raw data, artifacts, and
 * cache/share files are included.
 *
 * Reservations are process-local. Callers must reserve their expected maximum
 * app-private growth before writing; the block runs without the mutex so network
 * work does not serialize other reservations.
 */
@Singleton
class StorageBudget @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsStore,
) {
    companion object {
        const val FREE_SPACE_MARGIN_BYTES = 16L * 1024L * 1024L
        const val DATABASE_GROWTH_RESERVE_BYTES = 1L * 1024L * 1024L
        const val MAX_SCAN_ENTRIES = 100_000

        private const val ATTEMPTS_DIRECTORY = "attempts"
        private const val RUNTIME_DIRECTORY = "sourcescribe-runtime"
        private const val ENGINES_DIRECTORY = "engines"
        private const val YTDLP_DIRECTORY = "youtubedl-android"
    }

    private val appContext = context.applicationContext
    private val backend = StorageBudgetBackend(
        roots = listOf(
            StorageBudgetRoot(
                directory = appContext.noBackupFilesDir,
                excludedTopLevelNames = setOf(RUNTIME_DIRECTORY, ENGINES_DIRECTORY, YTDLP_DIRECTORY),
            ),
            StorageBudgetRoot(appContext.filesDir),
            StorageBudgetRoot(appContext.cacheDir),
        ),
        attemptsRoot = File(appContext.noBackupFilesDir, ATTEMPTS_DIRECTORY),
        limitProvider = { settings.settings.first().storageLimitBytes },
    )

    suspend fun <T> withReservation(bytes: Long, block: suspend () -> T): T =
        backend.withReservation(bytes, block)

    suspend fun snapshot(): StorageSnapshot = backend.snapshot()

    /**
     * Deletes one retired attempt directory after the caller has proved in Room
     * that it is `FINISHED` with its artifact or `CANCELLED` and not executing.
     * With [keepAudio], only direct regular
     * `source.audio` and `audio-<index>.mp3` files remain; every other entry is
     * removed without following symlinks. This helper never infers retirement
     * and never touches `filesDir/artifacts`.
     */
    suspend fun deleteAttemptFiles(attemptId: String, keepAudio: Boolean = false): Boolean =
        backend.deleteAttemptFiles(attemptId, keepAudio)
}

internal data class StorageBudgetRoot(
    val directory: File,
    val excludedTopLevelNames: Set<String> = emptySet(),
)

internal class StorageBudgetBackend(
    private val roots: List<StorageBudgetRoot>,
    private val attemptsRoot: File,
    private val limitProvider: suspend () -> Long,
    private val usableSpaceProvider: (File) -> Long = { physicalFreeBytes(it) },
    private val maxEntries: Int = StorageBudget.MAX_SCAN_ENTRIES,
) {
    private val mutex = Mutex()
    private var reservedBytes = 0L

    init {
        require(roots.isNotEmpty())
        require(maxEntries > 0)
    }

    suspend fun <T> withReservation(bytes: Long, block: suspend () -> T): T {
        require(bytes >= 0L) { "reservation bytes must be non-negative" }
        mutex.withLock {
            val snapshot = readSnapshot(reservedBytes)
            ensureCapacity(snapshot, bytes)
            reservedBytes = checkedReservationAdd(reservedBytes, bytes)
        }

        try {
            return block()
        } finally {
            // Cancellation must not strand a reservation and make all later work fail closed.
            withContext(NonCancellable) {
                mutex.withLock {
                    check(reservedBytes >= bytes) { "reservation accounting underflow" }
                    reservedBytes -= bytes
                }
            }
        }
    }

    suspend fun snapshot(): StorageSnapshot = mutex.withLock { readSnapshot(reservedBytes) }

    suspend fun deleteAttemptFiles(attemptId: String, keepAudio: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock { deleteAttemptFilesBlocking(attemptId, keepAudio) }
    }

    private suspend fun readSnapshot(reserved: Long): StorageSnapshot {
        val used = withContext(Dispatchers.IO) { measureUsage() }
        val limit = try {
            limitProvider()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: StorageBudgetException) {
            throw failure
        } catch (failure: Exception) {
            throw StorageBudgetException(
                StorageBudgetException.STORAGE_SCAN_FAILED,
                "storage limit unavailable",
                failure,
            )
        }
        if (limit < 0L || reserved < 0L) {
            throw StorageBudgetException(
                StorageBudgetException.STORAGE_SCAN_FAILED,
                "negative storage accounting",
            )
        }
        return StorageSnapshot(used, reserved, limit)
    }

    private fun ensureCapacity(snapshot: StorageSnapshot, addition: Long) {
        val accounted = capacityAdd(snapshot.usedBytes, snapshot.reservedBytes)
        val requested = capacityAdd(accounted, addition)
        val withDatabaseGrowth = capacityAdd(
            requested,
            StorageBudget.DATABASE_GROWTH_RESERVE_BYTES,
        )
        if (addition < 0L || snapshot.usedBytes < 0L || snapshot.reservedBytes < 0L ||
            snapshot.limitBytes < 0L || withDatabaseGrowth > snapshot.limitBytes
        ) {
            throw StorageBudgetException(
                StorageBudgetException.STORAGE_LIMIT,
                "requested app data exceeds the configured storage limit",
            )
        }

        val reservedSpace = capacityAdd(
            capacityAdd(snapshot.reservedBytes, addition),
            StorageBudget.DATABASE_GROWTH_RESERVE_BYTES,
        )
        val requiredFree = capacityAdd(
            reservedSpace,
            StorageBudget.FREE_SPACE_MARGIN_BYTES,
        )
        val lowestUsableSpace = roots.minOf { root ->
            try {
                usableSpaceProvider(root.directory)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: StorageBudgetException) {
                throw failure
            } catch (failure: Exception) {
                throw StorageBudgetException(
                    StorageBudgetException.DEVICE_STORAGE_LOW,
                    "device free space unavailable",
                    failure,
                )
            }
        }
        if (lowestUsableSpace < 0L || lowestUsableSpace <= requiredFree) {
            throw StorageBudgetException(
                StorageBudgetException.DEVICE_STORAGE_LOW,
                "device free space must retain a 16 MiB margin",
            )
        }
    }

    private fun measureUsage(): Long {
        val seenRoots = HashSet<Path>()
        var total = 0L
        roots.forEach { root ->
            val path = root.directory.toPath().toAbsolutePath().normalize()
            if (!seenRoots.add(path)) return@forEach
            val usage = measureRoot(path, root.excludedTopLevelNames)
            total = checkedAdd(total, usage, "storage usage overflow")
        }
        return total
    }

    private fun measureRoot(path: Path, excludedTopLevelNames: Set<String>): Long {
        val rootAttributes = try {
            Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            return 0L
        } catch (failure: IOException) {
            throw scanFailure("storage root cannot be inspected", failure)
        } catch (failure: SecurityException) {
            throw scanFailure("storage root cannot be inspected", failure)
        }
        if (rootAttributes.isSymbolicLink) {
            throw scanFailure("storage root is a symbolic link", null)
        }
        if (!rootAttributes.isDirectory) throw scanFailure("storage root is not a directory", null)

        var total = 0L
        var entries = 0
        fun countEntry() {
            entries++
            if (entries > maxEntries) throw scanFailure("storage entry limit exceeded", null)
        }

        try {
            Files.walkFileTree(
                path,
                emptySet<FileVisitOption>(),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        directory: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        if (directory != path && directory.parent == path &&
                            directory.fileName.toString() in excludedTopLevelNames
                        ) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        countEntry()
                        if (attributes.isSymbolicLink) return FileVisitResult.SKIP_SUBTREE
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        countEntry()
                        if (attributes.isSymbolicLink) return FileVisitResult.CONTINUE
                        if (!attributes.isRegularFile) {
                            throw scanFailure("non-regular storage entry", null)
                        }
                        total = checkedAdd(total, attributes.size(), "storage usage overflow")
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult =
                        throw scanFailure("storage entry cannot be inspected", exception)
                },
            )
        } catch (failure: StorageBudgetException) {
            throw failure
        } catch (failure: IOException) {
            throw scanFailure("storage tree cannot be inspected", failure)
        } catch (failure: SecurityException) {
            throw scanFailure("storage tree cannot be inspected", failure)
        }
        return total
    }

    private fun deleteAttemptFilesBlocking(attemptId: String, keepAudio: Boolean): Boolean {
        val canonicalId = try {
            UUID.fromString(attemptId).toString()
        } catch (_: IllegalArgumentException) {
            throw StorageBudgetException(
                StorageBudgetException.INVALID_ATTEMPT_ID,
                "attempt id must be a canonical UUID",
            )
        }
        if (canonicalId != attemptId) {
            throw StorageBudgetException(
                StorageBudgetException.INVALID_ATTEMPT_ID,
                "attempt id must be a canonical UUID",
            )
        }

        val root = attemptsRoot.toPath().toAbsolutePath().normalize()
        rejectSymlinkComponents(root, "attempt root contains a symbolic link")
        val target = root.resolve(canonicalId).normalize()
        if (target.parent != root) {
            throw StorageBudgetException(StorageBudgetException.INVALID_ATTEMPT_ID, "attempt path escaped")
        }
        rejectSymlinkComponents(target, "attempt path contains a symbolic link")
        val targetAttributes = try {
            Files.readAttributes(target, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            return false
        } catch (failure: IOException) {
            throw StorageBudgetException(
                StorageBudgetException.CLEANUP_FAILED,
                "attempt directory cannot be inspected",
                failure,
            )
        } catch (failure: SecurityException) {
            throw StorageBudgetException(
                StorageBudgetException.CLEANUP_FAILED,
                "attempt directory cannot be inspected",
                failure,
            )
        }
        if (!targetAttributes.isDirectory) {
            throw StorageBudgetException(
                StorageBudgetException.CLEANUP_FAILED,
                "attempt target is not a directory",
            )
        }
        val entries = collectDeletionEntries(target, keepAudio)
        entries.asReversed().forEach { entry ->
            if (keepAudio && Files.isSymbolicLink(entry)) {
                val parent = entry.parent ?: throw StorageBudgetException(
                    StorageBudgetException.CLEANUP_FAILED,
                    "attempt entry has no parent",
                )
                rejectSymlinkComponents(parent, "attempt entry path contains a symbolic link")
            } else {
                rejectSymlinkComponents(entry, "attempt entry path contains a symbolic link")
            }
            try {
                Files.deleteIfExists(entry)
            } catch (failure: IOException) {
                throw StorageBudgetException(
                    StorageBudgetException.CLEANUP_FAILED,
                    "attempt files could not be deleted",
                    failure,
                )
            } catch (failure: SecurityException) {
                throw StorageBudgetException(
                    StorageBudgetException.CLEANUP_FAILED,
                    "attempt files could not be deleted",
                    failure,
                )
            }
        }
        return true
    }

    private fun collectDeletionEntries(target: Path, keepAudio: Boolean): List<Path> {
        val entries = ArrayList<Path>()
        var count = 0
        var retainedAudio = false
        fun countEntry() {
            count++
            if (count > maxEntries) {
                throw StorageBudgetException(
                    StorageBudgetException.CLEANUP_FAILED,
                    "attempt entry limit exceeded",
                )
            }
        }
        fun addEntry(path: Path) {
            countEntry()
            entries.add(path)
        }
        try {
            Files.walkFileTree(
                target,
                emptySet<FileVisitOption>(),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        directory: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        if (attributes.isSymbolicLink) {
                            if (!keepAudio) {
                                throw StorageBudgetException(
                                    StorageBudgetException.CLEANUP_FAILED,
                                    "attempt directory is a symbolic link",
                                )
                            }
                            addEntry(directory)
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        if (keepAudio && directory == target) countEntry() else addEntry(directory)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes,
                    ): FileVisitResult {
                        if (keepAudio && attributes.isSymbolicLink) {
                            addEntry(file)
                            return FileVisitResult.CONTINUE
                        }
                        if (!attributes.isRegularFile) {
                            throw StorageBudgetException(
                                StorageBudgetException.CLEANUP_FAILED,
                                "attempt entry is not a regular file",
                            )
                        }
                        if (keepAudio && file.parent == target && isRetainedAudioName(file.fileName.toString())) {
                            retainedAudio = true
                            return FileVisitResult.CONTINUE
                        }
                        addEntry(file)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult =
                        throw StorageBudgetException(
                            StorageBudgetException.CLEANUP_FAILED,
                            "attempt entry cannot be inspected",
                            exception,
                        )
                },
            )
        } catch (failure: StorageBudgetException) {
            throw failure
        } catch (failure: IOException) {
            throw StorageBudgetException(
                StorageBudgetException.CLEANUP_FAILED,
                "attempt tree cannot be inspected",
                failure,
            )
        } catch (failure: SecurityException) {
            throw StorageBudgetException(
                StorageBudgetException.CLEANUP_FAILED,
                "attempt tree cannot be inspected",
                failure,
            )
        }
        if (keepAudio && !retainedAudio) entries.add(0, target)
        return entries
    }

    private fun isRetainedAudioName(name: String): Boolean =
        name == "source.audio" || name.matches(AUDIO_CHUNK_PATTERN)

    private fun rejectSymlinkComponents(path: Path, message: String) {
        val components = ArrayList<Path>()
        var current: Path? = path.toAbsolutePath().normalize()
        while (true) {
            val component = current ?: break
            components.add(component)
            current = component.parent
        }
        components.asReversed().forEach { component ->
            if (app.sourcescribe.core.isUntrustedStorageSymlink(component)) {
                throw StorageBudgetException(StorageBudgetException.CLEANUP_FAILED, message)
            }
        }
    }

    private fun checkedAdd(left: Long, right: Long, detail: String): Long {
        if (left < 0L || right < 0L || Long.MAX_VALUE - left < right) {
            throw scanFailure(detail, null)
        }
        return left + right
    }

    private fun checkedReservationAdd(left: Long, right: Long): Long {
        if (left < 0L || right < 0L || Long.MAX_VALUE - left < right) {
            throw StorageBudgetException(
                StorageBudgetException.STORAGE_LIMIT,
                "reservation accounting overflow",
            )
        }
        return left + right
    }

    private fun capacityAdd(left: Long, right: Long): Long {
        if (left < 0L || right < 0L || Long.MAX_VALUE - left < right) {
            throw StorageBudgetException(
                StorageBudgetException.STORAGE_LIMIT,
                "storage accounting overflow",
            )
        }
        return left + right
    }

    private fun scanFailure(detail: String, cause: Throwable?): StorageBudgetException =
        StorageBudgetException(StorageBudgetException.STORAGE_SCAN_FAILED, detail, cause)

    private companion object {
        val AUDIO_CHUNK_PATTERN = Regex("audio-[0-9]+\\.mp3")
    }
}
