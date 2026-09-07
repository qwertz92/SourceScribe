package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.testing.WorkManagerTestInitHelper
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchCreationTest {
    @Test
    fun savesTwoLocalJobsWithTheirImmutableSnapshots() = withFixture {
        val first = localSource("a", "First audio")
        val second = localSource("b", "Second audio")
        val firstConfig = JobConfig(mode = AcquisitionMode.STT_ONLY, language = "de", maxCostMicrousd = 1_000)
        val secondConfig = JobConfig(mode = AcquisitionMode.STT_ONLY, language = "en", maxCostMicrousd = 2_000)
        insertImported(first)
        insertImported(second)

        val ids = coordinator.createBatch(listOf(first.source to firstConfig, second.source to secondConfig))

        jobIds += ids
        val jobs = dao.allJobs().associateBy { it.sourceId }
        assertEquals(2, jobs.size)
        assertEquals(firstConfig, json.decodeFromString<JobConfig>(requireNotNull(jobs[first.source.id]).config))
        assertEquals(secondConfig, json.decodeFromString<JobConfig>(requireNotNull(jobs[second.source.id]).config))
        assertEquals(setOf(first.row, second.row), dao.observeSources().first().toSet())
        assertEquals(
            mapOf(first.source.id to first.source, second.source.id to second.source),
            jobs.mapValues { (_, job) ->
                val checkpoint = json.decodeFromString<AttemptCheckpoint>(dao.attempts(job.id).single().checkpoint)
                assertEquals(null, dao.attempts(job.id).single().engineId)
                requireNotNull(checkpoint.source)
            },
        )
        assertEquals(0, providerRequests())
    }

    @Test
    fun invalidSecondInputPersistsNothing() = withFixture {
        val first = localSource("c", "Valid audio").source
        val second = localSource("d", "Invalid audio").source

        val failure = runCatching {
            coordinator.createBatch(
                listOf(
                    first to JobConfig(mode = AcquisitionMode.STT_ONLY),
                    second to JobConfig(mode = AcquisitionMode.CAPTIONS_ONLY),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(dao.observeSources().first().isEmpty())
        assertTrue(dao.allJobs().isEmpty())
        assertTrue(dao.allAttempts().isEmpty())
        assertEquals(0, providerRequests())
    }

    @Test
    fun sqliteAbortOnSecondJobRollsBackWholeBatchWithoutEnqueue() = withFixture {
        val beforeWork = activeWorkIds()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER abort_second_batch_job
            BEFORE INSERT ON jobs
            WHEN (SELECT COUNT(*) FROM jobs) = 1
            BEGIN
                SELECT RAISE(ABORT, 'batch_abort_second_job');
            END
            """.trimIndent(),
        )

        val failure: Throwable? = runCatching {
            coordinator.createBatch(
                listOf(
                    localSource("e", "Rollback one").source to JobConfig(mode = AcquisitionMode.STT_ONLY),
                    localSource("f", "Rollback two").source to JobConfig(mode = AcquisitionMode.STT_ONLY),
                ),
            )
        }.exceptionOrNull()

        assertTrue(generateSequence(failure) { it.cause }.any { it.message.orEmpty().contains("batch_abort_second_job") })
        assertTrue(dao.observeSources().first().isEmpty())
        assertTrue(dao.allJobs().isEmpty())
        assertTrue(dao.allAttempts().isEmpty())
        assertEquals(beforeWork, activeWorkIds())
        assertEquals(0, providerRequests())
    }

    private fun <T> withFixture(block: suspend Fixture.() -> T): T {
        val fixture = Fixture(InstrumentationRegistry.getInstrumentation().targetContext)
        return try {
            runBlocking { fixture.block() }
        } finally {
            fixture.close()
        }
    }

    private class Fixture(base: Context) {
        private val root = File(base.cacheDir, "batch-creation-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        private val context = IsolatedContext(base, root)
        private val workManager = ensureWorkManager(base)
        val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        val dao = database.records()
        val jobIds = mutableSetOf<String>()
        val json = Json { encodeDefaults = true }
        private val settings = SettingsStore(context)
        private val runtime = NativeRuntime(context)
        private val extractor = ExtractorEngine(runtime)
        private val engines = EngineUpdateManager(context, runtime)
        private val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))
        private val exports = ExportStore(context, dao, artifacts)
        private val notifications = JobNotifications(context)
        private val storage = StorageBudget(context, settings)
        private val credentials = CredentialStore(context)
        private val providerGuard = ProviderRequestGuard()
        private val providerHttp = ProviderHttp(
            OkHttpClient.Builder().addInterceptor(providerGuard.interceptor).build(),
        )
        private val stt = SttStep(
            context,
            database,
            dao,
            credentials,
            runtime,
            extractor,
            engines,
            artifacts,
            providerHttp,
        )
        val coordinator = JobCoordinator(
            context,
            database,
            dao,
            settings,
            extractor,
            engines,
            artifacts,
            stt,
            exports,
            notifications,
            storage,
            credentials,
            providerHttp,
        )

        fun localSource(suffix: String, title: String): LocalSource {
            val hash = suffix.repeat(64)
            val source = Source(
                id = "local:$hash",
                kind = SourceKind.LOCAL_AUDIO,
                contentHash = hash,
                title = title,
                fileName = "$suffix.wav",
                mimeType = "audio/wav",
                fileBytes = 1,
                durationMs = 1_000,
            )
            val imported = File(context.noBackupFilesDir, "imports/$hash.audio").also {
                check(it.parentFile?.mkdirs() == true || it.parentFile?.isDirectory == true)
                it.writeText(suffix)
            }
            return LocalSource(
                source,
                SourceRow(source.id, json.encodeToString(source), title, imported.absolutePath),
            )
        }

        suspend fun insertImported(source: LocalSource) {
            assertTrue(dao.insertSource(source.row) > 0)
        }

        fun activeWorkIds(): Set<UUID> = workManager.getWorkInfos(
            WorkQuery.Builder.fromStates(
                listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED),
            ).build(),
        ).get(10, TimeUnit.SECONDS).mapTo(mutableSetOf()) { it.id }

        fun providerRequests(): Int = providerGuard.requestCount

        fun close() {
            jobIds.forEach { workManager.cancelAllWorkByTag("job:$it") }
            database.close()
            root.deleteRecursively()
        }
    }

    private class IsolatedContext(base: Context, root: File) : ContextWrapper(base) {
        private val files = File(root, "files").also { check(it.mkdirs()) }
        private val noBackup = File(root, "no-backup").also { check(it.mkdirs()) }
        private val cache = File(root, "cache").also { check(it.mkdirs()) }

        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = files
        override fun getNoBackupFilesDir(): File = noBackup
        override fun getCacheDir(): File = cache
    }

    private data class LocalSource(val source: Source, val row: SourceRow)

    private class ProviderRequestGuard {
        private val requests = AtomicInteger()
        val interceptor = Interceptor {
            requests.incrementAndGet()
            throw AssertionError("provider HTTP is forbidden in BatchCreationTest")
        }
        val requestCount: Int get() = requests.get()
    }

    private companion object {
        fun ensureWorkManager(context: Context): WorkManager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            WorkManager.getInstance(context)
        }
    }
}
