package it.citiverse.app.ui.components

/**
 * Formattazione Markdown senza doverlo conoscere: i pulsanti dell'editor aggiungono
 * (o tolgono) i simboli attorno alla selezione. Solo testo, senza dipendenze Android,
 * per poterlo testare.
 */
data class MdEdit(val text: String, val selStart: Int, val selEnd: Int)

enum class MdInline(val marker: String) {
    BOLD("**"),
    ITALIC("*"),
    STRIKE("~~"),
    CODE("`"),
}

enum class MdLine(val prefix: String) {
    HEADING("## "),
    QUOTE("> "),
    BULLET("- "),
    NUMBERED("1. "),
}

object MarkdownEdit {

    /** Grassetto, corsivo, barrato, codice: avvolge la selezione o, se è già avvolta, toglie i simboli. */
    fun toggleInline(e: MdEdit, kind: MdInline): MdEdit {
        val m = kind.marker
        var s = minOf(e.selStart, e.selEnd).coerceIn(0, e.text.length)
        var t = maxOf(e.selStart, e.selEnd).coerceIn(0, e.text.length)
        // Gli spazi ai bordi restano fuori: "**parola **" non sarebbe grassetto.
        while (s < t && e.text[s].isWhitespace()) s++
        while (t > s && e.text[t - 1].isWhitespace()) t--

        // Già formattato attorno alla selezione (o al cursore): togli.
        if (isWrapped(e.text, s, t, m)) {
            val out = e.text.removeRange(t, t + m.length).removeRange(s - m.length, s)
            return MdEdit(out, s - m.length, t - m.length)
        }
        // Selezione che include già i simboli: togli.
        val sel = e.text.substring(s, t)
        if (sel.length >= 2 * m.length && sel.startsWith(m) && sel.endsWith(m) && !sameMarkerLonger(sel, kind)) {
            val inner = sel.substring(m.length, sel.length - m.length)
            return MdEdit(e.text.replaceRange(s, t, inner), s, s + inner.length)
        }
        if (kind == MdInline.CODE && sel.contains('\n')) {
            // Più righe di codice: blocco ``` … ```
            val before = if (s > 0 && e.text[s - 1] != '\n') "\n" else ""
            val block = "$before```\n$sel\n```\n"
            return MdEdit(e.text.replaceRange(s, t, block), s + before.length + 4, s + before.length + 4 + sel.length)
        }
        val out = e.text.replaceRange(s, t, m + sel + m)
        return MdEdit(out, s + m.length, t + m.length)
    }

    private fun isWrapped(text: String, s: Int, t: Int, m: String): Boolean {
        if (s < m.length || t + m.length > text.length) return false
        if (text.substring(s - m.length, s) != m || text.substring(t, t + m.length) != m) return false
        // "*" non deve essere metà di "**" (corsivo dentro un grassetto).
        if (m == "*") {
            val outerBefore = s - 2 >= 0 && text[s - 2] == '*'
            val outerAfter = t + 1 < text.length && text[t + 1] == '*'
            if (outerBefore != outerAfter) return false
            if (outerBefore && outerAfter) {
                val tripleBefore = s - 3 >= 0 && text[s - 3] == '*'
                val tripleAfter = t + 2 < text.length && text[t + 2] == '*'
                return tripleBefore && tripleAfter
            }
        }
        return true
    }

    private fun sameMarkerLonger(sel: String, kind: MdInline): Boolean =
        kind == MdInline.ITALIC && sel.startsWith("**") && sel.endsWith("**") && !sel.startsWith("***")

    /** Titolo, citazione, elenco: aggiunge il prefisso a ogni riga selezionata, o lo toglie se c'è già ovunque. */
    fun toggleLine(e: MdEdit, kind: MdLine): MdEdit {
        val s = minOf(e.selStart, e.selEnd).coerceIn(0, e.text.length)
        val t = maxOf(e.selStart, e.selEnd).coerceIn(0, e.text.length)
        val lineStart = e.text.lastIndexOf('\n', (s - 1).coerceAtLeast(-1)).let { if (s == 0) 0 else it + 1 }
        val lineEndRaw = e.text.indexOf('\n', t)
        val lineEnd = if (lineEndRaw == -1) e.text.length else lineEndRaw
        val lines = e.text.substring(lineStart, lineEnd).split('\n')

        val pattern = linePattern(kind)
        val all = lines.filter { it.isNotBlank() }.let { nb -> nb.isNotEmpty() && nb.all { pattern.containsMatchIn(it) } }
        var n = 0
        val changed = lines.map { line ->
            when {
                all -> line.replaceFirst(pattern, "")
                line.isBlank() && lines.size > 1 -> line
                else -> {
                    n++
                    // Un solo tipo di prefisso per riga: "## " sostituisce "> ", ecc.
                    val bare = ANY_PREFIX.replaceFirst(line, "")
                    val prefix = if (kind == MdLine.NUMBERED) "$n. " else kind.prefix
                    prefix + bare
                }
            }
        }.joinToString("\n")
        val out = e.text.replaceRange(lineStart, lineEnd, changed)
        val delta = changed.length - (lineEnd - lineStart)
        return if (s == t) {
            // Cursore alla fine della riga modificata (se era in una riga sola).
            val pos = if (lines.size == 1) (lineEnd + delta) else (t + delta).coerceIn(0, out.length)
            MdEdit(out, pos, pos)
        } else {
            MdEdit(out, lineStart, lineEnd + delta)
        }
    }

    private val ANY_PREFIX = Regex("^(#{1,6} |> ?|[-*+] |\\d+\\. )")

    private fun linePattern(kind: MdLine) = when (kind) {
        MdLine.HEADING -> Regex("^#{1,6} ")
        MdLine.QUOTE -> Regex("^> ?")
        MdLine.BULLET -> Regex("^[-*+] ")
        MdLine.NUMBERED -> Regex("^\\d+\\. ")
    }

