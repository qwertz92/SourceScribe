package app.sourcescribe.extractor

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.EngineVerificationCode
import app.sourcescribe.core.EngineVerificationException
import app.sourcescribe.core.EngineVerifier
import app.sourcescribe.core.SourceResolver
import app.sourcescribe.core.boundedJsonText
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Update checks are opt-in because they execute the real bundled runtime and
 * network tests must never be mistaken for deterministic instrumentation.
 */
@RunWith(AndroidJUnit4::class)
class EngineUpdateManagerTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bundledInstallationIsOfficialAndLivesInItsHashSlot() = runBlocking {
        withIsolatedManager { harness ->
            val installation = harness.manager.bundled()
            assertEquals("1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6", installation.id)
            assertEquals(installation.id, installation.sha256)
            assertTrue(installation.bundled)
            assertTrue(installation.healthy)
            val file = harness.manager.file(installation)
            assertEquals("yt-dlp", file.name)
            assertEquals(installation.id, file.parentFile?.name)
            assertTrue(file.isFile)
            assertEquals(installation.id, sha256(file))
        }
    }

    @Test
    fun corruptStateFallsBackToVerifiedBundledSlot() = runBlocking {
        withIsolatedManager { harness ->
            val expected = harness.manager.bundled()
            val state = stateFile(harness)
            state.writeText("{not-json", Charsets.UTF_8)
            val recovered = newManager(harness).active()
            assertEquals(expected.id, recovered.id)
            assertTrue(recovered.healthy)
        }
    }

    @Test
    fun badChecksumCannotBecomeAnActiveInstallation() = runBlocking {
        withIsolatedManager { harness ->
            val bundled = harness.manager.bundled()
            val checksums = context.resources.openRawResource(R.raw.ytdlp_checksums).use { it.readBytes() }
            checksums[0] = (checksums[0].toInt() xor 1).toByte()
            val signature = context.resources.openRawResource(R.raw.ytdlp_checksums_sig).use { it.readBytes() }
            val failure = assertThrows(EngineVerificationException::class.java) {
                EngineVerifier.verify(harness.manager.file(bundled), checksums, signature)
            }
            assertEquals(EngineVerificationCode.SIGNATURE, failure.code)
            assertEquals(bundled.id, harness.manager.active().id)
        }
    }

    @Test
    fun activeCorruptionFallsBackToBundledAndFileRejectsTheSlot() = runBlocking {
        withIsolatedManager { harness ->
            val bundled = harness.manager.bundled()
            val corrupt = fakeInstallation("a".repeat(64))
            createRegularSlot(harness, corrupt, "corrupt".toByteArray())
            writeState(harness, active = corrupt, previous = null, candidate = corrupt)

            val restarted = newManager(harness)
            assertThrows(EngineUpdateException::class.java) { restarted.file(corrupt) }
            assertEquals(bundled.id, restarted.active().id)
        }
    }

    @Test
    fun activeSymlinkFallsBackToBundledAndFileRejectsTheSlot() = runBlocking {
        withIsolatedManager { harness ->
            val bundled = harness.manager.bundled()
            val symlinked = fakeInstallation("b".repeat(64))
            createSymlinkSlot(harness, symlinked)
            writeState(harness, active = symlinked, previous = null, candidate = symlinked)

            val restarted = newManager(harness)
            assertThrows(EngineUpdateException::class.java) { restarted.file(symlinked) }
            assertEquals(bundled.id, restarted.active().id)
        }
    }

    @Test
    fun oversizedActiveSlotFallsBackToBundled() = runBlocking {
        withIsolatedManager { harness ->
            val bundled = harness.manager.bundled()
            val oversized = fakeInstallation("9".repeat(64))
            val slot = File(harness.root, "engines/${oversized.id}")
            assertTrue(slot.mkdirs())
            RandomAccessFile(File(slot, "yt-dlp"), "rw").use {
                it.setLength(EngineVerifier.MAX_ARTIFACT_BYTES.toLong() + 1L)
            }
            writeState(harness, active = oversized, previous = null, candidate = oversized)

            assertEquals(bundled.id, newManager(harness).active().id)
        }
    }

    @Test
    fun corruptPreviousCannotBeUsedForRollback() = runBlocking {
        withIsolatedManager { harness ->
            val bundled = harness.manager.bundled()
            val corrupt = fakeInstallation("c".repeat(64))
            createRegularSlot(harness, corrupt, "corrupt".toByteArray())
            writeState(harness, active = bundled, previous = corrupt, candidate = corrupt)

            val failure = expectUpdateFailure { newManager(harness).rollback() }
            assertEquals(EngineUpdateCode.NO_PREVIOUS, failure.code)
        }
    }

    @Test
    fun symlinkPreviousCannotBeUsedForRollback() = runBlocking {
        withIsolatedManager { harness ->
            val bundled = harness.manager.bundled()
            val symlinked = fakeInstallation("d".repeat(64))
            createSymlinkSlot(harness, symlinked)
            writeState(harness, active = bundled, previous = symlinked, candidate = symlinked)

            val failure = expectUpdateFailure { newManager(harness).rollback() }
            assertEquals(EngineUpdateCode.NO_PREVIOUS, failure.code)
        }
    }

    @Test
    fun discardUnhealthyCandidateRemovesOnlyTheUnhealthySlot() = runBlocking {
        withIsolatedManager { harness ->
            harness.manager.bundled()
            val candidate = fakeInstallation("e".repeat(64), healthy = false)
            createRegularSlot(harness, candidate, "candidate".toByteArray())
            writeState(harness, active = null, previous = null, candidate = candidate)

            assertTrue(newManager(harness).discardUnhealthyCandidate(candidate.id))
            assertTrue(!File(harness.root, "engines/${candidate.id}").exists())
        }
    }

    @Test
    fun initialLoadCleansOnlyUnregisteredCrashArtifacts() = runBlocking {
        withIsolatedManager { harness ->
            val bundled = harness.manager.bundled()
            val orphan = fakeInstallation("f".repeat(64))
            createRegularSlot(harness, orphan, "orphan".toByteArray())
            val temporary = File(harness.root, "engines/.slot-${UUID.randomUUID()}")
            assertTrue(temporary.mkdirs())
            File(temporary, "partial").writeText("partial")

            assertEquals(bundled.id, newManager(harness).active().id)
            assertTrue(!File(harness.root, "engines/${orphan.id}").exists())
            assertTrue(!temporary.exists())
            assertTrue(harness.manager.file(bundled).isFile)
        }
    }

    @Test
    fun fileRejectsAnUnmaterializedHashSlot() {
        val root = File(context.cacheDir, "engine-manager-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        try {
            val storage = IsolatedStorageContext(context, root)
            val manager = EngineUpdateManager(storage, NativeRuntime(storage))
            val installation = fakeInstallation("0".repeat(64))
            val failure = assertThrows(EngineUpdateException::class.java) { manager.file(installation) }
            assertEquals(EngineUpdateCode.VERIFICATION, failure.code)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun notModifiedResponseIsClassifiedBeforeRedirect() {
        assertEquals(EngineHttpResponseKind.NOT_MODIFIED, classifyHttpResponse(304))
        assertEquals(EngineHttpResponseKind.REDIRECT, classifyHttpResponse(302))
    }

    @Test
    fun offlineUpdateCheckKeepsTheActiveSlotAndDoesNotStage() = runBlocking {
        withIsolatedManager { harness ->
            val active = harness.manager.bundled()
            val slots = hashSlotNames(harness)
            val manager = newManager(harness, fixtureCalls { request ->
                assertEquals(STABLE_RELEASE_URL, request.url.toString())
                throw IOException("offline fixture")
            })

            val failure = expectUpdateFailure { manager.check(EngineChannel.STABLE, force = true) }

            assertEquals(EngineUpdateCode.NETWORK, failure.code)
            assertEquals(active.id, manager.active().id)
            assertEquals(slots, hashSlotNames(harness))
            assertNoUpdateTemporaryDirectories(harness)
        }
    }

    @Test
    fun rateLimitedUpdateCheckPersistsRetryAfterWithoutAnotherRequestOrStage() = runBlocking {
        withIsolatedManager { harness ->
            val active = harness.manager.bundled()
            val slots = hashSlotNames(harness)
            val requests = AtomicInteger()
            val calls = fixtureCalls { request ->
                assertEquals(STABLE_RELEASE_URL, request.url.toString())
                requests.incrementAndGet()
                response(request, 429, "Retry-After" to "17")
            }
            val first = expectUpdateFailure {
                newManager(harness, calls).check(EngineChannel.STABLE, force = true)
            }
            val second = expectUpdateFailure {
                newManager(harness, calls).check(EngineChannel.STABLE, force = true)
            }

            assertEquals(EngineUpdateCode.RATE_LIMIT, first.code)
            assertEquals(17L, first.retryAfterSeconds)
            assertEquals(EngineUpdateCode.RATE_LIMIT, second.code)
            assertTrue(requireNotNull(second.retryAfterSeconds) in 1L..17L)
            assertEquals(1, requests.get())
            assertEquals(active.id, newManager(harness).active().id)
            assertEquals(slots, hashSlotNames(harness))
            assertNoUpdateTemporaryDirectories(harness)
        }
    }

    @Test
    fun artifactWriteFailureKeepsActiveSlotAndCleansStagingAcrossRestart() = runBlocking {
        withIsolatedManager { harness ->
            val active = harness.manager.bundled()
            val repository = when (active.channel) {
                EngineChannel.STABLE -> "yt-dlp/yt-dlp"
                EngineChannel.NIGHTLY -> "yt-dlp/yt-dlp-nightly-builds"
            }
            val releaseBase = "https://github.com/$repository/releases/download/${active.version}"
            val expectedRequests = ArrayDeque(
                listOf(
                    "$releaseBase/SHA2-256SUMS",
                    "$releaseBase/SHA2-256SUMS.sig",
                    "$releaseBase/yt-dlp",
                ),
            )
            val calls = fixtureCalls { request ->
                assertEquals(expectedRequests.removeFirst(), request.url.toString())
                response(request, 200)
            }
            val manager = EngineUpdateManager(harness.storage, NativeRuntime(harness.storage), calls) { _, _ ->
                throw IOException("controlled disk-full fixture")
            }
            assertEquals(active.id, manager.bundled().id)
            val slots = hashSlotNames(harness)
            val update = AvailableEngine(
                id = active.version,
                version = active.version,
                ejsVersion = active.ejsVersion,
                channel = active.channel,
                gitHead = active.gitHead,
                sha256 = active.sha256,
            )

            val failure = expectUpdateFailure { manager.stage(update) }

            assertEquals(EngineUpdateCode.STORAGE, failure.code)
            assertTrue(expectedRequests.isEmpty())
            assertEquals(active.id, manager.active().id)
            assertEquals(slots, hashSlotNames(harness))
            assertNoUpdateTemporaryDirectories(harness)
            assertEquals(active.id, newManager(harness).active().id)
            assertEquals(slots, hashSlotNames(harness))
        }
    }

    @Test
    fun privateAndOfflineProbeFailuresKeepCandidateStagedAndActiveSlotUnchanged() = runBlocking {
        withIsolatedManager { harness ->
            val active = harness.manager.bundled()
            val candidates = listOf(
                createScriptedCandidate(harness, "ERROR: Private video. Sign in if you have access"),
                createScriptedCandidate(harness, "ERROR: Unable to download webpage: Temporary failure in name resolution"),
            )
            candidates.forEach { writeState(harness, active = active, previous = null, candidate = it) }
            val manager = newManager(harness)
            val source = SourceResolver.youtube("https://www.youtube.com/watch?v=dQw4w9WgXcQ")

            candidates.forEach { candidate ->
                val failure = expectUpdateFailure { manager.activate(candidate.id, source) }
                assertEquals(EngineUpdateCode.UNCERTAIN_PROBE, failure.code)
                assertEquals(active.id, manager.active().id)
                assertTrue(manager.installations().any { it.id == candidate.id && it.healthy })
            }
        }
    }

    @Test
    fun realReleaseStageActivateAndRollbackSurvivesManagerRestart() = runBlocking {
        assumeTrue(
            "Pass sourcescribeEngineLiveUpdate=true and engineProbeSource=<URL> for live update evidence",
            InstrumentationRegistry.getArguments().getString("sourcescribeEngineLiveUpdate") == "true",
        )
        val source = InstrumentationRegistry.getArguments().getString("engineProbeSource").orEmpty()
        assumeTrue("engineProbeSource instrumentation argument is required", source.isNotBlank())
        withIsolatedManager { harness ->
            val channel = InstrumentationRegistry.getArguments().getString("engineUpdateChannel")
                ?.let(EngineChannel::valueOf) ?: EngineChannel.NIGHTLY
            val update = requireNotNull(harness.manager.check(channel, force = true)) {
                "NO_NEW_RELEASE_FOR_CONFIGURED_CHANNEL"
            }
            val staged = harness.manager.stage(update)
            val activated = harness.manager.activate(staged.id, SourceResolver.youtube(source))
            assertEquals(staged.id, activated.id)
            val rolledBack = newManager(harness).rollback()
            assertNotEquals(activated.id, rolledBack.id)
            assertTrue(rolledBack.healthy)
            InstrumentationRegistry.getInstrumentation().sendStatus(107, Bundle().apply {
                putString("engine.version", activated.version)
                putString("engine.ejs", activated.ejsVersion)
                putString("engine.sha256", activated.sha256)
                putString("engine.gitHead", activated.gitHead)
                putString("engine.channel", activated.channel.name)
                putString("rollback.version", rolledBack.version)
                putString("rollback.sha256", rolledBack.sha256)
            })
        }
    }

    private suspend fun withIsolatedManager(block: suspend (Harness) -> Unit) {
        requireEnabled()
        NativeRuntime(context).initialize()
        val actualRuntimeDirectory = File(context.noBackupFilesDir, "youtubedl-android")
        assertTrue(actualRuntimeDirectory.isDirectory)
        val root = File(context.cacheDir, "engine-manager-${UUID.randomUUID()}")
        assertTrue(root.mkdirs())
        val runtimeLink = File(root, "youtubedl-android")
        try {
            Files.createSymbolicLink(runtimeLink.toPath(), actualRuntimeDirectory.toPath())
            val storage = IsolatedStorageContext(context, root)
            block(Harness(storage, EngineUpdateManager(storage, NativeRuntime(storage)), root))
        } finally {
            // File.deleteRecursively follows directory symlinks; unlink the shared runtime first.
            Files.deleteIfExists(runtimeLink.toPath())
            root.deleteRecursively()
        }
    }

    private fun newManager(harness: Harness): EngineUpdateManager =
        EngineUpdateManager(harness.storage, NativeRuntime(harness.storage))

    private fun newManager(harness: Harness, calls: Call.Factory): EngineUpdateManager =
        EngineUpdateManager(harness.storage, NativeRuntime(harness.storage), calls, ::writeBytes)

    private fun fixtureCalls(response: (Request) -> Response): Call.Factory = OkHttpClient.Builder()
        .addInterceptor { chain -> response(chain.request()) }
        .build()

    private fun response(request: Request, code: Int, header: Pair<String, String>? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("fixture")
            .body(ByteArray(0).toResponseBody())
            .apply { header?.let { addHeader(it.first, it.second) } }
            .build()

    private fun writeBytes(destination: File, bytes: ByteArray) {
        destination.writeBytes(bytes)
    }

    private fun hashSlotNames(harness: Harness): Set<String> = File(harness.root, "engines")
        .listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.name.matches(Regex("[0-9a-f]{64}")) }
        .mapTo(mutableSetOf(), File::getName)

    private fun assertNoUpdateTemporaryDirectories(harness: Harness) {
        assertTrue(
            File(harness.root, "engines").listFiles().orEmpty().none {
                it.name.startsWith(".staging-") || it.name.startsWith(".slot-")
            },
        )
    }

    private suspend fun expectUpdateFailure(block: suspend () -> Unit): EngineUpdateException = try {
        block()
        throw AssertionError("Expected EngineUpdateException")
    } catch (failure: EngineUpdateException) {
        failure
    }

    private fun stateFile(harness: Harness): File = File(harness.root, "engines/state.json")

    private fun writeState(
        harness: Harness,
        active: EngineInstallation?,
        previous: EngineInstallation?,
        candidate: EngineInstallation?,
    ) {
        val state = JSONObject(boundedJsonText(stateFile(harness).readText()))
        state.put("active", active?.id ?: JSONObject.NULL)
        state.put("previous", previous?.id ?: JSONObject.NULL)
        candidate?.let { installation ->
            state.getJSONArray("installations").put(installationJson(installation))
            if (installation.healthy) state.getJSONArray("healthy").put(installation.id)
        }
        stateFile(harness).writeText(state.toString())
    }

    private fun installationJson(installation: EngineInstallation): JSONObject = JSONObject()
        .put("id", installation.id)
        .put("version", installation.version)
        .put("ejsVersion", installation.ejsVersion)
        .put("channel", installation.channel.name)
        .put("sha256", installation.sha256)
        .put("healthy", installation.healthy)
        .put("bundled", installation.bundled)

    private fun createRegularSlot(harness: Harness, installation: EngineInstallation, content: ByteArray) {
        val slot = File(harness.root, "engines/${installation.id}")
        assertTrue(slot.mkdirs())
        File(slot, "yt-dlp").writeBytes(content)
    }

    private fun createScriptedCandidate(harness: Harness, stderr: String): EngineInstallation {
        val temporary = File(harness.root, "scripted-${UUID.randomUUID()}.zip")
        ZipOutputStream(temporary.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("__main__.py"))
            output.write(
                """
                import sys
                if "--version" in sys.argv:
                    print("2026.08.19")
                    raise SystemExit(0)
                print(${JSONObject.quote(stderr)}, file=sys.stderr)
                raise SystemExit(1)
                """.trimIndent().toByteArray(),
            )
            output.closeEntry()
            output.putNextEntry(ZipEntry("yt_dlp_ejs/__init__.py"))
            output.write("version = \"0.8.0\"\n".toByteArray())
            output.closeEntry()
        }
        val installation = fakeInstallation(sha256(temporary))
        val slot = File(harness.root, "engines/${installation.id}")
        assertTrue(slot.mkdirs())
        Files.move(temporary.toPath(), File(slot, "yt-dlp").toPath())
        return installation
    }

    private fun createSymlinkSlot(harness: Harness, installation: EngineInstallation) {
        val slot = File(harness.root, "engines/${installation.id}")
        assertTrue(slot.mkdirs())
        val target = File(harness.root, "symlink-target-${installation.id}").also { it.writeText("target") }
        try {
            Files.createSymbolicLink(File(slot, "yt-dlp").toPath(), target.toPath())
        } catch (_: Exception) {
            assumeTrue("Symbolic links are required for this test", false)
        }
    }

    private fun fakeInstallation(id: String, healthy: Boolean = true) = EngineInstallation(
        id = id,
        version = "2026.08.19",
        ejsVersion = "0.8.0",
        channel = EngineChannel.STABLE,
        gitHead = null,
        sha256 = id,
        healthy = healthy,
        bundled = false,
    )

    private fun requireEnabled() = assumeTrue(
        "Pass -Pandroid.testInstrumentationRunnerArguments.sourcescribeEngineUpdate=true for opt-in update tests",
        InstrumentationRegistry.getArguments().getString("sourcescribeEngineUpdate") == "true",
    )

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private data class Harness(
        val storage: Context,
        val manager: EngineUpdateManager,
        val root: File,
    )

    private companion object {
        const val STABLE_RELEASE_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
    }

    private class IsolatedStorageContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getNoBackupFilesDir(): File = root
    }
}
