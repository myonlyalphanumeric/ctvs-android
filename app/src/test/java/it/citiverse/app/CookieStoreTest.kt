package it.citiverse.app

import it.citiverse.app.data.CookiePersistence
import it.citiverse.app.data.PersistentCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieStoreTest {

    /** Le preferenze dell'app, simulate con una mappa. */
    private class MapPersistence(initial: Map<String, String> = emptyMap()) : CookiePersistence {
        val data = initial.toMutableMap()
        override fun all(): Map<String, String> = data.toMap()
        override fun write(clear: Boolean, remove: Collection<String>, put: Map<String, String>) {
            if (clear) data.clear()
            remove.forEach { data.remove(it) }
            data.putAll(put)
        }
    }

    private val site = "https://citiverse.it/api/config".toHttpUrl()
    private val now = 1_700_000_000_000L

    private fun jar(store: MapPersistence) = PersistentCookieJar(store) { now }

    private fun cookie(url: HttpUrl, header: String): Cookie = Cookie.parse(url, header)!!

    private fun cookieKeys(store: MapPersistence) = store.data.keys.filter { it != "_format" }

    @Test
    fun cookiesFromOtherHostsAreDiscarded() {
        val store = MapPersistence()
        val j = jar(store)
        val images = "https://media.altro-forum.example/files/a.png".toHttpUrl()
        j.saveFromResponse(images, listOf(cookie(images, "express.sid=rubato; Path=/")))

        assertTrue(j.loadForRequest(site).isEmpty())
        assertFalse(j.hasSession())
        assertTrue(cookieKeys(store).isEmpty())
        // Neanche dopo un riavvio diventa un cookie di citiverse.it.
        assertTrue(jar(store).loadForRequest(site).isEmpty())
    }

    @Test
    fun subdomainsOfTheSiteAreDiscardedToo() {
        val store = MapPersistence()
        val j = jar(store)
        val sub = "https://cdn.citiverse.it/x.png".toHttpUrl()
        j.saveFromResponse(sub, listOf(cookie(sub, "express.sid=x; Path=/")))
        assertTrue(cookieKeys(store).isEmpty())
        assertFalse(j.hasSession())
    }

    @Test
    fun hostOnlyCookieSurvivesRestartAsHostOnly() {
        val store = MapPersistence()
        jar(store).saveFromResponse(site, listOf(cookie(site, "express.sid=abc; Path=/; Secure; HttpOnly")))

        val restored = jar(store).loadForRequest(site).single()
        assertEquals("express.sid", restored.name)
        assertEquals("abc", restored.value)
        assertEquals("citiverse.it", restored.domain)
        assertTrue(restored.hostOnly)
        assertTrue(restored.secure)
        assertTrue(restored.httpOnly)
        assertFalse(restored.persistent)
        // Host-only: non va ai sottodomini.
        assertTrue(jar(store).loadForRequest("https://cdn.citiverse.it/".toHttpUrl()).isEmpty())
    }

    @Test
    fun domainCookieSurvivesRestartWithItsDomainAndExpiry() {
        val store = MapPersistence()
        val original = cookie(site, "pref=1; Domain=citiverse.it; Path=/; Max-Age=3600")
        jar(store).saveFromResponse(site, listOf(original))

        val restored = jar(store).loadForRequest(site).single()
        assertFalse(restored.hostOnly)
        assertEquals("citiverse.it", restored.domain)
        assertTrue(restored.persistent)
        assertEquals(original.expiresAt, restored.expiresAt)
        assertEquals(1, jar(store).loadForRequest("https://cdn.citiverse.it/".toHttpUrl()).size)
    }

    @Test
    fun encodeDecodeKeepsEveryField() {
        val c = cookie(site, "a=b; Path=/api; Secure; Max-Age=60")
        val d = PersistentCookieJar.decode(PersistentCookieJar.encode(c))
        assertNotNull(d)
        assertEquals(c, d)
    }

    @Test
    fun storedCookieForAnotherDomainIsIgnored() {
        // Voce manomessa o scritta da una versione futura: un dominio diverso non viene caricato.
        val foreign = Cookie.Builder().name("express.sid").value("x").hostOnlyDomain("evil.example").build()
        val store = MapPersistence(
            mapOf("_format" to "2", "c|evil.example|/|express.sid" to PersistentCookieJar.encode(foreign))
        )
        val j = jar(store)
        assertFalse(j.hasSession())
        assertTrue(cookieKeys(store).isEmpty())
    }

    @Test
    fun expiredCookieRemovesTheSavedOne() {
        val store = MapPersistence()
        val j = jar(store)
        j.saveFromResponse(site, listOf(cookie(site, "express.sid=abc; Path=/")))
        j.saveFromResponse(site, listOf(cookie(site, "express.sid=; Path=/; Max-Age=0")))
        assertFalse(j.hasSession())
        assertTrue(cookieKeys(store).isEmpty())
    }

    @Test
    fun legacyCookiesAreDroppedOnceOnUpgrade() {
        // Formato delle versioni precedenti: chiave "dominio|path|nome", valore Cookie.toString().
        val store = MapPersistence(
            mapOf(
                "citiverse.it|/|express.sid" to "express.sid=abc; path=/; secure; httponly",
                "media.altro-forum.example|/|tracker" to "tracker=1; path=/",
            )
        )
        val first = jar(store)
        assertTrue(first.droppedLegacyCookies)
        assertFalse(first.hasSession())
        assertTrue(first.loadForRequest(site).isEmpty())
        assertEquals(setOf("_format"), store.data.keys)

        // Al riavvio successivo non c'è più niente da migrare: il nuovo login non va chiesto di nuovo.
        val second = jar(store)
        assertFalse(second.droppedLegacyCookies)
    }

    @Test
    fun freshInstallIsNotTreatedAsMigration() {
        val store = MapPersistence()
        assertFalse(jar(store).droppedLegacyCookies)
        assertEquals("2", store.data["_format"])
    }

    @Test
    fun clearKeepsTheNewFormat() {
        val store = MapPersistence()
        val j = jar(store)
        j.saveFromResponse(site, listOf(cookie(site, "express.sid=abc; Path=/")))
        j.clear()
        assertFalse(j.hasSession())
        assertEquals(setOf("_format"), store.data.keys)
        assertFalse(jar(store).droppedLegacyCookies)
    }
}
