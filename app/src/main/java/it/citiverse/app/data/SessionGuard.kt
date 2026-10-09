package it.citiverse.app.data

/**
 * Chiude la sessione solo se è ancora quella con cui era partita una richiesta.
 *
 * Una risposta 401 può arrivare da una richiesta partita prima di un nuovo accesso: in quel caso
 * la sessione appena aperta non va toccata. Si confronta l'identità dell'oggetto [Me]: ogni accesso
 * ne crea uno nuovo, anche quando l'utente è lo stesso, mentre finché la sessione dura resta lo stesso.
 */
class SessionGuard(
    private val current: () -> Me?,
    private val end: () -> Unit,
) {
    /** Da leggere prima della richiesta. */
    fun snapshot(): Me? = current()

    /** True se ha chiuso la sessione; false se nel frattempo è cambiata e quindi non l'ha toccata. */
    fun endIfStill(startedAs: Me?): Boolean {
        if (current() !== startedAs) return false
        end()
        return true
    }
}
