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
        val found = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            statedNumberNames(file.readText()).forEach { found += "${file.name.removeSuffix(".kt")}.$it" }
        }
        // An entry is file plus name, with no enclosing object in it, so two constants sharing a name in
        // one file would arrive as one entry and the list below would speak for whichever came first. A
        // set swallows that silently, which is why the duplicates are asked for by themselves.
        assertEquals(
            "Two constants in one file of this module share a name, so a single line here would have to " +
                "stand for both of them. Rename one, or the check below cannot mean what it says.",
            emptyList<String>(),
            found.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted(),
        )
        assertEquals(
            "The numbers this module declares and the numbers this file names have come apart. " +
                "A new constant needs a line here saying where its value comes from; a removed one " +
                "needs its line taken out. Source root read: $root",
            NAMED_ABOVE.toSortedSet(),
            found.toSortedSet(),
        )
    }

    /**
     * What the check above is able to see, asserted on text rather than on the tree.
     *
     * The tree contains none of these forms today, so reading it proves nothing about them: the check
     * agreed with its list both before and after round 13 changed what it can read. Each line here is a
     * declaration the line-by-line scanner walked past, and none of them is unusual Kotlin — a value moved
     * to the next line to keep a line short, and an annotation where a lint rule is suppressed.
     */
    @Test fun theScannerSeesTheDeclarationsThatUsedToSlipPastIt() {
        // Round 13 closed these three.
        assertEquals(listOf("WRAPPED"), statedNumberNames("    const val WRAPPED =\n        8_388_608L\n"))
        assertEquals(
            listOf("ANNOTATED"),
            statedNumberNames("    @Suppress(\"MagicNumber\") const val ANNOTATED = 224\n"),
        )
        assertEquals(listOf("TYPED"), statedNumberNames("    internal const val TYPED: Long = 10\n"))

        // Round 14 closed these four. The break can come before the `=` as well as after it; an
        // annotation's arguments can nest deeper than a pattern can count; a declaration can follow a
        // semicolon; and a block comment after the value used to put a quotation mark into it.
        assertEquals(listOf("EARLY"), statedNumberNames("    const val EARLY\n        = 5\n"))
        assertEquals(
            listOf("NESTED"),
            statedNumberNames("    @Deprecated(\"x\", ReplaceWith(\"y()\")) const val NESTED = 5\n"),
        )
        assertEquals(listOf("AFTER"), statedNumberNames("    val other = 1; const val AFTER = 5\n"))
        assertEquals(listOf("COUNT"), statedNumberNames("    const val COUNT = 5 /* \"units\" */\n"))

        // Two annotations in a row, which round 13's pattern already read correctly — its prefix group
        // repeated, so it never needed to count anything. Round 14's version of the comment above put this
        // case among the five forms it closed; running both patterns over every case in round 15 showed it
        // was never one of them. It stays here as the guard it actually is.
        assertEquals(listOf("TWO"), statedNumberNames("    @JvmStatic @Suppress(\"F\") const val TWO = 5\n"))

        // Two of one name in one file are both reported, which is what lets the check above see the
        // collision instead of comparing its list against whichever of them came first.
        assertEquals(listOf("SAME", "SAME"), statedNumberNames("const val SAME = 1\nconst val SAME = 2\n"))

        // And what it must keep passing over. The last two are the other direction: a declaration that
        // only looks like one. Counting those would fail this test for a constant that does not exist.
        assertEquals(emptyList<String>(), statedNumberNames("    private const val HIDDEN = 5\n"))
        assertEquals(emptyList<String>(), statedNumberNames("val a = 1; private const val Q = 5\n"))
        assertEquals(emptyList<String>(), statedNumberNames("    const val SOURCE = \"whisper-1\"\n"))
        assertEquals(emptyList<String>(), statedNumberNames("    // const val MENTIONED = 5\n"))
        assertEquals(emptyList<String>(), statedNumberNames("/*\nconst val IN_A_BLOCK = 5\n*/\n"))
        assertEquals(emptyList<String>(), statedNumberNames("/**\n * const val IN_KDOC = 5\n */\n"))
        assertEquals(emptyList<String>(), statedNumberNames("    val text = \"const val QUOTED = 5\"\n"))

        // A real declaration after a block comment is still found, so removing them loses nothing.
        assertEquals(listOf("AFTER_BLOCK"), statedNumberNames("/*\n c\n*/\nconst val AFTER_BLOCK = 7\n"))

        // Round 15 closed four more. The first two were loud: a phantom declaration puts an entry into the
        // list that nothing names, and the test above fails for a constant that does not exist. The last two
        // were silent, which is worse: a declaration the scanner cannot see is missing from both sides of
        // that comparison once nobody has listed it, so nothing fails and the number is never stated.
        assertEquals(
            emptyList<String>(),
            statedNumberNames("val t = \"\"\"\n const val IN_A_RAW_STRING = 1\n\"\"\"\n"),
        )
        assertEquals(
            emptyList<String>(),
            statedNumberNames("/* a /* b */ still commented, const val IN_A_NESTED_BLOCK = 1 */\n"),
        )
        assertEquals(
            listOf("PAST_A_SLASH_STAR"),
            statedNumberNames(
                "val a = \"opens /* c\"\nconst val PAST_A_SLASH_STAR = 42\nval b = \"closes */ c\"\n"
            ),
        )
        assertEquals(listOf("ONE", "OTHER"), statedNumberNames("const val ONE = 1; const val OTHER = 2\n"))

        // And three things the tokeniser has to read past, the first two because this module contains
        // them: a character literal holding a quotation mark (`BoundedJson`), one holding a semicolon
        // (`CaptionParser`), and an escaped quotation mark inside a string. Each case puts a declaration on
        // the line after it and requires that declaration to be found. The version before round 15 read
        // all three correctly; they are here so a later change to the tokeniser cannot stop doing so unseen.
        assertEquals(
            listOf("PAST_A_CHAR_QUOTE"),
            statedNumberNames("when (c) {\n    '\"' -> quoted = true\n}\nconst val PAST_A_CHAR_QUOTE = 3\n"),
        )
        assertEquals(
            listOf("PAST_A_CHAR_SEMICOLON"),
            statedNumberNames("val s = ';'\nconst val PAST_A_CHAR_SEMICOLON = 4\n"),
        )
        assertEquals(
            listOf("PAST_AN_ESCAPED_QUOTE"),
            statedNumberNames("val a = \"one \\\" two\"\nconst val PAST_AN_ESCAPED_QUOTE = 6\n"),
        )
    }

    /**
     * The names of the non-private `const val`s in one file whose value is a number.
     *
     * The file is tokenised rather than matched line by line, because a declaration can be written over
     * two lines, can have anything in front of it, and can sit inside a comment or a literal — and none of
     * that is unusual Kotlin. Round 13 closed three forms the line-by-line version walked past: a value on
     * the following line, an annotation in front, two constants of one name. Round 14 closed four more and
     * one in the other direction: a break before the `=` rather than after it, an annotation whose
     * arguments nest, a declaration after a semicolon, a value followed by a block comment containing a
     * quotation mark, and a declaration written inside a block comment, which was counted as real and
     * would have failed this test for a constant that does not exist. Round 15 replaced the two patterns
     * that did that work with `codeOnly` and closed four more, two of them silent. Every form was
     * reproduced in a model before it was fixed, and none of them occurs in the tree today.
     *
     * What `codeOnly` does not model is written down rather than left to be found: a string template
     * whose expression holds a string of its own, as in `"${x ?: "unknown"}"`. The inner quotation mark
     * ends the outer literal early and the inner literal is read as code, so a comment opener inside it
     * would start a comment that is not there. No line of this module that holds a template followed by a
     * quotation mark has a comment opener in it, and the scanner reads the same 33 constants over the real
     * tree as the version before it — measured, in a model, not assumed. DEFECTS 34 carries it.
     *
     * "A number" is decided by what is written after the `=`, not by a declared type: it must carry a digit
     * and no quotation mark. So `"whisper-1"` is passed over — a name that happens to contain a digit —
     * while `MAX_AUDIO_SECONDS / 60` is kept, because an expression over numbers still states one.
     */
    private fun statedNumberNames(source: String): List<String> =
        DECLARATION.findAll(codeOnly(source)).mapNotNull { match ->
            val value = match.groupValues[3]
            if (match.groupValues[1].split(WHITESPACE).any { it == "private" }) return@mapNotNull null
            match.groupValues[2].takeIf { value.none { char -> char == '"' } && value.any(Char::isDigit) }
        }.toList()

    /**
     * The same file with its comments and literals blanked out, so that what is left is code.
     *
     * Round 14 approximated this with one pattern for block comments and a cut at `//` per line, and
     * round 15 found three ways for that to be wrong. A `const val` inside a raw string was read as real.
     * A nested block comment — legal Kotlin, and no regular expression can count brackets — ended at the
     * inner closing delimiter, leaving the rest of the outer comment standing as code. And an opening
     * delimiter inside a string literal began a comment that ran to the next closing delimiter in some
     * later string, swallowing whatever stood between them. The first two were loud — a phantom entry
     * fails the comparison in `everyNumberThisModuleStatesHasALineInThisFile` — and the third was
     * silent: a declaration this function cannot see is missing from both sides of that comparison once
     * nobody has listed it, so nothing fails and nobody is asked to state the number. Separating code from
     * not-code is tokenising, so this tokenises instead of approximating.
     *
     * The delimiters are named rather than written here for the reason this function exists: a closing one
     * inside a KDoc ends the KDoc, and the first draft of this paragraph contained one. Everything after
     * it was compiled as code, which is how the build reported it.
     *
     * Character literals are read for a reason that has nothing to do with those three: this module holds
     * a quotation mark as a character literal in `BoundedJson` and a semicolon as one in `CaptionParser`.
     * A tokeniser that knew strings but not characters would open a string at the first and see a statement
     * boundary at the second. Over this module that costs nothing today — a port of this function reads
     * the same 33 constants without the branch — so the assertions for both do not show that the branch
     * is needed. They hold that a declaration after either is still found, however the tokeniser changes.
     *
     * A comment becomes spaces and keeps its line breaks, so a declaration wrapped across lines still
     * reads as one. A literal becomes an empty literal of its own kind, which is what lets the value test
     * keep refusing `const val SOURCE = "whisper-1"` for the quotation mark rather than by accident.
     */
    private fun codeOnly(source: String): String {
        val out = StringBuilder(source.length)
        var index = 0
        var depth = 0
        while (index < source.length) {
            val char = source[index]
            when {
                depth > 0 && source.startsWith("/*", index) -> { depth++; out.append("  "); index += 2 }
                depth > 0 && source.startsWith("*/", index) -> { depth--; out.append("  "); index += 2 }
                depth > 0 -> { out.append(if (char == '\n') '\n' else ' '); index++ }
                source.startsWith("/*", index) -> { depth = 1; out.append("  "); index += 2 }
                source.startsWith("//", index) ->
                    while (index < source.length && source[index] != '\n') index++
                source.startsWith(TRIPLE_QUOTE, index) -> {
                    val end = source.indexOf(TRIPLE_QUOTE, index + TRIPLE_QUOTE.length)
                    out.append("\"\"")
                    index = if (end < 0) source.length else end + TRIPLE_QUOTE.length
                }
                char == '"' || char == '\'' -> {
                    var scan = index + 1
                    while (scan < source.length && source[scan] != char && source[scan] != '\n') {
                        scan += if (source[scan] == '\\') 2 else 1
                    }
                    out.append(char).append(char)
                    index = minOf(scan + 1, source.length)
                }
                else -> { out.append(char); index++ }
            }
        }
        return out.toString()
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
        // A shouting `const val` and its value, wherever it stands. Group one is whatever else shares
        // the line: annotations, modifiers, or another declaration before a semicolon. It is
        // deliberately not parsed. Round 13 spelled the annotation out as a name and one optional
        // parenthesised group, and an annotation's argument list nests — @Deprecated("x",
        // ReplaceWith("y()")) is three levels deep — which no regular expression can count. Since the
        // prefix is only ever read for the word `private`, it does not need to be understood.
        //
        // Group two is the name, group three the value: whatever follows the `=` up to the end of the
        // line or the next `;`, whichever comes first. The `\s*` on both sides of the `=` spans a line
        // break, so a declaration wrapped before or after the `=` is still one match. Stopping at the
        // semicolon is what round 15 added: a greedy group three ate `; const val OTHER = 2` as part of
        // the first value, and the second declaration on that line was never found.
        private val DECLARATION = Regex(
            """(?m)(?:^|;)([^\n;]*?)\bconst[ \t]+val[ \t]+""" +
                """([A-Z][A-Z0-9_]*)\s*(?::[^=\n]+)?\s*=\s*([^\n;]+)"""
        )

        private const val TRIPLE_QUOTE = "\"\"\""

        private val WHITESPACE = Regex("""\s+""")

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
