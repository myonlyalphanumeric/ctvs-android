package it.citiverse.app.ui.notifications

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.ApiException
import it.citiverse.app.data.BASE_URL
import it.citiverse.app.data.NotificationItem
import it.citiverse.app.data.ago
import it.citiverse.app.push.NotifStrings
import it.citiverse.app.push.NotifText
import it.citiverse.app.push.Notifier
import it.citiverse.app.ui.LinkRouter
import it.citiverse.app.ui.Routes
import it.citiverse.app.ui.appViewModel
import it.citiverse.app.ui.components.Avatar
import it.citiverse.app.ui.components.EmptyBox
import it.citiverse.app.ui.components.ErrorBox
import it.citiverse.app.ui.components.LoadingBox
import it.citiverse.app.ui.components.LoginPrompt
import it.citiverse.app.ui.components.openExternal
import it.citiverse.app.ui.components.toast
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotifRow(val item: NotificationItem, val title: String, val body: String)

data class NotifUi(
    val rows: List<NotifRow> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val markingAll: Boolean = false,
)

class NotificationsViewModel(private val app: CitiverseApp) : ViewModel() {
    private val _ui = MutableStateFlow(NotifUi())
    val ui: StateFlow<NotifUi> = _ui.asStateFlow()

    fun load(pull: Boolean = false) {
        if (app.prefs.me.value == null) return
        _ui.update { it.copy(loading = !pull && it.rows.isEmpty(), refreshing = pull, error = null) }
        viewModelScope.launch {
            try {
                val strings = NotifStrings.get(app, app.api)
                val items = app.api.notifications()
                _ui.value = NotifUi(
                    rows = items.map { NotifRow(it, NotifText.translate(it.bodyShort, strings), NotifText.translate(it.bodyLong, strings)) },
                    loading = false,
                )
                app.unreadCount.value = items.count { !it.read }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, refreshing = false, error = e.message ?: "Errore") }
            }
        }
    }

    /** "Segna tutte come lette": aggiorna subito la lista, poi lo dice al server. */
    fun markAllRead(onDone: (List<String>) -> Unit, onError: (String) -> Unit) {
        val unread = _ui.value.rows.filter { !it.item.read }.map { it.item.nid }
        if (unread.isEmpty() || _ui.value.markingAll) return
        _ui.update { s -> s.copy(markingAll = true) }
        viewModelScope.launch {
            try {
                app.api.markAllNotificationsRead(unread)
                _ui.update { s -> s.copy(markingAll = false, rows = s.rows.map { it.copy(item = it.item.copy(read = true)) }) }
                app.unreadCount.value = 0
                onDone(unread)
            } catch (e: Exception) {
                _ui.update { s -> s.copy(markingAll = false) }
                onError(e.message ?: "Operazione non riuscita")
            }
        }
    }

    /**
     * Notifica aperta: la segna letta sul server (PUT /api/v3/notifications/{nid}/read) e,
     * solo se il server conferma, anche nella lista. Gli errori non bloccano l'apertura.
     */
    fun markOpened(item: NotificationItem) {
        if (item.read) return
        viewModelScope.launch {
            try {
                app.api.markNotificationRead(item.nid)
            } catch (e: ApiException) {
                // 404: notifica non più presente sul server; negli altri casi resta non letta.
                Log.w("Notifications", "notifica ${item.nid} non segnata come letta (${e.code})")
                return@launch
            }
            var changed = false
            _ui.update { s ->
                s.copy(rows = s.rows.map {
                    if (it.item.nid == item.nid && !it.item.read) {
                        changed = true
                        it.copy(item = it.item.copy(read = true))
                    } else it
                })
            }
            if (changed) app.unreadCount.update { (it - 1).coerceAtLeast(0) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navigate: (String) -> Unit, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CitiverseApp
    val me by app.prefs.me.collectAsState()
    val vm = appViewModel { NotificationsViewModel(it) }
    val ui by vm.ui.collectAsState()

    LaunchedEffect(me?.uid) { vm.load() }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Notifiche",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Cv.colors.text,
                modifier = Modifier.weight(1f),
            )
            val hasUnread = ui.rows.any { !it.item.read }
            AnimatedVisibility(visible = me != null && hasUnread, enter = fadeIn(), exit = fadeOut()) {
                TextButton(
                    enabled = !ui.markingAll,
                    onClick = {
                        vm.markAllRead(
                            onDone = { nids ->
                                nids.forEach { Notifier.cancel(ctx, it) }
                                toast(ctx, "Tutte le notifiche sono segnate come lette")
                            },
                            onError = { toast(ctx, it) },
                        )
                    },
                ) {
                    Icon(CvIcons.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (ui.markingAll) "Un attimo…" else "Segna tutte come lette", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (me == null) {
            LoginPrompt("Accedi per vedere risposte, menzioni e voti ai tuoi post.", onLogin = { navigate(Routes.LOGIN) })
        } else {
            NotificationList(ui, vm, navigate, contentPadding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationList(
    ui: NotifUi,
    vm: NotificationsViewModel,
    navigate: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val ctx = LocalContext.current
    run {
        PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = { vm.load(pull = true) }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    ui.loading -> item { LoadingBox() }
                    ui.error != null && ui.rows.isEmpty() -> item { ErrorBox(ui.error!!, onRetry = { vm.load() }) }
                    ui.rows.isEmpty() -> item { EmptyBox("Non hai nuove notifiche.") }
                }
                items(ui.rows, key = { it.item.nid }) { row ->
                    NotifRowView(row) {
                        val item = row.item
                        vm.markOpened(item)
                        Notifier.cancel(ctx, item.nid)
                        val route = LinkRouter.forNotification(item.path, item.tid, item.pid)
                        when {
                            route != null -> navigate(route)
                            item.path != null -> openExternal(ctx, if (item.path.startsWith("/")) BASE_URL + item.path else item.path)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifRowView(row: NotifRow, onClick: () -> Unit) {
    val c = Cv.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (row.item.read) c.bg else c.card)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box {
            val from = row.item.from
            if (from != null) Avatar(from, 40.dp) else Box(Modifier.size(40.dp).clip(CircleShape).background(c.pill))
            if (!row.item.read) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.title,
                color = c.text,
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                fontWeight = if (row.item.read) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.body.isNotBlank()) {
                Text(row.body, color = c.muted, fontSize = 13.5.sp, lineHeight = 19.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(ago(row.item.datetime), color = c.muted, fontSize = 12.sp)
        }
    }
}
