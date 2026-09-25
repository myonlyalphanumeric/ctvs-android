package it.citiverse.app.push

import it.citiverse.app.data.ApiException
import it.citiverse.app.data.BASE_URL
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Chiamata socket.io "usa e getta": apre la connessione, manda un evento con risposta
 * (ack), aspetta il risultato e chiude. Serve per le azioni che NodeBB offre solo via
 * socket, come il blocco di un utente (user.toggleBlock).
 */
object SocketCall {
    private const val ACK_ID = 1

    suspend fun emit(http: OkHttpClient, csrf: String?, event: String, arg: JsonElement, timeoutMs: Long = 15_000): JsonElement? =
        withTimeout(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val url = "$BASE_URL/socket.io/".toHttpUrl().newBuilder()
                    .addQueryParameter("EIO", "4")
                    .addQueryParameter("transport", "websocket")
                    .apply { if (csrf != null) addQueryParameter("_csrf", csrf) }
                    .build()
                val req = Request.Builder().url(url).header("Origin", BASE_URL).build()
                val ws = http.newWebSocket(req, object : WebSocketListener() {
                    private var sent = false

                    private fun finish(socket: WebSocket, block: () -> Unit) {
                        socket.close(1000, null)
                        if (cont.isActive) block()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        when (val p = SioPacket.parse(text)) {
                            is SioPacket.Open -> webSocket.send("40")
                            SioPacket.Ping -> webSocket.send("3")
                            SioPacket.Connected -> if (!sent) {
                                sent = true
                                webSocket.send(SioPacket.encodeEvent(event, listOf(arg), ACK_ID))
                            }
                            is SioPacket.Ack -> if (p.id == ACK_ID) finish(webSocket) {
                                // Risposta di NodeBB: [errore, risultato]
                                val err = p.args.firstOrNull() as? JsonObject
                                val msg = (err?.get("message") as? JsonPrimitive)?.content
                                if (msg != null) cont.resumeWithException(ApiException(readable(msg)))
                                else cont.resume(p.args.getOrNull(1))
                            }
                            is SioPacket.ConnectError -> finish(webSocket) {
                                cont.resumeWithException(ApiException("Connessione rifiutata dal server: accedi di nuovo."))
                            }
                            else -> Unit
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (cont.isActive) cont.resumeWithException(ApiException("Connessione non riuscita. Controlla la rete e riprova."))
                    }
                })
                cont.invokeOnCancellation { ws.cancel() }
            }
        }

    private fun readable(msg: String): String = when {
        msg.contains("cannot-block-self") -> "Non puoi bloccare te stesso."
        msg.contains("cannot-block-privileged") -> "Non puoi bloccare un amministratore o un moderatore."
        msg.contains("already-blocked") -> "Hai già bloccato questo utente."
        msg.contains("already-unblocked") -> "Questo utente non è bloccato."
        msg.contains("no-privileges") || msg.contains("not-logged-in") -> "Devi accedere per farlo."
        msg.startsWith("[[") -> "Operazione non riuscita."
        else -> msg
    }
}
