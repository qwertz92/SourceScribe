package app.sourcescribe.core

/**
 * One rule about a keyterms prompt, in the one place every reader of it can reach.
 *
 * Both provider paths refuse a list that holds a blank entry, and they refuse the whole request rather
 * than the entry. Each used to say so in its own words — `AssemblyAiAdapter.validateConfig` trimmed every
 * term and called `invalidInput()` on the first empty one, `SyncProviderSupport.validateOptions` asked
 * `any { it.isBlank() }` — and both now ask [refused]. So a list with one blank entry among real ones is
 * not a smaller prompt: it is a job that cannot start.
 *
 * Round 14 wrote `any { it.isNotBlank() }` into the cost estimate and closed only half the gap: for
 * `["real term", ""]` that predicate is true, the estimate added the surcharge, and the submission would
 * still have been refused. Everything that decides what a term list means reads the rule from here
 * instead of restating it: both provider paths, the check `SttStep.validate` makes before anything is
 * sent, the cost formula in `SttStep`, the preview's error line and price in `MainViewModel`, and the two
 * places that leave blank entries out through [withoutBlanks]: the field over the terms as they are typed,
 * and `MainViewModel.configurationForStart` for a list stored before that field did. A reader added later
 * belongs on that list.
 */
object ContextTerms {
    /** True when the providers would refuse this list outright, which is on any blank entry. */
    fun refused(terms: List<String>): Boolean = terms.any { it.isBlank() }

    /** True when this list asks for a prompt a provider would accept, and so may carry a surcharge. */
    fun charged(terms: List<String>): Boolean = terms.isNotEmpty() && !refused(terms)

    /** The same terms without their blank entries, which ask for nothing and which [refused] turns down. */
    fun withoutBlanks(terms: List<String>): List<String> = terms.filterNot { it.isBlank() }
}
