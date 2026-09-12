package app.sourcescribe.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.MainViewModel
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Provider
import app.sourcescribe.core.Region
import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.core.providers.OpenAiAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SttStepTest {
    @Test
    fun chunkPlanUsesExactNonOverlappingMediaIntervals() {
        val plan = SttStep.chunkPlan(1_200_001L)

        assertEquals(3, plan.size)
        assertEquals(0, plan[0].index)
        assertEquals(0L, plan[0].offsetMs)
        assertEquals(600_000L, plan[0].durationMs)
        assertEquals(1, plan[1].index)
        assertEquals(600_000L, plan[1].offsetMs)
        assertEquals(599_841L, plan[1].durationMs)
        assertEquals(2, plan[2].index)
        assertEquals(1_199_841L, plan[2].offsetMs)
        assertEquals(160L, plan[2].durationMs)
        assertTrue(plan.zipWithNext().all { (left, right) -> left.offsetMs + left.durationMs == right.offsetMs })
        assertEquals(1_200_001L, plan.last().offsetMs + plan.last().durationMs)
    }

    @Test
    fun chunkPlanRejectsAudioBeyondTheTenHourBound() {
        try {
            SttStep.chunkPlan(SttStep.MAX_AUDIO_DURATION_MS + 1)
            fail("Expected the bounded planner to reject audio beyond ten hours")
        } catch (_: IllegalArgumentException) {
            // Expected: the orchestration bound is enforced before allocation.
        }
    }

    @Test
    fun estimateCostCeilsMinimumAndAssemblyAddonsAndBlocksUnknownPrice() {
        val assembly = AssemblyAiAdapter()
        val assemblyConfig = JobConfig(
            provider = Provider.ASSEMBLYAI,
            model = AssemblyAiAdapter.MODEL_U35,
            region = Region.EU,
            diarization = true,
            contextTerms = listOf("security"),
        )
        assertEquals(
            280_000L,
            SttStep.estimateCostMicrousd(
                assembly.capabilities(AssemblyAiAdapter.MODEL_U35),
                assemblyConfig,
                3_600_000L,
            ),
        )

        // The same prompt on the cheaper model costs nothing extra: the provider's add-on table reads
        // "Included" in the Universal-2 column of the keyterms row. Round 12 read that cell as five cents
        // an hour, wrote 200 000 here, and made every such estimate a third too high — which can refuse a
        // job the provider would have billed inside the budget the user set.
        val universal2 = assemblyConfig.copy(model = AssemblyAiAdapter.MODEL_U2, diarization = false)
        assertEquals(
            150_000L,
            SttStep.estimateCostMicrousd(
                assembly.capabilities(AssemblyAiAdapter.MODEL_U2),
                universal2,
                3_600_000L,
            ),
        )

        // A list holding only blanks is not a prompt the provider charges for, it is a request the
        // adapter rejects outright. Adding the surcharge for it quoted a higher price for a job that
        // cannot start — the same shape as the length defect round 13 fixed, through another door.
        val blankTerms = assemblyConfig.copy(contextTerms = listOf("", "  "), diarization = false)
        assertEquals(
            210_000L,
            SttStep.estimateCostMicrousd(
                assembly.capabilities(AssemblyAiAdapter.MODEL_U35),
                blankTerms,
                3_600_000L,
            ),
        )

        val groq = GroqAdapter()
        val groqConfig = JobConfig(provider = Provider.GROQ, model = GroqAdapter.MODEL_TURBO)
        assertEquals(
            112L,
            SttStep.estimateCostMicrousd(
                groq.capabilities(GroqAdapter.MODEL_TURBO),
                groqConfig,
                1L,
            ),
        )

        val unknown = OpenAiAdapter().capabilities(OpenAiAdapter.MODEL_GPT_4O_TRANSCRIBE_DIARIZE)
        val unknownConfig = JobConfig(provider = Provider.OPENAI, model = OpenAiAdapter.MODEL_GPT_4O_TRANSCRIBE_DIARIZE)
        assertNull(SttStep.estimateCostMicrousd(unknown, unknownConfig, 60_000L))
    }

    /**
     * The figure shown under a source before anyone agrees to pay it.
     *
     * Round 12 moved the display onto the same chunk plan and the same per-chunk function the budget gate
     * uses, so the two cannot disagree — and then nothing tested the sum itself. The single-chunk function
     * above was covered; adding it up over a plan was not, and that addition is where rounds 3 to 12 kept
     * finding the defects.
     */
    @Test
    fun theEstimateShownIsTheSumOverTheChunksTheRunWouldSubmit() {
        // Twenty-five minutes is three chunks: ten, ten and five. Universal-2 at fifteen cents an hour
        // makes 25 000, 25 000 and 12 500 micro-USD of it.
        val universal2 = JobConfig(
            provider = Provider.ASSEMBLYAI,
            model = AssemblyAiAdapter.MODEL_U2,
            region = Region.EU,
        )
        assertEquals(62_500L, MainViewModel.estimatedCostMicrousd(universal2, 1_500_000L))

        // With speaker labels each chunk rounds up on its own, as each is billed on its own: 28 334 plus
        // 28 334 plus 14 167 is one micro-dollar above the 70 834 an undivided calculation gives. The
        // higher figure is the one the provider charges, so it is the one the reader is shown.
        assertEquals(70_835L, MainViewModel.estimatedCostMicrousd(universal2.copy(diarization = true), 1_500_000L))

        // Groq bills ten seconds however short the audio is, so eight seconds costs what ten would.
        val groq = JobConfig(provider = Provider.GROQ, model = GroqAdapter.MODEL_TURBO)
        assertEquals(112L, MainViewModel.estimatedCostMicrousd(groq, 8_000L))

        // Outside the planner's bounds there is no sum to show, and no number is shown instead of a wrong
        // one: a length of zero, and a length past the ceiling the planner refuses.
        assertNull(MainViewModel.estimatedCostMicrousd(universal2, 0L))
        assertNull(MainViewModel.estimatedCostMicrousd(universal2, SttStep.MAX_AUDIO_DURATION_MS + 1))
    }
}
