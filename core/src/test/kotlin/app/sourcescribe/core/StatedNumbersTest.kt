package app.sourcescribe.core

import app.sourcescribe.core.providers.AssemblyAiAdapter
import app.sourcescribe.core.providers.GroqAdapter
import app.sourcescribe.core.providers.OpenAiAdapter
import app.sourcescribe.core.providers.SyncProviderSupport
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every number this module states, written out once.
 *
 * A test that builds its input from the constant it checks cannot see that constant change. `ByteArray(n + 1)`
 * is larger than `n` whatever `n` is; a record built around text as long as `n` is longer than `n` whatever
 * `n` is. Such tests are worth keeping — they check the comparison and the reason it reports — but they say
 * nothing at all about the value. This review loop found them one at a time in rounds 9, 10 and 11, and
 * round 12 then found that three separate attempts to total them up disagreed with each other — which
 * settles the question of whether finding them one at a time was the right shape better than the total
 * would have. The values live here instead, in one list, so that changing one fails in a place that reads
 * as what it is: a statement about the world, or a budget this program chose.
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
     * came from. The date is shown to the reader beside the figure; the page is not shown anywhere at all,
     * which is recorded as an open point rather than quietly tolerated here.
     *
     * All eight were re-read on 2026-09-11, and the reading of one of them was wrong. AssemblyAI's add-on
     * table carries a column per model, and its keyterms row reads "$0.05 /hr" under Universal-3.5 Pro and
     * "Included" under Universal-2. Round 12 recorded five cents an hour for both and removed the condition
     * that had been right, which made every Universal-2 estimate with a term list a third too high and
     * could refuse a job the provider would have billed within budget. Read again on 2026-09-12 from the
     * page's own markup rather than from a summary of it — a summarising fetch is what turned the word
     * "Included" into a price — and the condition is back.
     *
     * One of the three source pages was wrong in the other direction. OpenAI's pointed at its speech-to-text
     * guide, which carries no prices; it now names the pricing page. That page shows $0.0045 a minute for
     * `gpt-transcribe` in its table, but the row for the second figure is labelled "Whisper", sits behind
     * the table's show-more control, and the string `whisper-1` does not appear on the page at all. The
     * figure below is right; a reader checking it has to make that last step themselves.
     */
    @Test fun theMoneyFiguresBehindEveryEstimateTheReaderIsShown() {
        // All of these are micro-USD per hour, so 210_000 is twenty-one cents an hour.
        // AssemblyAI, read 2026-09-07 from its pricing page.
        assertEquals(210_000L, AssemblyAiAdapter.PRICE_U35_MICRO_USD_PER_HOUR)
        assertEquals(150_000L, AssemblyAiAdapter.PRICE_U2_MICRO_USD_PER_HOUR)
        assertEquals(20_000L, AssemblyAiAdapter.SPEAKER_LABELS_MICRO_USD_PER_HOUR)
        // Five cents an hour on Universal-3.5 Pro only: the same row of the same table reads "Included"
        // under Universal-2, where the prompt is part of the fifteen cents an hour above. The name says
        // which model carries it because round 12 dropped that condition and charged both.
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

        // OpenAI, same date, from its pricing page: $0.0045 a minute for gpt-transcribe and $0.006 for
        // whisper-1. The page named here must be one that shows them, which is what round 12 corrected.
        assertEquals(270_000L, OpenAiAdapter.PRICE_GPT_TRANSCRIBE_MICRO_USD_PER_HOUR)
        assertEquals(360_000L, OpenAiAdapter.PRICE_WHISPER_MICRO_USD_PER_HOUR)
        assertEquals("2026-09-07", OpenAiAdapter.PRICE_AS_OF)
        assertEquals("https://developers.openai.com/api/docs/pricing", OpenAiAdapter.PRICING_SOURCE)
    }

    /**
     * What each provider allows, which this app refuses to exceed rather than letting a paid request fail.
     *
     * Every one of these belongs to somebody else and can move without this repository hearing about it, so
     * a line here is a claim about a provider and should be re-read against its documentation now and then.
     * Every line below was re-read on 2026-09-11 and each says what that reading found, because a group
     * whose whole point is "somebody else's number" earns nothing from a date that covers only some of it.
     *
     * Two numbers that used to stand here have moved to the group below, where the reader can see that no
     * provider page confirms them as written: Groq's upload ceiling, which is the smaller of its two tiers
     * and chosen by this program, and the prompt budget, which counts bytes where the provider counts
     * tokens. Both are still correct, and both were being vouched for by a source that does not say them.
     */
    @Test fun whatTheProvidersThemselvesAllow() {
        // AssemblyAI: 2.2 GB, ten hours, 160 ms. All three matched on re-reading.
        assertEquals(2_200_000_000L, AssemblyAiAdapter.MAX_UPLOAD_BYTES)
        assertEquals(36_000_000L, AssemblyAiAdapter.MAX_DURATION_MS)
        assertEquals(160L, AssemblyAiAdapter.MIN_DURATION_MS)

        // Groq: a documented minimum file length of one hundredth of a second. Matched.
        assertEquals(10L, GroqAdapter.MIN_DURATION_MS)

        // OpenAI: 25 MB per upload, matched. And the length above which the caller is told to ask for a
        // chunking strategy — a threshold, not a chunk size, which is what the old wording here had it as.
        assertEquals(25_000_000L, OpenAiAdapter.MAX_UPLOAD_BYTES)
        assertEquals(30_000L, OpenAiAdapter.DIARIZATION_AUTO_CHUNKING_MS)
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
     *
     * The last three came from the group above in round 12. A number can be borrowed from a provider and
     * still be this program's own decision: the smaller of two tiers, or a limit re-expressed in a unit the
     * provider never used. Filed as somebody else's, each would have sent a later reader to a page that
     * does not say it.
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

        // How much of a digest goes into an export file name. Six bytes rather than four is a decision the
        // comment beside it argues out: it puts an even chance of two names colliding near twenty million
        // instead of near seventy-seven thousand. Three tests read the length back from this constant, so
        // until round 12 the argument fixed nothing at all.
        assertEquals(6, TranscriptExporter.SHORT_ID_BYTES)

        // What one upload may weigh at Groq. It documents 25 MB on the free tier and 100 MB on the
        // developer tier, and this app cannot tell which tier a key holds, so it holds everyone to the
        // smaller figure. OpenAI's own 25 MB is a provider number and stays in the group above.
        //
        // A fourth constant of this value stood in `SyncProviderSupport` and was read by nothing at all:
        // each adapter passes its own into `capabilities`, and validation reads it from there. The scan
        // below is what turned it up, by asking for a line about a number nobody could say anything about.
        assertEquals(25_000_000L, GroqAdapter.MAX_UPLOAD_BYTES)

        // Bytes standing in for OpenAI's 224 *tokens*. Safe because a byte-level tokeniser cannot make more
        // tokens than bytes, and therefore stricter than the provider — but no page states this number in
        // this unit, so nothing outside this program can confirm it.
        assertEquals(224, SyncProviderSupport.MAX_PROMPT_BYTES)

        // How long the warning list may grow, and how many kinds of warning it keeps room for past that.
        assertEquals(64, Warnings.LIMIT)
        assertEquals(160, Warnings.KIND_LIMIT)
    }

    /**
     * Whether the three lists above are still the whole of it.
     *
     * Everything before this point catches a number that changed. None of it catches a number that was
     * never entered, and round 11 left that as the open question: nothing stopped the next person from
     * adding a constant and not adding a line. Memory is what was holding it together, and memory is what
     * this loop keeps finding at the bottom of its defects.
     *
     * So the module's own source is read and every number it declares outside its private scope is
     * collected. A name here that the source no longer has, or a number in the source that no name here
     * covers, fails — and the failure names both sides, so the fix is to decide which of the two is wrong
     * rather than to guess. The entry is file plus name rather than object plus name, which also makes a
     * constant moving between files visible; `SyncProviderSupport` lives in `SyncTranscriptParser.kt`, and
     * that is the only place where the two differ today.
     *
     * A string is not a number and is not collected: the error codes, model identifiers and endpoints in
     * this module are names, and pinning them here would say nothing about what they name. The pricing
     * dates and source pages are strings too, and they are pinned above anyway, because a figure whose date
     * can drift silently is a figure without a date.
     */
    @Test fun everyNumberThisModuleStatesHasALineInThisFile() {
        val root = sourceRoot()
        val found = sortedSetOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEach { line ->
                statedNumberName(line)?.let { found += "${file.name.removeSuffix(".kt")}.$it" }
            }
        }
        assertEquals(
            "The numbers this module declares and the numbers this file names have come apart. " +
                "A new constant needs a line here saying where its value comes from; a removed one " +
                "needs its line taken out. Source root read: $root",
            NAMED_ABOVE.toSortedSet(),
            found,
        )
    }

    /**
     * The name of a non-private `const val` whose value is a number, or `null` for every other line.
     *
     * "A number" is decided by what is written after the `=`, not by a declared type: it must carry a digit
     * and no quotation mark. So `"whisper-1"` is passed over — a name that happens to contain a digit —
     * while `MAX_AUDIO_SECONDS / 60` is kept, because an expression over numbers still states one.
     */
    private fun statedNumberName(line: String): String? {
        val code = line.substringBefore("//").trim()
        if (!code.startsWith("const val ") && !code.startsWith("internal const val ")) return null
        val name = code.substringAfter("const val ").substringBefore("=").substringBefore(":").trim()
        if (name.isEmpty() || !name.all { it.isUpperCase() || it.isDigit() || it == '_' }) return null
        val value = code.substringAfter("=", "").trim()
        return name.takeIf { value.none { char -> char == '"' } && value.any(Char::isDigit) }
    }

    /**
     * Where this module's source is, found by walking up from wherever the test happens to run.
     *
     * A test that cannot find what it is meant to read must fail rather than find nothing and report
     * agreement, which is the same mistake as a gate that prints OK over a skipped assumption.
     */
    private fun sourceRoot(): File {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "core/src/main/kotlin")
            if (candidate.isDirectory) return candidate
            val inside = File(directory, "src/main/kotlin")
            if (inside.isDirectory && directory.name == "core") return inside
            directory = directory.parentFile
        }
        throw AssertionError("core/src/main/kotlin was not found from ${File("").absolutePath}")
    }

    private companion object {
        /**
         * Every number named above, as the file it is declared in and the name it is declared under.
         *
         * Adding a line here is not the point; saying where the value comes from, next to the assertion
         * above, is. A name added here without an assertion passes this test and states nothing.
         */
        val NAMED_ABOVE = listOf(
            "ArtifactFiles.MAX_CANONICAL_BYTES", "ArtifactFiles.MAX_RAW_BYTES",
            "AssemblyAiAdapter.MAX_UPLOAD_BYTES", "AssemblyAiAdapter.MAX_DURATION_MS",
            "AssemblyAiAdapter.MIN_DURATION_MS", "AssemblyAiAdapter.PRICE_U35_MICRO_USD_PER_HOUR",
            "AssemblyAiAdapter.PRICE_U2_MICRO_USD_PER_HOUR",
            "AssemblyAiAdapter.SPEAKER_LABELS_MICRO_USD_PER_HOUR",
            "AssemblyAiAdapter.KEYTERMS_U35_MICRO_USD_PER_HOUR",
            "CaptionParser.MAX_INPUT_CHARS", "CaptionParser.MAX_SEGMENTS",
            "EngineVerifier.MAX_CHECKSUM_BYTES", "EngineVerifier.MAX_SIGNATURE_BYTES",
            "EngineVerifier.MAX_ARTIFACT_BYTES", "EngineVerifier.MAX_ARCHIVE_ENTRIES",
            "EngineVerifier.MAX_ENTRY_BYTES", "EngineVerifier.MAX_TOTAL_UNPACKED_BYTES",
            "ExtractorMetadata.MAX_URL_LENGTH",
            "GroqAdapter.MAX_UPLOAD_BYTES", "GroqAdapter.PRICE_V3_MICRO_USD_PER_HOUR",
            "GroqAdapter.PRICE_TURBO_MICRO_USD_PER_HOUR", "GroqAdapter.MINIMUM_BILLED_SECONDS",
            "GroqAdapter.MIN_DURATION_MS",
            "JobLimits.MAX_AUDIO_SECONDS", "JobLimits.MAX_AUDIO_MINUTES",
            "OpenAiAdapter.MAX_UPLOAD_BYTES", "OpenAiAdapter.PRICE_GPT_TRANSCRIBE_MICRO_USD_PER_HOUR",
            "OpenAiAdapter.PRICE_WHISPER_MICRO_USD_PER_HOUR", "OpenAiAdapter.DIARIZATION_AUTO_CHUNKING_MS",
            "SyncTranscriptParser.MAX_PROMPT_BYTES",
            "TranscriptExporter.SHORT_ID_BYTES",
            "Warnings.LIMIT", "Warnings.KIND_LIMIT",
        )
    }
}
