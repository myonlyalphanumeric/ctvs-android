package it.citiverse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.citiverse.app.data.CategoryRef
import it.citiverse.app.data.Topic
import it.citiverse.app.data.UserRef
import it.citiverse.app.data.ago
import it.citiverse.app.data.shortNum
import it.citiverse.app.ui.VoteState
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons
import it.citiverse.app.ui.theme.PinGreen
import it.citiverse.app.ui.theme.parseHexColor

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun TopicCard(
    topic: Topic,
    vote: VoteState,
    showCategory: Boolean,
    showUnread: Boolean,
    votesEnabled: Boolean,
    onOpen: () -> Unit,
    onCategory: (CategoryRef) -> Unit,
    onUp: () -> Unit,
    onDown: (() -> Unit)?,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Cv.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(c.card)
            .border(1.dp, c.border, CardShape)
            .clickable(onClickLabel = "Apri la discussione", onClick = onOpen)
            .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Byline(
                    category = if (showCategory) topic.category else null,
                    user = topic.user,
                    timestamp = topic.timestamp,
                    onCategory = onCategory,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    if (showUnread && topic.unread) {
                        Box(
                            Modifier
                                .padding(top = 7.dp, end = 7.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    Text(
                        topic.title,
                        color = c.text,
                        fontSize = 16.5.sp,
                        lineHeight = 21.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.1).sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (topic.pinned || topic.locked) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (topic.pinned) SoftChip("Fissato", PinGreen)
                        if (topic.locked) SoftChip("Bloccato", c.muted)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            TopicThumb(topic, onOpen)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (votesEnabled && topic.mainPid != null) {
                VotePill(vote, onUp = onUp, onDown = onDown)
            }
            val n = topic.replies
            ActionPill(
                icon = CvIcons.Comment,
                text = shortNum(n),
                contentDescription = if (n == 1) "1 commento" else "$n commenti",
                onClick = onOpen,
            )
            Spacer(Modifier.weight(1f))
            ActionPill(icon = CvIcons.Share, text = null, contentDescription = "Condividi", onClick = onShare)
        }
    }
}

/** comunità (o avatar) · autore [🌐 istanza] · tempo */
@Composable
fun Byline(
    category: CategoryRef?,
    user: UserRef,
    timestamp: Long,
    onCategory: (CategoryRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Cv.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (category != null) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(CircleShape)
                    .clickable { onCategory(category) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CategoryIcon(category, 20.dp)
                Text(
                    category.name,
                    color = c.text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Dot()
        } else {
            Avatar(user, 20.dp)
        }
        val domain = user.remoteDomain
        val name = if (domain != null) user.displayName.substringBefore('@').ifBlank { user.displayName } else user.displayName
        Text(
            name,
            color = c.muted,
            fontSize = 12.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (domain != null) FediChip(domain, Modifier.weight(1f, fill = false))
        if (timestamp > 0) {
            Dot()
            Text(ago(timestamp), color = c.muted, fontSize = 12.5.sp, maxLines = 1)
        }
    }
}

/** Miniatura della discussione o, se manca, riquadro col colore e l'immagine della comunità. */
@Composable
fun TopicThumb(topic: Topic, onClick: () -> Unit) {
    val c = Cv.colors
    val shape = RoundedCornerShape(12.dp)
    val mod = Modifier.size(72.dp).clip(shape).clickable(onClick = onClick)
    if (topic.thumb != null) {
        Box(mod.background(c.pill)) {
            AsyncImage(
                model = topic.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (topic.numThumbs > 1) {
                Text(
                    "+${topic.numThumbs - 1}",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
        }
        return
    }
    val cat = topic.category
    val catColor = parseHexColor(cat?.bgColor, c.muted)
    Box(
        modifier = mod.background(
            Brush.linearGradient(listOf(lerp(c.card, catColor, 0.30f), lerp(c.card, catColor, 0.06f)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        val img = cat?.backgroundImage
        if (img != null) {
            AsyncImage(
                model = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(10.dp).alpha(0.45f),
            )
            AsyncImage(
                model = img,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Text(
                cat?.name?.take(1)?.uppercase() ?: "#",
                color = catColor.copy(alpha = 0.6f),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
