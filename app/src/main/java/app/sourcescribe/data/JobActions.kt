package app.sourcescribe.data

import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Outcome

/**
 * One thing the reader can do with a job from the history.
 *
 * These are the actions the job actions dialog offers; each one maps to exactly one call on [JobCoordinator],
 * through `MainViewModel`, except [PREPARE_AGAIN], which only fills the new-source screen.
 */
enum class JobAction {
    /** `JobCoordinator.cancel`: stop a job that has not finished. */
    CANCEL,

    /** `JobCoordinator.resume`: requeue the stopped attempt as it stands. Never sends a new paid submission. */
    RESUME,

    /** `JobCoordinator.retry(missingOnly = true)`: a new attempt for the parts that are missing. */
    RETRY_MISSING,

    /** `JobCoordinator.retry(missingOnly = false)`: a new attempt for every branch of the job. */
    RETRY_ALL,

    /** Loads this job's source and settings into the new-source screen. Changes nothing about this job. */
    PREPARE_AGAIN,

    /** `JobCoordinator.deleteRemote`: asks the provider to remove what it stored for this job. */
    DELETE_REMOTE,

    /** `JobCoordinator.delete`: removes the entry and the results kept only here. */
    DELETE_JOB,

    /**
     * Not an action and never a button: the answer for a job nothing on offer can move. The dialog says so in
     * words instead of highlighting something that would not help.
     */
    NOTHING,
}

/**
 * What the job actions dialog knows about one job, and the only input [JobActions] reads.
 *
 * It is a plain value so the rule below can be decided and tested without a screen, a database or a coordinator.
 */
data class JobSituation(
    val state: ExecutionState,
    val outcome: Outcome,
    /**
     * The error of the newest attempt of every branch, without duplicates and in no particular order. Empty for a
     * job that recorded none - one that succeeded, or one still on its way.
     */
    val errors: List<String> = emptyList(),
    /** False when the stored configuration cannot be decoded; resuming and retrying both need it. */
    val configReadable: Boolean = true,
    val cancelRequested: Boolean = false,
    val remoteDeletionPossible: Boolean = false,
    /** A stored result of this job that is not complete, so asking only for what is missing is a real option. */
    val incompleteResult: Boolean = false,
)

/**
 * Which actions a job can still be moved by, and which one of them to try first.
 *
 * Until 0.4.0 the dialog listed every action a state allowed and said nothing about any of them: the owner's
 * report of 16 September was that he saw "Decision required" and six buttons without knowing what had gone wrong,
 * what each button did, or which one to press. Worse, the first button in the list was "Resume safely", and for a
 * job whose pinned extraction component had been replaced that button requeued the same attempt with the same
 * component and reproduced the same stop, every time.
 *
 * So the rule lives here rather than in the dialog: what an error asks for is a fact about the error and the
 * job's state, testable without a screen, and [actionFor] names every code this app can put in front of a reader.
 */
object JobActions {
    /** Both outcomes that count as a finished result; a job with one has nothing missing to fetch. */
    private val SUCCESSFUL = setOf(Outcome.SUCCESS, Outcome.SUCCESS_WITH_WARNINGS)

    /** The states in which a job is over, whether it worked or not. */
    private val OVER = setOf(ExecutionState.FINISHED, ExecutionState.CANCELLED)

    /**
     * Codes a retry of only the missing parts would carry into the new attempt, so for them the whole job has to
     * run again.
     *
     * `JobCoordinator.retry(missingOnly = true)` reuses `prior.engineId` when it finds a partial result for the
     * STT branch, because the chunks already paid for were prepared with that component. That is right for every
     * other reason and exactly wrong for this one: the attempt would be pinned again to the component that is no
     * longer there.
     */
    private val CARRIED_INTO_A_PARTIAL_RETRY = setOf("ENGINE_NOT_AVAILABLE")

