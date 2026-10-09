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
    /** Esito di una sessione scaduta scoperta da chi lavora in background (vedi [resolveExpired]). */
    enum class Expired {
        /** Era ancora la sessione attuale: è stata chiusa, va chiesto un nuovo accesso. */
        ENDED,

        /** Nel frattempo c'è stato un nuovo accesso: niente da chiudere, si riparte con quello. */
        REPLACED,

        /** Nel frattempo l'utente è uscito: niente da chiudere né da segnalare. */
        LOGGED_OUT,
    }

    /** Da leggere prima della richiesta. */
    fun snapshot(): Me? = current()

    /** True se la sessione è ancora quella letta con [snapshot]. */
    fun isCurrent(startedAs: Me?): Boolean = current() === startedAs

    /** True se ha chiuso la sessione; false se nel frattempo è cambiata e quindi non l'ha toccata. */
    fun endIfStill(startedAs: Me?): Boolean {
        if (current() !== startedAs) return false
        end()
        return true
    }

    /** Come [endIfStill], ma dice anche che cosa è successo se la sessione era già cambiata. */
    fun resolveExpired(startedAs: Me?): Expired = when {
        endIfStill(startedAs) -> Expired.ENDED
        current() != null -> Expired.REPLACED
        else -> Expired.LOGGED_OUT
    }
}
