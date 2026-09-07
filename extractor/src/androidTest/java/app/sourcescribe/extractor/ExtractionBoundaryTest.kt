package app.sourcescribe.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.ExtractorMetadata
import app.sourcescribe.core.InvalidSource
import app.sourcescribe.core.SourceResolver
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtractionBoundaryTest {
    private val source = SourceResolver.youtube("https://www.youtube.com/watch?v=jNQXAC9IVRw")
    private fun resolved(url: String, kind: String = "subtitles") = ExtractorMetadata.parse(
        """{"id":"jNQXAC9IVRw","$kind":{"en":[{"url":"$url","ext":"vtt"}]}}""", source)

    @Test fun downloadRecipeContainsOnlyTheValidatedTrackAndCannotRefreshSource() {
        for (kind in listOf("subtitles", "automatic_captions")) {
            val url = "https://www.youtube.com/api/timedtext?v=jNQXAC9IVRw&lang=en"
            val resolved = resolved(url, kind)
            val info = captionDownloadInfo(resolved, resolved.captions.single())
            assertEquals(setOf("id", "title", kind), info.keys().asSequence().toSet())
            val languages = info.getJSONObject(kind)
            assertEquals(setOf("en"), languages.keys().asSequence().toSet())
            val tracks = languages.getJSONArray("en")
            assertEquals(1, tracks.length())
            assertEquals(url, tracks.getJSONObject(0).getString("url"))
            assertEquals("https", tracks.getJSONObject(0).getString("protocol"))
        }
    }

    @Test fun hlsManifestUsesNativeAssemblyAndDifferentVideoBindingIsRejected() {
        val resolved = resolved("https://manifest.googlevideo.com/api/manifest/hls_timedtext_playlist/id/example")
        assertEquals("m3u8_native", captionDownloadInfo(resolved, resolved.captions.single())
            .getJSONObject("subtitles").getJSONArray("en").getJSONObject(0).getString("protocol"))
        for (query in listOf("v=BaW_jenozKc", "%76=BaW_jenozKc", "v=jNQXAC9IVRw&%76=BaW_jenozKc")) {
            try {
                val changed = resolved("https://www.youtube.com/api/timedtext?$query&lang=en")
                captionDownloadInfo(changed, changed.captions.single()); fail("wrong source accepted")
            }
            catch (expected: InvalidSource) { assertTrue(expected.reason in setOf("SOURCE_ID_MISMATCH", "INVALID_CAPTION_URL")) }
        }
    }

    @Test fun effectiveLanguageIsBoundIncludingTranslatedAndOriginalTracks() {
        for (query in listOf("lang=de", "lang=en&%74lang=fr")) {
            val wrong = resolved("https://www.youtube.com/api/timedtext?v=jNQXAC9IVRw&$query")
            try { captionDownloadInfo(wrong, wrong.captions.single()); fail("wrong language accepted") }
            catch (expected: InvalidSource) { assertEquals("CAPTION_TRACK_CHANGED", expected.reason) }
        }
        val translated = resolved("https://www.youtube.com/api/timedtext?v=jNQXAC9IVRw&lang=de&tlang=en")
        assertEquals(app.sourcescribe.core.Translation.AUTOMATIC, translated.captions.single().translation)
        captionDownloadInfo(translated, translated.captions.single())
    }

    @Test fun rejectedNativeAudioIsRemovedWhileValidAudioRemains() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = ExtractorEngine(NativeRuntime(context))
        val file = File(context.cacheDir, "audio-validation-${UUID.randomUUID()}")
        try {
            val success = RuntimeOutput(0, "SS_SOURCE_ID=jNQXAC9IVRw", "")
            for ((result, limit) in listOf(RuntimeOutput(1, "", "network error") to 8L,
                RuntimeOutput(0, "SS_SOURCE_ID=BaW_jenozKc", "") to 8L,
                RuntimeOutput(0, "", "") to 8L, success to 1L)) {
                file.writeBytes(byteArrayOf(1, 2))
                try { engine.validateAudioDownload(source, file, limit, result); fail("invalid audio accepted") }
                catch (_: java.io.IOException) { /* Typed extraction/source rejection. */ }
                catch (_: InvalidSource) { /* Source mismatch. */ }
                assertFalse(file.exists())
            }
            file.writeBytes(byteArrayOf(1, 2))
            assertEquals(file, engine.validateAudioDownload(source, file, 8, success))
            assertTrue(file.exists())
        } finally { file.delete() }
    }
}
