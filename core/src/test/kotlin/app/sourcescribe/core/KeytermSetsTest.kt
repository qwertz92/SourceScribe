package app.sourcescribe.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Item 13 of round 25. The keyterms of a job are typed into a field and lost with the job; the same dozen
 * words get retyped for the next video on the same topic. A named set keeps them.
 *
 * The rules live here rather than in the screen because the same list has to survive `SettingsStore`'s
 * validation on the way to disk and come back from a stored file that another version wrote.
 */
class KeytermSetsTest {
    @Test
    fun aSavedSetHoldsExactlyTheTermsAProviderWouldAccept() {
        // `ContextTerms.refused` turns down a whole list over one blank entry, so a set that stored one would
        // be a saved job refusal. The blank entries go; what asks for something stays, in the typed order.
        val saved = KeytermSets.saved(emptyMap(), "technical", listOf("Kubernetes", "  ", "etcd", ""))
        assertEquals(mapOf("technical" to listOf("Kubernetes", "etcd")), saved)
        assertEquals(false, ContextTerms.refused(requireNotNull(saved).getValue("technical")))

        // A list that holds nothing but blanks asks for nothing at all and is not a set.
        assertNull(KeytermSets.saved(emptyMap(), "empty", listOf("", "   ")))
        assertNull(KeytermSets.saved(emptyMap(), "empty", emptyList()))
    }

    @Test
    fun aNameIsWhatARelaxedReaderCanPickOutOfAList() {
        val terms = listOf("Kubernetes")
        assertNull("blank", KeytermSets.saved(emptyMap(), "   ", terms))
        assertNull("too long", KeytermSets.saved(emptyMap(), "n".repeat(KeytermSets.MAX_NAME_LENGTH + 1), terms))
        assertNull("control character", KeytermSets.saved(emptyMap(), "tech\nnical", terms))
        // The surrounding spaces of a dictated name are not part of it, and the name is stored trimmed.
        assertEquals(mapOf("technical" to terms), KeytermSets.saved(emptyMap(), "  technical  ", terms))
        assertEquals(KeytermSets.MAX_NAME_LENGTH, KeytermSets.saved(emptyMap(),
            "n".repeat(KeytermSets.MAX_NAME_LENGTH), terms)?.keys?.single()?.length)
    }

    @Test
    fun savingUnderAnExistingNameReplacesItAndNeitherGrowsTheList() {
        val first = requireNotNull(KeytermSets.saved(emptyMap(), "technical", listOf("etcd")))
        val second = requireNotNull(KeytermSets.saved(first, "technical", listOf("Kubernetes", "etcd")))
        assertEquals(setOf("technical"), second.keys)
        assertEquals(listOf("Kubernetes", "etcd"), second.getValue("technical"))

        // The ceiling stops a new name, never a name that is already there: overwriting the set a reader
        // just loaded must not fail because the list happens to be full.
        val full = (1..KeytermSets.MAX_SETS).associate { "set $it" to listOf("term $it") }
        assertEquals(KeytermSets.MAX_SETS, full.size)
        assertNull(KeytermSets.saved(full, "one more", listOf("Kubernetes")))
        assertEquals(listOf("Kubernetes"),
            KeytermSets.saved(full, "set 1", listOf("Kubernetes"))?.getValue("set 1"))
    }

    @Test
    fun aTermListBeyondWhatTheProvidersTakeIsNotStored() {
        // The same bounds `SettingsStore.validateConfig` already holds a job's own terms to, so a stored set
        // can never be loaded into a draft the settings file would then refuse to keep.
        assertNull(KeytermSets.saved(emptyMap(), "many", List(KeytermSets.MAX_TERMS + 1) { "term $it" }))
        assertEquals(KeytermSets.MAX_TERMS,
            KeytermSets.saved(emptyMap(), "many", List(KeytermSets.MAX_TERMS) { "term $it" })?.getValue("many")?.size)
        assertNull(KeytermSets.saved(emptyMap(), "long", listOf("t".repeat(KeytermSets.MAX_TERM_LENGTH + 1))))
        assertNull(KeytermSets.saved(emptyMap(), "control", listOf("Kuber${Char(7)}netes")))
    }

    @Test
    fun theNamesAreOfferedInAnOrderThatDoesNotDependOnWhenTheyWereSaved() {
        val sets = mapOf("zeta" to listOf("z"), "alpha" to listOf("a"), "Beta" to listOf("b"))
        assertEquals(listOf("alpha", "Beta", "zeta"), KeytermSets.names(sets))
    }
}
