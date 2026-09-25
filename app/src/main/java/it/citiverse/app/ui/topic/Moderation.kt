package it.citiverse.app.ui.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.citiverse.app.data.Post
import it.citiverse.app.data.UserRef
import it.citiverse.app.ui.components.ActionPill
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons

/** Cosa si sta segnalando ai moderatori. */
data class ReportTarget(val type: String, val id: String, val label: String)

/**
 * Menu "⋯" di un post: segnala il post, segnala l'autore, blocca l'autore.
 * Richiesto da Google Play per le app con contenuti degli utenti.
 */
@Composable
fun PostMenu(
    post: Post,
    isMine: Boolean,
    onReportPost: () -> Unit,
    onReportUser: () -> Unit,
    onBlockUser: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val name = shortName(post.user)
    Box {
        ActionPill(CvIcons.More, null, "Altre azioni", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Segnala post") }, onClick = { open = false; onReportPost() })
            if (!isMine) {
                DropdownMenuItem(text = { Text("Segnala $name") }, onClick = { open = false; onReportUser() })
                DropdownMenuItem(
                    text = { Text("Blocca $name", color = MaterialTheme.colorScheme.error) },
                    onClick = { open = false; onBlockUser() },
                )
            }
        }
    }
}

fun shortName(u: UserRef): String = u.displayName.substringBefore('@').ifBlank { u.username }

private val REASONS = listOf(
    "Spam o pubblicità",
    "Offensivo o molesto",
    "Contenuto inappropriato o illegale",
    "Disinformazione",
    "Altro",
)

/** Scelta del motivo della segnalazione (più un commento facoltativo). */
@Composable
fun ReportDialog(target: ReportTarget, busy: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var choice by remember { mutableStateOf(REASONS.first()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (target.type == "post") "Segnala post" else "Segnala ${target.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "La segnalazione arriva ai moderatori di Citiverse. Chi hai segnalato non saprà che sei stato tu.",
                    color = Cv.colors.muted, fontSize = 14.sp,
                )
                Column(Modifier.selectableGroup().padding(top = 6.dp)) {
                    REASONS.forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.RadioButton) { choice = r }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == r, onClick = null)
                            Text(r, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(250) },
                    label = { Text(if (choice == "Altro") "Spiega il motivo" else "Dettagli (facoltativo)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (choice != "Altro" || note.isNotBlank()),
                onClick = { onSend(if (note.isBlank()) choice else "$choice: ${note.trim()}") },
            ) { Text(if (busy) "Invio…" else "Invia segnalazione") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Annulla") } },
    )
}

/** Conferma prima di bloccare un utente. */
@Composable
fun BlockDialog(user: UserRef, busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val name = shortName(user)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Bloccare $name?") },
        text = {
            Text(
                "Non vedrai più i suoi post e non potrà scriverti in chat. Vale anche sul sito. " +
                    "Puoi sbloccarlo quando vuoi dalla pagina del suo profilo su citiverse.it.",
            )
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onConfirm) {
                Text(if (busy) "Blocco…" else "Blocca", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Annulla") } },
    )
}
