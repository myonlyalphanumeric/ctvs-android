package it.citiverse.app.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Cookie salvati nelle preferenze private dell'app, così la sessione di NodeBB
 * (cookie express.sid) sopravvive alla chiusura dell'app e al servizio in background.
 */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookies", Context.MODE_PRIVATE)
    private val lock = Any()
    private val cookies = mutableMapOf<String, Cookie>()

    init {
        val base = BASE_URL.toHttpUrl()
        prefs.all.forEach { (key, value) ->
            val c = (value as? String)?.let { Cookie.parse(base, it) }
            if (c != null) cookies[key] = c
        }
    }

    private fun key(c: Cookie) = "${c.domain}|${c.path}|${c.name}"

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val edit = prefs.edit()
            for (c in cookies) {
                val k = key(c)
                if (c.expiresAt < System.currentTimeMillis()) {
                    this.cookies.remove(k)
                    edit.remove(k)
                } else {
                    this.cookies[k] = c
                    // Anche i cookie "di sessione" vengono tenuti: li scade il server.
                    edit.putString(k, c.toString())
                }
            }
            edit.apply()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            return cookies.values.filter { it.expiresAt >= now && it.matches(url) }
        }
    }

    fun hasSession(): Boolean = synchronized(lock) { cookies.values.any { it.name == "express.sid" } }

    fun clear() {
        synchronized(lock) {
            cookies.clear()
            prefs.edit().clear().apply()
        }
    }
}
