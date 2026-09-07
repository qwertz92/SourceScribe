package app.sourcescribe.core

enum class CaptionAvailability { NOT_CHECKED, AVAILABLE, MISSING, FETCH_ERROR }
enum class PlannedAction { FETCH_CAPTIONS, TRANSCRIBE, WAIT_FOR_USER, CAPTION_FAILURE }

object AcquisitionPlanner {
    fun requestedBranches(mode: AcquisitionMode): Set<Branch> = when (mode) {
        AcquisitionMode.CAPTIONS_ONLY -> setOf(Branch.CAPTIONS)
        AcquisitionMode.CAPTIONS_THEN_STT -> setOf(Branch.CAPTIONS, Branch.STT)
        AcquisitionMode.STT_ONLY -> setOf(Branch.STT)
        AcquisitionMode.BOTH -> setOf(Branch.CAPTIONS, Branch.STT)
    }

    fun plan(config: JobConfig, captions: CaptionAvailability): Set<PlannedAction> {
        val captionAction = when (captions) {
            CaptionAvailability.NOT_CHECKED, CaptionAvailability.AVAILABLE -> PlannedAction.FETCH_CAPTIONS
            CaptionAvailability.MISSING -> PlannedAction.CAPTION_FAILURE
            CaptionAvailability.FETCH_ERROR -> PlannedAction.WAIT_FOR_USER
        }
        return when (config.mode) {
            AcquisitionMode.CAPTIONS_ONLY -> setOf(captionAction)
            AcquisitionMode.STT_ONLY -> setOf(PlannedAction.TRANSCRIBE)
            AcquisitionMode.BOTH -> setOf(captionAction, PlannedAction.TRANSCRIBE)
            AcquisitionMode.CAPTIONS_THEN_STT -> when (captions) {
                CaptionAvailability.MISSING -> setOf(PlannedAction.TRANSCRIBE)
                CaptionAvailability.FETCH_ERROR -> setOf(if (config.fallbackOnCaptionError) PlannedAction.TRANSCRIBE else PlannedAction.WAIT_FOR_USER)
                else -> setOf(PlannedAction.FETCH_CAPTIONS)
            }
        }
    }

    fun outcome(mode: AcquisitionMode, successful: Set<Branch>, warnings: Boolean, complete: Boolean): Outcome {
        if (successful.isEmpty()) return Outcome.FAILED
        val satisfied = when (mode) {
            AcquisitionMode.CAPTIONS_ONLY -> Branch.CAPTIONS in successful
            AcquisitionMode.STT_ONLY -> Branch.STT in successful
            AcquisitionMode.CAPTIONS_THEN_STT -> successful.isNotEmpty()
            AcquisitionMode.BOTH -> successful.containsAll(setOf(Branch.CAPTIONS, Branch.STT))
        }
        return if (!satisfied || !complete) Outcome.PARTIAL_SUCCESS else if (warnings) Outcome.SUCCESS_WITH_WARNINGS else Outcome.SUCCESS
    }
}
