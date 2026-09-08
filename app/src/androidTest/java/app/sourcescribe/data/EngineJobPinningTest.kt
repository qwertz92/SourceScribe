package app.sourcescribe.data

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import app.sourcescribe.core.ProviderHttp
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceResolver
import app.sourcescribe.extractor.EngineChannel
import app.sourcescribe.extractor.EngineInstallation
import app.sourcescribe.extractor.EngineUpdateManager
import app.sourcescribe.extractor.ExtractorEngine
import app.sourcescribe.extractor.NativeRuntime
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in T26 evidence for two running Coordinator jobs across a real signed update.
 * Job captions come from a test-only engine; update activation and old-runtime probes are real.
 */
@RunWith(AndroidJUnit4::class)
class EngineJobPinningTest {
    @Test
    fun runningCoordinatorJobsFinishWithTheirPinnedEngineAcrossRealActivation() {
        requireEnabled()
        val arguments = InstrumentationRegistry.getArguments()
        val sourceArgument = arguments.getString("engineProbeSource").orEmpty()
        assumeTrue("engineProbeSource instrumentation argument is required", sourceArgument.isNotBlank())
        val source = SourceResolver.youtube(sourceArgument)
        val channel = arguments.getString("engineUpdateChannel")
            ?.let(EngineChannel::valueOf) ?: EngineChannel.NIGHTLY

        withFixture {
            val realOld = engines.active()
            val realOldFile = engines.file(realOld)
            val realOldHash = sha256(realOldFile)
            val realOldVersions = runtime.versions(realOldFile)
            assertEquals(realOld.sha256, realOldHash)
            assertEquals(realOld.version, realOldVersions["yt-dlp"])
            assertEquals(realOld.ejsVersion, realOldVersions["ejs"])

            val update = requireNotNull(engines.check(channel, force = true)) {
                "NO_NEW_RELEASE_FOR_CONFIGURED_CHANNEL"
            }
            val staged = engines.stage(update)
            assertNotEquals(realOld.id, staged.id)

            val fixtureEngine = installCaptionBarrierEngine(realOld, source)
            assertEquals(fixtureEngine.id, engines.active().id)
            val fixtureVersions = runtime.versions(engines.file(fixtureEngine))
            assertEquals(realOld.version, fixtureVersions["yt-dlp"])
            assertEquals(fixtureEngine.ejsVersion, fixtureVersions["ejs"])

            val pending = List(2) { createAttempt(source) }
            assertEquals(2, pending.map(AttemptRow::jobId).distinct().size)
            val activated = coroutineScope {
                val runs = pending.map { attempt ->
                    async(Dispatchers.IO) { coordinator.run(attempt.id) }
                }
                val activation = try {
                    awaitCaptionBarrier(pending)
                    assertEquals(2, captionBarrierCount())
                    pending.forEach { attempt ->
                        val running = requireNotNull(dao.attempt(attempt.id))
                        assertEquals(ExecutionState.RUNNING, running.state)
                        assertEquals(Phase.FETCH_CAPTIONS, running.phase)
                        assertEquals(fixtureEngine.id, running.engineId)
                        assertTrue(running.leaseOwner != null)
                        assertTrue(running.leaseUntil > System.currentTimeMillis())
                    }
                    engines.activate(staged.id, source)
                } finally {
                    releaseCaptionBarrier()
                }
                assertEquals(listOf(false, false), runs.awaitAll())
                activation
            }
            assertEquals(staged.id, activated.id)
            assertEquals(staged.id, engines.active().id)

            pending.forEach { originalAttempt ->
                val finished = requireNotNull(dao.attempt(originalAttempt.id))
                assertEquals(ExecutionState.FINISHED, finished.state)
                assertEquals(Outcome.SUCCESS, finished.outcome)
                assertEquals(fixtureEngine.id, finished.engineId)
                assertEquals(ExecutionState.FINISHED, requireNotNull(dao.job(finished.jobId)).state)
                val document = artifacts.read(dao.artifacts(finished.jobId).single().id)
                assertEquals(SYNTHETIC_CAPTION, document.text)
                assertEquals(
                    mapOf("yt-dlp" to fixtureEngine.version, "yt-dlp-ejs" to fixtureEngine.ejsVersion),
                    document.provenance.engineVersions,
                )
            }

            val retainedRealOld = engines.installations().single { it.id == realOld.id }
            val retainedRealOldFile = engines.file(retainedRealOld)
            assertEquals(realOld, retainedRealOld)
            assertEquals(realOldFile.canonicalFile, retainedRealOldFile.canonicalFile)
            assertEquals(realOldHash, sha256(retainedRealOldFile))
            val retainedRealOldVersions = runtime.versions(retainedRealOldFile)
            assertEquals(realOld.version, retainedRealOldVersions["yt-dlp"])
            assertEquals(realOld.ejsVersion, retainedRealOldVersions["ejs"])

            val newAttempt = createAttempt(source)
            assertEquals(ExecutionState.QUEUED, newAttempt.state)
            assertEquals(activated.id, newAttempt.engineId)
            assertEquals(0, providerRequests())

            InstrumentationRegistry.getInstrumentation().sendStatus(126, Bundle().apply {
                putString("job.execution", "TESTED_WITH_FIXTURES")
                putInt("job.finished", pending.size)
                putString("fixture.engineId", fixtureEngine.id)
                putString("fixture.caption", SYNTHETIC_CAPTION)
                putString("fixture.ytDlpMetadata", "DECLARED_FROM_REAL_OLD_PROBE")
                putString("fixture.ejsMetadata", "DECLARED_FROM_REAL_OLD_PROBE")
                putString("real.old.engineId", realOld.id)
                putString("real.old.ytDlp", retainedRealOldVersions["yt-dlp"])
                putString("real.old.ejs", retainedRealOldVersions["ejs"])
                putString("real.old.sha256", realOldHash)
                putString("real.new.engineId", activated.id)
                putString("real.new.ytDlp", activated.version)
                putString("real.new.ejs", activated.ejsVersion)
                putString("real.new.sha256", activated.sha256)
                putString("real.activationProbe", "YOUTUBE_SOURCE_ARGUMENT")
                putString("real.updateVerification", "SIGNED_HASH_RUNTIME")
            })
        }
    }