    /**
     * The action that can get a job past [code], or null when this app has no answer for it.
     *
     * Null is not "nothing helps" - that is [JobAction.NOTHING]. Null means the code is not one this table names,
     * which `JobActionsTest` forbids for every code the app can show. The dialog then offers its actions without
     * recommending one rather than pointing at a guess.
     */
    fun actionFor(code: String): JobAction? = when (code) {
        // The cause lies outside this job, so the attempt that stopped can get further once it is gone - and
        // resuming never sends a new paid submission, which is what makes it the first thing to try.
        "NETWORK", "PROVIDER_NETWORK", "RESPONSE_NETWORK", "NETWORK_RAW_SAVED", "ENGINE_NETWORK",
        "RATE_LIMIT", "PROVIDER_RATE_LIMIT", "RESPONSE_RATE_LIMIT", "RATE_LIMIT_RAW_SAVED", "ENGINE_RATE_LIMIT",
        "SERVER", "PROVIDER_SERVER", "RESPONSE_SERVER", "SERVER_RAW_SAVED",
        "QUOTA", "PROVIDER_QUOTA", "RESPONSE_QUOTA", "QUOTA_RAW_SAVED",
        "REMOTE_TIMEOUT", "RESPONSE_NOT_READY",
        // A key the provider refused: `resume` puts a submission rejected for authentication back to prepared, so
        // a corrected key is picked up without paying again. That is the one case resume repairs by itself.
        "AUTHENTICATION", "PROVIDER_AUTHENTICATION", "RESPONSE_AUTHENTICATION", "AUTHENTICATION_RAW_SAVED",
        "ACCESS_DENIED", "PROVIDER_ACCESS_DENIED", "RESPONSE_ACCESS_DENIED", "ACCESS_DENIED_RAW_SAVED",
        "KEY_NOT_FOUND", "KEY_LOCKED_OR_INVALIDATED", "KEY_CORRUPT", "KEY_INVALID_INPUT", "KEY_STORAGE",
        "CREDENTIAL_NOT_FOUND", "CREDENTIAL_LOCKED_OR_INVALIDATED", "CREDENTIAL_CORRUPT",
        "CREDENTIAL_INVALID_INPUT", "CREDENTIAL_STORAGE",
        // Room on the device, which the reader can make.
        "STORAGE", "ENGINE_STORAGE", "AUDIO_STORAGE_FAILED", "AUDIO_IMPORT_STORAGE", "AUDIO_IMPORT_STORAGE_LIMIT",
        "RESPONSE_STORAGE", "PROVIDER_RESPONSE_STORAGE", "RESPONSE_RESPONSE_STORAGE", "RESPONSE_STORAGE_RAW_SAVED",
        "STORAGE_FAILURE", "STORAGE_LIMIT", "DEVICE_STORAGE_LOW", "STORAGE_SCAN_FAILED",
        // What YouTube answered this time: a video that was unreachable or a page that demanded a challenge can
        // be reachable an hour later, and the extraction is redone from scratch on every run anyway.
        "SOURCE_UNAVAILABLE", "CHALLENGE_REQUIRED", "INVALID_RESPONSE",
        // The app's own run was cut short, or a slot the next full check repairs.
        "INTERRUPTED", "SCHEDULING_FAILED", "NATIVE", "LOCAL_PROCESSING_FAILED",
        "ENGINE_VERIFICATION", "ENGINE_UNCERTAIN_PROBE", "ENGINE_SLOTS_IN_USE",
        -> JobAction.RESUME

        // What this attempt stored is what refused it: the component it is pinned to, its checkpoint, its files,
        // its submissions, its artifact. `resume` writes only the attempt's state, its retry counter, its error
        // and its lease, so it would read the same stored data and stop in the same place. A new attempt builds
        // all of it again and binds the component in use now.
        "ENGINE_NOT_AVAILABLE", "ENGINE_REQUIRES_APP_UPDATE", "ENGINE_PROBE_FAILED",
        "CHECKPOINT_DAMAGED", "INVALID_STT_PHASE", "INVALID_ATTEMPT_ID",
        "AUDIO_TRACK_MISSING", "SOURCE_AUDIO_UNBOUND", "DOWNLOADED_AUDIO_INVALID", "SOURCE_AUDIO_HASH_FAILED",
        "AUDIO_INPUT_MISSING", "PREPARED_AUDIO_ORPHAN", "PREPARED_AUDIO_CHANGED", "PREPARED_AUDIO_INVALID",
        "AUDIO_INVALID_INPUT", "AUDIO_INPUT_NOT_FILE", "AUDIO_INVALID_OUTPUT_DIRECTORY", "AUDIO_OUTPUT_EXISTS",
        "AUDIO_OUT_OF_RANGE", "AUDIO_PROBE_FAILED", "AUDIO_CONVERSION_FAILED", "AUDIO_OUTPUT_INVALID",
        "SUBMISSION_BINDING_MISMATCH", "SUBMISSION_CHUNK_MISSING",
        "REMOTE_HANDLE_INVALID", "REMOTE_RECEIPT_MISSING", "REMOTE_RESPONSE_ID_MISMATCH",
        "NO_TRANSCRIPT", "RAW_RESPONSE_MISSING",
        "PROVIDER_INVALID_RESPONSE", "RESPONSE_INVALID_RESPONSE", "INVALID_RESPONSE_RAW_SAVED",
        "REMOTE_FAILED", "PROVIDER_REMOTE_FAILED", "RESPONSE_REMOTE_FAILED", "REMOTE_FAILED_RAW_SAVED",
        "MISSING_RETRY_DATA", "MISSING_RETRY_RESPONSE_SAVED",
        "NORMALIZED_ARTIFACT_INVALID", "ARTIFACT_BINDING_MISMATCH", "ARTIFACT_FILE_MISSING",
        "INVALID_ARTIFACT_ID", "INVALID_RAW_EXTENSION", "RAW_EXTENSION_WITHOUT_DATA", "RAW_DATA_WITHOUT_EXTENSION",
        "RAW_HASH_MISMATCH", "CONFLICTING_CONTENT", "NOT_FOUND", "INCOMPLETE_ARTIFACT",
        "MALFORMED_CANONICAL", "CORRUPT_CANONICAL", "MALFORMED_METADATA", "MALFORMED_ARTIFACT", "CORRUPT_RAW",
        "SYMLINK_NOT_ALLOWED", "PATH_ESCAPE",
        // A caption file that could not be read. Fetching captions costs nothing and is done again from scratch,
        // so a fresh run is the cheap thing to try before the reader changes the job.
        "UNSUPPORTED_FORMAT", "EMPTY_INPUT", "MALFORMED_INPUT", "NO_SEGMENTS", "INPUT_TOO_LARGE",
        // The track this job bound is no longer among the ones the source offers; a new attempt resolves again.
        "CAPTION_TRACK_CHANGED", "AUDIO_TRACK_CHANGED",
        // What the source's own metadata said did not hold together; it is fetched again on every attempt.
        "SOURCE_ID_MISMATCH", "METADATA_TOO_LARGE", "INVALID_METADATA", "INVALID_DURATION", "SOURCE_VALIDATION",
        "INVALID_CAPTION_URL",
        -> JobAction.RETRY_ALL

        // The job's own settings, limits or choices are what refused it, and a job carries the configuration it
        // was started with for good: resuming and retrying both reuse it, so only a new job can differ.
        "PROVIDER_REQUIRED", "MODEL_REQUIRED", "CREDENTIAL_REQUIRED", "UPLOAD_APPROVAL_REQUIRED",
        "PROVIDER_CAPABILITY_OR_CREDENTIAL_INVALID",
        "UNSUPPORTED_OPTION", "PROVIDER_UNSUPPORTED_OPTION", "RESPONSE_UNSUPPORTED_OPTION",
        "UNSUPPORTED_OPTION_RAW_SAVED",
        "INVALID_INPUT", "PROVIDER_INVALID_INPUT", "RESPONSE_INVALID_INPUT", "INVALID_INPUT_RAW_SAVED",
        "BUDGET_INVALID", "BUDGET_EXCEEDED", "PRICE_UNKNOWN", "CONTEXT_TERM_BLANK",
        "AUDIO_DURATION_LIMIT", "AUDIO_LONGER_THAN_LIMIT", "SOURCE_LONGER_THAN_LIMIT", "AUDIO_DURATION_UNKNOWN",
        "SOURCE_DURATION_UNKNOWN",
        "NO_ACCEPTABLE_CAPTIONS", "NO_CAPTIONS", "CHOOSE_CAPTION_TRACK", "CHOOSE_AUDIO_TRACK", "NO_AUDIO",
        "AUDIO_OUTPUT_TOO_LARGE", "RAW_RESPONSE_TOO_LARGE", "RAW_TOO_LARGE", "RAW_NOT_ALLOWED", "RAW_REQUIRED",
        // An imported file that is gone or unreadable. Only a new job can point at another file.
        "SOURCE_CHANGED", "IMPORTED_AUDIO_NOT_FOUND",
        "AUDIO_IMPORT_INVALID_INPUT", "AUDIO_IMPORT_INPUT_UNAVAILABLE", "AUDIO_IMPORT_PROBE_FAILED",
        "AUDIO_IMPORT_CORRUPT",
        // The link this job was made from cannot name one finished video, and a job's source never changes.
        "INVALID_URL", "INVALID_HOST", "INVALID_PATH", "INVALID_QUERY", "INVALID_VIDEO_ID",
        "AMBIGUOUS_VIDEO", "TOO_MANY_VIDEOS", "EXPLICIT_VIDEO_REQUIRED", "LIVE_OR_PLAYLIST_UNSUPPORTED",
        -> JobAction.PREPARE_AGAIN

        // A record this job needs is gone, and nothing rebuilds it: every action reads the stored source or the
        // stored configuration first, so the entry is all that is left to decide about.
        "JOB_CONFIG_INVALID", "SOURCE_MISSING", "SOURCE_NOT_FOUND", "SOURCE_SNAPSHOT_INVALID",
        -> JobAction.DELETE_JOB

        // Nothing on offer changes these. Either the app is already working on it, or acting would risk paying a
        // second time for a submission that may well have gone through.
        "SUBMISSION_UNCERTAIN", "PROVIDER_SUBMISSION_UNCERTAIN", "RESPONSE_SUBMISSION_UNCERTAIN",
        "SUBMISSION_UNCERTAIN_RAW_SAVED", "REMOTE_MAY_CONTINUE",
        "AUDIO_RESOURCE_BUSY", "PROVIDER_RESOURCE_BUSY",
        "JOB_STILL_RUNNING", "DELETE_PENDING", "NO_MISSING_BRANCH", "NO_REMOTE_HANDLE",
        "CANONICAL_ARTIFACT_TOO_LARGE", "CANONICAL_TOO_LARGE", "CLEANUP_FAILED",
        "ENGINE_NO_PREVIOUS", "ENGINE_ROLLBACK_TARGET_CHANGED",
        -> JobAction.NOTHING

        else -> null
    }

