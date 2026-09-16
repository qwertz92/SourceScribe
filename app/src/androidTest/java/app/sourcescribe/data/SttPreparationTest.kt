package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.AudioRetention
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Provider
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Region
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.extractor.AudioPreparation
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prepares audio with the bundled ffmpeg instead of a fixture chunk, so the actual encoder output
 * decides. Provider HTTP is refused; nothing here leaves the device.
 */
@RunWith(AndroidJUnit4::class)
class SttPreparationTest {
    /**
     * A source longer than one chunk window: `chunkPlan` makes window 0 exactly
     * `MAX_CHUNK_DURATION_MS` long, and the MP3 the encoder writes for it runs past that window by
     * the frames it has to round up to plus its own delay (84 ms measured with the bundled
     * ffmpeg 7.1.1). The attempt has to keep going, and the chunk has to keep the planned media
     * interval so the chunks still tile the source without a gap or an overlap.
     */
    @Test
    fun theFirstFullWindowIsPreparedAndKeepsItsPlannedInterval() = withFixture {
        val seeded = seed(sourceSeconds = 601)

        val probed = step.run(seeded.attempt, seeded.owner, seeded.config)
        assertEquals(Phase.PREPARE_AUDIO, probed.phase)
        assertEquals(ExecutionState.QUEUED, probed.state)
        assertNull(probed.error)
        val afterProbe = checkpointOf(probed)
        val windows = SttStep.chunkPlan(requireNotNull(afterProbe.durationMs))
        assertEquals(2, windows.size)
        assertEquals(SttStep.MAX_CHUNK_DURATION_MS, windows[0].durationMs)

        val prepared = step.run(probed, seeded.owner, seeded.config)

        assertNull(prepared.error)
        assertEquals(ExecutionState.QUEUED, prepared.state)
        assertEquals(Outcome.NONE, prepared.outcome)
        assertEquals(Phase.PREPARE_AUDIO, prepared.phase)
        val chunk = checkpointOf(prepared).prepared.single()
        assertEquals(0, chunk.index)
        assertEquals(0L, chunk.offsetMs)
        assertEquals(windows[0].durationMs, chunk.durationMs)
        assertEquals(windows[1].offsetMs, chunk.offsetMs + chunk.durationMs)

        // The encoder's output really is longer than the window it was asked for; without that the
        // case above would no longer be exercised and this test would be worthless.
        val file = attemptFile(seeded.attempt.id, "audio-0.mp3")
        assertEquals(file.length(), chunk.bytes)
        val measured = AudioPreparation(runtime).probe(file).durationMs
        assertTrue(
            "encoder output $measured did not exceed the window ${windows[0].durationMs}",
            measured > windows[0].durationMs,
        )
        assertTrue(measured - windows[0].durationMs <= 250L)
    }

