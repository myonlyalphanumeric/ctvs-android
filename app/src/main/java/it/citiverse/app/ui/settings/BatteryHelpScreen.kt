package it.citiverse.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.citiverse.app.push.PushController
import it.citiverse.app.ui.components.openExternal
import it.citiverse.app.ui.theme.Cv

private data class Vendor(
    val key: String,
    val name: String,
    val matches: List<String>,
    val steps: List<String>,
    val components: List<ComponentName>,
    val dontKill: String,
)

private val VENDORS = listOf(
    Vendor(
        "xiaomi", "Xiaomi, Redmi, POCO (MIUI / HyperOS)",
        listOf("xiaomi", "redmi", "poco"),
        listOf(
            "Impostazioni → App → Gestisci app → Citiverse → attiva «Avvio automatico».",
            "Nella stessa pagina: Risparmio batteria → «Nessuna restrizione».",
            "Apri le app recenti, tieni premuta la scheda di Citiverse e tocca il lucchetto per bloccarla in memoria.",
            "Sicurezza → Batteria → disattiva le ottimizzazioni aggressive se presenti.",
        ),
        listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
        ),
        "xiaomi",
    ),
    Vendor(
        "samsung", "Samsung (One UI)",
        listOf("samsung"),
        listOf(
            "Impostazioni → Assistenza dispositivo e batteria → Batteria → Limiti utilizzo in background.",
            "Disattiva «Sospendi app non utilizzate» oppure aggiungi Citiverse ad «App mai in sospensione».",
            "Impostazioni → App → Citiverse → Batteria → «Senza restrizioni».",
        ),
        listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        ),
        "samsung",
    ),
    Vendor(
        "huawei", "Huawei e Honor (EMUI / MagicOS)",
        listOf("huawei", "honor"),
        listOf(
            "Impostazioni → Batteria → Avvio app → Citiverse: disattiva «Gestisci automaticamente».",
            "Nella finestra che si apre lascia attivi Avvio automatico, Avvio secondario ed Esegui in background.",
            "Blocca Citiverse nelle app recenti (scorri giù sulla scheda o tocca il lucchetto).",
        ),
        listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ),
        "huawei",
    ),
    Vendor(
        "oppo", "OPPO, Realme, OnePlus (ColorOS / OxygenOS)",
        listOf("oppo", "realme", "oneplus"),
        listOf(
            "Impostazioni → Batteria → Altre impostazioni → Ottimizza utilizzo batteria → Citiverse → «Non ottimizzare».",
            "Impostazioni → App → Citiverse → Utilizzo batteria → consenti attività in background e avvio automatico.",
            "Blocca l'app nelle app recenti.",
        ),
        listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ),
        "oneplus",
    ),
    Vendor(
        "vivo", "vivo e iQOO (Funtouch / OriginOS)",
        listOf("vivo", "iqoo"),
        listOf(
            "Impostazioni → Batteria → Consumo energetico in background elevato → attiva Citiverse.",
            "i Manager → Gestione app → Avvio automatico → attiva Citiverse.",
        ),
        listOf(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        "vivo",
    ),
)

private fun tryOpen(ctx: Context, components: List<ComponentName>): Boolean {
    for (cn in components) {
        val ok = runCatching {
            ctx.startActivity(Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (ok) return true
    }
    return false
}

private fun openAppDetails(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BatteryHelpScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val c = Cv.colors
    val maker = Build.MANUFACTURER.lowercase()
    // il produttore di questo telefono va in cima
    val vendors = remember { VENDORS.sortedBy { v -> if (v.matches.any { maker.contains(it) }) 0 else 1 } }
    val batteryOk = PushController.isIgnoringBatteryOptimizations(ctx)

    Scaffold(
        containerColor = c.bg,
        topBar = {
            TopAppBar(
                title = { Text("Notifiche affidabili") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Senza Google Play Services l'app tiene aperta una connessione con Citiverse. " +
                    "Molti produttori chiudono le app in background per risparmiare batteria, e così le notifiche smettono di arrivare. " +
                    "Questi passaggi lo evitano. In alternativa puoi scegliere «Ogni 15 minuti» nelle impostazioni.",
                color = c.muted, fontSize = 14.sp, lineHeight = 20.sp,
            )
            Step(
                "1. Ottimizzazione batteria di Android",
                if (batteryOk) "Fatto: l'app è già esclusa." else "Vale per tutti i telefoni: esclude l'app dal risparmio energetico (Doze).",
            ) {
                if (!batteryOk) Button(onClick = { requestIgnoreBattery(ctx) }, shape = CircleShape) { Text("Escludi l'app") }
            }
            Step(
                "2. Notifica fissa",
                "La notifica «Notifiche Citiverse attive» serve a tenere viva la connessione. Puoi nasconderla disattivando solo il canale «Connessione in background».",
            ) {
                OutlinedButton(onClick = { openNotificationSettings(ctx) }, shape = CircleShape) { Text("Canali di notifica") }
            }
            vendors.forEach { v ->
                val mine = v.matches.any { maker.contains(it) }
                Step(
                    (if (mine) "3. Il tuo telefono: " else "") + v.name,
                    null,
                    highlight = mine,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        v.steps.forEach { s ->
                            Row {
                                Text("•", color = c.muted, modifier = Modifier.width(16.dp))
                                Text(s, color = c.text, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { if (!tryOpen(ctx, v.components)) openAppDetails(ctx) },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) { Text("Apri impostazioni") }
                            OutlinedButton(
                                onClick = { openExternal(ctx, "https://dontkillmyapp.com/${v.dontKill}") },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 14.dp),
                            ) { Text("Guida completa") }
                        }
                    }
                }
            }
            Text(
                "Altri produttori: cerca il tuo telefono su dontkillmyapp.com.",
                color = c.muted, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun Step(title: String, text: String?, highlight: Boolean = false, content: @Composable () -> Unit) {
    val c = Cv.colors
    val shape = RoundedCornerShape(16.dp)
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(if (highlight) 2.dp else 1.dp, if (highlight) primary else c.border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, color = c.text, fontSize = 16.sp)
        if (text != null) Text(text, color = c.muted, fontSize = 14.sp, lineHeight = 20.sp)
        content()
    }
}
