package app.sourcescribe.data

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExportFormat
import app.sourcescribe.core.ExportState
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Origin
import app.sourcescribe.core.Provenance
import app.sourcescribe.core.Segment
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.TranscriptDocument
import app.sourcescribe.core.TranscriptExporter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportStoreTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context
        get() = instrumentation.targetContext

    @After
    fun tearDown() {
        providerAdmin { ExportFixtureProvider.reset(context) }
    }

    @Test
    fun successfulExportPersistsVerifiedRowAndExactContent() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.SUCCESS) { harness ->
            val row = harness.store.export(harness.artifactId, ExportFormat.MARKDOWN, harness.treeUri)

            assertEquals(ExportState.EXPORTED, row.state)
            assertEquals("VERIFIED", row.verification)
            assertNotNull(row.documentUri)
            assertEquals(row, harness.dao.export(row.id))
            assertEquals(row, harness.store.reconcile(row.id))
            assertArrayEquals(
                TranscriptExporter.render(harness.document, ExportFormat.MARKDOWN).toByteArray(),
                providerAdmin { ExportFixtureProvider.bytes(context, requireNotNull(row.documentUri)) },
            )
        }
    }

    @Test
    fun permissionFailureKeepsInternalArtifactAndRecordsPermissionRequired() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.PERMISSION_DENIED) { harness ->
            val row = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)

            assertEquals(ExportState.PERMISSION_REQUIRED, row.state)
            assertEquals("PERMISSION_REQUIRED", row.error)
            assertNull(row.documentUri)
            assertEquals(harness.document, harness.artifacts.read(harness.artifactId))
        }
    }

    @Test
    fun realTreeGrantRevocationKeepsArtifactAndRequiresPermissionAgain() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.SUCCESS) { harness ->
            val exported = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)
            assertEquals(ExportState.EXPORTED, exported.state)
            val documentUri = requireNotNull(exported.documentUri)

            providerAdmin { ExportFixtureProvider.revokeTreeGrant(context, harness.treeUri) }

            assertEquals(1, providerAdmin { ExportFixtureProvider.names(context) }.size)
            assertArrayEquals(
                TranscriptExporter.render(harness.document, ExportFormat.TEXT).toByteArray(),
                providerAdmin { ExportFixtureProvider.bytes(context, documentUri) },
            )
            val reconciled = harness.store.reconcile(exported.id)
            val repeated = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)
            assertEquals(ExportState.PERMISSION_REQUIRED, reconciled.state)
            assertEquals("PERMISSION_REQUIRED", reconciled.error)
            assertEquals(documentUri, reconciled.documentUri)
            assertEquals(ExportState.PERMISSION_REQUIRED, repeated.state)
            assertEquals("PERMISSION_REQUIRED", repeated.error)
            assertNull(repeated.documentUri)
            assertEquals(harness.document, harness.artifacts.read(harness.artifactId))
        }
    }

    @Test
    fun controlCallRequiresSeparateAdminPermissionAfterTreeGrant() {
        val treeUri = configureProvider(ExportFixtureProvider.Mode.SUCCESS).toUri()
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )

        context.contentResolver.query(
            rootUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null,
        )!!.use { cursor -> assertTrue(cursor.moveToFirst()) }
        assertThrows(SecurityException::class.java) { ExportFixtureProvider.names(context) }
    }

    @Test
    fun partialWriteCleansOnlyOwnedDocumentAndKeepsArtifact() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.OUTPUT_FAILURE) { harness ->
            providerAdmin { ExportFixtureProvider.seed(context, "keep.txt") }
            val row = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)

            assertEquals(ExportState.FAILED, row.state)
            assertEquals("IO_FAILURE", row.error)
            assertTrue(
                "partial-write fixture did not reach its second failing write",
                providerAdmin { ExportFixtureProvider.outputFailureWriteAttempts(context) } >= 2,
            )
            assertNull(row.documentUri)
            assertEquals(listOf("keep.txt"), providerAdmin { ExportFixtureProvider.names(context) })
            assertEquals(harness.document, harness.artifacts.read(harness.artifactId))
        }
    }

    @Test
    fun diskFullCleansOwnedDocumentAndKeepsSiblingAndArtifact() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.DISK_FULL) { harness ->
            val siblingBytes = "unchanged sibling".toByteArray()
            val siblingUri = providerAdmin {
                ExportFixtureProvider.seedDocument(context, "keep.txt", siblingBytes)
            }

            val row = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)

            assertTrue(
                "disk-full fixture did not execute its ENOSPC callback",
                providerAdmin { ExportFixtureProvider.diskFullWriteAttempted(context) },
            )
            assertEquals(ExportState.FAILED, row.state)
            assertEquals("IO_FAILURE", row.error)
            assertNull(row.documentUri)
            assertEquals(listOf("keep.txt"), providerAdmin { ExportFixtureProvider.names(context) })
            assertArrayEquals(siblingBytes, providerAdmin { ExportFixtureProvider.bytes(context, siblingUri) })
            assertEquals(harness.document, harness.artifacts.read(harness.artifactId))
        }
    }

    @Test
    fun retryCreatesFreshRowAndDoesNotRerunTranscription() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.PERMISSION_DENIED) { harness ->
            val failed = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)
            providerAdmin { ExportFixtureProvider.setMode(context, ExportFixtureProvider.Mode.SUCCESS) }

            val retried = harness.store.retry(failed.id)

            assertEquals(ExportState.PERMISSION_REQUIRED, failed.state)
            assertEquals(ExportState.EXPORTED, retried.state)
            assertNotEquals(failed.id, retried.id)
            assertEquals(failed, harness.dao.export(failed.id))
            assertEquals(harness.document, harness.artifacts.read(harness.artifactId))
        }
    }

    @Test
    fun reconcileMarksExternallyDeletedDocumentMissing() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.SUCCESS) { harness ->
            val exported = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)
            val documentUri = requireNotNull(exported.documentUri)

            assertTrue(DocumentsContract.deleteDocument(context.contentResolver, documentUri.toUri()))

            val reconciled = harness.store.reconcile(exported.id)
            assertEquals(ExportState.FAILED, reconciled.state)
            assertEquals("EXTERNAL_DOCUMENT_MISSING", reconciled.error)
            assertEquals(documentUri, reconciled.documentUri)
            assertEquals(reconciled, harness.dao.export(exported.id))
        }
    }

    @Test
    fun reconcileMapsRevokedPermissionWithoutTouchingInternalArtifact() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.SUCCESS) { harness ->
            val exported = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)
            providerAdmin { ExportFixtureProvider.setMode(context, ExportFixtureProvider.Mode.PERMISSION_DENIED) }

            val reconciled = harness.store.reconcile(exported.id)
            assertEquals(ExportState.PERMISSION_REQUIRED, reconciled.state)
            assertEquals("PERMISSION_REQUIRED", reconciled.error)
            assertEquals(exported.documentUri, reconciled.documentUri)
            assertEquals(harness.document, harness.artifacts.read(harness.artifactId))
        }
    }

    @Test
    fun reconcileTurnsPendingAndWritingRowsIntoInterruptedFailures() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.SUCCESS) { harness ->
            for (state in listOf(ExportState.PENDING, ExportState.WRITING)) {
                val interrupted = ExportRow(
                    id = UUID.randomUUID().toString(),
                    artifactId = harness.artifactId,
                    format = ExportFormat.TEXT.name,
                    treeUri = harness.treeUri,
                    createdAt = System.currentTimeMillis(),
                    state = state,
                    documentUri = "content://${ExportFixtureProvider.AUTHORITY}/document/interrupted",
                )
                harness.dao.insertExport(interrupted)

                val reconciled = harness.store.reconcile(interrupted.id)
                assertEquals(ExportState.FAILED, reconciled.state)
                assertEquals("EXPORT_INTERRUPTED", reconciled.error)
                assertEquals(interrupted.documentUri, reconciled.documentUri)
            }
        }
    }

    @Test
    fun retryWithNewTreeCreatesNewRowAndKeepsPreviousAttempt() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.PERMISSION_DENIED) { harness ->
            val failed = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)
            val oldTreeUri = failed.treeUri
            val newTreeUri = configureProvider(ExportFixtureProvider.Mode.SUCCESS)

            val retried = harness.store.retry(failed.id, newTreeUri)

            assertNotEquals(failed.id, retried.id)
            assertEquals(newTreeUri, retried.treeUri)
            assertEquals(ExportState.EXPORTED, retried.state)
            assertEquals(failed, harness.dao.export(failed.id))
            assertEquals(oldTreeUri, requireNotNull(harness.dao.export(failed.id)).treeUri)
            assertEquals(harness.document, harness.artifacts.read(harness.artifactId))
        }
    }

    @Test
    fun unreadableReadbackIsExportedAsUnverified() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.READBACK_UNAVAILABLE) { harness ->
            val row = harness.store.export(harness.artifactId, ExportFormat.JSON, harness.treeUri)

            assertEquals(ExportState.EXPORTED, row.state)
            assertEquals("UNVERIFIED", row.verification)
            assertNotEquals("VERIFIED", row.verification)
            assertNotNull(row.documentUri)
        }
    }

    @Test
    fun permissionDeniedReadbackKeepsWrittenDocumentAsUnverified() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.READBACK_PERMISSION_DENIED) { harness ->
            val expected = TranscriptExporter.render(harness.document, ExportFormat.JSON).toByteArray()

            val row = harness.store.export(harness.artifactId, ExportFormat.JSON, harness.treeUri)

            assertEquals(ExportState.EXPORTED, row.state)
            assertEquals("UNVERIFIED", row.verification)
            assertArrayEquals(
                expected,
                providerAdmin { ExportFixtureProvider.bytes(context, requireNotNull(row.documentUri)) },
            )
            assertEquals(harness.document, harness.artifacts.read(harness.artifactId))
        }
    }

    @Test
    fun readbackMismatchFailsAndCleansOwnedDocument() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.READBACK_MISMATCH) { harness ->
            val row = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)

            assertEquals(ExportState.FAILED, row.state)
            assertEquals("MISMATCH", row.error)
            assertNull(row.documentUri)
            assertTrue(providerAdmin { ExportFixtureProvider.names(context) }.isEmpty())
        }
    }

    @Test
    fun repeatedExportsUseDistinctNamesAndDoNotOverwrite() = runBlocking {
        withHarness(ExportFixtureProvider.Mode.NAME_COLLISION) { harness ->
            val first = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)
            val second = harness.store.export(harness.artifactId, ExportFormat.TEXT, harness.treeUri)

            assertEquals(ExportState.EXPORTED, first.state)
            assertEquals(ExportState.EXPORTED, second.state)
            assertNotEquals(first.id, second.id)
            assertNotEquals(first.documentUri, second.documentUri)
            assertEquals(2, providerAdmin { ExportFixtureProvider.names(context) }.size)
            assertArrayEquals(
                providerAdmin { ExportFixtureProvider.bytes(context, requireNotNull(first.documentUri)) },
                providerAdmin { ExportFixtureProvider.bytes(context, requireNotNull(second.documentUri)) },
            )
        }
    }

    @Test
    fun generatedNameContainsFullArtifactAndExportIds() {
        val artifactId = "ffffffff-ffff-ffff-ffff-ffffffffffff"
        val exportId = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"

        val name = ExportStore.collisionSafeFileName(document(artifactId), ExportFormat.MARKDOWN, exportId)

        assertTrue(name.endsWith("-$artifactId-$exportId.md"))
    }

    @Test
    fun rawNameRetainsExactMetadataExtension() {
        val artifactId = "11111111-1111-1111-1111-111111111111"
        val exportId = "22222222-2222-2222-2222-222222222222"

        val name = ExportStore.collisionSafeFileName(document(artifactId), ExportFormat.RAW, exportId, "json3")

        assertTrue(name.endsWith("-$artifactId-$exportId.json3"))
    }

    private suspend fun withHarness(mode: ExportFixtureProvider.Mode, block: suspend (Harness) -> Unit) {
        val treeUri = configureProvider(mode)
        val artifactRoot = File(context.cacheDir, "sourcescribe-artifacts-test-${UUID.randomUUID()}")
        val artifacts = ArtifactFiles(artifactRoot)
        val document = document("00000000-0000-0000-0000-000000000001")
        val stored = artifacts.write(document)
        val job = JobRow(UUID.randomUUID().toString(), "source-1", Json.encodeToString(JobConfig()), 1L)
        val attempt = AttemptRow(UUID.randomUUID().toString(), job.id, Branch.CAPTIONS, 1, 1L)
        val artifact = ArtifactRow(
            id = document.artifactId,
            jobId = job.id,
            attemptId = attempt.id,
            branch = Branch.CAPTIONS,
            createdAt = document.createdAt,
            sha256 = stored.sha256,
            bytes = stored.bytes,
            language = document.language,
            providerModel = document.provenance.reportedModel,
            complete = document.scope.technicallyComplete,
            warningCount = document.warnings.size,
        )
        val database = Room.inMemoryDatabaseBuilder(context, SourceScribeDatabase::class.java).build()
        try {
            val dao = database.records()
            dao.createJob(SourceRow("source-1", Json.encodeToString(document.source), "Fixture source"), job, listOf(attempt))
            dao.insertArtifact(artifact)
            block(Harness(ExportStore(context, dao, artifacts), dao, artifacts, document, document.artifactId, treeUri))
        } finally {
            database.close()
            artifactRoot.deleteRecursively()
        }
    }

    private fun configureProvider(mode: ExportFixtureProvider.Mode): String {
        return providerAdmin {
            ExportFixtureProvider.configure(context, mode, context.packageName)
        }
    }

    private inline fun <T> providerAdmin(operation: () -> T): T {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            ExportFixtureProvider.CONTROL_PERMISSION,
        )
        return try {
            operation()
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private data class Harness(
        val store: ExportStore,
        val dao: SourceScribeDao,
        val artifacts: ArtifactFiles,
        val document: TranscriptDocument,
        val artifactId: String,
        val treeUri: String,
    )

    private fun document(artifactId: String) = TranscriptDocument(
        artifactId = artifactId,
        source = Source(id = "source-1", kind = SourceKind.YOUTUBE, title = "Fixture source"),
        acquisition = JobConfig(mode = AcquisitionMode.CAPTIONS_ONLY),
        provenance = Provenance(origin = Origin.YOUTUBE),
        segments = listOf(Segment("fixture transcript")),
        createdAt = 1L,
    )
}