    /**
     * Link: con del testo selezionato diventa [testo](https://) con l'indirizzo selezionato,
     * da incollare; senza selezione inserisce [testo](https://) con "testo" selezionato.
     */
    fun link(e: MdEdit): MdEdit {
        val s = minOf(e.selStart, e.selEnd).coerceIn(0, e.text.length)
        val t = maxOf(e.selStart, e.selEnd).coerceIn(0, e.text.length)
        val sel = e.text.substring(s, t)
        if (sel.startsWith("http://") || sel.startsWith("https://")) {
            val out = e.text.replaceRange(s, t, "[testo]($sel)")
            return MdEdit(out, s + 1, s + 6)
        }
        val label = sel.ifEmpty { "testo" }
        val out = e.text.replaceRange(s, t, "[$label](https://)")
        return if (sel.isEmpty()) MdEdit(out, s + 1, s + 1 + label.length)
        else MdEdit(out, s + label.length + 3, s + label.length + 11)
    }
}

/* ---------- riconoscimento, per colorare il testo mentre si scrive ---------- */

enum class MdKind { MARKER, BOLD, ITALIC, STRIKE, CODE, HEADING, QUOTE, LIST, LINK, URL }

data class MdSpan(val kind: MdKind, val start: Int, val end: Int)

object MarkdownHighlight {
    private val HEADING = Regex("^(#{1,6} )(.*)$")
    private val QUOTE = Regex("^(> ?)(.*)$")
    private val LIST = Regex("^(\\s*)([-*+]|\\d+\\.)( )")
    private val CODE = Regex("`([^`\\n]+)`")
    private val BOLD = Regex("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1")
    private val ITALIC = Regex("(?<![*\\w])([*_])(?![*_\\s])(.+?)(?<![*_\\s])\\1(?![*\\w])")
    private val STRIKE = Regex("(~~)(?=\\S)(.+?)(?<=\\S)~~")
    private val LINK = Regex("\\[([^\\]\\n]+)\\]\\(([^)\\s]+)\\)")

    /** Zone da evidenziare. Le parti dentro `codice` non vengono interpretate. */
    fun spans(text: String): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        var offset = 0
        var inFence = false
        for (line in text.split('\n')) {
            val end = offset + line.length
            if (line.trimStart().startsWith("```")) {
                out += MdSpan(MdKind.MARKER, offset, end)
                inFence = !inFence
            } else if (inFence) {
                out += MdSpan(MdKind.CODE, offset, end)
            } else {
                lineSpans(line, offset, out)
            }
            offset = end + 1
        }
        return out
    }

    private fun lineSpans(line: String, base: Int, out: MutableList<MdSpan>) {
        var body = 0
        HEADING.find(line)?.let { m ->
            out += MdSpan(MdKind.MARKER, base, base + m.groupValues[1].length)
            out += MdSpan(MdKind.HEADING, base + m.groupValues[1].length, base + line.length)
            body = m.groupValues[1].length
        } ?: QUOTE.find(line)?.let { m ->
            out += MdSpan(MdKind.MARKER, base, base + m.groupValues[1].length)
            out += MdSpan(MdKind.QUOTE, base + m.groupValues[1].length, base + line.length)
            body = m.groupValues[1].length
        } ?: LIST.find(line)?.let { m ->
            val s = m.groupValues[1].length
            out += MdSpan(MdKind.LIST, base + s, base + s + m.groupValues[2].length)
            body = m.value.length
        }

        val rest = line.substring(body)
        val b = base + body
        // Il codice in linea esclude il resto della formattazione al suo interno.
        val code = CODE.findAll(rest).map { it.range }.toList()
        code.forEach { r ->
            out += MdSpan(MdKind.MARKER, b + r.first, b + r.first + 1)
            out += MdSpan(MdKind.CODE, b + r.first + 1, b + r.last)
            out += MdSpan(MdKind.MARKER, b + r.last, b + r.last + 1)
        }
        fun free(r: IntRange) = code.none { c -> r.first <= c.last && c.first <= r.last }

        fun wrapped(re: Regex, kind: MdKind) = re.findAll(rest).filter { free(it.range) }.forEach { m ->
            val ml = m.groupValues[1].length
            val r = m.range
            out += MdSpan(MdKind.MARKER, b + r.first, b + r.first + ml)
            out += MdSpan(kind, b + r.first + ml, b + r.last + 1 - ml)
            out += MdSpan(MdKind.MARKER, b + r.last + 1 - ml, b + r.last + 1)
        }
        wrapped(BOLD, MdKind.BOLD)
        wrapped(ITALIC, MdKind.ITALIC)
        wrapped(STRIKE, MdKind.STRIKE)

        LINK.findAll(rest).filter { free(it.range) }.forEach { m ->
            val r = m.range
            val label = m.groups[1]!!.range
            val url = m.groups[2]!!.range
            out += MdSpan(MdKind.MARKER, b + r.first, b + label.first)
            out += MdSpan(MdKind.LINK, b + label.first, b + label.last + 1)
            out += MdSpan(MdKind.MARKER, b + label.last + 1, b + url.first)
            out += MdSpan(MdKind.URL, b + url.first, b + url.last + 1)
            out += MdSpan(MdKind.MARKER, b + url.last + 1, b + r.last + 1)
        }
    }

    /** Formati attivi nel punto del cursore, per illuminare i pulsanti. */
    fun activeAt(spans: List<MdSpan>, pos: Int): Set<MdKind> =
        spans.filter { it.kind != MdKind.MARKER && pos >= it.start && pos <= it.end && it.end > it.start }
            .map { it.kind }.toSet()
}
