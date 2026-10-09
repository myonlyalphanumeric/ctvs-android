package it.citiverse.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotifMode(val key: String) {
    /** Connessione WebSocket sempre attiva con servizio in primo piano. */
    REALTIME("realtime"),

    /** Controllo ogni 15 minuti con WorkManager: consuma meno, ma arriva in ritardo. */
    PERIODIC("periodic"),
    OFF("off");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: REALTIME
    }
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "Come il sistema"),
    LIGHT("light", "Chiaro"),
    DARK("dark", "Scuro");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Impostazioni e dati dell'utente collegato (preferenze private dell'app). */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _me = MutableStateFlow(loadMe())
    val me: StateFlow<Me?> = _me.asStateFlow()

    private val _theme = MutableStateFlow(ThemeMode.of(p.getString("theme", null)))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _notifMode = MutableStateFlow(NotifMode.of(p.getString("notif_mode", null)))
    val notifMode: StateFlow<NotifMode> = _notifMode.asStateFlow()

    private fun loadMe(): Me? {
        val uid = p.getString("me_uid", null) ?: return null
        return Me(
            uid = uid,
            username = p.getString("me_username", "") ?: "",
            userslug = p.getString("me_userslug", "") ?: "",
            displayName = p.getString("me_display", "") ?: "",
            picture = p.getString("me_picture", null),
            iconText = p.getString("me_icon_text", "?") ?: "?",
            iconBg = p.getString("me_icon_bg", null),
        )
    }

    fun setMe(me: Me?) {
        val e = p.edit()
        if (me == null) {
            listOf("me_uid", "me_username", "me_userslug", "me_display", "me_picture", "me_icon_text", "me_icon_bg")
                .forEach { e.remove(it) }
        } else {
            e.putString("me_uid", me.uid)
                .putString("me_username", me.username)
                .putString("me_userslug", me.userslug)
                .putString("me_display", me.displayName)
                .putString("me_picture", me.picture)
                .putString("me_icon_text", me.iconText)
                .putString("me_icon_bg", me.iconBg)
        }
        e.apply()
        _me.value = me
    }

    fun setTheme(t: ThemeMode) {
        p.edit().putString("theme", t.key).apply()
        _theme.value = t
    }

    fun setNotifMode(m: NotifMode) {
        p.edit().putString("notif_mode", m.key).apply()
        _notifMode.value = m
    }

    /** Le notifiche più vecchie di questo istante non vengono mostrate (evita raffiche di vecchi avvisi). */
    var notifWatermark: Long
        get() = p.getLong("notif_watermark", 0L)
        set(v) = p.edit().putLong("notif_watermark", v).apply()

    /** Ultimi nid già mostrati, per non mostrare due volte la stessa notifica (socket + controllo periodico). */
    fun wasShown(nid: String): Boolean = shownSet().contains(nid)

    @Synchronized
    fun markShown(nid: String) {
        val list = (p.getString("shown_nids", "") ?: "").split('\n').filter { it.isNotEmpty() }.toMutableList()
        if (list.contains(nid)) return
        list.add(nid)
        val trimmed = list.takeLast(300)
        p.edit().putString("shown_nids", trimmed.joinToString("\n")).apply()
    }

    private fun shownSet(): Set<String> =
        (p.getString("shown_nids", "") ?: "").split('\n').filter { it.isNotEmpty() }.toSet()

    var askedBatteryOnce: Boolean
        get() = p.getBoolean("asked_battery", false)
        set(v) = p.edit().putBoolean("asked_battery", v).apply()

    /** Stato leggibile del servizio, mostrato nelle impostazioni. */
    var pushStatus: String
        get() = p.getString("push_status", "") ?: ""
        set(v) = p.edit().putString("push_status", v).apply()

    /** Da mostrare all'apertura: dopo l'aggiornamento della gestione dei cookie serve un nuovo accesso. */
    var reloginNotice: Boolean
        get() = p.getBoolean("relogin_notice", false)
        set(v) = p.edit().putBoolean("relogin_notice", v).apply()

    var lastPollAt: Long
        get() = p.getLong("last_poll", 0L)
        set(v) = p.edit().putLong("last_poll", v).apply()
}
