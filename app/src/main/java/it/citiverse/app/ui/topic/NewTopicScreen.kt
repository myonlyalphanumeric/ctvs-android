package it.citiverse.app.ui.topic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.citiverse.app.CitiverseApp
import it.citiverse.app.ui.components.MarkdownField
import it.citiverse.app.ui.components.MarkdownToolbar
import it.citiverse.app.ui.components.toast
import it.citiverse.app.ui.theme.Cv
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewTopicScreen(cid: String, openTopic: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CitiverseApp
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    // TextFieldValue: servono anche la selezione e il cursore per i pulsanti di formattazione.
    var body by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var busy by remember { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val tid = app.api.newTopic(cid, title.trim(), body.text.trim())
                openTopic(tid)
            } catch (e: Exception) {
                toast(ctx, e.message ?: "Pubblicazione non riuscita")
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = Cv.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Nuova discussione") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.Filled.Close, "Chiudi") } },
                actions = {
                    Button(
                        onClick = { submit() },
                        enabled = !busy && title.isNotBlank() && body.text.isNotBlank(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else Text("Pubblica")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cv.colors.bg),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Lo spazio in basso già dato dallo Scaffold non va contato due volte con la tastiera.
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titolo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MarkdownField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = "Scrivi qui il testo…",
                    modifier = Modifier.fillMaxWidth(),
                    onFocusChange = { bodyFocused = it },
                )
                Text(
                    "Seleziona del testo e usa i pulsanti sopra la tastiera per formattarlo. " +
                        "Chi conosce il Markdown può anche scriverlo a mano.",
                    color = Cv.colors.muted,
                    fontSize = 13.sp,
                )
            }
            // La barra dei formati sta sopra la tastiera, lontano dal menu Copia/Incolla,
            // e compare solo mentre si scrive il testo.
            AnimatedVisibility(
                visible = bodyFocused,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                MarkdownToolbar(value = body, onValueChange = { body = it })
            }
        }
    }
}
