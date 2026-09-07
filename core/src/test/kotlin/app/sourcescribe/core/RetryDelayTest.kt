package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryDelayTest {
    @Test
    fun zeroAndSmallBasesNeverShortenTheMinimum() {
        assertEquals(0L, retryDelayMillis(0L))

        repeat(100) {
            val delay = retryDelayMillis(1L)
            assertTrue("delay must not be below the base", delay >= 1L)
            assertTrue("one millisecond has no representable 20% jitter", delay <= 1L)
        }
    }

    @Test
    fun jitterIsBoundedByTwentyPercentAndFiveSeconds() {
        for (base in listOf(10_000L, 30_000L, 100_000L)) {
            val maximumJitter = minOf(5_000L, base / 5L)
            repeat(100) {
                val delay = retryDelayMillis(base)
                assertTrue("delay must respect the minimum", delay >= base)
                assertTrue("jitter must stay bounded", delay - base <= maximumJitter)
            }
        }
    }

    @Test
    fun maximumAllowedBaseCannotOverflowWhenJitterIsAdded() {
        val base = 86_400_000L
        repeat(100) {
            val delay = retryDelayMillis(base)
            assertTrue(delay >= base)
            assertTrue(delay - base <= 5_000L)
        }
    }

    @Test
    fun invalidBasesAreRejectedBeforeArithmetic() {
        assertThrows(IllegalArgumentException::class.java) { retryDelayMillis(-1L) }
        assertThrows(IllegalArgumentException::class.java) { retryDelayMillis(86_400_001L) }
        assertThrows(IllegalArgumentException::class.java) { retryDelayMillis(Long.MAX_VALUE) }
    }
}
