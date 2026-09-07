package app.sourcescribe.extractor

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.CaptionParser
import app.sourcescribe.core.SourceResolver
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Free public-source evidence, explicitly gated and independent of provider credentials. */
@RunWith(AndroidJUnit4::class)
class ExtractionChainTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun source() = SourceResolver.youtube(InstrumentationRegistry.getArguments()
        .getString("publicSourceUrl").orEmpty().also { assumeTrue("publicSourceUrl required", it.isNotBlank()) })

    @Test
    fun actualCaptionIsFetchedAndParsedForExactSource() = runBlocking {
        val requested = source()
        val runtime = NativeRuntime(context)
        val manager = EngineUpdateManager(context, runtime)
        val engine = ExtractorEngine(runtime)
        val resolved = engine.resolve(requested, manager.file(manager.bundled()))
        assertEquals(requested.id, resolved.source.id)
        val track = requireNotNull(resolved.captions.firstOrNull()) { "NO_CAPTION_ON_CONFIGURED_SOURCE" }
        val raw = engine.caption(resolved, track, manager.file(manager.bundled()), context.cacheDir)
        val parsed = CaptionParser.parse(raw.toString(Charsets.UTF_8), track.format)
        assertTrue(parsed.segments.isNotEmpty())
        instrumentation.sendStatus(105, Bundle().apply {
            putString("caption.sourceId", requested.id)
            putString("caption.trackId", track.id)
            putString("caption.format", track.format)
            putInt("caption.bytes", raw.size)
            putInt("caption.segments", parsed.segments.size)
            putBoolean("caption.complete", parsed.technicallyComplete)
            putString("caption.sha256", MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) })
        })
    }

    @Test
    fun actualAudioIsDownloadedProbedAndPreparedForExactSource() = runBlocking {
        val requested = source()
        val runtime = NativeRuntime(context)
        val manager = EngineUpdateManager(context, runtime)
        val engine = ExtractorEngine(runtime)
        val installation = manager.file(manager.bundled())
        val resolved = engine.resolve(requested, installation)
        val duration = requireNotNull(resolved.source.durationMs)
        require(duration in 1..120_000) { "LIVE_FIXTURE_MUST_BE_AT_MOST_TWO_MINUTES" }
        val track = requireNotNull(resolved.audio.firstOrNull()) { "NO_AUDIO_ON_CONFIGURED_SOURCE" }
        val directory = File(context.cacheDir, "extraction-chain-${UUID.randomUUID()}").also { check(it.mkdirs()) }
        try {
            val audio = engine.downloadAudio(resolved.source, track.id, directory, 16L * 1024 * 1024, installation)
            val preparation = AudioPreparation(runtime)
            val probed = preparation.probe(audio)
            assertTrue(probed.durationMs > 0)
            val chunk = preparation.chunk(audio, directory, 0, 0, minOf(probed.durationMs, 10_000), 1_000_000)
            instrumentation.sendStatus(106, Bundle().apply {
                putString("audio.sourceId", requested.id)
                putString("audio.trackId", track.id)
                putLong("audio.bytes", audio.length())
                putLong("audio.durationMs", probed.durationMs)
                putLong("prepared.bytes", chunk.file.length())
                putString("prepared.sha256", chunk.sha256)
            })
        } finally { directory.deleteRecursively() }
    }
}