    /**
     * The other end of the same source: the last window of a multi-chunk plan, about a second long.
     *
     * The last window is the one case where the encoder comes out *shorter* than it was asked for rather than
     * longer - it cannot write more audio than the source still holds, while a full window overshoots by the
     * frames it rounds up to plus the encoder delay. Measured on the API 37 emulator with the bundled ffmpeg
     * 7.1.1: a window of 1 092 ms produced 1 080 ms of MP3, 12 ms short.
     *
     * What this test is for is the tiling: the two stored intervals have to meet at 600 000 ms and the second
     * has to end exactly at the source's own length, because `NORMALIZE` reads them back and reports
     * `AUDIO_INTERVAL_GAP_OR_OVERLAP` for a transcript whose audio was all there. Storing the encoded length
     * instead of the planned one leaves those 12 ms as a gap at the end of the source, and the first window
     * cannot show that: its own surplus is absorbed by the next window's planned offset.
     */
    @Test
    fun theShortLastWindowTilesExactlyToTheEndOfTheSource() = withFixture {
        val seeded = seed(sourceSeconds = 601)

        val probed = step.run(seeded.attempt, seeded.owner, seeded.config)
        val durationMs = requireNotNull(checkpointOf(probed).durationMs)
        val windows = SttStep.chunkPlan(durationMs)
        assertEquals(2, windows.size)
        assertTrue("the last window is not a short one: ${windows[1].durationMs}",
            windows[1].durationMs < SttStep.MAX_CHUNK_DURATION_MS)

        val firstChunk = step.run(probed, seeded.owner, seeded.config)
        assertNull(firstChunk.error)
        assertEquals(Phase.PREPARE_AUDIO, firstChunk.phase)

        val lastChunk = step.run(firstChunk, seeded.owner, seeded.config)
        assertNull(lastChunk.error)
        assertEquals(Outcome.NONE, lastChunk.outcome)
        // Every window is prepared, so the attempt moves on to submitting rather than preparing again.
        assertEquals(Phase.SUBMIT, lastChunk.phase)

        val chunks = checkpointOf(lastChunk).prepared.sortedBy { it.index }
        assertEquals(listOf(0, 1), chunks.map { it.index })
        // First the claim only the last window can make: the tiles end exactly where the source ends. The
        // first window's own surplus never shows here, because the next window's offset is planned, not
        // measured - which is why the existing test above cannot reach this.
        assertEquals("the last tile does not end at the source's length",
            durationMs, chunks[1].offsetMs + chunks[1].durationMs)
        // Then that they meet rather than leaving a gap or overlapping, and that each is its planned window.
        assertEquals(0L, chunks[0].offsetMs)
        assertEquals(chunks[1].offsetMs, chunks[0].offsetMs + chunks[0].durationMs)
        for ((index, window) in windows.withIndex()) {
            assertEquals("window $index offset", window.offsetMs, chunks[index].offsetMs)
            assertEquals("window $index duration", window.durationMs, chunks[index].durationMs)
        }

        // And the encoded file really is a different length from its window, so the case above is exercised
        // rather than passing because the encoder happened to hit the planned length exactly.
        val file = attemptFile(seeded.attempt.id, "audio-1.mp3")
        assertEquals(file.length(), chunks[1].bytes)
        val measured = AudioPreparation(runtime).probe(file).durationMs
        assertTrue(
            "encoder output $measured equals the short window ${windows[1].durationMs}, so nothing is tested",
            measured != windows[1].durationMs,
        )
        assertTrue("encoder output $measured is outside the tolerance around ${windows[1].durationMs}",
            kotlin.math.abs(measured - windows[1].durationMs) <= 250L)
    }

    private fun withFixture(block: suspend PreparationFixture.() -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = PreparationFixture(base)
        try {
            runBlocking { fixture.block() }
        } finally {
            fixture.close()
        }
    }

    /**
     * The database, the imports and the attempt directories are isolated, while the native runtime
     * keeps the target context: its packages are already unpacked there, and a second copy would
     * cost hundreds of megabytes on the device.
     */
    private class PreparationFixture(base: Context) {
        val root = File(base.cacheDir, "stt-preparation-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        private val context = IsolatedContext(base, root)
        private val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        val dao = database.records()
        private val credentials = CredentialStore(context)
        val runtime = NativeRuntime(base)
        private val extractor = ExtractorEngine(runtime)
        private val engines = EngineUpdateManager(context, runtime)
        private val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))
        private val refuseHttp = Interceptor { throw AssertionError("provider HTTP is forbidden in SttPreparationTest") }
        val step = SttStep(
            context,
            database,
            dao,
            credentials,
            runtime,
            extractor,
            engines,
            artifacts,
            ProviderHttp(OkHttpClient.Builder().addInterceptor(refuseHttp).build()),
        )

