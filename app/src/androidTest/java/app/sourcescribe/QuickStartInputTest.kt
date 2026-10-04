package app.sourcescribe

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickStartInputTest {
    @Test
    fun aNewIdenticalShareSurvivesThePreviousStartsCallback() {
        val shared = "https://youtu.be/jNQXAC9IVRw"
        var input = shared
        var revision = 1L
        val submittedRevision = revision
        // Sharing the same source again is still a new pending input.
        revision++
        clearStartedInput(submittedRevision, revision) { input = "" }
        assertEquals(shared, input)
        clearStartedInput(revision, revision) { input = "" }
        assertEquals("", input)
    }
}
