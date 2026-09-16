package app.sourcescribe.core.providers

import java.util.Locale

/**
 * Turns what a provider wrote in a `language` field into the ISO-639-1 code the app stores.
 *
 * Groq and OpenAI answer an OpenAI-shaped `verbose_json` body, and Groq writes the language out as an English
 * word: the first real run of `LiveGroqTranscriptionTest`, 16 September 2026, returned `"language":"english"`
 * for a transcription that otherwise succeeded, and the parser kept nothing, because it accepted a two-letter
 * code and nothing else. A word is a name of the same language, not a second fact, so it is read as the code
 * it names.
 *
 * The table is the JVM's own: every code [Locale.getISOLanguages] lists, under the English display name the
 * platform gives it. This program therefore states no language list of its own and cannot drift from the one
 * the platform ships. Nothing is guessed: a value neither two letters nor a name in that list answers null,
 * and the caller drops it exactly as it did before.
 *
 * The raw value stays what the provider sent - the stored raw response keeps it byte for byte where the job
 * asked for raw data, and this function reads it without rewriting anything.
 */
internal object LanguageNames {
    /**
     * The code [value] names, or null when this table does not name it.
     *
     * A two-letter value is taken as a code and only lowercased, which is what this program did before and what
     * every caller of a language field already relies on. Everything else is looked up whole: "spoken english"
     * is not English, because a provider that writes a sentence there has not named a language.
     */
    fun codeFor(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length == 2 && trimmed.all { it in 'a'..'z' || it in 'A'..'Z' }) {
            return trimmed.lowercase(Locale.ROOT)
        }
        return byEnglishName[trimmed.lowercase(Locale.ROOT)]
    }

    /**
     * English display name to code, built once. The codes are walked in sorted order and the first one to claim
     * a name keeps it, so a name two codes share - Java lists the superseded `iw`, `in` and `ji` beside `he`,
     * `id` and `yi` - always answers the same code on the same platform instead of whichever came last.
     */
    private val byEnglishName: Map<String, String> = buildMap {
        for (code in Locale.getISOLanguages().sorted()) {
            val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
                .trim()
                .lowercase(Locale.ENGLISH)
            // A platform without a name for a code answers the code itself; that is no name and would shadow it.
            if (name.isEmpty() || name == code) continue
            putIfAbsent(name, code)
        }
    }
}
