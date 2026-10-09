package it.citiverse.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Archivio chiave/valore dei cookie: le preferenze dell'app, oppure una mappa nei test. */
interface CookiePersistence {
    fun all(): Map<String, String>
    fun write(clear: Boolean = false, remove: Collection<String> = emptyList(), put: Map<String, String> = emptyMap())
}

class PrefsCookiePersistence(private val prefs: SharedPreferences) : CookiePersistence {
    override fun all(): Map<String, String> =
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    override fun write(clear: Boolean, remove: Collection<String>, put: Map<String, String>) {
        val e = prefs.edit()
        if (clear) e.clear()
        remove.forEach { e.remove(it) }
        put.forEach { (k, v) -> e.putString(k, v) }
        e.apply()
    }
}

/**
 * Cookie salvati nelle preferenze private dell'app, così la sessione di NodeBB
 * (cookie express.sid) sopravvive alla chiusura dell'app e al servizio in background.
 *
 * Tiene solo i cookie di citiverse.it: quelli di altri server (per esempio le immagini
 * dei forum federati) vengono scartati. Dominio e flag host-only vengono salvati
 * esplicitamente, così al riavvio ogni cookie torna esattamente com'era.
 */
class PersistentCookieJar(
    private val store: CookiePersistence,
    private val now: () -> Long = System::currentTimeMillis,
) : CookieJar {

    constructor(context: Context) :
        this(PrefsCookiePersistence(context.getSharedPreferences("cookies", Context.MODE_PRIVATE)))

    private val lock = Any()
    private val cookies = mutableMapOf<String, Cookie>()

    /**
     * True se all'avvio c'erano cookie nel vecchio formato (versioni precedenti dell'app):
     * non si distinguono da quelli iniettati da altri server, quindi sono stati cancellati.
     */
    val droppedLegacyCookies: Boolean

    init {
        val saved = store.all()
        if (saved[FORMAT_KEY] != FORMAT) {
            droppedLegacyCookies = saved.keys.any { it != FORMAT_KEY }
            store.write(clear = true, put = mapOf(FORMAT_KEY to FORMAT))
        } else {
            droppedLegacyCookies = false
            val t = now()
            val stale = mutableListOf<String>()
            saved.forEach { (k, v) ->
                if (k == FORMAT_KEY) return@forEach
                val c = decode(v)
                if (c == null || !isSiteCookie(c) || c.expiresAt < t) stale.add(k) else cookies[key(c)] = c
            }
            if (stale.isNotEmpty()) store.write(remove = stale)
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.host != SITE_HOST) return
        synchronized(lock) {
            val put = mutableMapOf<String, String>()
            val remove = mutableListOf<String>()
            val t = now()
            for (c in cookies) {
                if (!isSiteCookie(c)) continue
                val k = key(c)
                if (c.expiresAt < t) {
                    this.cookies.remove(k)
                    remove.add(k)
                    put.remove(k)
                } else {
                    this.cookies[k] = c
                    // Anche i cookie "di sessione" vengono tenuti: li scade il server.
                    put[k] = encode(c)
                    remove.remove(k)
                }
            }
            if (put.isNotEmpty() || remove.isNotEmpty()) store.write(remove = remove, put = put)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val t = now()
            return cookies.values.filter { it.expiresAt >= t && it.matches(url) }
        }
    }

    fun hasSession(): Boolean = synchronized(lock) { cookies.values.any { it.name == "express.sid" } }

    fun clear() {
        synchronized(lock) {
            cookies.clear()
            store.write(clear = true, put = mapOf(FORMAT_KEY to FORMAT))
        }
    }

    companion object {
        private const val FORMAT_KEY = "_format"
        private const val FORMAT = "2"
        private const val PREFIX = "c|"

        private fun key(c: Cookie) = "$PREFIX${c.domain}|${c.path}|${c.name}"

        /** Solo cookie con dominio esattamente citiverse.it (host-only o no). */
        private fun isSiteCookie(c: Cookie) = c.domain == SITE_HOST

        internal fun encode(c: Cookie): String = buildJsonObject {
            put("name", c.name)
            put("value", c.value)
            put("domain", c.domain)
            put("hostOnly", c.hostOnly)
            put("path", c.path)
            put("secure", c.secure)
            put("httpOnly", c.httpOnly)
            put("persistent", c.persistent)
            put("expiresAt", c.expiresAt)
        }.toString()

        internal fun decode(s: String): Cookie? = runCatching {
            val o: JsonObject = JsonParser.parseToJsonElement(s).jsonObject
            fun text(k: String) = o.getValue(k).jsonPrimitive.content
            fun flag(k: String) = o.getValue(k).jsonPrimitive.boolean
            val domain = text("domain")
            Cookie.Builder()
                .name(text("name"))
                .value(text("value"))
                .apply { if (flag("hostOnly")) hostOnlyDomain(domain) else domain(domain) }
                .path(text("path"))
                .apply { if (flag("persistent")) expiresAt(o.getValue("expiresAt").jsonPrimitive.long) }
                .apply { if (flag("secure")) secure() }
                .apply { if (flag("httpOnly")) httpOnly() }
                .build()
        }.getOrNull()
    }
}
