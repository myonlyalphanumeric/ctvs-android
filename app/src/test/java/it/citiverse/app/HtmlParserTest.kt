package it.citiverse.app

import it.citiverse.app.ui.components.HtmlBlock
import it.citiverse.app.ui.components.HtmlParser
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlParserTest {
    private fun images(html: String) = HtmlParser.parse(html).filterIsInstance<HtmlBlock.Image>().map { it.url }

    @Test
    fun keepsWebImages() {
        assertEquals(
            listOf("https://citiverse.it/assets/a.png", "http://example.org/b.png"),
            images("""<p><img src="/assets/a.png"></p><p><img src="http://example.org/b.png"></p>"""),
        )
        // Lo schema non distingue maiuscole e minuscole.
        assertEquals(1, images("""<p><img src="HTTPS://example.org/c.png"></p>""").size)
    }

    @Test
    fun dropsOtherSchemes() {
        val html = listOf(
            "file:///data/data/it.citiverse.app/shared_prefs/cookies.xml",
            "content://media/external/images/1",
            "data:image/png;base64,AAAA",
            "javascript:alert(1)",
            "android.resource://it.citiverse.app/1",
        ).joinToString("") { """<p><img src="$it"></p>""" }
        assertEquals(emptyList<String>(), images(html))
    }
}
