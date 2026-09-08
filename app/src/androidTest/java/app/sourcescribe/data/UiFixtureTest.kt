package app.sourcescribe.data

import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.core.AudioRetention
import app.sourcescribe.core.Branch
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Generation
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Origin
import app.sourcescribe.core.Outcome
import app.sourcescribe.core.Phase
import app.sourcescribe.core.Provenance
import app.sourcescribe.core.Segment
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import app.sourcescribe.core.TranscriptDocument
import app.sourcescribe.core.TranscriptScope
import app.sourcescribe.core.Translation
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Leaves one explicit synthetic transcript for manual viewer/ADB checks.
 *
 * This test is intentionally opt-in because it writes to the real application
 * database and artifact directory. It never removes existing application data.
 */
@RunWith(AndroidJUnit4::class)
class UiFixtureTest {
    @Test
    fun seedSyntheticUiFixtureForAdbViewer(): Unit = runBlocking {
        assumeTrue(
            "Ui fixture is opt-in: pass sourcescribeUiFixture=true",
            InstrumentationRegistry.getArguments().getString("sourcescribeUiFixture") == "true",
        )

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val artifacts = ArtifactFiles(File(targetContext.filesDir, "artifacts"))
        val database = Room.databaseBuilder(
            targetContext,
            SourceScribeDatabase::class.java,
            "sourcescribe.db",
        ).addMigrations(SourceScribeDatabase.MIGRATION_1_2, SourceScribeDatabase.MIGRATION_2_3).build()
        try {
            val ids = FixtureIds(
                sourceId = UUID.randomUUID().toString(),
                jobId = UUID.randomUUID().toString(),
                attemptId = UUID.randomUUID().toString(),
                artifactId = UUID.randomUUID().toString(),
            )
            val createdAt = System.currentTimeMillis()
            val source = syntheticSource(ids.sourceId)
            val config = JobConfig(
                mode = AcquisitionMode.STT_ONLY,
                exportFormats = emptySet(),
                audioRetention = AudioRetention.KEEP,
            )
            val document = syntheticDocument(ids.artifactId, source, config, createdAt)
            val checkpoint = AttemptCheckpoint(
                artifactId = ids.artifactId,
                artifactCreatedAt = createdAt,
                source = source,
            )
            val attempt = AttemptRow(
                id = ids.attemptId,
                jobId = ids.jobId,
                branch = Branch.STT,
                number = 1,
                createdAt = createdAt,
                state = ExecutionState.FINISHED,
                phase = Phase.PERSIST,
                outcome = Outcome.SUCCESS_WITH_WARNINGS,
                checkpoint = json.encodeToString(checkpoint),
                engineId = null,
            )
            val job = JobRow(
                id = ids.jobId,
                sourceId = source.id,
                config = json.encodeToString(config),
                createdAt = createdAt,
                state = ExecutionState.FINISHED,
                outcome = Outcome.SUCCESS_WITH_WARNINGS,
            )

            // Publish the immutable artifact before its Room reference. A partial DB write
            // therefore cannot leave the viewer pointing at a missing transcript file.
            val stored = artifacts.write(document)
            database.withTransaction {
                val dao = database.records()
                check(dao.insertSource(SourceRow(source.id, json.encodeToString(source), requireNotNull(source.title))) != -1L) {
                    "synthetic source was unexpectedly already present"
                }
                dao.insertJob(job)
                dao.insertAttempt(attempt)
                dao.insertArtifact(
                    ArtifactRow(
                        id = stored.artifactId,
                        jobId = job.id,
                        attemptId = attempt.id,
                        branch = attempt.branch,
                        createdAt = document.createdAt,
                        sha256 = stored.sha256,
                        bytes = stored.bytes,
                        language = document.language,
                        providerModel = document.provenance.reportedModel,
                        complete = document.scope.technicallyComplete,
                        warningCount = document.warnings.size,
                    ),
                )
            }

            val dao = database.records()
            val persistedAttempt = requireNotNull(dao.attempt(ids.attemptId))
            val persistedArtifact = requireNotNull(dao.artifact(ids.artifactId))
            val persistedDocument = artifacts.read(ids.artifactId)
            val persistedCheckpoint = json.decodeFromString<AttemptCheckpoint>(persistedAttempt.checkpoint)
            val persistedJob = requireNotNull(dao.job(ids.jobId))
            assertEquals(ExecutionState.FINISHED, persistedJob.state)
            assertEquals(Outcome.SUCCESS_WITH_WARNINGS, persistedJob.outcome)
            assertEquals(ExecutionState.FINISHED, persistedAttempt.state)
            assertEquals(Outcome.SUCCESS_WITH_WARNINGS, persistedAttempt.outcome)
            assertEquals(Phase.PERSIST, persistedAttempt.phase)
            assertEquals(ids.artifactId, persistedCheckpoint.artifactId)
            assertEquals(source.id, persistedCheckpoint.source?.id)
            assertEquals(source.contentHash, persistedCheckpoint.source?.contentHash)
            assertEquals(ids.artifactId, persistedArtifact.id)
            assertEquals(job.id, persistedArtifact.jobId)
            assertEquals(attempt.id, persistedArtifact.attemptId)
            assertEquals(Branch.STT, persistedArtifact.branch)
            assertEquals(stored.sha256, persistedArtifact.sha256)
            assertEquals(stored.bytes, persistedArtifact.bytes)
            assertTrue(persistedArtifact.complete == true)
            assertEquals(SEGMENT_COUNT, persistedDocument.segments.size)
            assertTrue(persistedDocument.segments.last().text.endsWith(SEARCH_MARKER))
            assertTrue(persistedDocument.source.title.orEmpty().length >= LONG_TITLE_MIN_LENGTH)
            assertEquals(SourceKind.LOCAL_AUDIO, persistedDocument.source.kind)
            assertTrue(persistedDocument.source.contentHash?.matches(SHA256_PATTERN) == true)
            assertTrue(persistedDocument.segments.all { it.startMs == null && it.endMs == null })
            assertTrue(persistedDocument.provenance.provider == null)
            assertNull(persistedArtifact.providerModel)
            assertTrue(persistedDocument.warnings.any { it.contains("kein Provideraufruf") })
            assertTrue(artifacts.canonicalFile(ids.artifactId).isFile)
            assertNotNull(dao.source(source.id))

            Log.i(
                LOG_TAG,
                "SYNTHETISCHE UI-PRÜFDATEI " +
                    "sourceId=${source.id} jobId=${job.id} attemptId=${attempt.id} " +
                    "artifactId=${ids.artifactId} state=FINISHED phase=PERSIST " +
                    "bytes=${stored.bytes} segments=${persistedDocument.segments.size}",
            )
        } finally {
            database.close()
        }
    }

