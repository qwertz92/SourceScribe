package app.sourcescribe.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobLimitsTest {
    @Test fun everySourceUpToTheCeilingIsAccepted() {
        // Since 0.4.0 the ceiling is the only length limit, so a source of any length below it has to pass
        // — including the lengths the old typed limit defaulted to, one hour and sixty minutes of it.
        for (minutes in 1..JobLimits.MAX_AUDIO_MINUTES) {
            assertFalse("$minutes min", JobLimits.exceeds(minutes * 60_000L))
        }
    }

    @Test fun theAnswersAtTheCeilingAreGivenAsNumbersAndNotAsTheConstant() {
        // Every other assertion in this file reaches the ceiling through the constant and therefore moves
        // with it: set to one hour, a tenth of what it is, all of them stay green while the app quietly
        // stops accepting sources it offers to carry. These name the edge outright, so a moved ceiling
        // changes an answer here. The ceiling itself is stated in `StatedNumbersTest`, beside the note that
        // two translated strings carry the same six hundred minutes without reading them from it.
        assertFalse(JobLimits.exceeds(36_000_000L))
        assertTrue(JobLimits.exceeds(36_000_001L))
    }

    @Test fun anUnknownLengthIsNotTooLong() {
        // It cannot start either, but for a reason of its own: the preview refuses a source whose length
        // nobody knows with SOURCE_DURATION_UNKNOWN, not as a source that is too long.
        assertFalse(JobLimits.exceeds(null))
    }
}
