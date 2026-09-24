package it.citiverse.app.data

import org.jsoup.Jsoup
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val IT = Locale.ITALIAN

/** Testo semplice da un frammento HTML (decodifica anche le entità come &amp;). */
fun textFromHtml(html: String?): String {
    if (html.isNullOrEmpty()) return ""
    if (html.none { it == '<' || it == '&' }) return html.trim()
    return Jsoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
}

/** "adesso", "5 min", "3 h", "2 g", poi la data. Come nel feed del sito. */
fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
    if (ts <= 0) return ""
    val s = ((now - ts) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "adesso"
        s < 3600 -> "${s / 60} min"
        s < 86400 -> "${s / 3600} h"
        s < 86400 * 7 -> "${s / 86400} g"
        else -> {
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            val thisYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
            val pattern = if (cal.get(Calendar.YEAR) == thisYear) "d MMM" else "d MMM yyyy"
            SimpleDateFormat(pattern, IT).format(Date(ts))
        }
    }
}

fun fullDate(ts: Long): String =
    if (ts <= 0) "" else SimpleDateFormat("d MMMM yyyy, HH:mm", IT).format(Date(ts))

/** 999 → "999", 1234 → "1,2k". */
fun shortNum(n: Int): String {
    val abs = kotlin.math.abs(n)
    if (abs < 1000) return n.toString()
    val v = n / 1000.0
    val s = String.format(Locale.ROOT, "%.1f", v).removeSuffix(".0").replace('.', ',')
    return "${s}k"
}

fun fmtNum(n: Int): String = NumberFormat.getIntegerInstance(IT).format(n)

/** Minuscole senza accenti, per cercare "citta" e trovare "Città". */
fun norm(s: String): String =
    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(IT)
