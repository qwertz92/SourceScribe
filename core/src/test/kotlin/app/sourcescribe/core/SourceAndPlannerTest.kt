package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceAndPlannerTest {
    private val id = "BaW_jenozKc"

    @Test fun exactVideoAcrossSupportedForms() {
        listOf("https://youtu.be/$id?t=10", "https://www.youtube.com/watch?v=$id&list=ignored", "https://m.youtube.com/shorts/$id", "youtube.com/live/$id").forEach {
            assertEquals(id, SourceResolver.youtube(it).videoId)
            assertEquals("https://www.youtube.com/watch?v=$id", SourceResolver.youtube(it).canonicalUrl)
        }
    }

    @Test fun rejectsHostAndIdentityConfusion() {
        listOf("https://youtube.com.evil.test/watch?v=$id", "https://youtube.com@evil.test/watch?v=$id", "https://evil.test@youtube.com/watch?v=$id", "https://youtube.com/playlist?list=$id", "https://youtube.com/watch?v=$id&v=abcdefghijk", "https://youtu.be/%42aW_jenozKc", "https://youtu.be/$id/other", "https://youtu.be/$id?v=abcdefghijk", "http://youtu.be/$id", "https://youtu.be:443/$id", "https://yоutube.com/watch?v=$id").forEach {
            assertThrows(it, InvalidSource::class.java) { SourceResolver.youtube(it) }
        }
        assertThrows(InvalidSource::class.java) { SourceResolver.requireMatchingVideo(SourceResolver.youtube("youtu.be/$id"), "abcdefghijk") }
    }

    @Test fun theNoCookieEmbedAddressIsTheSameVideoAndNothingElseOfThatHostIs() {
        listOf("https://www.youtube-nocookie.com/embed/$id", "https://youtube-nocookie.com/embed/$id?start=10", "www.youtube-nocookie.com/embed/$id").forEach {
            assertEquals(it, "youtube:$id", SourceResolver.youtube(it).id)
            assertEquals(it, "https://www.youtube.com/watch?v=$id", SourceResolver.youtube(it).canonicalUrl)
        }
        assertEquals(listOf("youtube:$id"), SourceResolver.sharedText("Eingebettet: https://www.youtube-nocookie.com/embed/$id https://youtu.be/$id").map { it.id })
        assertEquals(listOf("youtube:$id"), SourceResolver.sharedText("youtube-nocookie.com/embed/$id").map { it.id })
        // The host serves the embedded player; no other path of it names a video, and no look-alike host does either.
        listOf("https://www.youtube-nocookie.com/watch?v=$id", "https://www.youtube-nocookie.com/shorts/$id", "https://m.youtube-nocookie.com/embed/$id", "https://youtube-nocookie.com.evil.test/embed/$id").forEach {
            assertThrows(it, InvalidSource::class.java) { SourceResolver.youtube(it) }
        }
    }

    @Test fun aShareBatchIsExplicitAndDeduplicated() {
        assertEquals(1, SourceResolver.sharedText("Titel https://youtu.be/$id https://youtu.be/$id?t=2").size)
        assertThrows(InvalidSource::class.java) { SourceResolver.sharedText("https://youtu.be/$id https://evil.test/") }
    }

    @Test fun modesDoNotAuthorizeSilentPaidFallbacks() {
        for (state in CaptionAvailability.entries) {
            assertFalse(AcquisitionPlanner.plan(JobConfig(mode = AcquisitionMode.CAPTIONS_ONLY), state).contains(PlannedAction.TRANSCRIBE))
            assertEquals(setOf(PlannedAction.TRANSCRIBE), AcquisitionPlanner.plan(JobConfig(mode = AcquisitionMode.STT_ONLY), state))
        }
        val fallback = JobConfig()
        assertEquals(setOf(PlannedAction.WAIT_FOR_USER), AcquisitionPlanner.plan(fallback, CaptionAvailability.FETCH_ERROR))
        assertEquals(setOf(PlannedAction.TRANSCRIBE), AcquisitionPlanner.plan(fallback, CaptionAvailability.MISSING))
        assertEquals(setOf(PlannedAction.TRANSCRIBE), AcquisitionPlanner.plan(fallback.copy(fallbackOnCaptionError = true), CaptionAvailability.FETCH_ERROR))
        assertEquals(Outcome.PARTIAL_SUCCESS, AcquisitionPlanner.outcome(AcquisitionMode.BOTH, setOf(Branch.CAPTIONS), false, true))
    }
}