    private fun syntheticSource(sourceId: String): Source {
        val title = "SYNTHETISCHE UI-PRÜFDATEI — Unicode ✓ äöü ÄÖÜ ß 東京 Привет 🚀 — " +
            "Langer Titel für die Layout-Prüfung ".repeat(14)
        return Source(
            id = sourceId,
            kind = SourceKind.LOCAL_AUDIO,
            contentHash = sha256Hex("synthetic-ui-fixture:$sourceId"),
            title = title,
            fileName = "SYNTHETISCHE_UI_PRUEFDATEI_unicode.wav",
            mimeType = "audio/wav",
            fileBytes = 123_456,
            durationMs = null,
        )
    }

    private fun syntheticDocument(
        artifactId: String,
        source: Source,
        config: JobConfig,
        createdAt: Long,
    ): TranscriptDocument = TranscriptDocument(
        artifactId = artifactId,
        source = source,
        acquisition = config,
        provenance = Provenance(
            origin = Origin.PROVIDER,
            generation = Generation.UNKNOWN,
            translation = Translation.UNKNOWN,
            provider = null,
            languageEvidence = "synthetic_fixture_only",
        ),
        language = "de",
        scope = TranscriptScope(technicallyComplete = true),
        segments = List(SEGMENT_COUNT) { index ->
            val marker = if (index == SEGMENT_COUNT - 1) " — $SEARCH_MARKER" else ""
            Segment("SYNTHETISCHE UI-PRÜFDATEI Segment $index — äöü ÄÖÜ ß ✓ 東京 Привет 🚀$marker")
        },
        warnings = listOf(
            "SYNTHETISCHE UI-PRÜFDATEI: nur für manuelle Viewer-/ADB-Prüfung.",
            "Keine echte Quelle, kein Provideraufruf und keine Live- oder Genauigkeitsaussage.",
        ),
        createdAt = createdAt,
    )

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class FixtureIds(
        val sourceId: String,
        val jobId: String,
        val attemptId: String,
        val artifactId: String,
    )

    private companion object {
        const val LOG_TAG = "SourceScribeUiFixture"
        const val SEGMENT_COUNT = 10_000
        const val LONG_TITLE_MIN_LENGTH = 400
        const val SEARCH_MARKER = "SUCHMARKE"
        val SHA256_PATTERN = Regex("[a-f0-9]{64}")
        val json = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }
    }
}
