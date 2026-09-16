package app.sourcescribe.core

/**
 * Keyterm lists a reader saved under a name, so the terms of one topic are typed once rather than once per
 * video.
 *
 * The bounds are the ones a job's own `contextTerms` already live under in `SettingsStore.validateConfig`,
 * because a saved set is loaded straight into a draft: a set the settings file would refuse to store could be
 * loaded into a configuration the same file then refuses to save. [saved] answers null for anything it will
 * not store, so the screen says the set was not saved instead of showing one that is not there.
 */
object KeytermSets {
    const val MAX_SETS = 30
    const val MAX_NAME_LENGTH = 80
    const val MAX_TERMS = 1000
    const val MAX_TERM_LENGTH = 500

    /**
     * [sets] with [terms] stored under [name], or null when this app will not store it. An existing name is
     * replaced, which is also why [MAX_SETS] only stops a name that is not there yet: overwriting the set a
     * reader has just loaded must not fail because the list happens to be full.
     */
    fun saved(
        sets: Map<String, List<String>>,
        name: String,
        terms: List<String>,
    ): Map<String, List<String>>? {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH || trimmed.any(Char::isISOControl)) return null
        // A blank entry makes every provider refuse the whole request (`ContextTerms`), so a set may not hold
        // one; a list that is nothing but blanks asks for nothing and is not a set at all.
        val kept = ContextTerms.withoutBlanks(terms)
        if (kept.isEmpty() || kept.size > MAX_TERMS) return null
        if (kept.any { it.length > MAX_TERM_LENGTH || it.any(Char::isISOControl) }) return null
        if (trimmed !in sets && sets.size >= MAX_SETS) return null
        return sets + (trimmed to kept)
    }

    /** The names to offer, in an order that does not depend on when each set was saved. */
    fun names(sets: Map<String, List<String>>): List<String> =
        sets.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
}
