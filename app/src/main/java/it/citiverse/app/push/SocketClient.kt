package it.citiverse.app.push

import it.citiverse.app.data.BASE_URL
import it.citiverse.app.data.JsonParser
import it.citiverse.app.data.long
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pacchetti di Engine.IO v4 / socket.io v5, cioè il protocollo del socket.io di NodeBB.
 * Tenuto separato e senza dipendenze Android per poterlo testare.
 */
sealed class SioPacket {
    data class Open(val pingInterval: Long, val pingTimeout: Long) : SioPacket()
    data object Ping : SioPacket()
    data object Pong : SioPacket()
    data object Close : SioPacket()
    data object Connected : SioPacket()
    data class ConnectError(val message: String) : SioPacket()
    data class Event(val name: String, val args: List<JsonElement>) : SioPacket()
    data class Ack(val id: Int, val args: List<JsonElement>) : SioPacket()
    data object Disconnect : SioPacket()
    data class Unknown(val raw: String) : SioPacket()

    companion object {
        fun parse(text: String): SioPacket {
            if (text.isEmpty()) return Unknown(text)
            return when (text[0]) {
                '0' -> {
                    val o = runCatching { JsonParser.parseToJsonElement(text.substring(1)).jsonObject }.getOrNull()
                    Open(o?.long("pingInterval")?.takeIf { it > 0 } ?: 25_000, o?.long("pingTimeout")?.takeIf { it > 0 } ?: 20_000)
                }
                '1' -> Close
                '2' -> Ping
                '3' -> Pong
                '4' -> parseSocketIo(text.substring(1))
                else -> Unknown(text)
            }
        }

        private fun parseSocketIo(s: String): SioPacket {
            if (s.isEmpty()) return Unknown(s)
            val type = s[0]
            var i = 1
            // namespace opzionale ("/nome,"): NodeBB usa quello principale
            if (i < s.length && s[i] == '/') {
                val comma = s.indexOf(',', i)
                i = if (comma == -1) s.length else comma + 1
            }
            val idStart = i
            while (i < s.length && s[i].isDigit()) i++
            val id = s.substring(idStart, i).toIntOrNull()
            val payload = s.substring(i)
            return when (type) {
                '0' -> Connected
                '1' -> Disconnect
                '2' -> {
                    val arr = runCatching { JsonParser.parseToJsonElement(payload).jsonArray }.getOrNull()
                        ?: return Unknown(s)
                    val name = (arr.firstOrNull() as? JsonPrimitive)?.content ?: return Unknown(s)
                    Event(name, arr.drop(1))
                }
                '3' -> {
                    val arr = runCatching { JsonParser.parseToJsonElement(payload).jsonArray }.getOrNull()
                    Ack(id ?: -1, arr ?: emptyList())
                }
                '4' -> {
                    val o = runCatching { JsonParser.parseToJsonElement(payload) }.getOrNull()
                    val msg = ((o as? JsonObject)?.get("message") as? JsonPrimitive)?.content ?: payload
                    ConnectError(msg)
                }
                else -> Unknown(s)
            }
        }

        fun encodeEvent(name: String, args: List<JsonElement>, ackId: Int?): String {
            val arr = JsonArray(listOf(JsonPrimitive(name)) + args)
            return "42" + (ackId?.toString() ?: "") + arr.toString()
        }
    }
}

/**
 * Client socket.io minimale su WebSocket OkHttp.
 *
 * Keepalive allineato al server: è NodeBB che manda un "ping" ogni pingInterval
 * (25 s su citiverse.it) e noi rispondiamo "pong". Se non arriva nulla entro
 * pingInterval + pingTimeout la connessione è considerata morta e viene chiusa.
 */
class SocketClient(
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        fun onConnected()
        fun onEvent(name: String, args: List<JsonElement>)
        fun onDisconnected(reason: String)
    }

    private val generation = AtomicInteger(0)
    private val ackIds = AtomicInteger(0)
    private val acks = java.util.concurrent.ConcurrentHashMap<Int, (List<JsonElement>) -> Unit>()

    @Volatile private var ws: WebSocket? = null
    @Volatile var isConnected = false
        private set
    @Volatile var lastActivityAt = 0L
        private set

    private var pingInterval = 25_000L
    private var pingTimeout = 20_000L
    private var watchdog: Job? = null

    @Synchronized
    fun connect(csrf: String?) {
        close()
        val gen = generation.incrementAndGet()
        val url = "$BASE_URL/socket.io/".toHttpUrl().newBuilder()
            .addQueryParameter("EIO", "4")
            .addQueryParameter("transport", "websocket")
            .apply { if (csrf != null) addQueryParameter("_csrf", csrf) }
            .build()
        // OkHttp aggiunge da solo il cookie di sessione (express.sid) dal CookieJar.
        val req = Request.Builder().url(url)
            .header("Origin", BASE_URL)
            .build()
        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation.get()) return
                handle(webSocket, text, gen)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (gen == generation.get()) lost("chiusa dal server ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen == generation.get()) {
                    val why = response?.let { "HTTP ${it.code}" } ?: (t.message ?: t.javaClass.simpleName)
                    lost(why)
                }
            }
        })
        armWatchdog(gen, pingInterval + pingTimeout)
    }

    private fun handle(socket: WebSocket, text: String, gen: Int) {
        lastActivityAt = System.currentTimeMillis()
        when (val p = SioPacket.parse(text)) {
            is SioPacket.Open -> {
                pingInterval = p.pingInterval
                pingTimeout = p.pingTimeout
                socket.send("40")
                armWatchdog(gen, pingInterval + pingTimeout)
            }
            SioPacket.Ping -> {
                socket.send("3")
                armWatchdog(gen, pingInterval + pingTimeout)
            }
            SioPacket.Connected -> {
                isConnected = true
                listener.onConnected()
            }
            is SioPacket.ConnectError -> lost("rifiutata: ${p.message}")
            SioPacket.Disconnect, SioPacket.Close -> lost("disconnessa dal server")
            is SioPacket.Event -> listener.onEvent(p.name, p.args)
            is SioPacket.Ack -> acks.remove(p.id)?.invoke(p.args)
            else -> Unit
        }
    }

    @Synchronized
    private fun armWatchdog(gen: Int, timeout: Long) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(timeout + 2_000)
            if (gen == generation.get()) lost("nessun ping dal server")
        }
    }

    @Synchronized
    private fun lost(reason: String) {
        generation.incrementAndGet()
        watchdog?.cancel()
        val wasOpen = ws != null
        ws?.cancel()
        ws = null
        isConnected = false
        acks.clear()
        if (wasOpen) listener.onDisconnected(reason)
    }

    fun emit(event: String, args: List<JsonElement> = emptyList(), ack: ((List<JsonElement>) -> Unit)? = null): Boolean {
        val socket = ws ?: return false
        if (!isConnected) return false
        val id = ack?.let { ackIds.incrementAndGet().also { id -> acks[id] = it } }
        return socket.send(SioPacket.encodeEvent(event, args, id))
    }

    /** Chiusura voluta: non avvisa il listener. */
    @Synchronized
    fun close() {
        generation.incrementAndGet()
        watchdog?.cancel()
        ws?.close(1000, null)
        ws = null
        isConnected = false
        acks.clear()
    }
}
