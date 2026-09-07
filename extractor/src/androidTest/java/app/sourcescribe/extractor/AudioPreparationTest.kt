package app.sourcescribe.extractor

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Real native audio fixtures; no provider or network request is made. */
@RunWith(AndroidJUnit4::class)
class AudioPreparationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val runtime: NativeRuntime by lazy { NativeRuntime(context) }

    @Test
    fun contextCacheFileIsAccepted() = runBlocking {
        runtime.initialize()
        val fixture = temporaryDirectory("audio-preparation-context-cache")
        val source = File(fixture, "source.wav")
        try {
            generateSine(source, durationSeconds = 0.25)
            val info = AudioPreparation(runtime).probe(source)

            assertEquals("audio/wav", info.mimeType)
            assertTrue(info.durationMs > 0L)
        } finally {
            fixture.deleteRecursively()
        }
    }

    @Test
    fun finalAndInteriorSymlinksAreRejected() = runBlocking {
        runtime.initialize()
        val fixture = temporaryDirectory("audio-preparation-symlink")
        val target = File(fixture, "target.wav")
        try {
            generateSine(target, durationSeconds = 0.25)
            val preparation = AudioPreparation(runtime)

            val finalLink = File(fixture, "final-link.wav")
            Files.createSymbolicLink(finalLink.toPath(), target.toPath())
            expectFailure(AudioPreparationCode.INPUT_NOT_FILE) {
                preparation.probe(finalLink)
            }

            val interiorTarget = File(fixture, "interior-target")
            assertTrue(interiorTarget.mkdirs())
            val interiorFile = File(interiorTarget, target.name)
            Files.copy(target.toPath(), interiorFile.toPath())
            val interiorLink = File(fixture, "interior-link")
            Files.createSymbolicLink(interiorLink.toPath(), interiorTarget.toPath())
            expectFailure(AudioPreparationCode.INPUT_NOT_FILE) {
                preparation.probe(File(interiorLink, target.name))
            }
        } finally {
            fixture.deleteRecursively()
        }
    }

    @Test
    fun generatedSineIsProbedAndChunkedWithActualMetadata() = runBlocking {
        runtime.initialize()
        val fixture = temporaryDirectory("audio-preparation-sine")
        val source = File(fixture, "source.wav")
        try {
            generateSine(source, durationSeconds = 2.0)
            val sourceHash = sha256(source)
            val preparation = AudioPreparation(runtime)
            val sourceInfo = preparation.probe(source)

            assertTrue(sourceInfo.durationMs in 1_800L..2_200L)
            assertEquals("audio/wav", sourceInfo.mimeType)
            assertTrue(sourceInfo.sampleRate > 0)
            assertEquals(1, sourceInfo.channels)

            val chunk = preparation.chunk(
                input = source,
                outputDirectory = fixture,
                index = 0,
                startMs = 500L,
                durationMs = 700L,
            )
            assertTrue(chunk.file.isFile)
            assertFalse(chunk.file.canonicalFile == source.canonicalFile)
            assertEquals(500L, chunk.offsetMs)
            assertTrue(chunk.durationMs in 1L..950L)
            assertEquals("audio/mpeg", chunk.mimeType)
            assertTrue(chunk.file.length() in 1L..24_000_000L)
            assertTrue(chunk.sha256.matches(Regex("[0-9a-f]{64}")))
            assertEquals(sha256(chunk.file), chunk.sha256)
            assertEquals(sourceHash, sha256(source))
        } finally {
            fixture.deleteRecursively()
        }
    }

    @Test
    fun disjointChunkOffsetIsKeptAndOutOfRangeRequestsFail() = runBlocking {
        runtime.initialize()
        val fixture = temporaryDirectory("audio-preparation-range")
        val source = File(fixture, "source.wav")
        try {
            generateSine(source, durationSeconds = 2.0)
            val preparation = AudioPreparation(runtime)
            val separated = preparation.chunk(
                input = source,
                outputDirectory = fixture,
                index = 1,
                startMs = 900L,
                durationMs = 400L,
            )
            assertEquals(900L, separated.offsetMs)
            assertTrue(separated.durationMs > 0L)

            expectFailure(AudioPreparationCode.OUT_OF_RANGE) {
                preparation.chunk(
                    input = source,
                    outputDirectory = fixture,
                    index = 2,
                    startMs = 1_900L,
                    durationMs = 400L,
                )
            }
            expectFailure(AudioPreparationCode.OUT_OF_RANGE) {
                preparation.chunk(
                    input = source,
                    outputDirectory = fixture,
                    index = 3,
                    startMs = 2_500L,
                    durationMs = 100L,
                )
            }
            expectFailure(AudioPreparationCode.INVALID_INPUT) {
                preparation.chunk(
                    input = source,
                    outputDirectory = fixture,
                    index = 4,
                    startMs = 0L,
                    durationMs = 600_001L,
                )
            }
        } finally {
            fixture.deleteRecursively()
        }
    }

    @Test
    fun outputSizeBoundLeavesNoFinalOrTemporaryFile() = runBlocking {
        runtime.initialize()
        val fixture = temporaryDirectory("audio-preparation-size")
        val source = File(fixture, "source.wav")
        try {
            generateSine(source, durationSeconds = 2.0)
            val preparation = AudioPreparation(runtime)
            expectFailure(
                expected = AudioPreparationCode.OUTPUT_INVALID,
                alternative = AudioPreparationCode.OUTPUT_TOO_LARGE,
            ) {
                preparation.chunk(
                    input = source,
                    outputDirectory = fixture,
                    index = 5,
                    startMs = 0L,
                    durationMs = 1_000L,
                    maxBytes = 100L,
                )
            }
            assertFalse(File(fixture, "audio-5.mp3").exists())
            assertTrue(fixture.listFiles().orEmpty().none { it.name.startsWith("audio-prep-") })
        } finally {
            fixture.deleteRecursively()
        }
    }

    private suspend fun generateSine(output: File, durationSeconds: Double) {
        val result = runtime.ffmpeg(
            listOf(
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "lavfi",
                "-i",
                "sine=frequency=440:duration=$durationSeconds",
                "-c:a",
                "pcm_s16le",
                "-y",
                output.absolutePath,
            ),
            timeoutSeconds = 30,
        )
        assertEquals(0, result.exitCode)
        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)
    }

    private suspend fun expectFailure(
        expected: AudioPreparationCode,
        alternative: AudioPreparationCode? = null,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("audio preparation unexpectedly succeeded")
        } catch (exception: AudioPreparationException) {
            if (exception.code != expected && exception.code != alternative) {
                throw AssertionError("unexpected failure code: ${exception.code}")
            }
        }
    }

    private fun temporaryDirectory(prefix: String): File {
        val marker = File.createTempFile(prefix, ".dir", context.cacheDir)
        assertTrue(marker.delete())
        assertTrue(marker.mkdirs())
        return marker
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
