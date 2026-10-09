package it.citiverse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.citiverse.app.data.BASE_URL
import it.citiverse.app.ui.theme.Cv
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Blocchi in cui dividiamo l'HTML di un post. */
sealed interface HtmlBlock {
    data class Paragraph(val parts: List<Inline>) : HtmlBlock
    data class Heading(val level: Int, val parts: List<Inline>) : HtmlBlock
    data class Quote(val blocks: List<HtmlBlock>) : HtmlBlock
    data class Code(val text: String) : HtmlBlock
    data class Image(val url: String, val alt: String, val link: String?) : HtmlBlock
    data class ListBlock(val ordered: Boolean, val items: List<List<HtmlBlock>>) : HtmlBlock
    data object Rule : HtmlBlock
}

/** Pezzi di testo con stile (il link lo trasformiamo in LinkAnnotation al momento del disegno). */
data class Inline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val href: String? = null,
)

object HtmlParser {
    private val BLOCK_TAGS = setOf(
        "p", "div", "blockquote", "pre", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6",
        "hr", "figure", "table", "section", "article", "details", "li",
    )

    fun parse(html: String): List<HtmlBlock> {
        val doc = Jsoup.parseBodyFragment(html, "$BASE_URL/")
        return blocks(doc.body())
    }

    private fun isEmoji(el: Element) = el.tagName() == "img" &&
        (el.hasClass("emoji") || el.attr("src").contains("/emoji/"))

    private fun blocks(parent: Element): List<HtmlBlock> {
        val out = mutableListOf<HtmlBlock>()
        var inline = mutableListOf<Inline>()

        fun flush() {
            val trimmed = trimInline(inline)
            if (trimmed.isNotEmpty()) out.add(HtmlBlock.Paragraph(trimmed))
            inline = mutableListOf()
        }

        for (node in parent.childNodes()) {
            when {
                node is Element && node.tagName() == "img" && !isEmoji(node) -> {
                    flush()
                    image(node, null)?.let { out.add(it) }
                }
                node is Element && node.tagName() == "a" && node.children().size == 1 &&
                    node.child(0).tagName() == "img" && !isEmoji(node.child(0)) && node.text().isBlank() -> {
                    flush()
                    image(node.child(0), node.absUrl("href").ifBlank { null })?.let { out.add(it) }
                }
                node is Element && node.tagName() in BLOCK_TAGS -> {
                    flush()
                    out.addAll(block(node))
                }
                node is Element && node.tagName() == "br" -> inline.add(Inline("\n"))
                else -> collectInline(node, Style(), inline, out)
            }
        }
        flush()
        return out
    }

    private fun image(img: Element, link: String?): HtmlBlock? {
        val src = img.absUrl("src").ifBlank { img.attr("src") }.trim()
        // A Coil arrivano solo indirizzi web: niente file:, content:, data: o altri schemi.
        val scheme = src.substringBefore(':', "").lowercase()
        if (scheme != "http" && scheme != "https") return null
        return HtmlBlock.Image(src, img.attr("alt"), link)
    }

    private fun block(el: Element): List<HtmlBlock> = when (el.tagName()) {
        "blockquote" -> listOf(HtmlBlock.Quote(blocks(el)))
        "pre" -> listOf(HtmlBlock.Code(el.wholeText().trimEnd('\n')))
        "hr" -> listOf(HtmlBlock.Rule)
        "ul", "ol" -> listOf(
            HtmlBlock.ListBlock(
                ordered = el.tagName() == "ol",
                items = el.children().filter { it.tagName() == "li" }.map { blocks(it) },
            )
        )
        "h1", "h2", "h3", "h4", "h5", "h6" -> {
            val parts = mutableListOf<Inline>()
            el.childNodes().forEach { collectInline(it, Style(bold = true), parts, null) }
            listOf(HtmlBlock.Heading(el.tagName().substring(1).toInt(), trimInline(parts)))
        }
        else -> blocks(el)
    }

