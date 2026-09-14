package app.sourcescribe.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decision ADR 0012 rests on: when a start may trust the bundled engine a full check verified before. */
class BundledEngineMarkerTest {
    @Test
    fun onlyTheAppBuildAndEngineTheFullCheckPassedForSkipIt() {
        val marker = BundledEngineMarker.encode(VERIFIED)

        assertFalse(BundledEngineMarker.fullCheckNeeded(marker, VERIFIED))
        // An app update changes the version code, installing the same version again changes the update time, and a
        // build that bundles other bytes changes the hash. Each of them alone brings the full check back.
        assertTrue(BundledEngineMarker.fullCheckNeeded(marker, VERIFIED.copy(versionCode = VERIFIED.versionCode + 1)))
        assertTrue(BundledEngineMarker.fullCheckNeeded(marker, VERIFIED.copy(lastUpdateTime = VERIFIED.lastUpdateTime + 1)))
        assertTrue(BundledEngineMarker.fullCheckNeeded(marker, VERIFIED.copy(resourceSha256 = "0".repeat(64))))
    }

    @Test
    fun aMissingOrDamagedMarkerNeedsTheFullCheck() {
        val marker = BundledEngineMarker.encode(VERIFIED)

        assertTrue(BundledEngineMarker.fullCheckNeeded(null, VERIFIED))
        assertTrue(BundledEngineMarker.fullCheckNeeded("", VERIFIED))
        // What a write cut short by a crash leaves behind.
        assertTrue(BundledEngineMarker.fullCheckNeeded(marker.dropLast(1), VERIFIED))
    }

    private companion object {
        val VERIFIED = BundledEngineIdentity(
            versionCode = 3,
            lastUpdateTime = 1_757_860_000_000,
            resourceSha256 = "1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6",
        )
    }
}
