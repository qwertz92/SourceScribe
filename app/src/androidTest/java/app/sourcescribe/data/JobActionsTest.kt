package app.sourcescribe.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.ShownCodes
import app.sourcescribe.core.ExecutionState
import app.sourcescribe.core.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Item 15 of the 0.4.0 plan: the job actions dialog names the one action to try, and it can only do that if every
 * code a job can stop with has one.
 *
 * The inventory is the same object `MessageTextTest` reads, minus the codes a job never records - each of those is
 * named below with the place it does appear. A code added to the inventory and to neither list fails here, which is
 * the point: an action that gets a reader further is a decision somebody has to make, not a default.
 */
@RunWith(AndroidJUnit4::class)
class JobActionsTest {
    @Test
    fun everyCodeAJobCanStopWithHasARecommendedAction() {
        val stale = NOT_A_JOB_ERROR.filterNot { it in ShownCodes.ALL }.sorted()
        assertEquals("Excluded codes the app does not know at all", emptyList<String>(), stale)

        val jobErrors = ShownCodes.ALL - NOT_A_JOB_ERROR
        val missing = jobErrors.filter { JobActions.actionFor(it) == null }.sorted()
        assertEquals("Codes a job can stop with and no action is recommended for", emptyList<String>(), missing)

        // Every value of the table is reachable, so none of the five answers is dead wording nobody ever sees.
        val used = jobErrors.mapNotNull { JobActions.actionFor(it) }.toSet()
        assertEquals(
            setOf(
                JobAction.RESUME, JobAction.RETRY_ALL, JobAction.PREPARE_AGAIN,
                JobAction.DELETE_JOB, JobAction.NOTHING,
            ),
            used,
        )
        // A reason never asks for these two: cancelling is the reader's own wish, and removing data at the
        // provider is a decision about the provider, not a way to get this job further.
        assertFalse(JobAction.CANCEL in used)
        assertFalse(JobAction.DELETE_REMOTE in used)
        assertNull("A code nobody has classified must answer null, not a guess", JobActions.actionFor("NOT_A_CODE"))
    }

    @Test
    fun eachKindOfReasonAsksForTheActionThatCanGetPastIt() {
        // One code per answer, chosen because the reason is what the whole rule is built around.
        // The provider refused the key: `JobCoordinator.resume` puts a submission rejected for authentication
        // back to prepared, so a corrected key is picked up without paying again.
        assertEquals(JobAction.RESUME, JobActions.actionFor("PROVIDER_AUTHENTICATION"))
        // The review of 16 September: the attempt is pinned to a component that is gone, and resuming requeues
        // the same attempt with the same component. A new run binds the component in use now.
        assertEquals(JobAction.RETRY_ALL, JobActions.actionFor("ENGINE_NOT_AVAILABLE"))
        // A limit belongs to the job for good; resuming and retrying both reuse its configuration.
        assertEquals(JobAction.PREPARE_AGAIN, JobActions.actionFor("AUDIO_LONGER_THAN_LIMIT"))
        // The source record this job needs is gone, and every action reads it first.
        assertEquals(JobAction.DELETE_JOB, JobActions.actionFor("SOURCE_MISSING"))
        // A submission that may well have been accepted: acting could pay for it a second time.
        assertEquals(JobAction.NOTHING, JobActions.actionFor("SUBMISSION_UNCERTAIN"))
    }

