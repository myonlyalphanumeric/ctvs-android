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

    @Test
    fun placeholderInsideArgumentIsNotExpandedAgain() {
        // Un titolo che contiene "%2" non deve prendere il posto del secondo argomento.
        assertEquals(
            "Mario 100%2 ha risposto a: Titolo",
            NotifText.translate("[[notifications:user-posted-to, Mario 100%2, Titolo]]", strings),
        )
    }

    @Test
    fun fillReplacesInOnePass() {
        assertEquals("a=%2 b=x", NotifText.fill("a=%1 b=%2", listOf("%2", "x")))
        // %10 è il decimo argomento, non %1 seguito da "0"; se manca resta com'è.
        assertEquals("uno %10", NotifText.fill("%1 %10", listOf("uno")))
        assertEquals("dieci", NotifText.fill("%10", (1..10).map { if (it == 10) "dieci" else "x" }))
    }

    @Test
    fun escapedKeyInArgumentIsNotTranslated() {
        // Le parentesi scritte come entità in un titolo non diventano una chiave da tradurre.
        assertEquals(
            "Mario ha risposto a: [[notifications:user-posted-to, X, Y]]",
            NotifText.translate(
                "[[notifications:user-posted-to, Mario, &#91;&#91;notifications:user-posted-to&#44; X&#44; Y&#93;&#93;]]",
                strings,
            ),
        )
    }
}
