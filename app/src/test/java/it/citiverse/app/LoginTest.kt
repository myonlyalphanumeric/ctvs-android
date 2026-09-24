package it.citiverse.app

import it.citiverse.app.data.isSecondFactorPending
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginTest {
    @Test
    fun secondFactorIsDetectedFromTheStatusCode() {
        // Risposta vera di citiverse.it dopo la password, con la 2FA ancora da fare:
        // 401 {"status":{"code":"not-authorised","message":"È richiesta l'autenticazione a due fattori…"}}
        assertTrue(isSecondFactorPending(401))
        assertFalse(isSecondFactorPending(200))
        assertFalse(isSecondFactorPending(403))
    }
}
