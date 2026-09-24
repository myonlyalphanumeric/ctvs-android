package it.citiverse.app.ui.topic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.citiverse.app.CitiverseApp
import it.citiverse.app.ui.components.toast
import it.citiverse.app.ui.theme.Cv
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTopicScreen(cid: String, openTopic: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CitiverseApp
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val tid = app.api.newTopic(cid, title.trim(), body.trim())
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
                        enabled = !busy && title.isNotBlank() && body.isNotBlank(),
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
                .imePadding()
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
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Testo (Markdown)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )
            Text(
                "Il testo si formatta con Markdown: **grassetto**, *corsivo*, [link](https://…), > citazione.",
                color = Cv.colors.muted,
            )
        }
    }
}