    /**
     * Every action worth showing for this job, in the order the dialog lists them.
     *
     * An action is left out when it cannot change anything in this state. That is what the state allows, as
     * before, plus one rule that is new in 0.4.0: [JobAction.RESUME] is offered only for reasons resuming can
     * actually get past. `JobCoordinator.resume` requeues the attempt with everything it stored - the same
     * component, the same checkpoint, the same files - and writes only its state, retry counter, error and lease,
     * besides putting a submission the provider rejected for authentication back to prepared. So where the reason
     * is the attempt's own stored data, the same run reads the same data and stops in the same place; the button
     * was a loop, not an option.
     */
    fun offered(situation: JobSituation): List<JobAction> = buildList {
        if (situation.state !in OVER && !situation.cancelRequested) add(JobAction.CANCEL)
        if (situation.configReadable && !situation.cancelRequested &&
            situation.state == ExecutionState.WAITING_USER &&
            situation.errors.all { actionFor(it) == JobAction.RESUME }
        ) {
            add(JobAction.RESUME)
        }
        if (situation.configReadable && situation.state != ExecutionState.RUNNING) {
            if (situation.outcome !in SUCCESSFUL) add(JobAction.RETRY_MISSING)
            add(JobAction.RETRY_ALL)
        }
        // Never refused: it starts nothing and changes nothing about this job, it only fills the other screen.
        add(JobAction.PREPARE_AGAIN)
        if (situation.state in OVER && situation.remoteDeletionPossible) add(JobAction.DELETE_REMOTE)
        add(JobAction.DELETE_JOB)
    }