    private data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strike: Boolean = false,
        val href: String? = null,
    )

    /** Testo in linea; le immagini non-emoji dentro un paragrafo diventano blocchi a parte. */
    private fun collectInline(node: Node, st: Style, out: MutableList<Inline>, blocksOut: MutableList<HtmlBlock>?) {
        when (node) {
            is TextNode -> {
                val t = node.text().replace(Regex("\\s+"), " ")
                if (t.isNotEmpty()) out.add(Inline(t, st.bold, st.italic, st.code, st.strike, st.href))
            }
            is Element -> {
                val tag = node.tagName()
                when {
                    tag == "br" -> out.add(Inline("\n"))
                    tag == "img" && isEmoji(node) -> {
                        val alt = node.attr("alt").ifBlank { node.attr("title") }
                        if (alt.isNotBlank()) out.add(Inline(alt, st.bold, st.italic, false, st.strike, st.href))
                    }
                    tag == "img" -> blocksOut?.let { b -> image(node, st.href)?.let { b.add(it) } }
                    else -> {
                        val next = when (tag) {
                            "strong", "b" -> st.copy(bold = true)
                            "em", "i" -> st.copy(italic = true)
                            "code" -> st.copy(code = true)
                            "del", "s", "strike" -> st.copy(strike = true)
                            "a" -> st.copy(href = node.absUrl("href").ifBlank { node.attr("href") }.ifBlank { null })
                            else -> st
                        }
                        node.childNodes().forEach { collectInline(it, next, out, blocksOut) }
                    }
                }
            }
        }
    }

    private fun trimInline(parts: List<Inline>): List<Inline> {
        if (parts.isEmpty()) return parts
        val list = parts.toMutableList()
        while (list.isNotEmpty() && list.first().text.isBlank()) list.removeAt(0)
        while (list.isNotEmpty() && list.last().text.isBlank()) list.removeAt(list.lastIndex)
        if (list.isEmpty()) return list
        list[0] = list[0].copy(text = list[0].text.trimStart())
        list[list.lastIndex] = list[list.lastIndex].copy(text = list[list.lastIndex].text.trimEnd())
        return list
    }
}

/**
 * Contenuto di un post. I link interni (discussioni, comunità) li gestisce l'app,
 * gli altri si aprono nel browser.
 */
@Composable
fun HtmlContent(
    html: String,
    fontSize: TextUnit,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(html) { runCatching { HtmlParser.parse(html) }.getOrDefault(emptyList()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { RenderBlock(it, fontSize, onLink) }
    }
}

@Composable
private fun RenderBlock(block: HtmlBlock, fontSize: TextUnit, onLink: (String) -> Unit) {
    val c = Cv.colors
    val lineHeight = (fontSize.value * 1.6f).sp
    when (block) {
        is HtmlBlock.Paragraph -> Text(
            annotated(block.parts, onLink),
            color = c.text,
            fontSize = fontSize,
            lineHeight = lineHeight,
        )
        is HtmlBlock.Heading -> Text(
            annotated(block.parts, onLink),
            color = c.text,
            fontSize = when (block.level) {
                1 -> fontSize * 1.35f
                2 -> fontSize * 1.25f
                else -> fontSize * 1.12f
            },
            fontWeight = FontWeight.Bold,
            lineHeight = lineHeight * 1.1f,
        )
        is HtmlBlock.Quote -> {
            val bar = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .background(c.text.copy(alpha = 0.04f))
                    .drawBehind {
                        drawRect(bar, topLeft = Offset.Zero, size = size.copy(width = 3.dp.toPx()))
                    }
                    .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                block.blocks.forEach { RenderBlock(it, fontSize * 0.95f, onLink) }
            }
        }
        is HtmlBlock.Code -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.pill)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(block.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = c.text)
        }
        is HtmlBlock.Image -> AsyncImage(
            model = block.url,
            contentDescription = block.alt.ifBlank { null },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .heightIn(min = 60.dp, max = 460.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onLink(block.link ?: block.url) },
        )
        is HtmlBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { i, item ->
                Row {
                    Text(
                        if (block.ordered) "${i + 1}." else "•",
                        color = c.muted,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        modifier = Modifier.width(22.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.forEach { RenderBlock(it, fontSize, onLink) }
                    }
                }
            }
        }
        HtmlBlock.Rule -> HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun annotated(parts: List<Inline>, onLink: (String) -> Unit): AnnotatedString {
    val link = MaterialTheme.colorScheme.primary
    val codeBg = Cv.colors.pill
    return remember(parts, link, codeBg) {
        buildAnnotatedString {
            parts.forEach { p ->
                val style = SpanStyle(
                    fontWeight = if (p.bold) FontWeight.SemiBold else null,
                    fontStyle = if (p.italic) FontStyle.Italic else null,
                    fontFamily = if (p.code) FontFamily.Monospace else null,
                    background = if (p.code) codeBg else Color.Unspecified,
                    textDecoration = if (p.strike) TextDecoration.LineThrough else null,
                )
                if (p.href != null) {
                    val href = p.href
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = href,
                            styles = TextLinkStyles(SpanStyle(color = link)),
                            linkInteractionListener = { onLink(href) },
                        )
                    ) { withStyle(style) { append(p.text) } }
                } else {
                    withStyle(style) { append(p.text) }
                }
            }
        }
    }
}
