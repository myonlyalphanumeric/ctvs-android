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
}
