package it.citiverse.app.ui.login

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.citiverse.app.CitiverseApp
import it.citiverse.app.R
import it.citiverse.app.data.BASE_URL
import it.citiverse.app.data.LoginResult
import it.citiverse.app.data.Me
import it.citiverse.app.push.PushController
import it.citiverse.app.ui.components.openExternal
import it.citiverse.app.ui.theme.Brand
import it.citiverse.app.ui.theme.Cv
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(done: () -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CitiverseApp
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Secondo passo per gli account con verifica in due passaggi
    var needsCode by rememberSaveable { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var useBackup by remember { mutableStateOf(false) }

    // Dopo il login chiediamo il permesso per le notifiche (Android 13+), poi si torna indietro.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        PushController.apply(ctx)
        done()
    }

    fun loggedIn(me: Me) {
        app.resetUserData()
        app.prefs.setMe(me)
        password = ""
        code = ""
        if (Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PushController.apply(ctx)
            done()
        }
    }

    fun submit() {
        if (busy || username.isBlank() || password.isEmpty()) return
        busy = true
        error = null
        scope.launch {
            try {
                when (val r = app.api.login(username.trim(), password)) {
                    is LoginResult.Done -> loggedIn(r.me)
                    LoginResult.SecondFactor -> {
                        needsCode = true
                        password = ""
                    }
                }
            } catch (e: Exception) {
                error = e.message ?: "Accesso non riuscito"
            } finally {
                busy = false
            }
        }
    }

    fun submitCode() {
        if (busy || code.isBlank()) return
        busy = true
        error = null
        scope.launch {
            try {
                loggedIn(app.api.submitSecondFactor(code, useBackup))
            } catch (e: Exception) {
                code = ""
                error = e.message ?: "Codice non valido"
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = Cv.colors.bg,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // painterResource non legge le icone adattive: usiamo il primo piano su sfondo del marchio
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)).background(Brand),
            )
            if (needsCode) {
                Text("Verifica in due passaggi", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Cv.colors.text)
                Text(
                    if (useBackup) "Inserisci uno dei codici di backup che hai salvato quando hai attivato la verifica."
                    else "Apri l'app di autenticazione (Aegis, FreeOTP, Google Authenticator…) e inserisci il codice a 6 cifre di Citiverse.",
                    color = Cv.colors.muted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = if (useBackup) v.trim() else v.filter { it.isDigit() }.take(6)
                        // con 6 cifre il codice TOTP parte da solo
                        if (!useBackup && code.length == 6) submitCode()
                    },
                    label = { Text(if (useBackup) "Codice di backup" else "Codice a 6 cifre") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 22.sp, letterSpacing = 4.sp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (useBackup) KeyboardType.Ascii else KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submitCode() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                Button(
                    onClick = { submitCode() },
                    enabled = !busy && code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                ) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    else Text("Verifica", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = {
                    useBackup = !useBackup
                    code = ""
                    error = null
                }) {
                    Text(if (useBackup) "Usa il codice dell'app di autenticazione" else "Non hai il telefono? Usa un codice di backup")
                }
                TextButton(onClick = {
                    needsCode = false
                    code = ""
                    error = null
                    scope.launch { app.api.logout() }
                }) { Text("Annulla e torna al login") }
                Text(
                    "Le chiavi di sicurezza (passkey/WebAuthn) per ora non sono supportate dall'app.",
                    color = Cv.colors.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                )
            } else {
                Text("Accedi a Citiverse", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Cv.colors.text)
                Text(
                    "La città dei social liberi. Usa lo stesso account del sito.",
                    color = Cv.colors.muted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nome utente o email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Nascondi" else "Mostra") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                Button(
                    onClick = { submit() },
                    enabled = !busy && username.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                ) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    else Text("Accedi", fontWeight = FontWeight.SemiBold)
                }
            }
            TextButton(onClick = { openExternal(ctx, "$BASE_URL/register") }) {
                Text("Non hai un account? Registrati sul sito")
            }
            TextButton(onClick = { openExternal(ctx, "$BASE_URL/reset") }) {
                Text("Password dimenticata?")
            }
        }
    }
}
