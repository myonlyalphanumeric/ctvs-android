package it.citiverse.app.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.citiverse.app.CitiverseApp
import it.citiverse.app.MainActivity
import it.citiverse.app.R
import it.citiverse.app.data.JsonParser
import it.citiverse.app.data.NotificationItem
import it.citiverse.app.data.NodeBBApi
import it.citiverse.app.data.str
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/** Canali separati: l'utente può silenziare i voti e tenere le menzioni, ecc. */
object Notifier {
    const val CH_SERVICE = "service"
    const val CH_REPLIES = "replies"
    const val CH_MENTIONS = "mentions"
    const val CH_VOTES = "votes"
    const val CH_CHAT = "chat"
    const val CH_FOLLOWS = "follows"
    const val CH_OTHER = "other"

    const val SERVICE_ID = 1
    private const val SESSION_ID = 2

    const val EXTRA_TID = "tid"
    const val EXTRA_PID = "pid"
    const val EXTRA_PATH = "path"
    const val EXTRA_NID = "nid"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(NotificationChannelGroup("activity", "Attività"))
        fun ch(id: String, name: String, desc: String, importance: Int, group: String? = "activity") =
            NotificationChannel(id, name, importance).apply {
                description = desc
                group?.let { this.group = it }
            }
        nm.createNotificationChannels(
            listOf(
                ch(CH_REPLIES, "Risposte", "Risposte alle discussioni che segui e nuove discussioni", NotificationManager.IMPORTANCE_DEFAULT),
                ch(CH_MENTIONS, "Menzioni", "Quando qualcuno ti menziona con @", NotificationManager.IMPORTANCE_HIGH),
                ch(CH_VOTES, "Voti", "Voti positivi ai tuoi post", NotificationManager.IMPORTANCE_LOW),
                ch(CH_CHAT, "Chat", "Messaggi in chat", NotificationManager.IMPORTANCE_HIGH),
                ch(CH_FOLLOWS, "Nuovi follower", "Quando qualcuno inizia a seguirti", NotificationManager.IMPORTANCE_LOW),
                ch(CH_OTHER, "Altre notifiche", "Avvisi del forum e dell'app", NotificationManager.IMPORTANCE_DEFAULT),
                ch(
                    CH_SERVICE, "Connessione in background",
                    "Notifica fissa e silenziosa necessaria per restare connessi senza servizi Google. Puoi nasconderla da qui.",
                    NotificationManager.IMPORTANCE_MIN, group = null,
                ).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        )
    }

    fun channelFor(type: String): String = when {
        type.contains("chat") -> CH_CHAT
        type.contains("mention") -> CH_MENTIONS
        type.contains("upvote") || type.contains("vote") -> CH_VOTES
        type == "follow" || type.contains("follow") -> CH_FOLLOWS
        type.startsWith("new-reply") || type.startsWith("new-topic") || type.startsWith("post-") -> CH_REPLIES
        else -> CH_OTHER
    }

    fun openIntent(ctx: Context, item: NotificationItem): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            action = "it.citiverse.OPEN_${item.nid}"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NID, item.nid)
            item.tid?.let { putExtra(EXTRA_TID, it) }
            item.pid?.let { putExtra(EXTRA_PID, it) }
            item.path?.let { putExtra(EXTRA_PATH, it) }
        }
        return PendingIntent.getActivity(
            ctx, item.nid.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, item: NotificationItem, strings: Map<String, String>) {
        if (!canNotify(ctx)) return
        val title = NotifText.translate(item.bodyShort, strings).ifBlank { "Nuova notifica" }
        val body = NotifText.translate(item.bodyLong, strings)
        val b = NotificationCompat.Builder(ctx, channelFor(item.type))
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD9230F.toInt())
            .setContentTitle(title)
            .setContentIntent(openIntent(ctx, item))
            .setAutoCancel(true)
            .setCategory(if (item.type.contains("chat")) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_SOCIAL)
            .setWhen(if (item.datetime > 0) item.datetime else System.currentTimeMillis())
            .setShowWhen(true)
        if (body.isNotBlank()) {
            b.setContentText(body)
            b.setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(body))
        }
        NotificationManagerCompat.from(ctx).notify(item.nid.hashCode(), b.build())
    }

    fun cancel(ctx: Context, nid: String) {
        NotificationManagerCompat.from(ctx).cancel(nid.hashCode())
    }

    fun serviceNotification(ctx: Context, status: String): Notification {
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Notifiche Citiverse attive")
            .setContentText(status)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun sessionExpired(ctx: Context) {
        if (!canNotify(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CH_OTHER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Accedi di nuovo a Citiverse")
            .setContentText("La sessione è scaduta: le notifiche sono in pausa finché non accedi.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(SESSION_ID, n)
    }

    /**
     * Mostra le notifiche non lette nuove (dal socket o dal controllo periodico),
     * senza doppioni e senza ripescare quelle vecchie.
     */
    suspend fun deliver(ctx: Context, items: List<NotificationItem>, live: Boolean) {
        val app = ctx.applicationContext as CitiverseApp
        val prefs = app.prefs
        val watermark = prefs.notifWatermark
        val strings = NotifStrings.get(ctx, app.api)
        var newest = watermark
        items.sortedBy { it.datetime }.forEach { item ->
            val fresh = live || item.datetime > watermark
            if (!item.read && fresh && !prefs.wasShown(item.nid)) {
                show(ctx, item, strings)
                prefs.markShown(item.nid)
                if (live) app.unreadCount.value += 1
            }
            if (item.datetime > newest) newest = item.datetime
        }
        if (newest > watermark) prefs.notifWatermark = newest
    }
}

/** File di lingua delle notifiche, scaricato dal forum e tenuto in cache per una settimana. */
object NotifStrings {
    @Volatile private var cache: Map<String, String>? = null
    private const val MAX_AGE = 7L * 24 * 3600 * 1000

    suspend fun get(ctx: Context, api: NodeBBApi): Map<String, String> {
        cache?.let { return it }
        val file = File(ctx.filesDir, "lang_it_notifications.json")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE
        val text = if (fresh) {
            runCatching { file.readText() }.getOrNull()
        } else {
            runCatching { api.languageFile("notifications").toString().also { file.writeText(it) } }.getOrNull()
                ?: runCatching { file.readText() }.getOrNull()
        }
        val map = text?.let { t ->
            runCatching {
                val o = JsonParser.parseToJsonElement(t) as JsonObject
                o.keys.mapNotNull { k -> o.str(k)?.let { v -> "notifications:$k" to v } }.toMap()
            }.getOrNull()
        } ?: emptyMap()
        if (map.isNotEmpty()) cache = map
        return map
    }

    fun nidArg(nid: String) = listOf(JsonPrimitive(nid))
}
