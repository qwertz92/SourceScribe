package app.sourcescribe.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceFilesTest {
    @Test
    fun previewReservationsAreOwnerScopedAndUnregisterReleasesAllReferences() {
        val owner = "fixture-owner-${UUID.randomUUID()}"
        val siblingOwner = "fixture-owner-${UUID.randomUUID()}"
        val unregisteredOwner = "fixture-owner-${UUID.randomUUID()}"
        val source = "local:fixture-${UUID.randomUUID()}"
        val siblingSource = "local:fixture-${UUID.randomUUID()}"

        try {
            assertFalse(SourceFiles.hasPreview(source))
            SourceFiles.reservePreview(source, unregisteredOwner)
            assertFalse(SourceFiles.hasPreview(source))

            SourceFiles.registerPreviewOwner(owner)
            SourceFiles.registerPreviewOwner(siblingOwner)
            SourceFiles.reservePreview(source, owner)
            SourceFiles.reservePreview(source, owner)
            SourceFiles.reservePreview(source, siblingOwner)
            SourceFiles.reservePreview(siblingSource, owner)
            assertTrue(SourceFiles.hasPreview(source))
            assertTrue(SourceFiles.hasPreview(siblingSource))

            SourceFiles.releasePreview(source, owner)
            assertTrue(SourceFiles.hasPreview(source))
            SourceFiles.releasePreview(source, siblingOwner)
            assertFalse(SourceFiles.hasPreview(source))
            assertTrue(SourceFiles.hasPreview(siblingSource))

            SourceFiles.unregisterPreviewOwner(owner)
            assertFalse(SourceFiles.hasPreview(source))
            assertFalse(SourceFiles.hasPreview(siblingSource))
        } finally {
            SourceFiles.unregisterPreviewOwner(owner)
            SourceFiles.unregisterPreviewOwner(siblingOwner)
            SourceFiles.unregisterPreviewOwner(unregisteredOwner)
        }
    }
}
