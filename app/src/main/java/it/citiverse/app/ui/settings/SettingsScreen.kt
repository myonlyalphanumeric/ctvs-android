package it.citiverse.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import it.citiverse.app.BuildConfig
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.BASE_URL
import it.citiverse.app.data.NotifMode
import it.citiverse.app.data.ThemeMode
import it.citiverse.app.push.Notifier
import it.citiverse.app.push.PushController
import it.citiverse.app.push.PushService
import it.citiverse.app.ui.Routes
import it.citiverse.app.ui.components.AvatarRaw
import it.citiverse.app.ui.components.openExternal
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons
import it.citiverse.app.ui.theme.PinGreen
import kotlinx.coroutines.launch

/**
 * Porta l'utente dove si toglie l'ottimizzazione della batteria. Senza il permesso
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (Google Play lo concede solo a poche categorie
 * di app) si apre la pagina dell'app nelle impostazioni, con un suggerimento su dove toccare.
 */
fun requestIgnoreBattery(ctx: Context) {
    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { ctx.startActivity(details) }.isSuccess ||
        runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (ok) {
        android.widget.Toast.makeText(
            ctx,
            "Tocca \"Batteria\" e scegli \"Senza limitazioni\" (o \"Non ottimizzare\")",
            android.widget.Toast.LENGTH_LONG,
        ).show()
    }
}

fun openNotificationSettings(ctx: Context) {
    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
    runCatching { ctx.startActivity(i) }
}

/** Si aggiorna quando si torna nell'app (per esempio dopo aver cambiato un permesso). */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@Composable
fun SettingsScreen(navigate: (String) -> Unit, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CitiverseApp
    val me by app.prefs.me.collectAsState()
    val mode by app.prefs.notifMode.collectAsState()
    val theme by app.prefs.theme.collectAsState()
    val liveStatus by PushService.status.collectAsState()
    val tick = rememberResumeTick()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }

    val batteryOk = remember(tick) { PushController.isIgnoringBatteryOptimizations(ctx) }
    val notifOk = remember(tick) { Notifier.canNotify(ctx) }
    val status = remember(tick, liveStatus, mode) {
        if (mode == NotifMode.REALTIME && PushService.running) liveStatus else app.prefs.pushStatus
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val c = Cv.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 16.dp)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Impostazioni",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = c.text,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
        )

        // ---------- account ----------
        Card {
            val user = me
            if (user == null) {
                Text("Non hai effettuato l'accesso.", color = c.muted)
                Spacer(Modifier.size(8.dp))
                Button(onClick = { navigate(Routes.LOGIN) }, shape = CircleShape) { Text("Accedi") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarRaw(user.picture, user.iconText, user.iconBg, 48.dp)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.text)
                        Text("@${user.username}", color = c.muted, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = { confirmLogout = true }, shape = CircleShape) { Text("Esci") }
                }
            }
        }

        // ---------- notifiche ----------
        Card {
            CardTitle(CvIcons.Bell, "Notifiche")
            Text(
                "Le notifiche arrivano direttamente dal server di Citiverse (WebSocket), senza Google Play Services.",
                color = c.muted, fontSize = 13.sp, lineHeight = 18.sp,
            )
            Spacer(Modifier.size(6.dp))
            ModeOption(
                selected = mode == NotifMode.REALTIME,
                title = "In tempo reale",
                text = "Connessione sempre attiva con una notifica fissa silenziosa, come Signal o Molly senza Google.",
            ) { app.prefs.setNotifMode(NotifMode.REALTIME); PushController.apply(ctx) }
            ModeOption(
                selected = mode == NotifMode.PERIODIC,
                title = "Ogni 15 minuti",
                text = "Basso consumo: niente connessione fissa, le notifiche arrivano con qualche minuto di ritardo.",
            ) { app.prefs.setNotifMode(NotifMode.PERIODIC); PushController.apply(ctx) }
            ModeOption(
                selected = mode == NotifMode.OFF,
                title = "Disattivate",
                text = "Nessuna notifica sul telefono: le trovi comunque nella scheda Notifiche.",
            ) { app.prefs.setNotifMode(NotifMode.OFF); PushController.apply(ctx) }

            if (me != null && mode != NotifMode.OFF && status.isNotBlank()) {
                Text(
                    "Stato: $status",
                    color = c.muted, fontSize = 12.5.sp,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.pill)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            HorizontalDivider(color = c.border, modifier = Modifier.padding(vertical = 10.dp))

            if (!notifOk && Build.VERSION.SDK_INT >= 33) {
                CheckRow(
                    ok = false,
                    title = "Permesso notifiche",
                    text = "Android non permette ancora all'app di mostrare notifiche.",
                    action = "Consenti",
                ) { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            CheckRow(
                ok = batteryOk,
                title = "Ottimizzazione batteria",
                text = if (batteryOk) "L'app è esclusa: la connessione può restare attiva anche a schermo spento."
                else "Android può chiudere la connessione per risparmiare batteria. Escludi l'app per ricevere le notifiche puntuali.",
                action = if (batteryOk) null else "Escludi",
            ) { requestIgnoreBattery(ctx) }
            LinkRow(CvIcons.Help, "Il telefono chiude comunque l'app?", "Istruzioni per Xiaomi, Samsung, Huawei e altri") {
                navigate(Routes.BATTERY)
            }
            LinkRow(CvIcons.Bell, "Canali di notifica", "Scegli suoni e importanza per risposte, menzioni, voti…") {
                openNotificationSettings(ctx)
            }
        }

        // ---------- aspetto ----------
        Card {
            CardTitle(null, "Aspetto")
            ThemeMode.entries.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .selectable(selected = theme == t, role = Role.RadioButton) { app.prefs.setTheme(t) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = theme == t, onClick = null, modifier = Modifier.padding(8.dp))
                    Text(t.label, color = c.text)
                }
            }
        }

        // ---------- info ----------
        Card {
            CardTitle(null, "Informazioni")
            LinkRow(CvIcons.External, "Apri citiverse.it", "La città dei social liberi") { openExternal(ctx, BASE_URL) }
            Text(
                "Versione ${BuildConfig.VERSION_NAME} · app non ufficiale basata sulle API di NodeBB",
                color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Uscire dall'account?") },
            text = { Text("Le notifiche verranno interrotte finché non accedi di nuovo.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    scope.launch {
                        app.api.logout()
                        app.prefs.setMe(null)
                        app.resetUserData()
                        PushController.apply(ctx)
                    }
                }) { Text("Esci") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = Cv.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun CardTitle(icon: ImageVector?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Cv.colors.text)
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, text: String, onClick: () -> Unit) {
    val c = Cv.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.07f) else c.card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Column(Modifier.weight(1f).padding(top = 6.dp, end = 6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(text, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun CheckRow(ok: Boolean, title: String, text: String, action: String?, onAction: () -> Unit) {
    val c = Cv.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) CvIcons.Check else CvIcons.Battery, null,
            tint = if (ok) PinGreen else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(text, color = c.muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (action != null) {
            Spacer(Modifier.size(8.dp))
            Button(onClick = onAction, shape = CircleShape, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(action) }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    val c = Cv.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.muted, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(text, color = c.muted, fontSize = 13.sp)
        }
        Text("›", color = c.muted, fontSize = 22.sp)
    }
}
