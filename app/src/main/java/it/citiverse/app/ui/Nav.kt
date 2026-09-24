package it.citiverse.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.SITE_HOST

object Routes {
    const val HOME = "home"
    const val COMMUNITIES = "communities"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val BATTERY = "battery"

    const val CATEGORY = "category/{id}"
    const val TOPIC = "topic?tid={tid}&pid={pid}"
    const val NEW_TOPIC = "newtopic/{cid}"

    fun category(id: String) = "category/${Uri.encode(id)}"
    fun topic(tid: String?, pid: String? = null): String {
        val q = buildList {
            if (tid != null) add("tid=${Uri.encode(tid)}")
            if (pid != null) add("pid=${Uri.encode(pid)}")
        }
        return "topic?" + q.joinToString("&")
    }
    fun newTopic(cid: String) = "newtopic/${Uri.encode(cid)}"

    val TABS = setOf(HOME, COMMUNITIES, NOTIFICATIONS, SETTINGS)
}

/**
 * Trasforma un link del forum in una schermata dell'app:
 * /topic/…, /category/…, /post/…; null se va aperto nel browser.
 */
object LinkRouter {
    fun route(url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host
        if (host != null && host != SITE_HOST && host != "www.$SITE_HOST") return null
        val segs = uri.pathSegments ?: return null
        if (segs.size < 2) return null
        return when (segs[0]) {
            "topic" -> Routes.topic(segs[1])
            "category" -> Routes.category(segs[1])
            "post" -> Routes.topic(null, segs[1])
            else -> null
        }
    }

    /** Percorsi delle notifiche ("/post/123", "/topic/…") con eventuali tid/pid già noti. */
    fun forNotification(path: String?, tid: String?, pid: String?): String? {
        if (tid != null) return Routes.topic(tid, pid)
        if (pid != null) return Routes.topic(null, pid)
        if (path.isNullOrBlank()) return null
        val full = if (path.startsWith("/")) "https://$SITE_HOST$path" else path
        return route(full)
    }
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (CitiverseApp) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as CitiverseApp
    return viewModel(key = key, factory = viewModelFactory { initializer { create(app) } })
}
