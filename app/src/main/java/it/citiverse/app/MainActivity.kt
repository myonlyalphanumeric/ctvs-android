package it.citiverse.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import it.citiverse.app.data.ApiException
import it.citiverse.app.push.Notifier
import it.citiverse.app.push.PushController
import it.citiverse.app.ui.AppRoot
import it.citiverse.app.ui.LinkRouter
import it.citiverse.app.ui.theme.CitiverseTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "MainActivity"
    }

    private val pendingRoute = MutableStateFlow<String?>(null)
    private val app get() = application as CitiverseApp

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val theme by app.prefs.theme.collectAsState()
            val pending by pendingRoute.collectAsState()
            CitiverseTheme(theme) {
                AppRoot(pending) { pendingRoute.value = null }
            }
        }
        checkSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshUnread()
    }

    /** Tocco su una notifica o link a citiverse.it aperto con l'app. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val nid = intent.getStringExtra(Notifier.EXTRA_NID)
        if (nid != null) {
            markRead(nid)
            Notifier.cancel(this, nid)
            val route = LinkRouter.forNotification(
                intent.getStringExtra(Notifier.EXTRA_PATH),
                intent.getStringExtra(Notifier.EXTRA_TID),
                intent.getStringExtra(Notifier.EXTRA_PID),
            )
            if (route != null) pendingRoute.value = route
            return
        }
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.toString()?.let { LinkRouter.route(it) }?.let { pendingRoute.value = it }
        }
    }

    /** Notifica aperta dal sistema: la segna letta sul server, senza bloccare l'apertura. */
    private fun markRead(nid: String) {
        if (app.prefs.me.value == null) return
        lifecycleScope.launch {
            try {
                app.api.markNotificationRead(nid)
                refreshUnread()
            } catch (e: ApiException) {
                // 404: notifica già cancellata sul server. Negli altri casi resta non letta.
                Log.w(TAG, "notifica $nid non segnata come letta (${e.code})")
            }
        }
    }

    /** All'avvio controlla che la sessione sia ancora valida e riaccende le notifiche. */
    private fun checkSession() {
        lifecycleScope.launch {
            if (app.prefs.reloginNotice) showReloginNotice()
            val me = app.prefs.me.value
            if (me != null) {
                try {
                    val info = app.api.config()
                    // Sessione valida solo se il server riconosce proprio l'utente salvato.
                    if (!info.loggedIn || info.uid != me.uid) {
                        app.endSession()
                        Toast.makeText(this@MainActivity, "Sessione scaduta: accedi di nuovo", Toast.LENGTH_LONG).show()
                    }
                } catch (_: ApiException) {
                    // offline: teniamo la sessione e riproviamo al prossimo avvio
                }
            }
            PushController.apply(this@MainActivity)
        }
    }

    /** Una volta sola, dopo l'aggiornamento che ha cancellato i cookie salvati col vecchio formato. */
    private fun showReloginNotice() {
        app.prefs.reloginNotice = false
        Notifier.cancelLoginNotice(this)
        AlertDialog.Builder(this)
            .setTitle("Accedi di nuovo")
            .setMessage(
                "Abbiamo migliorato il modo in cui l'app conserva l'accesso a Citiverse. " +
                    "Per sicurezza i dati della sessione precedente sono stati cancellati: " +
                    "accedi di nuovo per continuare a usare il tuo account e ricevere le notifiche."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun refreshUnread() {
        if (app.prefs.me.value == null) return
        lifecycleScope.launch {
            runCatching { app.api.notifications() }.onSuccess { list ->
                app.unreadCount.value = list.count { !it.read }
            }
        }
    }
}
