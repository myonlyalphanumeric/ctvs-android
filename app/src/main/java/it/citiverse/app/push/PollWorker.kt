package it.citiverse.app.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.ApiException
import java.util.concurrent.TimeUnit

/**
 * Modalità a basso consumo: niente connessione fissa, controlla le notifiche
 * ogni 15 minuti (il minimo consentito da Android per i lavori periodici).
 */
class PollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CitiverseApp
        if (app.prefs.me.value == null) return Result.success()
        return try {
            Notifier.ensureChannels(applicationContext)
            val items = app.api.notifications()
            Notifier.deliver(applicationContext, items, live = false)
            app.prefs.lastPollAt = System.currentTimeMillis()
            app.prefs.pushStatus = "Ultimo controllo riuscito"
            Result.success()
        } catch (e: ApiException) {
            if (e.code == 401 || e.code == 403) {
                app.prefs.pushStatus = "Sessione scaduta: accedi di nuovo"
                Notifier.sessionExpired(applicationContext)
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "citiverse-poll"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
        }
    }
}
