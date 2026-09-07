package app.sourcescribe.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sourcescribe.core.ArtifactFiles
import app.sourcescribe.data.SttStep.Companion.RawResponse
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SttHardeningTest {
    @Test
    fun sixHundredOneSecondFixtureCreatesAdjacentNeighborChunk() {
        val plan = SttStep.chunkPlan(601_000L)

        assertEquals(2, plan.size)
        assertEquals(600_000L, plan[0].durationMs)
        assertEquals(600_000L, plan[1].offsetMs)
        assertEquals(1_000L, plan[1].durationMs)
        assertEquals(plan[0].offsetMs + plan[0].durationMs, plan[1].offsetMs)
    }

    @Test
    fun chunkPlanMergesShortFinalResidualWithoutGap() {
        val plan = SttStep.chunkPlan(1_200_001L)

        assertEquals(3, plan.size)
        assertEquals(599_841L, plan[1].durationMs)
        assertEquals(1_199_841L, plan[2].offsetMs)
        assertEquals(SttStep.MIN_FINAL_CHUNK_DURATION_MS, plan[2].durationMs)
        assertTrue(plan.zipWithNext().all { (left, right) -> left.offsetMs + left.durationMs == right.offsetMs })
        assertEquals(1_200_001L, plan.last().offsetMs + plan.last().durationMs)
    }

    @Test
    fun singleRawPayloadKeepsOriginalProviderBytes() {
        val original = byteArrayOf('{'.code.toByte(), ' '.code.toByte(), 0, '\n'.code.toByte(), '}'.code.toByte())

        val payload = requireNotNull(SttStep.rawPayload(listOf(RawResponse(7, original))))

        assertEquals("json", payload.extension)
        assertArrayEquals(original, payload.bytes)
    }

    @Test
    fun multipleRawPayloadsZipEachOriginalResponse() {
        val originals = listOf(
            RawResponse(4, "{\"text\":\"first\"}\n".toByteArray()),
            RawResponse(9, byteArrayOf(0, 1, 2, 127, (-1).toByte())),
        )

        val payload = requireNotNull(SttStep.rawPayload(originals))
        assertEquals("zip", payload.extension)

        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(payload.bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }

        assertEquals(listOf("chunk-4.json", "chunk-9.json"), entries.keys.toList())
        assertArrayEquals(originals[0].bytes, entries.getValue("chunk-4.json"))
        assertArrayEquals(originals[1].bytes, entries.getValue("chunk-9.json"))
    }

    @Test
    fun rawPayloadRejectsAggregateBeyondRetentionCap() {
        val first = ByteArray(ArtifactFiles.MAX_RAW_BYTES)

        assertNull(SttStep.rawPayload(listOf(
            RawResponse(0, first),
            RawResponse(1, byteArrayOf(1)),
        )))
    }
}
