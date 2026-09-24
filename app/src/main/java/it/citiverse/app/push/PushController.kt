package it.citiverse.app.push

import android.content.Context
import android.os.PowerManager
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.NotifMode

/** Accende il meccanismo giusto (servizio WebSocket, controllo periodico o niente). */
object PushController {

    fun apply(ctx: Context) {
        val app = ctx.applicationContext as CitiverseApp
        val loggedIn = app.prefs.me.value != null
        val mode = app.prefs.notifMode.value
        if (app.prefs.notifWatermark == 0L) app.prefs.notifWatermark = System.currentTimeMillis()
        when {
            !loggedIn || mode == NotifMode.OFF -> {
                PushService.stop(ctx)
                PollWorker.cancel(ctx)
                app.prefs.pushStatus = if (!loggedIn) "Accedi per ricevere le notifiche" else "Notifiche disattivate"
            }
            mode == NotifMode.REALTIME -> {
                PollWorker.cancel(ctx)
                runCatching { PushService.start(ctx) }
                    .onFailure { app.prefs.pushStatus = "Impossibile avviare il servizio: ${it.message}" }
            }
            else -> {
                PushService.stop(ctx)
                PollWorker.schedule(ctx)
                app.prefs.pushStatus = "Controllo ogni 15 minuti"
            }
        }
    }

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }
}
