package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobLimitsTest {
    @Test fun theSuggestedLimitAlwaysAdmitsTheSourceItWasSuggestedFor() {
        // Every source the ceiling can still carry gets a limit that actually covers it. The 595-to-600
        // minute band is the interesting one: the padding runs into the ceiling and must clamp, not give up.
        for (minutes in 1..JobLimits.MAX_AUDIO_MINUTES) {
            val durationMs = minutes * 60_000L
            val suggestion = requireNotNull(JobLimits.suggestedSeconds(durationMs)) { "no limit for $minutes min" }
            assertTrue("$minutes min", suggestion in 1..JobLimits.MAX_AUDIO_SECONDS)
            assertFalse("$minutes min", JobLimits.exceeds(durationMs, suggestion))
        }
    }

    @Test fun aSourceThatEndsBetweenTwoMinutesStillFitsIntoWholeMinutes() {
        assertEquals(5 * 60 + 60L, JobLimits.suggestedSeconds(1L))
        assertFalse(JobLimits.exceeds(60_001L, requireNotNull(JobLimits.suggestedSeconds(60_001L))))
        // Exactly on the ceiling the padding has nowhere to go, so the ceiling itself is the answer.
        assertEquals(JobLimits.MAX_AUDIO_SECONDS, JobLimits.suggestedSeconds(JobLimits.MAX_AUDIO_SECONDS * 1000L))
    }

    @Test fun pastTheCeilingThereIsNoLimitToOfferAndTheAppSaysSoInstead() {
        assertNull(JobLimits.suggestedSeconds(JobLimits.MAX_AUDIO_SECONDS * 1000L + 1L))
        assertNull(JobLimits.suggestedSeconds(601 * 60_000L))
    }

    @Test fun theCeilingItselfIsWrittenOutHereAndNotOnlyReachedThroughTheConstant() {
        // Every other assertion in this file reaches the ceiling through the constant and therefore moves
        // with it: set to one hour, a tenth of what it is, all of them stay green while the app quietly
        // stops accepting sources it offers to carry. And the offer is a second hard-coded number: the
        // `invalid_duration` string names 600 minutes in both languages without reading it from here. The
        // number is therefore written out, which ties the two together, and the answers at its edge with it.
        assertEquals(10 * 60 * 60L, JobLimits.MAX_AUDIO_SECONDS)
        assertEquals(600L, JobLimits.MAX_AUDIO_MINUTES)
        assertEquals(36_000L, JobLimits.suggestedSeconds(36_000_000L))
        assertNull(JobLimits.suggestedSeconds(36_000_001L))
    }

    @Test fun anImpossibleLimitIsNotReportedAsALengthProblem() {
        // 0 and anything above the ceiling are invalid entries; naming them a length problem would offer
        // the wrong remedy, so the length check stays silent and the config check refuses them.
        assertFalse(JobLimits.exceeds(60_000L, 0L))
        assertFalse(JobLimits.exceeds(60_000L, JobLimits.MAX_AUDIO_SECONDS + 1))
        assertFalse(JobLimits.exceeds(null, 600L))
        assertTrue(JobLimits.exceeds(600_001L, 600L))
        assertFalse(JobLimits.exceeds(600_000L, 600L))
    }
}
