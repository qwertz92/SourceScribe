package app.sourcescribe.extractor

import android.content.Context
import android.os.Process as AndroidProcess
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

private data class WebpAsset(
    val name: String,
    val sha256: String,
)

private const val WEBP_ASSET_DIRECTORY = "webp"
private const val MAX_WEBP_LIBRARY_BYTES = 2L * 1024L * 1024L
private const val ELF_HEADER_BYTES = 64
private const val ELF_MACHINE_OFFSET = 18
private const val ELFCLASS64 = 2
private const val ELFDATA2LSB = 1
private const val ELF_MACHINE_X86_64 = 62
private const val ELF_MACHINE_AARCH64 = 183
private val WEBP_ASSETS = mapOf(
    "arm64-v8a" to listOf(
        WebpAsset("libsharpyuv.so", "dfdafcf6cffc7ae1747ffa4165dd08849d62ca1f6302de10becb66ba9ac98a24"),
        WebpAsset("libwebp.so", "ddbcea8e5049dbcc8d530d01dcf001601f47f83476cc850743265ad170ecf307"),
        WebpAsset("libwebpmux.so", "1212479395c0f3479d35d094e386f17c9428b0cc3b5c2f229259caf01e107b7a"),
        WebpAsset("libwebpdemux.so", "2c27491bbd65b2db4c0cae73259120f3039e70b4d22bce66afa3532e05787d6d"),
        WebpAsset("libwebpdecoder.so", "cb8e2dc6a50fe4d6f2f639374705c8c33d635692e817386bdc74a803d5a90055"),
    ),
    "x86_64" to listOf(
        WebpAsset("libsharpyuv.so", "42545d124de709cd9993daccd347536f49f7034db420a9659bb9b569ce59bea4"),
        WebpAsset("libwebp.so", "e03a6dd38a80abb8edf5666417fbc1a79f67cbf346a83af3d1dd42244296b91d"),
        WebpAsset("libwebpmux.so", "70dbfdc81c9c69065af334e495fd0c725401cf3f58969591c8e7d4a01ba7db82"),
        WebpAsset("libwebpdemux.so", "928fd701336059e18c873f8bc6c1860a1d35a8f11b8270eacc43b2e06c2b36eb"),
        WebpAsset("libwebpdecoder.so", "547fce6a9ab4cc93858cd703df8f09c12fe8781e9cbc48586ac0d1615e2974f8"),
    ),
)

private fun readElfMachine(file: File): Int {
    val header = ByteArray(ELF_HEADER_BYTES)
    file.inputStream().use { input ->
        var offset = 0
        while (offset < header.size) {
            val count = input.read(header, offset, header.size - offset)
            if (count <= 0) throw IOException("short ELF header")
            offset += count
        }
    }
    if (header[0].toInt() and 0xff != 0x7f ||
        header[1].toInt() and 0xff != 'E'.code ||
        header[2].toInt() and 0xff != 'L'.code ||
        header[3].toInt() and 0xff != 'F'.code ||
        header[4].toInt() and 0xff != ELFCLASS64 ||
        header[5].toInt() and 0xff != ELFDATA2LSB
    ) {
        throw IOException("invalid ELF header")
    }
    return (header[ELF_MACHINE_OFFSET].toInt() and 0xff) or
        ((header[ELF_MACHINE_OFFSET + 1].toInt() and 0xff) shl 8)
}

