package it.citiverse.app

import it.citiverse.app.push.NotifText
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifTextTest {
    private val strings = mapOf(
        "notifications:user-posted-to" to "<strong>%1</strong> ha risposto a: <strong>%2</strong>",
        "notifications:upvoted-your-post-in" to "<strong>%1</strong> ha votato positivamente il tuo post in <strong>%2</strong>",
    )

    @Test
    fun translatesWithArgs() {
        assertEquals(
            "Mario ha risposto a: Ciao a tutti",
            NotifText.translate("[[notifications:user-posted-to, Mario, Ciao a tutti]]", strings),
        )
    }

    @Test
    fun keepsEscapedCommas() {
        assertEquals(
            "Anna ha votato positivamente il tuo post in Pane, burro e marmellata",
            NotifText.translate("[[notifications:upvoted-your-post-in, Anna, Pane&#44; burro e marmellata]]", strings),
        )
    }

    @Test
    fun unknownKeyFallsBackToArgs() {
        assertEquals("Luca · Titolo", NotifText.translate("[[notifications:qualcosa-di-nuovo, Luca, Titolo]]", strings))
    }

    @Test
    fun plainTextUntouched() {
        assertEquals("Testo & basta", NotifText.translate("Testo &amp; basta", strings))
    }
}
