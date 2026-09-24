package it.citiverse.app.ui

import it.citiverse.app.data.NodeBBApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections

data class VoteState(
    val up: Boolean = false,
    val down: Boolean = false,
    val score: Int = 0,
    val loaded: Boolean = false,
    val busy: Boolean = false,
)

/**
 * Stato dei voti condiviso tra feed e discussione, indicizzato per pid.
 * Il feed di NodeBB non dice se hai già votato: lo chiediamo post per post,
 * solo per le card visibili e al massimo 4 richieste insieme.
 */
class VoteStore(private val api: NodeBBApi) {
    private val _states = MutableStateFlow<Map<String, VoteState>>(emptyMap())
    val states: StateFlow<Map<String, VoteState>> = _states.asStateFlow()

    private val limiter = Semaphore(4)
    private val loading = Collections.synchronizedSet(mutableSetOf<String>())

    /** Valore iniziale dal feed (punteggio), senza sovrascrivere lo stato già noto. */
    fun seed(pid: String, score: Int) {
        _states.update { m -> if (m.containsKey(pid)) m else m + (pid to VoteState(score = score)) }
    }

    /** Valore completo (per esempio dai post della discussione, che includono upvoted/downvoted). */
    fun put(pid: String, up: Boolean, down: Boolean, score: Int) {
        _states.update { m ->
            val cur = m[pid]
            if (cur?.busy == true) m else m + (pid to VoteState(up, down, score, loaded = true))
        }
    }

    suspend fun ensureLoaded(pid: String) {
        if (_states.value[pid]?.loaded == true || !loading.add(pid)) return
        try {
            val post = limiter.withPermit { api.post(pid) } ?: return
            put(pid, post.upvoted, post.downvoted, post.votes)
        } catch (_: Exception) {
            // Lo stato resta quello del feed: riproveremo al prossimo voto.
        } finally {
            loading.remove(pid)
        }
    }

    /** Voto su/giù con aggiornamento immediato; se il server rifiuta torna com'era. */
    suspend fun toggle(pid: String, isUp: Boolean): Result<Unit> {
        ensureLoaded(pid)
        val prev = _states.value[pid] ?: VoteState()
        if (prev.busy) return Result.success(Unit)
        val nextUp = if (isUp) !prev.up else false
        val nextDown = if (isUp) false else !prev.down
        val score = prev.score - (if (prev.up) 1 else 0) + (if (prev.down) 1 else 0) +
            (if (nextUp) 1 else 0) - (if (nextDown) 1 else 0)
        _states.update { it + (pid to VoteState(nextUp, nextDown, score, loaded = true, busy = true)) }
        return try {
            api.vote(pid, if (nextUp) 1 else if (nextDown) -1 else 0)
            _states.update { it + (pid to VoteState(nextUp, nextDown, score, loaded = true, busy = false)) }
            Result.success(Unit)
        } catch (e: Exception) {
            _states.update { it + (pid to prev.copy(busy = false)) }
            Result.failure(e)
        }
    }

    fun clear() {
        _states.value = emptyMap()
    }
}