/** The bounded result of one native invocation. */
data class RuntimeOutput(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

enum class RuntimeFailureCode {
    INVALID_TIMEOUT,
    INITIALIZATION_FAILED,
    MISSING_BINARY,
    START_FAILED,
    TIMED_OUT,
    PROBE_FAILED,
    OUTPUT_LIMIT_EXCEEDED,
    OUTPUT_READ_FAILED,
}

/** A sanitized failure; process output is available only through RuntimeOutput. */
class NativeRuntimeException(
    val code: RuntimeFailureCode,
    val exitCode: Int? = null,
) : Exception(
    buildString {
        append("native_runtime:")
        append(code.name)
        exitCode?.let {
            append(":exit=")
            append(it)
        }
    },
)

/**
 * Runs the wrapper's bundled Python/QuickJS/FFmpeg binaries without inheriting its
 * unbounded output, shell-based cancellation, or updater.
 */
class NativeRuntime(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    private var runtimePaths: RuntimePaths? = null

    /** Initializes the wrapper exactly once per process and verifies its private files. */
    suspend fun initialize() {
        initializationMutex.withLock {
            if (runtimePaths != null) return@withLock

            try {
                val paths = runInterruptible(Dispatchers.IO) {
                    YoutubeDL.init(appContext)
                    FFmpeg.init(appContext)
                    val launcher = installLauncher()
                    val webpDirectory = installWebpLibraries()
                    RuntimePaths.from(appContext, launcher, webpDirectory).also(::verifyRuntimeFiles)
                }
                runtimePaths = paths
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: NativeRuntimeException) {
                throw exception
            } catch (_: Exception) {
                throw NativeRuntimeException(RuntimeFailureCode.INITIALIZATION_FAILED)
            }
        }
    }

    /** Probes the actual bundled runtimes, including EJS and Python TLS support. */
    suspend fun versions(engine: File? = null): Map<String, String> {
        val paths = initializedPaths()
        val python = successfulProbe(
            execute(
                Command.PYTHON,
                listOf("-c", PYTHON_PROBE),
                PROBE_TIMEOUT_SECONDS,
            ),
        )
        val pythonLines = python.stdout.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        if (pythonLines.size < PYTHON_PROBE_FIELDS) {
            throw NativeRuntimeException(RuntimeFailureCode.PROBE_FAILED, python.exitCode)
        }

        val ejs = firstNonEmptyLine(
            successfulProbe(
                execute(
                    Command.PYTHON,
                    listOf("-c", ejsProbe(engine ?: paths.ytDlp)),
                    PROBE_TIMEOUT_SECONDS,
                ),
            ),
        ) ?: throw NativeRuntimeException(RuntimeFailureCode.PROBE_FAILED)

        val quickJsEval = successfulProbe(
            execute(
                Command.QUICKJS,
                listOf("-e", "print('sourcescribe-quickjs-ok')"),
                PROBE_TIMEOUT_SECONDS,
            ),
        )
        if (!quickJsEval.stdout.contains(QUICKJS_PROBE_MARKER)) {
            throw NativeRuntimeException(RuntimeFailureCode.PROBE_FAILED, quickJsEval.exitCode)
        }

        // Upstream qjs.c help() intentionally exits with 1. Evaluation above must still exit 0.
        val quickJsHelp = execute(Command.QUICKJS, listOf("-h"), PROBE_TIMEOUT_SECONDS)
        if (quickJsHelp.exitCode !in 0..1 || !probeText(quickJsHelp).contains("usage: qjs")) {
            throw NativeRuntimeException(RuntimeFailureCode.PROBE_FAILED, quickJsHelp.exitCode)
        }
        val quickJsVersion = QUICKJS_VERSION_PATTERN.find(probeText(quickJsHelp))
            ?.groupValues
            ?.getOrNull(1)
            ?: throw NativeRuntimeException(RuntimeFailureCode.PROBE_FAILED, quickJsHelp.exitCode)

        val ytDlp = firstNonEmptyLine(
            successfulProbe(execute(Command.YTDLP, listOf("--version"), PROBE_TIMEOUT_SECONDS, engine)),
        ) ?: throw NativeRuntimeException(RuntimeFailureCode.PROBE_FAILED)

        val ffmpeg = FFMPEG_VERSION_PATTERN.find(
            probeText(successfulProbe(execute(Command.FFMPEG, listOf("-version"), PROBE_TIMEOUT_SECONDS))),
        )?.groupValues?.getOrNull(1)
            ?: throw NativeRuntimeException(RuntimeFailureCode.PROBE_FAILED)

        val ffprobe = FFPROBE_VERSION_PATTERN.find(
            probeText(successfulProbe(execute(Command.FFPROBE, listOf("-version"), PROBE_TIMEOUT_SECONDS))),
        )?.groupValues?.getOrNull(1)
            ?: throw NativeRuntimeException(RuntimeFailureCode.PROBE_FAILED)

        return linkedMapOf(
            "python" to pythonLines[0],
            "openssl" to pythonLines[1],
            "tls" to pythonLines[2],
            "ejs" to ejs,
            "quickjs" to quickJsVersion,
            "yt-dlp" to ytDlp,
            "ffmpeg" to ffmpeg,
            "ffprobe" to ffprobe,
            "abi" to paths.abi,
        ).toMap()
    }

    suspend fun ytDlp(
        arguments: List<String>,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        engine: File? = null,
    ): RuntimeOutput = execute(Command.YTDLP, arguments.toList(), timeoutSeconds, engine)

    suspend fun ffmpeg(arguments: List<String>, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): RuntimeOutput =
        execute(Command.FFMPEG, arguments.toList(), timeoutSeconds)

    suspend fun ffprobe(arguments: List<String>, timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS): RuntimeOutput =
        execute(Command.FFPROBE, arguments.toList(), timeoutSeconds)

    internal suspend fun pythonForTest(
        arguments: List<String>,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
    ): RuntimeOutput = execute(Command.PYTHON, arguments.toList(), timeoutSeconds)

    private suspend fun execute(
        command: Command,
        arguments: List<String>,
        timeoutSeconds: Long,
        engine: File? = null,
    ): RuntimeOutput {
        validateTimeout(timeoutSeconds)
        val paths = initializedPaths()
        val selectedEngine = engine?.let(::verifiedEngine)
        return runInterruptible(Dispatchers.IO) {
            executeBlocking(paths, command, arguments, timeoutSeconds, selectedEngine)
        }
    }

    private suspend fun initializedPaths(): RuntimePaths {
        initialize()
        return runtimePaths ?: throw NativeRuntimeException(RuntimeFailureCode.INITIALIZATION_FAILED)
    }

    private fun executeBlocking(
        paths: RuntimePaths,
        command: Command,
        arguments: List<String>,
        timeoutSeconds: Long,
        engine: File?,
    ): RuntimeOutput {
        val pidDirectory = paths.pidDirectory
        if (!pidDirectory.exists() && !pidDirectory.mkdirs()) {
            throw NativeRuntimeException(RuntimeFailureCode.START_FAILED)
        }
        val pidFile = try {
            File.createTempFile("native-", ".pid", pidDirectory).also { it.delete() }
        } catch (_: IOException) {
            throw NativeRuntimeException(RuntimeFailureCode.START_FAILED)
        }
        val ackFile = File(pidFile.absolutePath + ACK_FILE_SUFFIX)
        val processBuilder = ProcessBuilder(buildCommand(paths, command, arguments, pidFile, engine))
            .directory(appContext.cacheDir)
        configureEnvironment(processBuilder, paths)

        var process: Process? = null
        var processGroupId: Int? = null
        var completedNormally = false
        var stdoutThread: Thread? = null
        var stderrThread: Thread? = null
        try {
            val startedProcess = try {
                processBuilder.start()
            } catch (_: IOException) {
                throw NativeRuntimeException(RuntimeFailureCode.START_FAILED)
            }
            process = startedProcess

            val startedStdoutCapture = BoundedCapture(startedProcess.inputStream, MAX_STDOUT_BYTES)
            val startedStderrCapture = BoundedCapture(startedProcess.errorStream, MAX_STDERR_BYTES)
            stdoutThread = startCapture(startedStdoutCapture, "sourcescribe-native-stdout")
            stderrThread = startCapture(startedStderrCapture, "sourcescribe-native-stderr")
            processGroupId = awaitProcessGroupId(pidFile, startedProcess)
                ?: throw NativeRuntimeException(RuntimeFailureCode.START_FAILED)
            writeAck(ackFile, processGroupId)

            if (!startedProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                terminate(startedProcess, processGroupId)
                throw NativeRuntimeException(RuntimeFailureCode.TIMED_OUT)
            }

            joinCapture(stdoutThread, CAPTURE_DRAIN_TIMEOUT_MILLIS)
            joinCapture(stderrThread, CAPTURE_DRAIN_TIMEOUT_MILLIS)
            closeQuietly(startedProcess.inputStream)
            closeQuietly(startedProcess.errorStream)
            joinCapture(stdoutThread, CAPTURE_CLOSE_TIMEOUT_MILLIS)
            joinCapture(stderrThread, CAPTURE_CLOSE_TIMEOUT_MILLIS)

            if (stdoutThread.isAlive || stderrThread.isAlive) {
                throw NativeRuntimeException(RuntimeFailureCode.OUTPUT_READ_FAILED)
            }
            if (startedStdoutCapture.failed || startedStderrCapture.failed) {
                throw NativeRuntimeException(RuntimeFailureCode.OUTPUT_READ_FAILED)
            }
            if (startedStdoutCapture.overflowed || startedStderrCapture.overflowed) {
                throw NativeRuntimeException(RuntimeFailureCode.OUTPUT_LIMIT_EXCEEDED)
            }

            completedNormally = true
            return RuntimeOutput(
                exitCode = startedProcess.exitValue(),
                stdout = startedStdoutCapture.text(),
                stderr = startedStderrCapture.text(),
            )
        } finally {
            process?.let {
                if (!completedNormally) terminate(it, processGroupId)
                closeQuietly(it.inputStream)
                closeQuietly(it.errorStream)
            }
            joinCaptureQuietly(stdoutThread, CAPTURE_CLOSE_TIMEOUT_MILLIS)
            joinCaptureQuietly(stderrThread, CAPTURE_CLOSE_TIMEOUT_MILLIS)
            ackFile.delete()
            pidFile.delete()
        }
    }

    private fun buildCommand(
        paths: RuntimePaths,
        command: Command,
        arguments: List<String>,
        pidFile: File,
        engine: File?,
    ): List<String> = buildList {
        add(paths.pythonBinary.absolutePath)
        add(paths.launcher.absolutePath)
        add(if (command == Command.YTDLP) "--python" else "--exec")
        add(pidFile.absolutePath)
        add(
            when (command) {
                Command.YTDLP -> (engine ?: paths.ytDlp).absolutePath
                Command.PYTHON -> paths.pythonBinary.absolutePath
                Command.QUICKJS -> paths.quickJs.absolutePath
                Command.FFMPEG -> paths.ffmpeg.absolutePath
                Command.FFPROBE -> paths.ffprobe.absolutePath
            },
        )
        if (command == Command.YTDLP) {
            // Keep fixed options before the caller's -- delimiter and URL.
            addAll(YTDLP_FIXED_ARGUMENTS)
            add("--js-runtimes")
            add("quickjs:${paths.quickJs.absolutePath}")
            add("--ffmpeg-location")
            add(paths.ffmpeg.absolutePath)
        }
        addAll(arguments)
    }

    private fun configureEnvironment(processBuilder: ProcessBuilder, paths: RuntimePaths) {
        val environment = processBuilder.environment()
        val path = System.getenv("PATH")
        environment["LD_LIBRARY_PATH"] = listOf(
            paths.webpLibraryDirectory.absolutePath,
            paths.pythonLibraryDirectory.absolutePath,
            paths.ffmpegLibraryDirectory.absolutePath,
            paths.aria2cLibraryDirectory.absolutePath,
        ).joinToString(File.pathSeparator)
        environment["SSL_CERT_FILE"] = paths.sslCertificate.absolutePath
        environment["PYTHONHOME"] = paths.pythonHome.absolutePath
        environment["PATH"] = listOfNotNull(path, paths.nativeLibraryDirectory.absolutePath)
            .joinToString(File.pathSeparator)
        environment["HOME"] = paths.pythonHome.absolutePath
        environment["TMPDIR"] = appContext.cacheDir.absolutePath
        environment.remove("PYTHONPATH")
        environment.remove("PYTHONSTARTUP")
        environment.remove("PYTHONINSPECT")
        environment["PYTHONNOUSERSITE"] = "1"
    }

    private fun awaitProcessGroupId(pidFile: File, process: Process): Int? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PID_READY_TIMEOUT_MILLIS)
        while (process.isAlive && System.nanoTime() < deadline) {
            readProcessGroupId(pidFile, process)?.let { return it }
            Thread.sleep(PID_POLL_INTERVAL_MILLIS)
        }
        return readProcessGroupId(pidFile, process)
    }

    private fun readProcessGroupId(pidFile: File, process: Process): Int? {
        if (!pidFile.isFile) return null
        val pid = try {
            pidFile.readText(StandardCharsets.US_ASCII).trim().toLongOrNull()
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        val candidate = pid?.takeIf { it in 2L..Int.MAX_VALUE.toLong() }?.toInt() ?: return null
        if (!process.isAlive) return null
        return processInfo(candidate)?.takeIf {
            it.parentPid == AndroidProcess.myPid() && it.processGroupId == candidate &&
                it.sessionId == candidate && it.uid == AndroidProcess.myUid()
        }?.let { candidate }
    }

    private fun writeAck(ackFile: File, processGroupId: Int) {
        val temporary = try {
            File.createTempFile(ackFile.name, ".tmp", ackFile.parentFile)
        } catch (_: IOException) {
            throw NativeRuntimeException(RuntimeFailureCode.START_FAILED)
        }
        try {
            FileOutputStream(temporary).use { output ->
                output.write(processGroupId.toString().toByteArray(StandardCharsets.US_ASCII))
                output.fd.sync()
            }
            if (!temporary.renameTo(ackFile)) {
                throw NativeRuntimeException(RuntimeFailureCode.START_FAILED)
            }
        } catch (exception: NativeRuntimeException) {
            throw exception
        } catch (_: IOException) {
            throw NativeRuntimeException(RuntimeFailureCode.START_FAILED)
        } finally {
            temporary.delete()
        }
    }

    private fun processInfo(pid: Int): ProcessInfo? {
        val fields = try {
            File("/proc/$pid/stat").readText(StandardCharsets.US_ASCII)
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        val endOfCommand = fields.lastIndexOf(") ")
        if (endOfCommand <= 0) return null
        val values = fields.substring(endOfCommand + 2).split(' ').filter(String::isNotEmpty)
        if (values.size < 5) return null
        return ProcessInfo(
            parentPid = values[1].toIntOrNull() ?: return null,
            processGroupId = values[2].toIntOrNull() ?: return null,
            sessionId = values[3].toIntOrNull() ?: return null,
            uid = try {
                File("/proc/$pid/status").useLines { lines ->
                    lines.firstOrNull { it.startsWith("Uid:") }
                        ?.substringAfter(':')
                        ?.trim()
                        ?.split(Regex("\\s+"))
                        ?.firstOrNull()
                        ?.toIntOrNull()
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } ?: return null,
        )
    }

    private fun terminate(process: Process, processGroupId: Int?) {
        if (!process.isAlive) return
        processGroupId?.let { signalGroup(it, OsConstants.SIGTERM) }
        process.destroy()
        try {
            val exited = process.waitFor(PROCESS_TERM_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            if (!exited) {
                processGroupId?.let { signalGroup(it, OsConstants.SIGKILL) }
                process.destroyForcibly()
                process.waitFor(PROCESS_TERM_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (_: InterruptedException) {
            if (process.isAlive) {
                processGroupId?.let { signalGroup(it, OsConstants.SIGKILL) }
                process.destroyForcibly()
            }
        }
    }

    private fun signalGroup(processGroupId: Int, signal: Int) {
        if (processGroupId <= 1) return
        try {
            Os.kill(-processGroupId, signal)
        } catch (_: ErrnoException) {
            // The group may have exited between wait and signal; the direct destroy remains.
        }
    }

    private fun successfulProbe(output: RuntimeOutput): RuntimeOutput {
        if (output.exitCode != 0) {
            throw NativeRuntimeException(RuntimeFailureCode.PROBE_FAILED, output.exitCode)
        }
        return output
    }

    private fun probeText(output: RuntimeOutput): String = output.stdout + '\n' + output.stderr

    private fun firstNonEmptyLine(output: RuntimeOutput): String? = output.stdout.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotEmpty)

    private fun ejsProbe(engine: File): String =
        "import sys; sys.path.insert(0, ${pythonStringLiteral(engine.absolutePath)}); from yt_dlp_ejs import version; print(version)"

    private fun pythonStringLiteral(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun validateTimeout(timeoutSeconds: Long) {
        if (timeoutSeconds !in 1L..MAX_TIMEOUT_SECONDS) {
            throw NativeRuntimeException(RuntimeFailureCode.INVALID_TIMEOUT)
        }
    }

    private fun verifiedEngine(engine: File): File {
        val candidate = try {
            engine.canonicalFile
        } catch (_: IOException) {
            throw NativeRuntimeException(RuntimeFailureCode.MISSING_BINARY)
        }
        val versionDirectory = candidate.parentFile
        val enginesDirectory = versionDirectory?.parentFile
        val expectedDirectory = try {
            File(appContext.noBackupFilesDir, ENGINES_DIRECTORY).canonicalFile
        } catch (_: IOException) {
            throw NativeRuntimeException(RuntimeFailureCode.MISSING_BINARY)
        }
        if (
            candidate.name != YTDLP_FILE_NAME || !candidate.isFile ||
            versionDirectory == null || !ENGINE_HASH_PATTERN.matches(versionDirectory.name) ||
            enginesDirectory != expectedDirectory
        ) {
            throw NativeRuntimeException(RuntimeFailureCode.MISSING_BINARY)
        }
        return candidate
    }

    private fun installLauncher(): File {
        val directory = File(appContext.noBackupFilesDir, RUNTIME_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) throw IOException("runtime directory")
        val content = appContext.assets.open(LAUNCHER_ASSET_NAME).use(InputStream::readBytes)
        val contentHash = sha256(content)
        val launcher = File(directory, "$LAUNCHER_FILE_PREFIX$contentHash.py")
        if (launcher.exists()) {
            if (!launcher.isFile || sha256(launcher) != contentHash) throw IOException("launcher hash")
            return launcher
        }

        val temporary = File.createTempFile(LAUNCHER_FILE_PREFIX, ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(content)
                output.fd.sync()
            }
            if (!temporary.renameTo(launcher)) {
                if (!launcher.isFile || sha256(launcher) != contentHash) throw IOException("launcher rename")
            }
        } finally {
            temporary.delete()
        }
        return launcher
    }

    private fun installWebpLibraries(): File {
        val abi = nativeAbi()
        val assets = WEBP_ASSETS[abi] ?: throw IOException("unsupported native ABI")
        val runtimeDirectory = File(appContext.noBackupFilesDir, RUNTIME_DIRECTORY)
        if (isSymbolicLink(runtimeDirectory) ||
            (!runtimeDirectory.exists() && !runtimeDirectory.mkdirs()) ||
            !runtimeDirectory.isDirectory
        ) {
            throw IOException("runtime directory")
        }

        val slot = File(runtimeDirectory, "webp-${webpBundleHash(assets)}")
        if (pathExists(slot)) {
            if (!verifyWebpSlot(slot, assets)) throw IOException("webp slot hash")
            return slot
        }

        val staging = File(runtimeDirectory, ".webp-${UUID.randomUUID()}")
        if (!staging.mkdirs()) throw IOException("webp staging directory")
        try {
            assets.forEach { asset ->
                copyWebpAsset(abi, asset, File(staging, asset.name))
            }
            if (pathExists(slot)) {
                if (!verifyWebpSlot(slot, assets)) throw IOException("webp slot hash")
                return slot
            }
            try {
                Files.move(staging.toPath(), slot.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (exception: IOException) {
                if (!pathExists(slot) || !verifyWebpSlot(slot, assets)) throw exception
            }
            if (!verifyWebpSlot(slot, assets)) throw IOException("webp slot hash")
            return slot
        } finally {
            if (pathExists(staging)) staging.deleteRecursively()
        }
    }

    private fun nativeAbi(): String {
        val python = File(File(appContext.applicationInfo.nativeLibraryDir), "libpython.so")
        if (isSymbolicLink(python) || !python.isFile) throw IOException("python binary")
        return when (readElfMachine(python)) {
            ELF_MACHINE_X86_64 -> "x86_64"
            ELF_MACHINE_AARCH64 -> "arm64-v8a"
            else -> throw IOException("unsupported native machine")
        }
    }

    private fun copyWebpAsset(abi: String, asset: WebpAsset, destination: File) {
        val temporary = File.createTempFile("${destination.name}-", ".tmp", destination.parentFile)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            appContext.assets.open("$WEBP_ASSET_DIRECTORY/$abi/${asset.name}").use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(READ_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count.toLong()
                        if (copied > MAX_WEBP_LIBRARY_BYTES) throw IOException("webp asset too large")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (copied <= 0L || digest.digest().toHex() != asset.sha256) {
                throw IOException("webp asset hash")
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temporary.delete()
        }
    }

    private fun verifyWebpSlot(slot: File, assets: List<WebpAsset>): Boolean {
        if (isSymbolicLink(slot) || !slot.isDirectory) return false
        assets.forEach { asset ->
            val file = File(slot, asset.name)
            if (isSymbolicLink(file) || !file.isFile || file.length() !in 1..MAX_WEBP_LIBRARY_BYTES) return false
            try {
                if (sha256(file, MAX_WEBP_LIBRARY_BYTES) != asset.sha256) return false
            } catch (_: IOException) {
                return false
            } catch (_: SecurityException) {
                return false
            }
        }
        return true
    }

    private fun webpBundleHash(assets: List<WebpAsset>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        assets.forEach { asset ->
            digest.update(asset.name.toByteArray(StandardCharsets.US_ASCII))
            digest.update(0.toByte())
            digest.update(asset.sha256.toByteArray(StandardCharsets.US_ASCII))
            digest.update(0.toByte())
        }
        return digest.digest().toHex()
    }

    private fun pathExists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun isSymbolicLink(file: File): Boolean = Files.isSymbolicLink(file.toPath())

    private fun sha256(content: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(content).toHex()

    private fun sha256(file: File, maxBytes: Long = Long.MAX_VALUE): String {
        if (file.length() > maxBytes) throw IOException("file too large")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(READ_BUFFER_SIZE)
            var readBytes = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                readBytes += count.toLong()
                if (readBytes > maxBytes) throw IOException("file too large")
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    private fun verifyRuntimeFiles(paths: RuntimePaths) {
        listOf(
            paths.pythonBinary,
            paths.quickJs,
            paths.ffmpeg,
            paths.ffprobe,
            paths.ytDlp,
            paths.pythonLibraryDirectory,
            paths.ffmpegLibraryDirectory,
            paths.sslCertificate,
            paths.webpLibraryDirectory,
        ).forEach { file ->
            if (!file.exists() || (!file.isFile && !file.isDirectory)) {
                throw NativeRuntimeException(RuntimeFailureCode.MISSING_BINARY)
            }
        }
    }

    private fun startCapture(capture: BoundedCapture, name: String): Thread = Thread(capture, name).apply {
        isDaemon = true
        start()
    }

    private fun joinCapture(thread: Thread?, timeoutMillis: Long) = thread?.join(timeoutMillis)

    private fun joinCaptureQuietly(thread: Thread?, timeoutMillis: Long) {
        try {
            joinCapture(thread, timeoutMillis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun closeQuietly(input: InputStream) {
        try {
            input.close()
        } catch (_: IOException) {
            // The process result is still useful when a pipe closes concurrently.
        }
    }

    private class BoundedCapture(
        private val input: InputStream,
        private val limit: Int,
    ) : Runnable {
        private val captured = ByteArrayOutputStream(min(limit, INITIAL_CAPTURE_CAPACITY))

        override fun run() {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            var capturedBytes = 0
            try {
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (capturedBytes < limit) {
                        val accepted = min(count, limit - capturedBytes)
                        captured.write(buffer, 0, accepted)
                        capturedBytes += accepted
                        if (accepted < count) overflowed = true
                    } else {
                        overflowed = true
                    }
                }
            } catch (_: IOException) {
                failed = true
            }
        }

        @Volatile
        var overflowed = false

        @Volatile
        var failed = false

        fun text(): String = String(captured.toByteArray(), Charsets.UTF_8)
    }

    private data class ProcessInfo(
        val parentPid: Int,
        val processGroupId: Int,
        val sessionId: Int,
        val uid: Int,
    )

    private data class RuntimePaths(
        val nativeLibraryDirectory: File,
        val abi: String,
        val pythonBinary: File,
        val quickJs: File,
        val ffmpeg: File,
        val ffprobe: File,
        val pythonLibraryDirectory: File,
        val ffmpegLibraryDirectory: File,
        val aria2cLibraryDirectory: File,
        val pythonHome: File,
        val sslCertificate: File,
        val ytDlp: File,
        val launcher: File,
        val pidDirectory: File,
        val webpLibraryDirectory: File,
    ) {
        companion object {
            fun from(context: Context, launcher: File, webpDirectory: File): RuntimePaths {
                val nativeDirectory = File(context.applicationInfo.nativeLibraryDir)
                val abi = when (readElfMachine(File(nativeDirectory, "libpython.so"))) {
                    ELF_MACHINE_X86_64 -> "x86_64"
                    ELF_MACHINE_AARCH64 -> "arm64-v8a"
                    else -> throw IOException("unsupported native machine")
                }
                val packageDirectory = File(
                    File(context.noBackupFilesDir, "youtubedl-android"),
                    "packages",
                )
                val pythonPackage = File(packageDirectory, "python")
                val ffmpegPackage = File(packageDirectory, "ffmpeg")
                val baseDirectory = packageDirectory.parentFile
                    ?: File(context.noBackupFilesDir, "youtubedl-android")
                return RuntimePaths(
                    nativeLibraryDirectory = nativeDirectory,
                    abi = abi,
                    pythonBinary = File(nativeDirectory, "libpython.so"),
                    quickJs = File(nativeDirectory, "libqjs.so"),
                    ffmpeg = File(nativeDirectory, "libffmpeg.so"),
                    ffprobe = File(nativeDirectory, "libffprobe.so"),
                    pythonLibraryDirectory = File(pythonPackage, "usr/lib"),
                    ffmpegLibraryDirectory = File(ffmpegPackage, "usr/lib"),
                    aria2cLibraryDirectory = File(packageDirectory, "aria2c/usr/lib"),
                    pythonHome = File(pythonPackage, "usr"),
                    sslCertificate = File(pythonPackage, "usr/etc/tls/cert.pem"),
                    ytDlp = File(baseDirectory, "yt-dlp/yt-dlp"),
                    launcher = launcher,
                    pidDirectory = File(File(context.noBackupFilesDir, RUNTIME_DIRECTORY), "pids"),
                    webpLibraryDirectory = webpDirectory,
                )
            }
        }
    }

    private enum class Command {
        PYTHON,
        QUICKJS,
        YTDLP,
        FFMPEG,
        FFPROBE,
    }

    private companion object {
        private val initializationMutex = Mutex()

        private const val RUNTIME_DIRECTORY = "sourcescribe-runtime"
        private const val ENGINES_DIRECTORY = "engines"
        private const val LAUNCHER_ASSET_NAME = "sourcescribe_launcher.py"
        private const val LAUNCHER_FILE_PREFIX = "sourcescribe-launcher-"
        private const val ACK_FILE_SUFFIX = ".ack"
        private const val YTDLP_FILE_NAME = "yt-dlp"
        private const val DEFAULT_TIMEOUT_SECONDS = 120L
        private const val MAX_TIMEOUT_SECONDS = 600L
        private const val PROBE_TIMEOUT_SECONDS = 30L
        private const val MAX_STDOUT_BYTES = 8 * 1024 * 1024
        private const val MAX_STDERR_BYTES = 64 * 1024
        private const val INITIAL_CAPTURE_CAPACITY = 16 * 1024
        private const val READ_BUFFER_SIZE = 16 * 1024
        private const val PID_READY_TIMEOUT_MILLIS = 1_500L
        private const val PID_POLL_INTERVAL_MILLIS = 5L
        private const val PROCESS_TERM_GRACE_MILLIS = 1_500L
        private const val CAPTURE_DRAIN_TIMEOUT_MILLIS = 1_000L
        private const val CAPTURE_CLOSE_TIMEOUT_MILLIS = 250L
        private const val PYTHON_PROBE_FIELDS = 3
        private const val QUICKJS_PROBE_MARKER = "sourcescribe-quickjs-ok"
        private const val PYTHON_PROBE =
            "import platform,ssl; context=ssl.create_default_context(); print(platform.python_version()); print(ssl.OPENSSL_VERSION); print(context.minimum_version.name)"
        private val QUICKJS_VERSION_PATTERN = Regex("(?m)^QuickJS version\\s+([^\\s]+)")
        private val FFMPEG_VERSION_PATTERN = Regex("(?m)^ffmpeg version\\s+([^\\s]+)")
        private val FFPROBE_VERSION_PATTERN = Regex("(?m)^ffprobe version\\s+([^\\s]+)")
        private val ENGINE_HASH_PATTERN = Regex("[0-9a-f]{64}")
        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
        private val YTDLP_FIXED_ARGUMENTS = listOf(
            "--ignore-config",
            "--no-plugin-dirs",
            "--no-remote-components",
            "--no-update",
            "--no-playlist",
            "--no-cache-dir",
            "--socket-timeout",
            "15",
            "--retries",
            "2",
            "--fragment-retries",
            "2",
        )
    }
}