    private fun withFixture(block: suspend Fixture.() -> Unit) {
        val fixture = Fixture(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            runBlocking {
                fixture.prepareNativeRuntime()
                fixture.block()
            }
        } finally {
            fixture.close()
        }
    }

    private class Fixture(private val base: Context) {
        private val root = File(base.cacheDir, "engine-job-pinning-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        private val context = IsolatedContext(base, root)
        private val runtimeLink = File(context.noBackupFilesDir, "youtubedl-android")
        private val barrierDirectory = File(root, "caption-barrier")
        private val barrierRelease = File(barrierDirectory, "release")
        private val workManager = ensureWorkManager(base)
        private val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        private val jobIds = mutableSetOf<String>()
        val dao = database.records()
        val runtime = NativeRuntime(context)
        var engines = EngineUpdateManager(context, runtime)
            private set
        val artifacts = ArtifactFiles(File(context.filesDir, "artifacts"))
        private val settings = SettingsStore(context)
        private val extractor = ExtractorEngine(runtime)
        private val credentials = CredentialStore(context)
        private val providerGuard = ProviderRequestGuard()
        private val providerHttp = ProviderHttp(
            OkHttpClient.Builder().addInterceptor(providerGuard.interceptor).build(),
        )
        var coordinator = newCoordinator(engines)
            private set

        suspend fun createAttempt(source: Source): AttemptRow {
            val jobId = coordinator.create(source, JobConfig(mode = AcquisitionMode.CAPTIONS_ONLY))
            jobIds += jobId
            return dao.attempts(jobId).single()
        }

        suspend fun prepareNativeRuntime() {
            NativeRuntime(base).initialize()
            val actualRuntime = File(base.noBackupFilesDir, runtimeLink.name)
            check(actualRuntime.isDirectory)
            Files.createSymbolicLink(runtimeLink.toPath(), actualRuntime.toPath())
        }

        fun installCaptionBarrierEngine(
            realOld: EngineInstallation,
            source: Source,
        ): EngineInstallation {
            check(barrierDirectory.mkdirs())
            val videoId = requireNotNull(source.videoId)
            val script = """
                # SourceScribe T26 Android instrumentation fixture; never packaged in production.
                import json
                import os
                import pathlib
                import sys
                import time

                barrier = pathlib.Path(json.loads(r'''${JSONObject.quote(barrierDirectory.absolutePath)}'''))
                release = barrier / "release"
                video_id = ${JSONObject.quote(videoId)}

                if "--version" in sys.argv:
                    # Declared fixture metadata; the actual old engine is probed separately before/after activation.
                    print(${JSONObject.quote(realOld.version)})
                    raise SystemExit(0)
                if "--dump-single-json" in sys.argv:
                    print(json.dumps({
                        "id": video_id,
                        "title": "T26 synthetic fixture source",
                        "duration": 1,
                        "language": "en",
                        "subtitles": {"en": [{
                            "ext": "vtt",
                            "name": "English",
                            "url": f"https://www.youtube.com/api/timedtext?v={video_id}&lang=en",
                        }]},
                    }))
                    raise SystemExit(0)
                if "--load-info-json" in sys.argv:
                    (barrier / f"arrived-{os.getpid()}").write_text("FETCH_CAPTIONS", encoding="ascii")
                    deadline = time.monotonic() + 170
                    while not release.is_file() and time.monotonic() < deadline:
                        time.sleep(0.02)
                    if not release.is_file():
                        print("T26 fixture barrier timed out", file=sys.stderr)
                        raise SystemExit(70)
                    info = pathlib.Path(sys.argv[sys.argv.index("--load-info-json") + 1])
                    (info.parent / f"{video_id}.en.vtt").write_text(
                        "WEBVTT\n\n00:00.000 --> 00:01.000\n$SYNTHETIC_CAPTION\n",
                        encoding="utf-8",
                    )
                    print(f"SS_SOURCE_ID={video_id}")
                    raise SystemExit(0)
                raise SystemExit(64)
            """.trimIndent().toByteArray(Charsets.UTF_8)
            val temporary = File(root, "fixture-engine-${UUID.randomUUID()}.zip")
            ZipOutputStream(temporary.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("__main__.py"))
                output.write(script)
                output.closeEntry()
                output.putNextEntry(ZipEntry("yt_dlp_ejs/__init__.py"))
                output.write("version = ${JSONObject.quote(realOld.ejsVersion)}\n".toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
            val id = sha256(temporary)
            val enginesDirectory = File(context.noBackupFilesDir, "engines")
            val slot = File(enginesDirectory, id).also { check(it.mkdir()) }
            Files.move(temporary.toPath(), File(slot, "yt-dlp").toPath())
            val installation = EngineInstallation(
                id = id,
                version = realOld.version,
                ejsVersion = realOld.ejsVersion,
                channel = realOld.channel,
                gitHead = null,
                sha256 = id,
                healthy = true,
                bundled = false,
            )
            val stateFile = File(enginesDirectory, "state.json")
            val state = JSONObject(stateFile.readText(Charsets.UTF_8))
            state.put("active", id)
            state.put("previous", realOld.id)
            state.getJSONArray("healthy").put(id)
            state.getJSONArray("installations").put(JSONObject()
                .put("id", id)
                .put("version", installation.version)
                .put("ejsVersion", installation.ejsVersion)
                .put("channel", installation.channel.name)
                .put("sha256", id)
                .put("healthy", true)
                .put("bundled", false))
            stateFile.writeText(state.toString(), Charsets.UTF_8)

            engines = EngineUpdateManager(context, runtime)
            coordinator = newCoordinator(engines)
            return installation
        }

        suspend fun awaitCaptionBarrier(attempts: List<AttemptRow>) {
            try {
                withTimeout(BARRIER_ARRIVAL_TIMEOUT_MS) {
                    while (captionBarrierCount() < 2) {
                        delay(BARRIER_POLL_MS)
                    }
                }
            } catch (failure: TimeoutCancellationException) {
                val attemptStates = attempts.mapIndexed { index, original ->
                    val current = dao.attempt(original.id)
                    "attempt[$index]=" + if (current == null) {
                        "MISSING"
                    } else {
                        "${current.state}/${current.phase}/error=${current.error ?: "none"}/retries=${current.retries}"
                    }
                }.joinToString(";")
                throw AssertionError(
                    "caption barrier timed out: arrived=${captionBarrierCount()};" +
                        " parallelJobs=${settings.settings.first().parallelJobs}; $attemptStates",
                    failure,
                )
            }
        }

        fun captionBarrierCount(): Int =
            barrierDirectory.listFiles()?.count { it.name.startsWith("arrived-") } ?: 0

        fun releaseCaptionBarrier() {
            if (!barrierDirectory.exists()) check(barrierDirectory.mkdirs())
            barrierRelease.writeText("release", Charsets.US_ASCII)
        }

        fun providerRequests(): Int = providerGuard.requestCount

        fun close() {
            releaseCaptionBarrier()
            try {
                jobIds.forEach { workManager.cancelAllWorkByTag("job:$it") }
                database.close()
            } finally {
                try {
                    Files.deleteIfExists(runtimeLink.toPath())
                } finally {
                    root.deleteRecursively()
                }
            }
        }

        private fun newCoordinator(manager: EngineUpdateManager): JobCoordinator {
            val stt = SttStep(
                context,
                database,
                dao,
                credentials,
                runtime,
                extractor,
                manager,
                artifacts,
                providerHttp,
            )
            return JobCoordinator(
                context,
                database,
                dao,
                settings,
                extractor,
                manager,
                artifacts,
                stt,
                ExportStore(context, dao, artifacts),
                JobNotifications(context),
                StorageBudget(context, settings),
                credentials,
                providerHttp,
            )
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

    private class ProviderRequestGuard {
        private val requests = AtomicInteger()
        val interceptor = Interceptor {
            requests.incrementAndGet()
            throw AssertionError("provider HTTP is forbidden in EngineJobPinningTest")
        }
        val requestCount: Int get() = requests.get()
    }

    private companion object {
        const val BARRIER_ARRIVAL_TIMEOUT_MS = 30_000L
        const val BARRIER_POLL_MS = 20L
        const val SYNTHETIC_CAPTION = "T26 synthetic pinned caption"

        fun requireEnabled() = assumeTrue(
            "Pass sourcescribeEngineJobPinning=true and engineProbeSource=<URL> for live T26 evidence",
            InstrumentationRegistry.getArguments().getString("sourcescribeEngineJobPinning") == "true",
        )

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun ensureWorkManager(context: Context): WorkManager = try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            WorkManager.getInstance(context)
        }
    }
}
