package it.citiverse.app.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.citiverse.app.data.CategoryRef
import it.citiverse.app.data.UserRef
import it.citiverse.app.data.shortNum
import it.citiverse.app.ui.VoteState
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons
import it.citiverse.app.ui.theme.VoteDown
import it.citiverse.app.ui.theme.VoteUp
import it.citiverse.app.ui.theme.parseHexColor

/* ---------- icone di comunità e avatar ---------- */

@Composable
fun CategoryIcon(cat: CategoryRef?, size: Dp, shape: Shape = CircleShape) {
    val bg = parseHexColor(cat?.bgColor, Cv.colors.pill)
    val fg = parseHexColor(cat?.color, Color.White)
    Box(
        modifier = Modifier.size(size).clip(shape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (cat?.backgroundImage != null) {
            AsyncImage(
                model = cat.backgroundImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val icon = faIcon(cat?.icon)
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.55f))
            } else {
                Text(
                    text = cat?.name?.take(1)?.uppercase() ?: "?",
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.45f).sp,
                )
            }
        }
    }
}

/** Poche icone FontAwesome usate dalle categorie; per le altre si usa l'iniziale. */
private fun faIcon(name: String?): ImageVector? = when (name?.removePrefix("fa ")?.trim()) {
    "fa-globe", "fa-earth-europe", "fa-earth" -> CvIcons.Globe
    "fa-comments", "fa-comment" -> CvIcons.Comment
    "fa-fire" -> CvIcons.Fire
    "fa-compass" -> CvIcons.Compass
    "fa-bell" -> CvIcons.Bell
    "fa-house", "fa-home" -> CvIcons.Home
    else -> null
}

@Composable
fun Avatar(user: UserRef, size: Dp) {
    AvatarRaw(user.picture, user.iconText, user.iconBg, size)
}

@Composable
fun AvatarRaw(picture: String?, iconText: String, iconBg: String?, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(parseHexColor(iconBg, Color(0xFF8A94A6))),
        contentAlignment = Alignment.Center,
    ) {
        Text(iconText.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.45f).sp)
        if (picture != null) {
            AsyncImage(
                model = picture,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/* ---------- chip ---------- */

/** 🌐 istanza: chi scrive da un'altra istanza del fediverso. */
@Composable
fun FediChip(domain: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Cv.colors.fediBg)
            .padding(horizontal = 7.dp, vertical = 1.dp)
            .widthIn(max = 170.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(CvIcons.Globe, null, tint = Cv.colors.fedi, modifier = Modifier.size(11.dp))
        Text(
            domain,
            color = Cv.colors.fedi,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SoftChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 1.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
fun Dot() {
    Text("·", color = Cv.colors.muted.copy(alpha = 0.7f), fontSize = 13.sp)
}

/* ---------- pillola dei voti ---------- */

@Composable
fun VotePill(
    state: VoteState,
    onUp: () -> Unit,
    onDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val active = state.up || state.down
    val bg by animateColorAsState(
        when {
            state.up -> VoteUp
            state.down -> VoteDown
            else -> Cv.colors.pill
        },
        animationSpec = tween(150),
        label = "voteBg",
    )
    val fg = if (active) Color.White else Cv.colors.muted
    Row(
        modifier = modifier.clip(CircleShape).background(bg).height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoteButton(
            icon = if (state.up) CvIcons.UpFilled else CvIcons.Up,
            tint = fg,
            hover = VoteUp,
            pressed = state.up,
            label = "Voto positivo",
            onClick = onUp,
        )
        Text(
            shortNum(state.score),
            color = if (active) Color.White else Cv.colors.text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 18.dp),
        )
        if (onDown != null) {
            VoteButton(
                icon = if (state.down) CvIcons.DownFilled else CvIcons.Down,
                tint = fg,
                hover = VoteDown,
                pressed = state.down,
                label = "Voto negativo",
                onClick = onDown,
            )
        } else {
            Spacer(Modifier.size(10.dp))
        }
    }
}

@Composable
private fun VoteButton(icon: ImageVector, tint: Color, hover: Color, pressed: Boolean, label: String, onClick: () -> Unit) {
    // piccolo "pop" quando il voto viene attivato
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(1.35f, tween(120))
            scale.animateTo(1f, tween(180))
        }
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp).scale(scale.value))
    }
}

/* ---------- pulsanti a pillola ---------- */

@Composable
fun ActionPill(
    icon: ImageVector,
    text: String?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Cv.colors.pill)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .height(36.dp)
            .padding(horizontal = if (text == null) 9.dp else 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = if (text == null) contentDescription else null, tint = Cv.colors.text, modifier = Modifier.size(17.dp))
        if (text != null) {
            Text(text, color = Cv.colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Pillola delle sezioni (Recenti / Popolari / Più votati). */
@Composable
fun TabPill(text: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    val c = Cv.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) c.text else c.card)
            .border(BorderStroke(1.dp, if (selected) c.text else c.border), CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .height(36.dp)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) c.card else c.muted, modifier = Modifier.size(15.dp))
        }
        Text(text, color = if (selected) c.card else c.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Chip di comunità della fila orizzontale. */
@Composable
fun CommunityChip(cat: CategoryRef, onClick: () -> Unit) {
    val c = Cv.colors
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(c.card)
            .border(BorderStroke(1.dp, c.border), CircleShape)
            .clickable(onClick = onClick)
            .height(36.dp)
            .widthIn(max = 220.dp)
            .padding(start = 5.dp, end = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CategoryIcon(cat, 26.dp)
        Text(cat.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            color = Cv.colors.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                "$action →",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/* ---------- stati di caricamento ---------- */

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, color = Cv.colors.muted, textAlign = TextAlign.Center)
        if (onRetry != null) OutlinedButton(onClick = onRetry) { Text("Riprova") }
    }
}

@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier) {
    Text(
        message,
        color = Cv.colors.muted,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(32.dp),
    )
}

@Composable
fun LoginPrompt(text: String, onLogin: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, color = Cv.colors.muted, textAlign = TextAlign.Center)
        Button(onClick = onLogin, contentPadding = PaddingValues(horizontal = 24.dp)) { Text("Accedi") }
    }
}

/** Card con bordo e angoli arrotondati come nel feed del sito. */
fun Modifier.cvCard(radius: Dp = 16.dp): Modifier = this
    .clip(RoundedCornerShape(radius))

/* ---------- azioni di sistema ---------- */

fun shareLink(ctx: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    ctx.startActivity(Intent.createChooser(send, "Condividi").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openExternal(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, url, Toast.LENGTH_LONG).show()
    }
}

fun copyText(ctx: Context, text: String, toast: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText("Citiverse", text))
    Toast.makeText(ctx, toast, Toast.LENGTH_LONG).show()
}

fun toast(ctx: Context, text: String) {
    Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
}
