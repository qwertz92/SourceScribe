package app.sourcescribe.core

import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.core.providers.OpenAiAdapter
import app.sourcescribe.core.providers.SyncProviderSupport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every number this module states, written out once.
 *
 * A test that builds its input from the constant it checks cannot see that constant change. `ByteArray(n + 1)`
 * is larger than `n` whatever `n` is; a record built around text as long as `n` is longer than `n` whatever
 * `n` is. Such tests are worth keeping — they check the comparison and the reason it reports — but they say
 * nothing at all about the value, and this review loop found twelve of them one at a time — one in round 9,
 * four in round 10, seven in round 11 — before noticing that one at a time is the wrong shape. The values live here instead, in one list, so that changing
 * one fails in a place that reads as what it is: a statement about the world, or a budget this program chose.
 *
 * Nothing in this file exercises behaviour. Each line asks one question of its number — is this still the
 * number we mean? — and the answer is only worth something because the line beside it says where the number
 * comes from. Where nothing outside this program fixes it, the line says that too, because "no second source"
 * is an honest answer and an unstated guess is not.
 */
class StatedNumbersTest {
    /**
     * What the reader is told a run will cost, before agreeing to pay for it.
     *
     * These eight figures reach the cost estimate directly. Three of them — both Groq prices and OpenAI's
     * gpt-transcribe — were already written out in `SyncProviderTest`. The other five were not: both
     * AssemblyAI model prices, its two surcharges and OpenAI's whisper-1 price were mentioned by tests only
     * through the constant that declares them, so a tenfold typo would have moved the estimate and left
     * every test green. Neither was the ten-second minimum Groq bills however short the audio is. Prices
     * also go stale by themselves, which is why each provider carries the date it was read and the page it
     * came from; those are shown to the reader beside the figure, and only AssemblyAI's date was pinned.
     */
    @Test fun theMoneyFiguresBehindEveryEstimateTheReaderIsShown() {
        // All of these are micro-USD per hour, so 210_000 is twenty-one cents an hour.
        // AssemblyAI, read 2026-09-07 from its pricing page.
        assertEquals(210_000L, AssemblyAiAdapter.PRICE_U35_MICRO_USD_PER_HOUR)
        assertEquals(150_000L, AssemblyAiAdapter.PRICE_U2_MICRO_USD_PER_HOUR)
        assertEquals(20_000L, AssemblyAiAdapter.SPEAKER_LABELS_MICRO_USD_PER_HOUR)
        assertEquals(50_000L, AssemblyAiAdapter.KEYTERMS_U35_MICRO_USD_PER_HOUR)
        assertEquals("2026-09-07", AssemblyAiAdapter.PRICING_DATE)
        assertEquals("https://www.assemblyai.com/pricing/", AssemblyAiAdapter.PRICING_SOURCE)

        // Groq, same date, from its speech-to-text documentation.
        assertEquals(111_000L, GroqAdapter.PRICE_V3_MICRO_USD_PER_HOUR)
        assertEquals(40_000L, GroqAdapter.PRICE_TURBO_MICRO_USD_PER_HOUR)
        // Groq bills at least ten seconds however short the audio is, so the estimate must round up to it.
        assertEquals(10, GroqAdapter.MINIMUM_BILLED_SECONDS)
        assertEquals("2026-09-07", GroqAdapter.PRICE_AS_OF)
        assertEquals("https://console.groq.com/docs/speech-to-text", GroqAdapter.PRICING_SOURCE)

        // OpenAI, same date, from its speech-to-text guide.
        assertEquals(270_000L, OpenAiAdapter.PRICE_GPT_TRANSCRIBE_MICRO_USD_PER_HOUR)
        assertEquals(360_000L, OpenAiAdapter.PRICE_WHISPER_MICRO_USD_PER_HOUR)
        assertEquals("2026-09-07", OpenAiAdapter.PRICE_AS_OF)
        assertEquals("https://developers.openai.com/api/docs/guides/speech-to-text", OpenAiAdapter.PRICING_SOURCE)
    }

