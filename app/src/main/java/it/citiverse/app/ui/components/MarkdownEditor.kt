package it.citiverse.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvColors
import it.citiverse.app.ui.theme.CvIcons

/**
 * Testo Markdown colorato mentre si scrive: grassetto in grassetto, titoli più grandi,
 * simboli (**, #, >, [ ]…) in grigio chiaro. Il testo resta identico: cambia solo l'aspetto.
 */
private class MarkdownLook(private val c: CvColors, private val link: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val b = AnnotatedString.Builder(raw)
        MarkdownHighlight.spans(raw).forEach { s ->
            if (s.end > s.start) b.addStyle(style(s.kind), s.start, s.end)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun style(k: MdKind): SpanStyle = when (k) {
        MdKind.MARKER -> SpanStyle(color = c.muted.copy(alpha = 0.55f))
        MdKind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        MdKind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        MdKind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        MdKind.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = c.pill)
        MdKind.HEADING -> SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        MdKind.QUOTE -> SpanStyle(color = c.muted, fontStyle = FontStyle.Italic)
        MdKind.LIST -> SpanStyle(color = link, fontWeight = FontWeight.Bold)
        MdKind.LINK -> SpanStyle(color = link, textDecoration = TextDecoration.Underline)
        MdKind.URL -> SpanStyle(color = c.muted.copy(alpha = 0.7f), fontSize = 13.sp)
    }

    override fun equals(other: Any?) = other is MarkdownLook && other.c == c && other.link == link
    override fun hashCode() = c.hashCode() * 31 + link.hashCode()
}

private fun TextFieldValue.edit() = MdEdit(text, selection.start, selection.end)
private fun MdEdit.value() = TextFieldValue(text, TextRange(selStart, selEnd))

/**
 * Campo di testo dei post: Markdown colorato mentre si scrive. La barra dei formati
 * è a parte ([MarkdownToolbar]), agganciata sopra la tastiera: così il menu
 * Copia/Incolla di Android, che compare sopra la selezione, non la copre mai.
 */
@Composable
fun MarkdownField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 220.dp,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val c = Cv.colors
    val link = MaterialTheme.colorScheme.primary
    val look = remember(c, link) { MarkdownLook(c, link) }
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, c.border), RoundedCornerShape(16.dp))
            .background(c.card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp, lineHeight = 23.sp),
            cursorBrush = SolidColor(link),
            visualTransformation = look,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .onFocusChanged { onFocusChange(it.isFocused) },
            decorationBox = { inner ->
                if (value.text.isEmpty()) Text(placeholder, color = c.muted, fontSize = 16.sp)
                inner()
            },
        )
    }
}

/**
 * Barra dei formati (grassetto, corsivo, barrato, titolo, elenchi, citazione, codice, link).
 * Chi non conosce il Markdown usa i pulsanti; un pulsante è illuminato se il formato è
 * già attivo dove si trova il cursore, e toccarlo di nuovo lo toglie.
 */
@Composable
fun MarkdownToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Cv.colors
    val haptic = LocalHapticFeedback.current
    val active = remember(value.text, value.selection) {
        MarkdownHighlight.activeAt(MarkdownHighlight.spans(value.text), value.selection.start)
    }

    fun format(change: (MdEdit) -> MdEdit) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onValueChange(change(value.edit()).value())
    }

    Column(modifier.fillMaxWidth().background(c.card)) {
        HorizontalDivider(color = c.border)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatButton("Grassetto", MdKind.BOLD in active, letter = "B", weight = FontWeight.ExtraBold) {
                format { MarkdownEdit.toggleInline(it, MdInline.BOLD) }
            }
            FormatButton("Corsivo", MdKind.ITALIC in active, letter = "I", italic = true) {
                format { MarkdownEdit.toggleInline(it, MdInline.ITALIC) }
            }
            FormatButton("Barrato", MdKind.STRIKE in active, letter = "S", strike = true) {
                format { MarkdownEdit.toggleInline(it, MdInline.STRIKE) }
            }
            FormatButton("Titolo", MdKind.HEADING in active, letter = "H", weight = FontWeight.Bold) {
                format { MarkdownEdit.toggleLine(it, MdLine.HEADING) }
            }
            Separator()
            FormatButton("Elenco puntato", false, icon = CvIcons.ListBullet) {
                format { MarkdownEdit.toggleLine(it, MdLine.BULLET) }
            }
            FormatButton("Elenco numerato", false, icon = CvIcons.ListNumber) {
                format { MarkdownEdit.toggleLine(it, MdLine.NUMBERED) }
            }
            FormatButton("Citazione", MdKind.QUOTE in active, icon = CvIcons.Quote) {
                format { MarkdownEdit.toggleLine(it, MdLine.QUOTE) }
            }
            Separator()
            FormatButton("Codice", MdKind.CODE in active, icon = CvIcons.Code) {
                format { MarkdownEdit.toggleInline(it, MdInline.CODE) }
            }
            FormatButton("Link", MdKind.LINK in active || MdKind.URL in active, icon = CvIcons.Link) {
                format { MarkdownEdit.link(it) }
            }
        }
    }
}

@Composable
private fun FormatButton(
    label: String,
    selected: Boolean,
    letter: String? = null,
    icon: ImageVector? = null,
    weight: FontWeight = FontWeight.SemiBold,
    italic: Boolean = false,
    strike: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Cv.colors
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) c.text else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (selected) c.bg else c.text
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        } else if (letter != null) {
            Text(
                letter,
                color = tint,
                fontSize = 17.sp,
                fontWeight = weight,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (strike) TextDecoration.LineThrough else null,
            )
        }
    }
}

@Composable
private fun Separator() {
    Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 20.dp).background(Cv.colors.border))
}