    @Test
    fun resumeIsOfferedOnlyWhereResumingWouldGetFurther() {
        // The defect this closes. A job waiting on the reader whose component was replaced used to show
        // "Resume safely" first; pressing it requeued the attempt with the same engine id and reproduced the same
        // stop, with nothing on screen saying why.
        val stranded = waitingFor("ENGINE_NOT_AVAILABLE")
        assertFalse("Resume is a loop for this reason", JobAction.RESUME in JobActions.offered(stranded))
        assertEquals(JobAction.RETRY_ALL, JobActions.recommended(stranded))

        // The same state with a reason that lies outside the job: resuming is offered and is the recommendation.
        val waitingForTheNetwork = waitingFor("NETWORK")
        assertTrue(JobAction.RESUME in JobActions.offered(waitingForTheNetwork))
        assertEquals(JobAction.RESUME, JobActions.recommended(waitingForTheNetwork))

        // Two branches, two reasons: the stronger one decides, and resuming the one would not finish the job.
        val mixed = waitingFor("NETWORK", "CHECKPOINT_DAMAGED")
        assertFalse(JobAction.RESUME in JobActions.offered(mixed))
        assertEquals(JobAction.RETRY_ALL, JobActions.recommended(mixed))

        // Only a job waiting on the reader can be resumed at all; a queued one has not stopped.
        assertFalse(JobAction.RESUME in JobActions.offered(waitingFor("NETWORK").copy(state = ExecutionState.QUEUED)))
        // Nor one whose stored configuration cannot be read - `resume` decodes it before it requeues anything.
        assertFalse(JobAction.RESUME in JobActions.offered(waitingFor("NETWORK").copy(configReadable = false)))
    }

    @Test
    fun aPartlyFinishedResultIsCompletedRatherThanPaidForAgain() {
        // Part of the transcript is there and was paid for. Asking only for the missing chunks keeps it.
        val partial = waitingFor("PROVIDER_REMOTE_FAILED").copy(
            outcome = Outcome.PARTIAL_SUCCESS,
            incompleteResult = true,
        )
        assertEquals(JobAction.RETRY_MISSING, JobActions.recommended(partial))

        // Except for this one reason: `JobCoordinator.retry(missingOnly = true)` reuses the previous attempt's
        // engine id when it finds a partial result, so the new attempt would be pinned to the component that is
        // gone and stop in exactly the same place.
        val partialWithNoEngine = partial.copy(errors = listOf("ENGINE_NOT_AVAILABLE"))
        assertEquals(JobAction.RETRY_ALL, JobActions.recommended(partialWithNoEngine))
        // Round 25, finding 2: not recommending it was not enough. The dialog still listed "Fetch only what
        // is missing" as a button, under the description "Take it when a result came back incomplete" — which
        // is literally this job — and pressing it pinned the new attempt to the same missing component and
        // reproduced the same stop. That is the loop `resumeIsOfferedOnlyWhereResumingWouldGetFurther` closes
        // for RESUME, and it was open for this action.
        assertFalse("A partial retry is a loop for this reason",
            JobAction.RETRY_MISSING in JobActions.offered(partialWithNoEngine))
        // Every other reason keeps it: the exclusion is about this one code, not about partial results.
        assertTrue(JobAction.RETRY_MISSING in JobActions.offered(partial))
        // Two branches, one of them stranded on the missing component: the whole job has to run again.
        assertFalse(JobAction.RETRY_MISSING in JobActions.offered(
            partial.copy(errors = listOf("PROVIDER_REMOTE_FAILED", "ENGINE_NOT_AVAILABLE"))))
        // And the same code without a partial result keeps it: `retry(missingOnly = true)` reuses the old
        // component only where it finds one, so with nothing to keep the new attempt binds the active
        // component like any other. Excluding it there would take away a branch the reader can still fetch.
        assertTrue(JobAction.RETRY_MISSING in JobActions.offered(
            waitingFor("ENGINE_NOT_AVAILABLE").copy(outcome = Outcome.FAILED)))

        // Nothing missing and nothing wrong: no action is recommended, and the dialog says so.
        val finished = JobSituation(state = ExecutionState.FINISHED, outcome = Outcome.SUCCESS)
        assertEquals(JobAction.NOTHING, JobActions.recommended(finished))
        assertFalse(JobAction.RETRY_MISSING in JobActions.offered(finished))
    }

