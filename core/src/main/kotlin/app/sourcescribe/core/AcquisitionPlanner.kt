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

    /**
     * The attempts a fresh job starts with. CAPTIONS_THEN_STT begins on captions alone and reaches
     * speech-to-text only as a fallback, so it starts with one attempt while requesting both branches.
     */
    fun initialBranches(mode: AcquisitionMode): List<Branch> = when (mode) {
        AcquisitionMode.STT_ONLY -> listOf(Branch.STT)
        AcquisitionMode.BOTH -> listOf(Branch.CAPTIONS, Branch.STT)
        AcquisitionMode.CAPTIONS_ONLY, AcquisitionMode.CAPTIONS_THEN_STT -> listOf(Branch.CAPTIONS)
    }

    /** True where a caption track is read, so which track that is stays the reader's choice. */
    fun usesCaptions(mode: AcquisitionMode): Boolean = Branch.CAPTIONS in requestedBranches(mode)

    /**
     * True where speech-to-text can still happen, so audio track, provider, limits and cost belong to
     * the decision. That includes CAPTIONS_THEN_STT, which reaches a provider when captions are missing.
     */
    fun mayUseSpeechToText(mode: AcquisitionMode): Boolean = Branch.STT in requestedBranches(mode)

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
