package app.sourcescribe.ui

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sourcescribe.R
import app.sourcescribe.ShownCodes
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageTextTest {
    @Test
    fun everyCodeTheAppCanShowHasASentenceOfItsOwn() {
        // Defect 4. A code without an entry shows only "The operation could not be completed" and its technical
        // name, ordinary outcomes such as a provider that took too long included. The inventory itself moved to
        // ShownCodes in round 25, so JobActionsTest can hold every code to a recommended action against the very
        // same list rather than a copy of it that would drift.
        val missing = ShownCodes.ALL.filter { messageSpec(it) == null }.sorted()
        assertEquals("Codes without a sentence of their own", emptyList<String>(), missing)
    }

    @Test
    fun theDurationLimitSentenceTakesItsNumberFromTheApp() {
        // Defect 23. Both languages wrote "600" into the sentence, a number of their own beside JobLimits, so a changed
        // limit would be enforced while the sentence still promised the old one. The sentence has to take the figure
        // as an argument; 7 is a number the app never uses, so a sentence that states its own figure fails here.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (tag in listOf("de", "en")) {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val sentence = context.createConfigurationContext(configuration).resources
                .getQuantityString(R.plurals.invalid_duration, 7, 7)
            assertTrue("$tag: $sentence", " 7 " in sentence)
        }
    }

}