    /**
     * The one action to highlight, or null when this app will not name one.
     *
     * Null happens for a reason [actionFor] does not name, and for a demanded action this state does not offer -
     * saying nothing is then the honest answer, and the other actions are still listed.
     */
    fun recommended(situation: JobSituation): JobAction? {
        val wanted = demanded(situation) ?: return null
        if (wanted == JobAction.NOTHING) return JobAction.NOTHING
        return wanted.takeIf { it in offered(situation) }
    }

    /**
     * What the job's reasons together ask for.
     *
     * With two branches and two reasons the stronger one decides: a job whose captions only need resuming while
     * its speech-to-text branch needs a new attempt is not finished by resuming. An unnamed reason makes the whole
     * answer null rather than being quietly skipped, because a recommendation drawn from half the reasons would
     * look exactly like one drawn from all of them.
     */
    private fun demanded(situation: JobSituation): JobAction? {
        if (situation.errors.isEmpty()) {
            return if (situation.incompleteResult) JobAction.RETRY_MISSING else JobAction.NOTHING
        }
        val strongest = situation.errors.map { actionFor(it) ?: return null }.maxBy(::rank)
        // Part of the result is there and was paid for. Asking only for what is missing keeps it, which is worth
        // more than the extra certainty of a whole new run - except where the new attempt would inherit the very
        // thing that refused this one.
        if (strongest == JobAction.RETRY_ALL && situation.incompleteResult &&
            situation.errors.none { it in CARRIED_INTO_A_PARTIAL_RETRY }
        ) {
            return JobAction.RETRY_MISSING
        }
        return strongest
    }

    /** How much of the job an action gives up, from keeping everything to keeping nothing. */
    private fun rank(action: JobAction): Int = when (action) {
        JobAction.NOTHING -> 0
        JobAction.RESUME -> 1
        JobAction.RETRY_MISSING -> 2
        JobAction.RETRY_ALL -> 3
        JobAction.PREPARE_AGAIN -> 4
        JobAction.DELETE_JOB -> 5
        // Neither is ever what a reason asks for: cancelling is the reader's own wish, and removing data at the
        // provider is a separate decision about the provider, not a way to get this job further.
        JobAction.CANCEL, JobAction.DELETE_REMOTE -> 0
    }
}
