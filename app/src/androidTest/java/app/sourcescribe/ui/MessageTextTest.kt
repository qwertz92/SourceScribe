package app.sourcescribe.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.MainViewModel
import app.sourcescribe.core.ArtifactFilesException
import app.sourcescribe.core.CaptionParseException
import app.sourcescribe.core.ExportState
import app.sourcescribe.core.ProviderErrorCode
import app.sourcescribe.data.AudioImportCode
import app.sourcescribe.data.CredentialException
import app.sourcescribe.data.StorageBudgetException
import app.sourcescribe.extractor.AudioPreparationCode
import app.sourcescribe.extractor.EngineUpdateCode
import app.sourcescribe.extractor.ExtractionFailure
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageTextTest {
    @Test
    fun everyCodeTheAppCanShowHasASentenceOfItsOwn() {
        // Defect 4. A code without an entry shows only "The operation could not be completed" and its technical name,
        // ordinary outcomes such as a provider that took too long included. The enum families below grow with their
        // enums. The literal codes are written out from the places that record them, so a new literal code belongs
        // in this list as well; the list is the inventory of 14 September 2026.
        val missing = SHOWN_CODES.filter { messageSpec(it) == null }.sorted()
        assertEquals("Codes without a sentence of their own", emptyList<String>(), missing)
    }


    private companion object {
        val SHOWN_CODES: Set<String> = buildSet {
            // Messages MainViewModel sets itself.
            addAll(
                listOf(
                    "JOBS_CREATED", "EXPORT_PERMISSION_REQUIRED", "CLIPBOARD_EMPTY", "REMOTE_DELETE_CONFIRMED",
                    "FILE_NAME_SAVED", "DEFAULTS_SAVED", "PRESET_SAVED", "KEY_SAVED", "ENGINE_CURRENT", "ENGINE_ACTIVE",
                    "ACTION_BUSY", "CLEANUP_FAILED", "LOCAL_PROCESSING_FAILED",
                ),
            )
            // What the preview and a start refuse: MainViewModel.previewError and configError.
            addAll(MainViewModel.PREVIEW_ERRORS_SHOWN_AS_TEXT)
            addAll(listOf("SOURCE_LONGER_THAN_LIMIT", "UPLOAD_APPROVAL_REQUIRED", "CONTEXT_TERM_BLANK"))
            // InvalidSource reasons: SourceResolver, ExtractorMetadata, ExtractorEngine, and the job path's summary.
            addAll(
                listOf(
                    "INVALID_URL", "INVALID_HOST", "INVALID_PATH", "INVALID_QUERY", "INVALID_VIDEO_ID", "AMBIGUOUS_VIDEO",
                    "EXPLICIT_VIDEO_REQUIRED", "INPUT_TOO_LARGE", "TOO_MANY_VIDEOS", "SOURCE_ID_MISMATCH",
                    "METADATA_TOO_LARGE", "INVALID_METADATA", "LIVE_OR_PLAYLIST_UNSUPPORTED", "INVALID_DURATION",
                    "INVALID_CAPTION_URL", "CAPTION_TRACK_CHANGED", "AUDIO_TRACK_CHANGED", "SOURCE_VALIDATION",
                ),
            )
            // Enum families, each with the prefixes and suffixes their producers add.
            addAll(ExtractionFailure.entries.map { it.name })
            addAll(EngineUpdateCode.entries.map { "ENGINE_${it.name}" })
            addAll(CredentialException.Code.entries.flatMap { listOf("KEY_${it.name}", "CREDENTIAL_${it.name}") })
            addAll(
                ProviderErrorCode.entries.flatMap {
                    listOf(it.name, "PROVIDER_${it.name}", "RESPONSE_${it.name}", "${it.name}_RAW_SAVED")
                },
            )
            addAll(AudioImportCode.entries.map { "AUDIO_IMPORT_${it.name}" })
            addAll(AudioPreparationCode.entries.map { "AUDIO_${it.name}" })
            addAll(ExportState.entries.map { "EXPORT_${it.name}" })
            // JobActionException codes from JobCoordinator and SttStep.
            addAll(
                listOf(
                    "JOB_STILL_RUNNING", "SUBMISSION_UNCERTAIN", "DELETE_PENDING", "NO_MISSING_BRANCH", "NO_REMOTE_HANDLE",
                    "IMPORTED_AUDIO_NOT_FOUND", "SOURCE_MISSING", "MISSING_RETRY_DATA", "MISSING_RETRY_RESPONSE_SAVED",
                    "NORMALIZED_ARTIFACT_INVALID",
                ),
            )
            // Attempt errors JobCoordinator and the database record.
            addAll(
                listOf(
                    "INTERRUPTED", "ENGINE_NOT_AVAILABLE", "NO_ACCEPTABLE_CAPTIONS", "CHOOSE_CAPTION_TRACK",
                    "JOB_CONFIG_INVALID", "ARTIFACT_FILE_MISSING", "ARTIFACT_BINDING_MISMATCH", "REMOTE_MAY_CONTINUE",
                    "SCHEDULING_FAILED",
                ),
            )
            // Attempt errors SttStep records.
            addAll(
                listOf(
                    "INVALID_STT_PHASE", "CHECKPOINT_DAMAGED", "PROVIDER_REQUIRED", "MODEL_REQUIRED", "CREDENTIAL_REQUIRED",
                    "UPLOAD_APPROVAL_REQUIRED", "BUDGET_INVALID", "AUDIO_DURATION_LIMIT",
                    "PROVIDER_CAPABILITY_OR_CREDENTIAL_INVALID", "SOURCE_NOT_FOUND", "SOURCE_SNAPSHOT_INVALID",
                    "SOURCE_CHANGED", "CHOOSE_AUDIO_TRACK", "AUDIO_TRACK_MISSING", "SOURCE_AUDIO_UNBOUND",
                    "DOWNLOADED_AUDIO_INVALID", "SOURCE_AUDIO_HASH_FAILED", "AUDIO_RESOURCE_BUSY", "AUDIO_INPUT_MISSING",
                    "PREPARED_AUDIO_ORPHAN", "AUDIO_DURATION_UNKNOWN", "AUDIO_LONGER_THAN_LIMIT", "PREPARED_AUDIO_CHANGED",
                    "PREPARED_AUDIO_INVALID", "PRICE_UNKNOWN", "BUDGET_EXCEEDED", "SUBMISSION_BINDING_MISMATCH",
                    "PROVIDER_RESOURCE_BUSY", "SUBMISSION_CHUNK_MISSING", "REMOTE_HANDLE_INVALID",
                    "REMOTE_RESPONSE_ID_MISMATCH", "REMOTE_RECEIPT_MISSING", "REMOTE_TIMEOUT", "RESPONSE_STORAGE",
                    "RESPONSE_NOT_READY", "NO_TRANSCRIPT", "RAW_RESPONSE_TOO_LARGE", "CANONICAL_ARTIFACT_TOO_LARGE",
                    "RAW_RESPONSE_MISSING",
                ),
            )
            // Reasons of exceptions a run records as they are.
            addAll(
                listOf(
                    CaptionParseException.UNSUPPORTED_FORMAT, CaptionParseException.INPUT_TOO_LARGE,
                    CaptionParseException.EMPTY_INPUT, CaptionParseException.MALFORMED_INPUT,
                    CaptionParseException.NO_SEGMENTS,
                ),
            )
            addAll(
                listOf(
                    ArtifactFilesException.INVALID_ARTIFACT_ID, ArtifactFilesException.INVALID_RAW_EXTENSION,
                    ArtifactFilesException.RAW_EXTENSION_WITHOUT_DATA, ArtifactFilesException.RAW_NOT_ALLOWED,
                    ArtifactFilesException.RAW_DATA_WITHOUT_EXTENSION, ArtifactFilesException.RAW_REQUIRED,
                    ArtifactFilesException.CANONICAL_TOO_LARGE, ArtifactFilesException.RAW_TOO_LARGE,
                    ArtifactFilesException.RAW_HASH_MISMATCH, ArtifactFilesException.CONFLICTING_CONTENT,
                    ArtifactFilesException.NOT_FOUND, ArtifactFilesException.INCOMPLETE_ARTIFACT,
                    ArtifactFilesException.MALFORMED_CANONICAL, ArtifactFilesException.CORRUPT_CANONICAL,
                    ArtifactFilesException.MALFORMED_METADATA, ArtifactFilesException.MALFORMED_ARTIFACT,
                    ArtifactFilesException.CORRUPT_RAW, ArtifactFilesException.SYMLINK_NOT_ALLOWED,
                    ArtifactFilesException.PATH_ESCAPE, ArtifactFilesException.STORAGE_FAILURE,
                ),
            )
            addAll(
                listOf(
                    StorageBudgetException.STORAGE_LIMIT, StorageBudgetException.DEVICE_STORAGE_LOW,
                    StorageBudgetException.STORAGE_SCAN_FAILED, StorageBudgetException.INVALID_ATTEMPT_ID,
                    StorageBudgetException.CLEANUP_FAILED,
                ),
            )
            // Export rows: the errors ExportStore and JobCoordinator record beside the export state.
            addAll(
                listOf(
                    "PERMISSION_REQUIRED", "EXTERNAL_DOCUMENT_MISSING", "EXTERNAL_DOCUMENT_UNCHECKED", "EXPORT_INTERRUPTED",
                    "EXPORT_SCHEDULING_FAILED", "CANCELLED", "RAW_NOT_RETAINED", "TOO_LARGE", "MISMATCH", "IO_FAILURE",
                    "INVALID_EXPORT",
                ),
            )
        }
    }
}
