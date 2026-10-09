package it.citiverse.app

import it.citiverse.app.data.Me
import it.citiverse.app.data.SessionGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGuardTest {

    private fun me(uid: String = "5") = Me(uid, "mario", "mario", "Mario", null, "M", null)

    /** Come Prefs.me + CitiverseApp.endSession, ridotti all'essenziale. */
    private class FakeApp(var me: Me?) {
        var ended = 0
        val guard = SessionGuard({ me }, { ended++; me = null })
    }

    @Test
    fun expiredResponseEndsTheSameSession() {
        val app = FakeApp(me())
        val startedAs = app.guard.snapshot()
        // ... richiesta ... risponde 401
        assertTrue(app.guard.endIfStill(startedAs))
        assertEquals(1, app.ended)
        assertNull(app.me)
    }

    @Test
    fun lateResponseDoesNotEndANewLoginOfTheSameUser() {
        val app = FakeApp(me())
        val startedAs = app.guard.snapshot()
        // Mentre la richiesta è in corso l'utente esce e rientra: stesso uid, oggetto nuovo.
        val relogged = me()
        app.me = relogged
        assertFalse(app.guard.endIfStill(startedAs))
        assertEquals(0, app.ended)
        assertSame(relogged, app.me)
    }

    @Test
    fun lateResponseDoesNotEndALoginOfAnotherUser() {
        val app = FakeApp(me("5"))
        val startedAs = app.guard.snapshot()
        app.me = me("9")
        assertFalse(app.guard.endIfStill(startedAs))
        assertEquals(0, app.ended)
    }

    @Test
    fun loginAfterALoggedOutRequestIsKept() {
        // Richiesta partita da disconnessi (es. il servizio prima dell'accesso), poi login.
        val app = FakeApp(null)
        val startedAs = app.guard.snapshot()
        app.me = me()
        assertFalse(app.guard.endIfStill(startedAs))
        assertEquals(0, app.ended)
    }

    /* ---- servizio in tempo reale (PushService.sessionExpired e riavvio) ---- */

    @Test
    fun serviceExpiryOfTheCurrentSessionEndsIt() {
        val app = FakeApp(me())
        val connectedAs = app.guard.snapshot()
        assertEquals(SessionGuard.Expired.ENDED, app.guard.resolveExpired(connectedAs))
        assertEquals(1, app.ended)
    }

    @Test
    fun serviceExpiryAfterANewLoginReconnectsWithoutEndingIt() {
        val app = FakeApp(me())
        val connectedAs = app.guard.snapshot()
        app.me = me() // nuovo accesso mentre il socket vecchio era aperto
        assertEquals(SessionGuard.Expired.REPLACED, app.guard.resolveExpired(connectedAs))
        assertEquals(0, app.ended)
        assertTrue(app.me != null)
    }

    @Test
    fun serviceExpiryAfterALogoutIsQuiet() {
        val app = FakeApp(me())
        val connectedAs = app.guard.snapshot()
        app.me = null // uscita dalle impostazioni
        assertEquals(SessionGuard.Expired.LOGGED_OUT, app.guard.resolveExpired(connectedAs))
        assertEquals(0, app.ended)
    }

    @Test
    fun newLoginMakesTheServiceConnectionStale() {
        val app = FakeApp(me())
        val connectedAs = app.guard.snapshot()
        assertTrue(app.guard.isCurrent(connectedAs))
        app.me = me()
        // È questo che fa ripartire il servizio con la sessione nuova all'avvio successivo.
        assertFalse(app.guard.isCurrent(connectedAs))
        // Servizio mai connesso (connectedAs null) con un utente collegato: va connesso.
        assertFalse(app.guard.isCurrent(null))
    }
}
