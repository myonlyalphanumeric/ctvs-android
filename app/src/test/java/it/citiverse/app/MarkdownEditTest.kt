package it.citiverse.app

import it.citiverse.app.ui.components.MarkdownEdit
import it.citiverse.app.ui.components.MarkdownHighlight
import it.citiverse.app.ui.components.MdEdit
import it.citiverse.app.ui.components.MdInline
import it.citiverse.app.ui.components.MdKind
import it.citiverse.app.ui.components.MdLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditTest {
    private fun sel(text: String, word: String): MdEdit {
        val i = text.indexOf(word)
        return MdEdit(text, i, i + word.length)
    }

    @Test
    fun boldWrapsSelectionAndKeepsItSelected() {
        val r = MarkdownEdit.toggleInline(sel("ciao mondo", "mondo"), MdInline.BOLD)
        assertEquals("ciao **mondo**", r.text)
        assertEquals("mondo", r.text.substring(r.selStart, r.selEnd))
    }

    @Test
    fun boldTwiceGivesBackTheOriginal() {
        val once = MarkdownEdit.toggleInline(sel("ciao mondo", "mondo"), MdInline.BOLD)
        val twice = MarkdownEdit.toggleInline(once, MdInline.BOLD)
        assertEquals("ciao mondo", twice.text)
        assertEquals("mondo", twice.text.substring(twice.selStart, twice.selEnd))
    }

    @Test
    fun spacesStayOutsideTheMarkers() {
        val r = MarkdownEdit.toggleInline(sel("ciao mondo ", "mondo "), MdInline.BOLD)
        assertEquals("ciao **mondo** ", r.text)
    }

    @Test
    fun emptySelectionPutsTheCursorBetweenMarkers() {
        val r = MarkdownEdit.toggleInline(MdEdit("ciao ", 5, 5), MdInline.ITALIC)
        assertEquals("ciao **", r.text)
        assertEquals(6, r.selStart)
        assertEquals(6, r.selEnd)
    }

    @Test
    fun italicInsideBoldIsNotMistakenForBold() {
        val bold = MarkdownEdit.toggleInline(sel("ciao mondo", "mondo"), MdInline.BOLD)
        val both = MarkdownEdit.toggleInline(bold, MdInline.ITALIC)
        assertEquals("ciao ***mondo***", both.text)
        val back = MarkdownEdit.toggleInline(both, MdInline.ITALIC)
        assertEquals("ciao **mondo**", back.text)
    }

    @Test
    fun multiLineCodeBecomesABlock() {
        val r = MarkdownEdit.toggleInline(MdEdit("a\nb", 0, 3), MdInline.CODE)
        assertEquals("```\na\nb\n```\n", r.text)
    }

    @Test
    fun linePrefixesToggleAndReplaceEachOther() {
        val q = MarkdownEdit.toggleLine(MdEdit("titolo", 3, 3), MdLine.QUOTE)
        assertEquals("> titolo", q.text)
        val h = MarkdownEdit.toggleLine(q, MdLine.HEADING)
        assertEquals("## titolo", h.text)
        val off = MarkdownEdit.toggleLine(h, MdLine.HEADING)
        assertEquals("titolo", off.text)
    }

    @Test
    fun listAppliesToEverySelectedLine() {
        val text = "uno\ndue\ntre"
        val r = MarkdownEdit.toggleLine(MdEdit(text, 0, text.length), MdLine.NUMBERED)
        assertEquals("1. uno\n2. due\n3. tre", r.text)
        val b = MarkdownEdit.toggleLine(MdEdit("x\ny", 0, 3), MdLine.BULLET)
        assertEquals("- x\n- y", b.text)
    }

    @Test
    fun lineToggleOnlyTouchesTheCursorLine() {
        val r = MarkdownEdit.toggleLine(MdEdit("uno\ndue", 5, 5), MdLine.QUOTE)
        assertEquals("uno\n> due", r.text)
    }

    @Test
    fun linkSelectsWhatIsLeftToFill() {
        val r = MarkdownEdit.link(sel("vedi qui", "qui"))
        assertEquals("vedi [qui](https://)", r.text)
        assertEquals("https://", r.text.substring(r.selStart, r.selEnd))
        val empty = MarkdownEdit.link(MdEdit("", 0, 0))
        assertEquals("[testo](https://)", empty.text)
        assertEquals("testo", empty.text.substring(empty.selStart, empty.selEnd))
    }

    @Test
    fun highlightFindsFormatsAndMarkers() {
        val text = "## Titolo\nciao **forte** e *piano* e `x*y*` e [sito](https://a.it)"
        val spans = MarkdownHighlight.spans(text)
        fun has(kind: MdKind, s: String) = spans.any { it.kind == kind && text.substring(it.start, it.end) == s }
        assertTrue(has(MdKind.HEADING, "Titolo"))
        assertTrue(has(MdKind.BOLD, "forte"))
        assertTrue(has(MdKind.ITALIC, "piano"))
        assertTrue(has(MdKind.CODE, "x*y*"))
        assertTrue(has(MdKind.LINK, "sito"))
        assertTrue(has(MdKind.URL, "https://a.it"))
        // dentro il codice niente corsivo
        assertFalse(has(MdKind.ITALIC, "y"))
    }

    @Test
    fun snakeCaseIsNotItalic() {
        val spans = MarkdownHighlight.spans("nome_utente_lungo")
        assertTrue(spans.none { it.kind == MdKind.ITALIC })
    }

    @Test
    fun activeFormatsAtTheCursor() {
        val text = "ciao **forte**"
        val spans = MarkdownHighlight.spans(text)
        assertTrue(MdKind.BOLD in MarkdownHighlight.activeAt(spans, text.indexOf("rt")))
        assertTrue(MarkdownHighlight.activeAt(spans, 1).isEmpty())
    }
}
