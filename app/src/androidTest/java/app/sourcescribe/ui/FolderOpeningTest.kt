package app.sourcescribe.ui

import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.core.AppSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderOpeningTest {
    @Test
    fun folderIntentOpensTheSelectedDirectoryAndGrantsOnlyReadAccessToItsTree() {
        val tree = "content://app.sourcescribe.test.documents/tree/folder".toUri()
        val intent = folderViewIntent(tree.toString())
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(DocumentsContract.Document.MIME_TYPE_DIR, intent.type)
        assertEquals(DocumentsContract.buildDocumentUriUsingTree(tree, "folder"), intent.data)
        assertEquals(tree, intent.clipData?.getItemAt(0)?.uri)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        assertThrows(IllegalArgumentException::class.java) { folderViewIntent("file:///sdcard") }
    }

    @Test
    fun olderSettingsUseChooserAndThePreferenceSurvivesSerialization() {
        assertTrue(Json.decodeFromString<AppSettings>("{}").chooseFolderApp)
        val selected = AppSettings(chooseFolderApp = false)
        assertFalse(Json.decodeFromString<AppSettings>(Json.encodeToString(AppSettings.serializer(), selected)).chooseFolderApp)
    }
}
