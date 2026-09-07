package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AcquisitionModeMatrixTest {
    @Test
    fun everyModePlansEachCaptionStateExactly() {
        val expected = mapOf(
            AcquisitionMode.CAPTIONS_ONLY to mapOf(
                CaptionAvailability.AVAILABLE to setOf(PlannedAction.FETCH_CAPTIONS),
                CaptionAvailability.MISSING to setOf(PlannedAction.CAPTION_FAILURE),
                CaptionAvailability.FETCH_ERROR to setOf(PlannedAction.WAIT_FOR_USER),
            ),
            AcquisitionMode.CAPTIONS_THEN_STT to mapOf(
                CaptionAvailability.AVAILABLE to setOf(PlannedAction.FETCH_CAPTIONS),
                CaptionAvailability.MISSING to setOf(PlannedAction.TRANSCRIBE),
                CaptionAvailability.FETCH_ERROR to setOf(PlannedAction.WAIT_FOR_USER),
            ),
            AcquisitionMode.STT_ONLY to mapOf(
                CaptionAvailability.AVAILABLE to setOf(PlannedAction.TRANSCRIBE),
                CaptionAvailability.MISSING to setOf(PlannedAction.TRANSCRIBE),
                CaptionAvailability.FETCH_ERROR to setOf(PlannedAction.TRANSCRIBE),
            ),
            AcquisitionMode.BOTH to mapOf(
                CaptionAvailability.AVAILABLE to setOf(PlannedAction.FETCH_CAPTIONS, PlannedAction.TRANSCRIBE),
                CaptionAvailability.MISSING to setOf(PlannedAction.CAPTION_FAILURE, PlannedAction.TRANSCRIBE),
                CaptionAvailability.FETCH_ERROR to setOf(PlannedAction.WAIT_FOR_USER, PlannedAction.TRANSCRIBE),
            ),
        )
        val expectedBranches = mapOf(
            AcquisitionMode.CAPTIONS_ONLY to setOf(Branch.CAPTIONS),
            AcquisitionMode.CAPTIONS_THEN_STT to setOf(Branch.CAPTIONS, Branch.STT),
            AcquisitionMode.STT_ONLY to setOf(Branch.STT),
            AcquisitionMode.BOTH to setOf(Branch.CAPTIONS, Branch.STT),
        )

        for ((mode, states) in expected) {
            assertEquals(expectedBranches[mode], AcquisitionPlanner.requestedBranches(mode))
            for ((captions, actions) in states) {
                assertEquals(actions, AcquisitionPlanner.plan(JobConfig(mode = mode), captions))
            }
        }
    }

    @Test
    fun fallbackOnCaptionErrorIsExplicitAndModeScoped() {
        assertEquals(
            setOf(PlannedAction.WAIT_FOR_USER),
            AcquisitionPlanner.plan(
                JobConfig(mode = AcquisitionMode.CAPTIONS_THEN_STT, fallbackOnCaptionError = false),
                CaptionAvailability.FETCH_ERROR,
            ),
        )
        assertEquals(
            setOf(PlannedAction.TRANSCRIBE),
            AcquisitionPlanner.plan(
                JobConfig(mode = AcquisitionMode.CAPTIONS_THEN_STT, fallbackOnCaptionError = true),
                CaptionAvailability.FETCH_ERROR,
            ),
        )

        for (mode in AcquisitionMode.entries.filter { it != AcquisitionMode.CAPTIONS_THEN_STT }) {
            val withoutFallback = AcquisitionPlanner.plan(
                JobConfig(mode = mode, fallbackOnCaptionError = false),
                CaptionAvailability.FETCH_ERROR,
            )
            val withFallback = AcquisitionPlanner.plan(
                JobConfig(mode = mode, fallbackOnCaptionError = true),
                CaptionAvailability.FETCH_ERROR,
            )
            assertEquals(withoutFallback, withFallback)
        }
    }
}
