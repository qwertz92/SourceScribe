package app.sourcescribe.extractor

import android.content.Context
import android.os.Bundle
import app.sourcescribe.core.SourceResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeRuntimeTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val runtime: NativeRuntime by lazy { NativeRuntime(context) }

    @Test
    fun bundledVersionsExposeNativeTlsAndEjs() = runBlocking {
        runtime.initialize()
        val manager = EngineUpdateManager(context, runtime)
        val versions = runtime.versions(manager.file(manager.bundled()))

        assertTrue(versions.getValue("python").matches(Regex("\\d+\\.\\d+\\.\\d+")))
        assertTrue(versions.getValue("openssl").startsWith("OpenSSL "))
        assertTrue(versions.getValue("tls").startsWith("TLS"))
        assertTrue(versions.getValue("ejs").matches(Regex("\\d+\\.\\d+\\.\\d+")))
        assertTrue(versions.getValue("quickjs").matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertTrue(versions.getValue("yt-dlp").isNotBlank())
        assertTrue(versions.getValue("ffmpeg").matches(Regex("\\d+\\.\\d+(?:\\.\\d+)?")))
        assertTrue(versions.getValue("ffprobe").matches(Regex("\\d+\\.\\d+(?:\\.\\d+)?")))
        assertTrue(versions.getValue("abi").isNotBlank())

        val status = Bundle()
        versions.forEach { (name, version) -> status.putString("version.$name", version) }
        InstrumentationRegistry.getInstrumentation().sendStatus(100, status)
    }

    @Test
    fun ffmpegGeneratesAudioAndFfprobeReadsDuration() = runBlocking {
        runtime.initialize()
        val audio = File.createTempFile("sourcescribe-runtime-", ".wav", context.cacheDir)
        try {
            val encoded = runtime.ffmpeg(
                listOf(
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-f",
                    "lavfi",
                    "-i",
                    "sine=frequency=1000:duration=1",
                    "-c:a",
                    "pcm_s16le",
                    "-y",
                    audio.absolutePath,
                ),
                timeoutSeconds = 30,
            )
            assertEquals(0, encoded.exitCode)
            assertTrue(audio.isFile)
            assertTrue(audio.length() > 0L)

            val probed = runtime.ffprobe(
                listOf(
                    "-v",
                    "error",
                    "-show_entries",
                    "format=duration",
                    "-of",
                    "default=noprint_wrappers=1:nokey=1",
                    audio.absolutePath,
                ),
                timeoutSeconds = 30,
            )
            assertEquals(0, probed.exitCode)
            val duration = probed.stdout.trim().toDoubleOrNull()
                ?: error("duration probe failed")
            assertTrue(duration > 0.9)
            assertTrue(duration < 1.1)
        } finally {
            audio.delete()
        }
    }

    @Test
    fun timeoutIsTypedAndBounded() = runBlocking {
        runtime.initialize()
        val endlessSine = listOf(
            "-hide_banner",
            "-loglevel",
            "error",
            "-f",
            "lavfi",
            "-i",
            "sine=frequency=1000",
            "-f",
            "null",
            "-",
        )
        var failure: NativeRuntimeException? = null
        try {
            runtime.ffmpeg(endlessSine, timeoutSeconds = 1)
            fail("endless native process did not time out")
        } catch (exception: NativeRuntimeException) {
            failure = exception
        }
        assertNotNull(failure)
        assertEquals(RuntimeFailureCode.TIMED_OUT, failure?.code)
    }

    @Test
    fun cancellationFinishesAndDoesNotPoisonNextInvocation() = runBlocking {
        runtime.initialize()
        val endlessSine = listOf(
            "-hide_banner",
            "-loglevel",
            "error",
            "-f",
            "lavfi",
            "-i",
            "sine=frequency=1000",
            "-f",
            "null",
            "-",
        )
        val job = launch { runtime.ffmpeg(endlessSine, timeoutSeconds = 120) }
        delay(250)
        withTimeout(5_000) { job.cancelAndJoin() }
        assertFalse(job.isActive)

        val probe = runtime.ffprobe(listOf("-version"), timeoutSeconds = 30)
        assertEquals(0, probe.exitCode)
    }

    @Test
    fun cancellationReapsDetachedChildWithoutKillingConcurrentInvocation() = runBlocking {
        runtime.initialize()
        val directory = File(context.cacheDir, "runtime-detached-${System.nanoTime()}")
        assertTrue(directory.mkdirs())
        val childPidFile = File(directory, "child.pid")
        try {
            coroutineScope {
                val concurrent = async {
                    runtime.pythonForTest(
                        listOf("-c", "import time; time.sleep(1); print('concurrent-ok')"),
                        timeoutSeconds = 10,
                    )
                }
                val cancelled = launch {
                    runtime.pythonForTest(
                        listOf(
                            "-c",
                            DETACHED_CHILD_SCRIPT,
                            childPidFile.absolutePath,
                        ),
                        timeoutSeconds = 30,
                    )
                }
                val childPid = awaitPid(childPidFile)
                withTimeout(5_000) { cancelled.cancelAndJoin() }

                assertFalse(File("/proc/$childPid").exists())
                val concurrentOutput = concurrent.await()
                assertEquals(0, concurrentOutput.exitCode)
                assertEquals("concurrent-ok", concurrentOutput.stdout.trim())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun normalTargetExitReapsChildThatHeldOutputPipes() = runBlocking {
        runtime.initialize()
        val directory = File(context.cacheDir, "runtime-normal-${System.nanoTime()}")
        assertTrue(directory.mkdirs())
        val childPidFile = File(directory, "child.pid")
        try {
            val output = runtime.pythonForTest(
                listOf("-c", NORMAL_EXIT_CHILD_SCRIPT, childPidFile.absolutePath),
                timeoutSeconds = 10,
            )
            val childPid = childPidFile.readText().trim().toInt()

            assertEquals(0, output.exitCode)
            assertEquals("parent-ok", output.stdout.trim())
            assertFalse(File("/proc/$childPid").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun concurrentRuntimeInitializationRunsEveryCommand() = runBlocking {
        val outputs = coroutineScope {
            (0 until 8).map { index ->
                async {
                    NativeRuntime(context).pythonForTest(
                        listOf("-c", "print('runtime-$index')"),
                        timeoutSeconds = 10,
                    )
                }
            }.awaitAll()
        }

        outputs.forEachIndexed { index, output ->
            assertEquals(0, output.exitCode)
            assertEquals("runtime-$index", output.stdout.trim())
        }
    }

    @Test
    fun publicSourceProbeUsesOnlyConfiguredSourceIdAndCounts() = runBlocking {
        val publicSourceUrl = InstrumentationRegistry.getArguments()
            .getString("publicSourceUrl")
            ?.trim()
            .orEmpty()
        assumeTrue("publicSourceUrl instrumentation argument is required", publicSourceUrl.isNotEmpty())

        runtime.initialize()
        val manager = EngineUpdateManager(context, runtime)
        val result = runtime.ytDlp(
            listOf("--dump-single-json", "--skip-download", publicSourceUrl),
            timeoutSeconds = 120,
            engine = manager.file(manager.bundled()),
        )
        val diagnostic = Bundle()
        diagnostic.putInt("publicSource.exit", result.exitCode)
        diagnostic.putString("publicSource.error", result.stderr.lineSequence().filter { it.startsWith("ERROR:") }
            .joinToString("\n").replace(Regex("https?://\\S+"), "[URL]").take(1000))
        InstrumentationRegistry.getInstrumentation().sendStatus(104, diagnostic)
        assertEquals(0, result.exitCode)
        val metadata = try {
            JSONObject(result.stdout)
        } catch (_: Exception) {
            fail("public source metadata was not JSON")
            return@runBlocking
        }
        val sourceId = metadata.optString("id").trim()
        val expectedSourceId = SourceResolver.youtube(publicSourceUrl).videoId
        assertEquals(expectedSourceId, sourceId)
        val subtitleCount = metadata.optJSONObject("subtitles")?.length() ?: 0
        val automaticSubtitleCount = metadata.optJSONObject("automatic_captions")?.length() ?: 0

        val status = Bundle()
        status.putString("sourceId", sourceId)
        status.putInt("subtitleCount", subtitleCount)
        status.putInt("automaticSubtitleCount", automaticSubtitleCount)
        val endpoints = mutableSetOf<String>()
        for (kind in listOf("subtitles", "automatic_captions")) {
            val languages = metadata.optJSONObject(kind) ?: continue
            for (language in languages.keys()) {
                val formats = languages.optJSONArray(language) ?: continue
                for (index in 0 until formats.length()) {
                    val item = formats.optJSONObject(index) ?: continue
                    if (item.optString("ext") !in setOf("json3", "vtt", "srt")) continue
                    val value = item.optString("url")
                    val uri = runCatching { java.net.URI(value) }.getOrNull()
                    // Public endpoint shape only; signed query values never enter test logs.
                    val shape = when {
                        uri?.path == "/api/timedtext" -> "/api/timedtext"
                        uri?.path?.startsWith("/api/manifest/hls_timedtext_playlist/") == true -> "/api/manifest/hls_timedtext_playlist/[redacted]"
                        else -> "[other path]"
                    }
                    endpoints += "$kind:$language:${item.optString("ext")}:${item.optString("protocol")}:${uri?.scheme}://${uri?.host}$shape"
                }
            }
        }
        status.putString("captionEndpoints", endpoints.sorted().take(20).joinToString("\n"))
        InstrumentationRegistry.getInstrumentation().sendStatus(101, status)
    }

    private suspend fun awaitPid(file: File): Int = withTimeout(3_000) {
        var observed: Int? = null
        while (observed == null) {
            observed = file.takeIf(File::isFile)?.readText()?.trim()?.toIntOrNull()
            delay(10)
        }
        observed
    }

    private companion object {
        private val DETACHED_CHILD_SCRIPT = """
            import os, sys, time
            child = os.fork()
            if child == 0:
                os.setsid()
                with open(sys.argv[1], "w", encoding="ascii") as output:
                    output.write(str(os.getpid()))
                os.closerange(0, 1024)
                time.sleep(30)
            else:
                time.sleep(30)
        """.trimIndent()

        private val NORMAL_EXIT_CHILD_SCRIPT = """
            import os, sys, time
            child = os.fork()
            if child == 0:
                with open(sys.argv[1], "w", encoding="ascii") as output:
                    output.write(str(os.getpid()))
                os.closerange(0, 1024)
                time.sleep(30)
            else:
                print("parent-ok", flush=True)
                os._exit(0)
        """.trimIndent()
    }
}