    /**
     * What each provider allows, which this app refuses to exceed rather than letting a paid request fail.
     *
     * Every one of these belongs to somebody else and can move without this repository hearing about it, so a
     * line here is a claim about a provider and should be re-read against its documentation now and then. Two
     * of them were re-read on 2026-09-11 and matched; the rest carry the date they were first written down.
     */
    @Test fun whatTheProvidersThemselvesAllow() {
        // Confirmed against AssemblyAI's own documentation on 2026-09-11: 2.2 GB, ten hours, 160 ms.
        assertEquals(2_200_000_000L, AssemblyAiAdapter.MAX_UPLOAD_BYTES)
        assertEquals(36_000_000L, AssemblyAiAdapter.MAX_DURATION_MS)
        assertEquals(160L, AssemblyAiAdapter.MIN_DURATION_MS)

        // Groq: 25 MB per upload, and a documented minimum file length of one hundredth of a second.
        assertEquals(25_000_000L, GroqAdapter.MAX_UPLOAD_BYTES)
        assertEquals(10L, GroqAdapter.MIN_DURATION_MS)

        // OpenAI: 25 MB per upload; diarization is chunked in thirty-second pieces by the provider itself.
        assertEquals(25_000_000L, OpenAiAdapter.MAX_UPLOAD_BYTES)
        assertEquals(30_000L, OpenAiAdapter.DIARIZATION_AUTO_CHUNKING_MS)

        // Shared by the two direct-upload providers, plus OpenAI's limit on the prompt that primes a request.
        assertEquals(25_000_000L, SyncProviderSupport.MAX_UPLOAD_BYTES)
        assertEquals(224, SyncProviderSupport.MAX_PROMPT_BYTES)
    }

    /**
     * What this program promises its own reader, and where the same number is written a second time.
     *
     * The duration ceiling is the one number here that a person is shown: two translated strings name six
     * hundred minutes without reading it from anywhere. Until those strings are generated from this constant,
     * the only thing joining them is this line and the comment beside it.
     */
    @Test fun whatThisProgramPromisesTheReader() {
        assertEquals(36_000L, JobLimits.MAX_AUDIO_SECONDS)
        // Named as 600 minutes by `invalid_duration` in both `values/strings.xml` and `values-en/strings.xml`.
        assertEquals(600L, JobLimits.MAX_AUDIO_MINUTES)
    }

    /**
     * Budgets this program chose for itself, with nothing outside it to check them against.
     *
     * That is not a reason to leave them unstated. It is the reason to state them here: a wrong internal
     * budget is invisible by construction — no provider page contradicts it, no screen shows it — so the
     * only way a change to one of these numbers can be noticed at all is a line that says what it was.
     */
    @Test fun budgetsWithNoSecondSourceAnywhere() {
        // A stored transcript and the raw provider answer kept beside it, so one job cannot fill the disk.
        assertEquals(32 * 1024 * 1024, ArtifactFiles.MAX_CANONICAL_BYTES)
        assertEquals(16 * 1024 * 1024, ArtifactFiles.MAX_RAW_BYTES)

        // What a caption file may carry before it is refused unparsed, and how many cues may come out of one.
        assertEquals(8_000_000, CaptionParser.MAX_INPUT_CHARS)
        assertEquals(100_000, CaptionParser.MAX_SEGMENTS)

        // The signed-update path. Everything here bounds an archive an attacker would like to make enormous,
        // so these six are the numbers in this file that a wrong value would matter most for.
        assertEquals(64 * 1024, EngineVerifier.MAX_CHECKSUM_BYTES)
        assertEquals(32 * 1024, EngineVerifier.MAX_SIGNATURE_BYTES)
        assertEquals(16 * 1024 * 1024, EngineVerifier.MAX_ARTIFACT_BYTES)
        assertEquals(10_000, EngineVerifier.MAX_ARCHIVE_ENTRIES)
        assertEquals(8 * 1024 * 1024, EngineVerifier.MAX_ENTRY_BYTES)
        assertEquals(64 * 1024 * 1024, EngineVerifier.MAX_TOTAL_UNPACKED_BYTES)

        // An address is kept whole or not at all, and this is where "not at all" begins.
        assertEquals(32768, ExtractorMetadata.MAX_URL_LENGTH)

        // How long the warning list may grow, and how many kinds of warning it keeps room for past that.
        assertEquals(64, Warnings.LIMIT)
        assertEquals(160, Warnings.KIND_LIMIT)
    }
}
