package it.citiverse.app

import it.citiverse.app.data.ago
import it.citiverse.app.data.norm
import it.citiverse.app.data.remoteDomainOf
import it.citiverse.app.data.shortNum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test
    fun shortNumbers() {
        assertEquals("999", shortNum(999))
        assertEquals("1,2k", shortNum(1234))
        assertEquals("3k", shortNum(3000))
    }

    @Test
    fun agoUnits() {
        val now = 1_000_000_000_000L
        assertEquals("adesso", ago(now - 10_000, now))
        assertEquals("5 min", ago(now - 5 * 60_000, now))
        assertEquals("3 h", ago(now - 3 * 3_600_000, now))
        assertEquals("2 g", ago(now - 2 * 86_400_000L, now))
    }

    @Test
    fun remoteUsers() {
        assertEquals("mastodon.uno", remoteDomainOf("https://mastodon.uno/users/steek_hutzee", "steek_hutzee@mastodon.uno"))
        assertEquals("poliversity.it", remoteDomainOf("https://poliversity.it/users/skariko", "skariko"))
        assertNull(remoteDomainOf("86", "mario", isLocal = true))
    }

    @Test
    fun normalizeAccents() {
        assertEquals("citta forli", norm("Città Forlì"))
    }
}
