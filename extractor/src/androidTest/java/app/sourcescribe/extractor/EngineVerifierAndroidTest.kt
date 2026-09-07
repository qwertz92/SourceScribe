package app.sourcescribe.extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.EngineVerifier
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The APK's actual public release bytes must verify on Android's crypto and ZIP implementation. */
@RunWith(AndroidJUnit4::class)
class EngineVerifierAndroidTest {
    @Test fun officialBundledReleaseVerifiesOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("release-verifier-", ".zip", context.cacheDir)
        try {
            context.resources.openRawResource(R.raw.ytdlp).use { input -> file.outputStream().use { input.copyTo(it) } }
            val manifest = context.resources.openRawResource(R.raw.ytdlp_checksums).use { it.readBytes() }
            val signature = context.resources.openRawResource(R.raw.ytdlp_checksums_sig).use { it.readBytes() }
            val verified = EngineVerifier.verify(file, manifest, signature)
            assertEquals("2026.08.19", verified.version)
            assertEquals("0.8.0", verified.ejsVersion)
            manifest[0] = (manifest[0].toInt() xor 1).toByte()
            org.junit.Assert.assertThrows(app.sourcescribe.core.EngineVerificationException::class.java) {
                EngineVerifier.verify(file, manifest, signature)
            }
        } finally { file.delete() }
    }
}