    @Test
    fun anActionThisStateCannotCarryOutIsNeitherOfferedNorRecommended() {
        // A running job: stopping it is all there is. Retrying would race the run, which `JobCoordinator.retry`
        // refuses with JOB_STILL_RUNNING anyway, so the dialog does not offer it.
        val running = JobSituation(state = ExecutionState.RUNNING, outcome = Outcome.NONE)
        assertEquals(
            listOf(JobAction.CANCEL, JobAction.PREPARE_AGAIN, JobAction.DELETE_JOB),
            JobActions.offered(running),
        )

        // A job whose configuration cannot be decoded can only be prepared anew or deleted, and deleting is what
        // the reason asks for.
        val broken = JobSituation(
            state = ExecutionState.WAITING_USER,
            outcome = Outcome.FAILED,
            errors = listOf("JOB_CONFIG_INVALID"),
            configReadable = false,
        )
        assertEquals(listOf(JobAction.CANCEL, JobAction.PREPARE_AGAIN, JobAction.DELETE_JOB), JobActions.offered(broken))
        assertEquals(JobAction.DELETE_JOB, JobActions.recommended(broken))

        // Removing the provider's copy is offered only once the job is over and the provider can be reached for
        // it, and it is never the recommendation.
        val finished = JobSituation(
            state = ExecutionState.FINISHED,
            outcome = Outcome.SUCCESS,
            remoteDeletionPossible = true,
        )
        assertTrue(JobAction.DELETE_REMOTE in JobActions.offered(finished))
        assertFalse(JobAction.DELETE_REMOTE in JobActions.offered(finished.copy(remoteDeletionPossible = false)))
        assertEquals(JobAction.NOTHING, JobActions.recommended(finished))

        // A reason this app has no answer for: the actions are still listed, none of them is pointed at.
        val unknown = waitingFor("SOMETHING_NOBODY_CLASSIFIED")
        assertNull(JobActions.recommended(unknown))
        assertTrue(JobActions.offered(unknown).isNotEmpty())
    }

    private fun waitingFor(vararg errors: String) = JobSituation(
        state = ExecutionState.WAITING_USER,
        outcome = Outcome.NONE,
        errors = errors.toList(),
    )

    private companion object {
        /**
         * Codes of the inventory a job never records, with the place each group does appear. They are listed
         * rather than filtered by shape, so adding one to the inventory forces a decision about it here too.
         */
        val NOT_A_JOB_ERROR = setOf(
            // A snackbar the screen shows for an action that has just run: saving a key, a preset or a keyterm
            // set, copying from the clipboard, checking for an engine update, opening an export folder.
            "JOBS_CREATED", "CLIPBOARD_EMPTY", "REMOTE_DELETE_CONFIRMED", "FILE_NAME_SAVED", "DEFAULTS_SAVED",
            "PRESET_SAVED", "KEY_SAVED", "ENGINE_CURRENT", "ENGINE_ACTIVE", "ACTION_BUSY",
            "KEYTERM_SET_SAVED", "KEYTERM_SET_REFUSED", "NO_FOLDER_APP",
            // An export row's own state and error. The job card shows those beside the format and offers "Save
            // export again" for them; they say nothing about the job that produced the transcript.
            "EXPORT_NOT_REQUESTED", "EXPORT_PENDING", "EXPORT_WRITING", "EXPORT_EXPORTED",
            "EXPORT_PERMISSION_REQUIRED", "EXPORT_FAILED", "EXPORT_INTERRUPTED", "EXPORT_SCHEDULING_FAILED",
            "PERMISSION_REQUIRED", "EXTERNAL_DOCUMENT_MISSING", "EXTERNAL_DOCUMENT_UNCHECKED", "CANCELLED",
            "RAW_NOT_RETAINED", "TOO_LARGE", "MISMATCH", "IO_FAILURE", "INVALID_EXPORT",
        )
    }
}
