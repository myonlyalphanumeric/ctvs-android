package it.citiverse.app.push

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.ApiException
import it.citiverse.app.data.NotificationItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.min
import kotlin.random.Random

/**
 * Servizio in primo piano che tiene aperto il WebSocket di NodeBB, come fanno
 * Signal/Molly senza Google Play Services. La notifica fissa è silenziosa e ha
 * un canale suo, che l'utente può nascondere.
 */
class PushService : Service(), SocketClient.Listener {

    companion object {
        private const val TAG = "PushService"
        const val ACTION_START = "it.citiverse.push.START"
        const val ACTION_CHECK = "it.citiverse.push.CHECK"
        const val ACTION_MARK_READ = "it.citiverse.push.MARK_READ"

        /** Ogni quanto l'allarme (anche in Doze) controlla che la connessione sia viva. */
        private const val WATCHDOG_MS = 15L * 60 * 1000
        private const val MAX_BACKOFF_MS = 5L * 60 * 1000

        private val _status = MutableStateFlow("Non attivo")
        val status: StateFlow<String> = _status.asStateFlow()

        @Volatile
        var running = false
            private set

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, PushService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PushService::class.java))
        }

        fun markRead(ctx: Context, nid: String) {
            if (!running) return
            runCatching {
                ctx.startService(Intent(ctx, PushService::class.java).setAction(ACTION_MARK_READ).putExtra("nid", nid))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val app get() = application as CitiverseApp
    private lateinit var socket: SocketClient
    private var connectJob: Job? = null
    private var attempt = 0
    private var lastAttemptAt = 0L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        Notifier.ensureChannels(this)
        goForeground("Connessione in corso…")
        socket = SocketClient(app.api.wsHttp, scope, this)
        registerNetwork()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Con startForegroundService bisogna chiamare startForeground a ogni avvio.
        if (!goForeground(_status.value)) {
            stopSelf()
            return START_NOT_STICKY
        }
        scheduleWatchdog()
        when (intent?.action) {
            ACTION_MARK_READ -> intent.getStringExtra("nid")?.let { nid ->
                scope.launch { socket.emit("notifications.markRead", listOf(JsonPrimitive(nid))) }
            }
            ACTION_CHECK -> scope.launch {
                // Controllo dell'allarme: se la connessione è caduta mentre il telefono dormiva, riparte subito.
                if (!socket.isConnected) {
                    attempt = 0
                    connectNow()
                }
            }
            else -> scope.launch { if (!socket.isConnected && connectJob?.isActive != true) connectNow() }
        }
        return START_STICKY
    }

    private fun goForeground(text: String): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.SERVICE_ID, Notifier.serviceNotification(this, text), type)
        true
    } catch (e: Exception) {
        // Android può vietare l'avvio da background (es. app non esclusa dall'ottimizzazione batteria).
        Log.w(TAG, "startForeground non consentito", e)
        app.prefs.pushStatus = "Android non ha permesso di avviare il servizio: apri l'app."
        false
    }

    private fun setStatus(text: String) {
        _status.value = text
        app.prefs.pushStatus = text
        if (Notifier.canNotify(this)) {
            runCatching {
                @Suppress("MissingPermission")
                NotificationManagerCompat.from(this).notify(Notifier.SERVICE_ID, Notifier.serviceNotification(this, text))
            }
        }
    }

    private fun hasNetwork(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Deve girare nello scope del servizio (un thread alla volta). */
    private fun connectNow() {
        lastAttemptAt = SystemClock.elapsedRealtime()
        connectJob?.cancel()
        connectJob = scope.launch {
            if (!hasNetwork()) {
                setStatus("In attesa della rete…")
                return@launch
            }
            setStatus("Connessione in corso…")
            try {
                // Token CSRF fresco: NodeBB lo chiede anche per aprire il socket.
                app.api.invalidateCsrf()
                val info = app.api.config()
                if (!info.loggedIn) {
                    sessionExpired()
                    return@launch
                }
                socket.connect(info.csrf)
            } catch (e: ApiException) {
                scheduleReconnect(e.message ?: "errore di rete")
            } catch (e: Exception) {
                scheduleReconnect(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Attese crescenti: 2 s, 4 s, 8 s… fino a 5 minuti, con un po' di casualità. */
    private fun scheduleReconnect(reason: String) {
        attempt++
        val base = min(MAX_BACKOFF_MS, 2_000L shl min(attempt - 1, 10))
        val wait = base + Random.nextLong(0, 1_000)
        setStatus("Disconnesso ($reason) · riprovo tra ${wait / 1000} s")
        connectJob?.cancel()
        connectJob = scope.launch {
            delay(wait)
            connectNow()
        }
    }

    private fun sessionExpired() {
        setStatus("Sessione scaduta: accedi di nuovo")
        Notifier.sessionExpired(this)
        stopSelf()
    }

    /* ---------- eventi del socket (arrivano dal thread di OkHttp) ---------- */

    override fun onConnected() {
        scope.launch {
            attempt = 0
            setStatus("Connesso · notifiche in tempo reale")
            // Recupera quello che è arrivato mentre eravamo disconnessi.
            runCatching { Notifier.deliver(this@PushService, app.api.notifications(), live = false) }
        }
    }

    override fun onDisconnected(reason: String) {
        scope.launch { scheduleReconnect(reason) }
    }

    override fun onEvent(name: String, args: List<JsonElement>) {
        when (name) {
            "event:new_notification" -> {
                val obj = args.firstOrNull() as? JsonObject ?: return
                val item = NotificationItem.from(obj) ?: return
                withWakeLock {
                    scope.launch {
                        runCatching { Notifier.deliver(this@PushService, listOf(item), live = true) }
                    }
                }
            }
            "checkSession" -> {
                // NodeBB comunica l'uid della sessione del socket: 0 vuol dire non autenticato.
                val uid = (args.firstOrNull() as? JsonPrimitive)?.content
                if (uid == "0" && app.prefs.me.value != null) scope.launch { sessionExpired() }
            }
            "event:logout" -> scope.launch { sessionExpired() }
        }
    }

    private inline fun withWakeLock(block: () -> Unit) {
        val pm = getSystemService(PowerManager::class.java)
        val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "citiverse:notification")
        wl?.acquire(10_000)
        block()
    }

    /* ---------- rete e allarme di controllo ---------- */

    private fun registerNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch {
                    // Rete tornata: niente attesa, si riconnette subito (se non lo sta già facendo).
                    if (!socket.isConnected && SystemClock.elapsedRealtime() - lastAttemptAt > 5_000) {
                        attempt = 0
                        connectNow()
                    }
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    private fun watchdogIntent(): PendingIntent =
        PendingIntent.getForegroundService(
            this, 10,
            Intent(this, PushService::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun scheduleWatchdog() {
        val am = getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + WATCHDOG_MS,
            watchdogIntent(),
        )
    }

    override fun onDestroy() {
        running = false
        _status.value = "Non attivo"
        socket.close()
        scope.cancel()
        networkCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        }
        getSystemService(AlarmManager::class.java)?.cancel(watchdogIntent())
        super.onDestroy()
    }
}
