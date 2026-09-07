package app.sourcescribe.data

import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
