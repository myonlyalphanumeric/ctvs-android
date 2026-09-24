package it.citiverse.app.push

/**
 * NodeBB manda i testi delle notifiche come chiavi da tradurre, per esempio
 * "[[notifications:user-posted-to, Mario, Titolo]]". Il sito le traduce nel browser;
 * qui usiamo gli stessi file di lingua (assets/language/it/notifications.json).
 */
object NotifText {

    // Gli argomenti non contengono parentesi quadre (NodeBB le scrive come &#91; &#93;),
    // così le chiavi annidate vengono tradotte per prime.
    private val TOKEN = Regex("""\[\[([\w-]+):([\w.\-]+)((?:,[^\[\]]*)?)]]""")

    /** Testi di riserva se il file di lingua non è ancora stato scaricato. */
    val FALLBACK: Map<String, String> = mapOf(
        "notifications:user-posted-to" to "<strong>%1</strong> ha risposto a: <strong>%2</strong>",
        "notifications:user-posted-to-dual" to "<strong>%1</strong> e <strong>%2</strong> hanno risposto a: <strong>%3</strong>",
        "notifications:user-posted-to-triple" to "<strong>%1</strong>, <strong>%2</strong> e <strong>%3</strong> hanno risposto a: <strong>%4</strong>",
        "notifications:user-posted-to-multiple" to "<strong>%1</strong>, <strong>%2</strong> e altri %3 hanno risposto a: <strong>%4</strong>",
        "notifications:user-posted-topic" to "<strong>%1</strong> ha aperto una nuova discussione: <strong>%2</strong>",
        "notifications:user-mentioned-you-in" to "<strong>%1</strong> ti ha menzionato in <strong>%2</strong>",
        "notifications:upvoted-your-post-in" to "<strong>%1</strong> ha votato positivamente il tuo post in <strong>%2</strong>",
        "notifications:user-started-following-you" to "<strong>%1</strong> ha iniziato a seguirti.",
        "notifications:new-message-from" to "Nuovo messaggio da <strong>%1</strong>",
        "notifications:new-message-in" to "Nuovo messaggio in <strong>%1</strong>",
    )

    /** Traduce tutte le chiavi presenti nel testo e toglie l'HTML. */
    fun translate(raw: String, strings: Map<String, String>): String {
        var text = raw
        // Le chiavi possono essere annidate come argomenti: traduciamo finché ne restano.
        repeat(4) {
            if (!TOKEN.containsMatchIn(text)) return@repeat
            text = TOKEN.replace(text) { m ->
                val ns = m.groupValues[1]
                val key = m.groupValues[2]
                val args = splitArgs(m.groupValues[3])
                val template = strings["$ns:$key"] ?: FALLBACK["$ns:$key"]
                if (template == null) {
                    args.joinToString(" · ").ifBlank { key.replace('-', ' ') }
                } else {
                    var out: String = template
                    // dal più alto al più basso, così %1 non tocca %10
                    for (i in args.indices.reversed()) out = out.replace("%${i + 1}", args[i])
                    out
                }
            }
        }
        return stripHtml(text)
    }

    private fun splitArgs(s: String): List<String> {
        if (s.isBlank()) return emptyList()
        return s.removePrefix(",").split(',').map { unescape(it.trim()) }
    }

    private fun unescape(s: String): String = s
        .replace("&#44;", ",")
        .replace("&#x2C;", ",")
        .replace("&#91;", "[")
        .replace("&#93;", "]")

    fun stripHtml(html: String): String = html
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace(Regex("\\s+"), " ")
        .trim()
}