        suspend fun seed(sourceSeconds: Int): Seeded {
            runtime.initialize()
            val imports = File(context.noBackupFilesDir, "imports").also { check(it.mkdirs()) }
            val audio = File(imports, "source.mp3")
            generateTone(audio, sourceSeconds)
            val contentHash = sha256(audio)
            val source = Source(
                id = "local:$contentHash",
                kind = SourceKind.LOCAL_AUDIO,
                contentHash = contentHash,
                fileName = audio.name,
                mimeType = "audio/mpeg",
                fileBytes = audio.length(),
                durationMs = sourceSeconds * 1_000L,
            )
            val credentialId = credentials.save(Provider.GROQ, Region.US, "preparation-test-key-${UUID.randomUUID()}")
            val config = JobConfig(
                mode = AcquisitionMode.STT_ONLY,
                provider = Provider.GROQ,
                model = GroqAdapter.MODEL_TURBO,
                region = Region.US,
                credentialId = credentialId,
                uploadApproved = true,
                maxAudioSeconds = 3_600,
                maxCostMicrousd = 10_000_000L,
                audioRetention = AudioRetention.TEMPORARY,
            )
            val jobId = UUID.randomUUID().toString()
            val attemptId = UUID.randomUUID().toString()
            val owner = "preparation-owner-${UUID.randomUUID()}"
            val now = System.currentTimeMillis()
            val attempt = AttemptRow(
                id = attemptId,
                jobId = jobId,
                branch = Branch.STT,
                number = 1,
                createdAt = now,
                state = ExecutionState.RUNNING,
                phase = Phase.PREPARE_AUDIO,
                checkpoint = json.encodeToString(
                    FixtureCheckpoint(
                        artifactId = UUID.randomUUID().toString(),
                        artifactCreatedAt = now,
                        source = source,
                    ),
                ),
                leaseOwner = owner,
                leaseUntil = now + LEASE_MS,
            )
            dao.createJob(
                source = SourceRow(source.id, json.encodeToString(source), "Preparation audio", audio.absolutePath),
                job = JobRow(jobId, source.id, json.encodeToString(config), now),
                attempts = listOf(attempt),
            )
            return Seeded(attempt, owner, config)
        }

        fun checkpointOf(row: AttemptRow): FixtureCheckpoint = json.decodeFromString(row.checkpoint)

        fun attemptFile(attemptId: String, name: String): File =
            File(File(File(context.noBackupFilesDir, "attempts"), attemptId), name)

        private suspend fun generateTone(output: File, seconds: Int) {
            val result = runtime.ffmpeg(
                listOf(
                    "-nostdin",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-f",
                    "lavfi",
                    "-i",
                    "sine=frequency=440:duration=$seconds",
                    "-ac",
                    "1",
                    "-ar",
                    "16000",
                    "-c:a",
                    "libmp3lame",
                    "-b:a",
                    "64k",
                    "-f",
                    "mp3",
                    "-y",
                    output.absolutePath,
                ),
                timeoutSeconds = 300,
            )
            check(result.exitCode == 0) { "fixture audio could not be generated" }
            check(output.length() > 0L) { "fixture audio is empty" }
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        }

        fun close() {
            database.close()
            root.deleteRecursively()
        }
    }

    private data class Seeded(val attempt: AttemptRow, val owner: String, val config: JobConfig)

    private class IsolatedContext(base: Context, root: File) : ContextWrapper(base) {
        private val files = File(root, "files").also { check(it.mkdirs()) }
        private val noBackup = File(root, "no-backup").also { check(it.mkdirs()) }
        private val cache = File(root, "cache").also { check(it.mkdirs()) }

        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = files
        override fun getNoBackupFilesDir(): File = noBackup
        override fun getCacheDir(): File = cache
    }

    @Serializable
    private data class FixtureCheckpoint(
        val artifactId: String,
        val artifactCreatedAt: Long,
        val source: Source,
        val durationMs: Long? = null,
        val chunkCount: Int = 0,
        val nextChunkIndex: Int = 0,
        val prepared: List<FixtureChunk> = emptyList(),
    )

    @Serializable
    private data class FixtureChunk(
        val index: Int,
        val offsetMs: Long,
        val durationMs: Long,
        val bytes: Long,
        val sha256: String,
        val mimeType: String,
    )

    private companion object {
        const val LEASE_MS = 9 * 60_000L
        val json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }
    }
}
