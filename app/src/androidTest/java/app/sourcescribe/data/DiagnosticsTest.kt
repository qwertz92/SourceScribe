package app.sourcescribe.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.core.AcquisitionMode
import app.sourcescribe.core.Branch
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Provider
import app.sourcescribe.core.Source
import app.sourcescribe.core.SourceKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticsTest {
    @Test
    fun canaryDoesNotExportSourceConfigOrCheckpointSecrets() {
        val source = Source(
            id = "source-canary-id",
            kind = SourceKind.YOUTUBE,
            canonicalUrl = "https://example.invalid/private-video?key=CANARY_URL_KEY",
            title = "Private canary title",
        )
        val config = JobConfig(
            mode = AcquisitionMode.CAPTIONS_ONLY,
            provider = Provider.OPENAI,
            credentialId = "credential-canary-id",
            contextTerms = listOf("CANARY_SECRET_TERM", "private context"),
        )
        val sourceRow = SourceRow(source.id, Json.encodeToString(source), source.title.orEmpty())
        val jobRow = JobRow(
            id = "job-canary-id",
            sourceId = sourceRow.id,
            config = Json.encodeToString(config),
            createdAt = 1L,
        )
        val attempt = AttemptRow(
            id = "attempt-canary-id",
            jobId = jobRow.id,
            branch = Branch.CAPTIONS,
            number = 1,
            createdAt = 1L,
            checkpoint = "checkpoint CANARY_CHECKPOINT ${sourceRow.snapshot} ${jobRow.config}",
            error = "NETWORK https://example.invalid/private-video?key=CANARY_URL_KEY",
        )
        val unknownErrorAttempt = attempt.copy(
            id = "attempt-unknown-error",
            error = "CANARY_API_KEY=https://example.invalid/secret",
        )

        val output = buildDiagnosticsText(
            buildVersion = "1.0.0",
            androidApi = 37,
            abis = listOf("arm64-v8a"),
            pageSizeBytes = 16_384L,
            attempts = listOf(attempt, unknownErrorAttempt),
            installedEngines = listOf(DiagnosticEngine("engine-canary", "2026.09.07")),
        )

        assertTrue(output.contains("error_code_NETWORK=1"))
        assertTrue(output.contains("error_code_OTHER=1"))
        listOf(
            "CANARY_URL_KEY",
            "https://example.invalid/private-video",
            "Private canary title",
            "CANARY_SECRET_TERM",
            "private context",
            "CANARY_CHECKPOINT",
            "source-canary-id",
            "job-canary-id",
            "credential-canary-id",
        ).forEach { secret -> assertFalse("leaked: $secret", output.contains(secret)) }
        assertTrue(output.toByteArray(Charsets.UTF_8).size <= 128 * 1024)
    }
}
