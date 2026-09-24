package it.citiverse.app.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.Post
import it.citiverse.app.data.TopicDetail
import it.citiverse.app.data.ago
import it.citiverse.app.data.fullDate
import it.citiverse.app.ui.LinkRouter
import it.citiverse.app.ui.Routes
import it.citiverse.app.ui.VoteState
import it.citiverse.app.ui.appViewModel
import it.citiverse.app.ui.components.ActionPill
import it.citiverse.app.ui.components.Avatar
import it.citiverse.app.ui.components.CategoryIcon
import it.citiverse.app.ui.components.Dot
import it.citiverse.app.ui.components.ErrorBox
import it.citiverse.app.ui.components.FediChip
import it.citiverse.app.ui.components.HtmlContent
import it.citiverse.app.ui.components.LoadingBox
import it.citiverse.app.ui.components.SoftChip
import it.citiverse.app.ui.components.VotePill
import it.citiverse.app.ui.components.openExternal
import it.citiverse.app.ui.components.shareLink
import it.citiverse.app.ui.components.toast
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TopicUi(
    val detail: TopicDetail? = null,
    val posts: List<Post> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val pageCount: Int = 0,
    val following: Boolean = false,
    val sending: Boolean = false,
    /** Post da mostrare all'apertura (per esempio da una notifica). */
    val scrollTo: String? = null,
) {
    val hasMore: Boolean get() = page < pageCount
}

class TopicViewModel(
    private val app: CitiverseApp,
    private var tid: String?,
    private val targetPid: String?,
) : ViewModel() {
    private val _ui = MutableStateFlow(TopicUi())
    val ui: StateFlow<TopicUi> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val id = tid ?: targetPid?.let { app.api.post(it)?.tid }?.also { tid = it }
                    ?: throw IllegalStateException("Discussione non trovata.")
                val d = app.api.topic(id, 1)
                seed(d.posts)
                _ui.value = TopicUi(
                    detail = d, posts = d.posts, loading = false,
                    page = d.page, pageCount = d.pageCount, following = d.isFollowing,
                )
                // Se si arriva da una notifica, carica le pagine fino al post cercato.
                if (targetPid != null) {
                    var guard = 0
                    while (_ui.value.posts.none { it.pid == targetPid } && _ui.value.hasMore && guard++ < 10) {
                        loadPage(_ui.value.page + 1)
                    }
                    if (_ui.value.posts.any { it.pid == targetPid }) _ui.update { it.copy(scrollTo = targetPid) }
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Errore") }
            }
        }
    }

    private fun seed(posts: List<Post>) {
        posts.forEach { app.votes.put(it.pid, it.upvoted, it.downvoted, it.votes) }
    }

    private suspend fun loadPage(page: Int) {
        val id = tid ?: return
        val d = app.api.topic(id, page)
        seed(d.posts)
        _ui.update {
            it.copy(
                detail = it.detail ?: d,
                posts = (it.posts + d.posts).distinctBy { p -> p.pid },
                page = d.page,
                pageCount = d.pageCount,
                loadingMore = false,
            )
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        _ui.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                loadPage(s.page + 1)
            } catch (e: Exception) {
                _ui.update { it.copy(loadingMore = false, error = e.message) }
            }
        }
    }

    fun scrolled() = _ui.update { it.copy(scrollTo = null) }

    fun reply(text: String, toPid: String?, onDone: (Boolean, String?) -> Unit) {
        val id = tid ?: return
        if (_ui.value.sending) return
        _ui.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val post = app.api.reply(id, text, toPid)
                if (post != null && post.content.isNotBlank()) {
                    app.votes.put(post.pid, false, false, 0)
                    // Se sono già caricate tutte le pagine la risposta va in fondo, altrimenti la vedrai arrivando in fondo.
                    _ui.update { s ->
                        if (!s.hasMore) s.copy(posts = (s.posts + post).distinctBy { it.pid }, scrollTo = post.pid) else s
                    }
                }
                _ui.update { it.copy(sending = false) }
                onDone(true, null)
            } catch (e: Exception) {
                _ui.update { it.copy(sending = false) }
                onDone(false, e.message)
            }
        }
    }

    fun toggleFollow(onError: (String) -> Unit) {
        val id = tid ?: return
        val next = !_ui.value.following
        _ui.update { it.copy(following = next) }
        viewModelScope.launch {
            try {
                app.api.setTopicFollowed(id, next)
            } catch (e: Exception) {
                _ui.update { it.copy(following = !next) }
                onError(e.message ?: "Operazione non riuscita")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TopicScreen(tid: String?, pid: String?, navigate: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CitiverseApp
    val vm = appViewModel(key = "topic:$tid:$pid") { TopicViewModel(it, tid, pid) }
    val ui by vm.ui.collectAsState()
    val me by app.prefs.me.collectAsState()
    val votes by app.votes.states.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val d = ui.detail
    var draft by rememberSaveable { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Post?>(null) }

    val onLink: (String) -> Unit = { url ->
        val route = LinkRouter.route(url)
        if (route != null) navigate(route) else openExternal(ctx, url)
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, ui.hasMore) { if (nearEnd) vm.loadMore() }

    LaunchedEffect(ui.scrollTo) {
        val target = ui.scrollTo ?: return@LaunchedEffect
        val idx = ui.posts.indexOfFirst { it.pid == target }
        if (idx >= 0) listState.animateScrollToItem(idx + 1) // +1 per l'intestazione
        vm.scrolled()
    }

    fun vote(p: Post, up: Boolean) {
        if (me == null) return navigate(Routes.LOGIN)
        scope.launch { app.votes.toggle(p.pid, up).onFailure { toast(ctx, it.message ?: "Voto non riuscito") } }
    }

    Scaffold(
        containerColor = Cv.colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    val cat = d?.category
                    if (cat != null) {
                        Row(
                            modifier = Modifier.clip(CircleShape).clickable { navigate(Routes.category(cat.routeId)) }.padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(cat, 26.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(cat.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") } },
                actions = {
                    if (d != null && me != null) {
                        IconButton(onClick = { vm.toggleFollow { toast(ctx, it) } }) {
                            Icon(
                                if (ui.following) CvIcons.BellFilled else CvIcons.Bell,
                                if (ui.following) "Non seguire più" else "Segui la discussione",
                                tint = if (ui.following) MaterialTheme.colorScheme.primary else Cv.colors.text,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                    if (d != null) {
                        IconButton(onClick = { shareLink(ctx, d.title, d.webUrl) }) {
                            Icon(CvIcons.Share, "Condividi", modifier = Modifier.size(20.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cv.colors.bg),
            )
        },
        bottomBar = {
            if (d != null) {
                Composer(
                    loggedIn = me != null,
                    canReply = d.canReply && !d.locked,
                    locked = d.locked,
                    draft = draft,
                    onDraft = { draft = it },
                    replyTo = replyTo,
                    onCancelReply = { replyTo = null },
                    sending = ui.sending,
                    onLogin = { navigate(Routes.LOGIN) },
                    onSend = {
                        val text = draft.trim()
                        vm.reply(text, replyTo?.pid) { ok, err ->
                            if (ok) {
                                draft = ""
                                replyTo = null
                                toast(ctx, "Risposta pubblicata")
                            } else {
                                toast(ctx, err ?: "Risposta non inviata")
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (d == null) {
                if (ui.error != null) item { ErrorBox(ui.error!!, onRetry = { vm.load() }) } else item { LoadingBox() }
                return@LazyColumn
            }
            item(key = "head") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
                    Text(
                        d.title,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        color = Cv.colors.text,
                    )
                    if (d.tags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            d.tags.forEach { Text("#$it", color = Cv.colors.muted, fontSize = 13.sp) }
                        }
                    }
                }
            }
            items(ui.posts, key = { "p:" + it.pid }) { p ->
                val isFirst = p.index == 0
                PostItem(
                    post = p,
                    isFirst = isFirst,
                    isTopicOwner = p.user.uid == d.ownerUid,
                    vote = votes[p.pid] ?: VoteState(p.upvoted, p.downvoted, p.votes, loaded = true),
                    showVotes = !d.reputationDisabled,
                    showDown = !d.downvoteDisabled,
                    onUp = { vote(p, true) },
                    onDown = { vote(p, false) },
                    onReply = {
                        if (me == null) navigate(Routes.LOGIN) else replyTo = p
                    },
                    onLink = onLink,
                )
            }
            if (ui.loadingMore) item(key = "more") { LoadingBox() }
        }
    }
}

@Composable
private fun PostItem(
    post: Post,
    isFirst: Boolean,
    isTopicOwner: Boolean,
    vote: VoteState,
    showVotes: Boolean,
    showDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onReply: () -> Unit,
    onLink: (String) -> Unit,
) {
    val c = Cv.colors
    val shape = RoundedCornerShape(16.dp)
    val outer = if (isFirst) {
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(shape)
            .background(c.card)
            .border(1.dp, c.border, shape)
            .padding(14.dp)
    } else {
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    }
    Column {
        if (!isFirst) HorizontalDivider(color = c.border, modifier = Modifier.padding(horizontal = 16.dp))
        Column(outer.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(post.user, if (isFirst) 38.dp else 32.dp)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    val domain = post.user.remoteDomain
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val name = if (domain != null) post.user.displayName.substringBefore('@').ifBlank { post.user.displayName } else post.user.displayName
                        Text(
                            name, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.text,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isTopicOwner && !isFirst) SoftChip("Autore", MaterialTheme.colorScheme.primary)
                        if (domain != null) FediChip(domain, Modifier.weight(1f, fill = false))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(ago(post.timestamp), fontSize = 12.5.sp, color = c.muted)
                        if (post.edited > 0) {
                            Dot()
                            Text("modificato", fontSize = 12.5.sp, color = c.muted)
                        }
                        if (!isFirst && post.parentUser != null) {
                            Dot()
                            Text(
                                "↳ a ${post.parentUser.substringBefore('@')}",
                                fontSize = 12.5.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (post.deleted) {
                Text("Questo post è stato eliminato.", color = c.muted, fontSize = 14.sp)
            } else {
                HtmlContent(
                    html = post.content,
                    fontSize = if (isFirst) 16.5.sp else 15.5.sp,
                    onLink = onLink,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showVotes) VotePill(vote, onUp = onUp, onDown = if (showDown) onDown else null)
                ActionPill(CvIcons.Reply, "Rispondi", "Rispondi a questo post", onReply)
                Spacer(Modifier.weight(1f))
                val url = post.url
                if (url != null && post.user.remoteDomain != null) {
                    ActionPill(CvIcons.External, null, "Apri sull'istanza originale", { onLink(url) })
                }
            }
            if (isFirst) {
                Spacer(Modifier.height(4.dp))
                Text(fullDate(post.timestamp), fontSize = 11.5.sp, color = c.muted)
            }
        }
    }
}

@Composable
private fun Composer(
    loggedIn: Boolean,
    canReply: Boolean,
    locked: Boolean,
    draft: String,
    onDraft: (String) -> Unit,
    replyTo: Post?,
    onCancelReply: () -> Unit,
    sending: Boolean,
    onLogin: () -> Unit,
    onSend: () -> Unit,
) {
    val c = Cv.colors
    Surface(color = c.card, tonalElevation = 0.dp, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            when {
                !loggedIn -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accedi per votare e rispondere", color = c.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Button(onClick = onLogin, shape = CircleShape) { Text("Accedi") }
                }
                locked -> Text("Discussione bloccata: non si possono aggiungere risposte.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                !canReply -> Text("Non hai i permessi per rispondere qui.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                else -> {
                    if (replyTo != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(CvIcons.Reply, null, tint = c.muted, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "Rispondi a ${replyTo.user.displayName.substringBefore('@')}",
                                color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = onCancelReply, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, "Annulla", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraft,
                            placeholder = { Text("Scrivi una risposta…") },
                            shape = RoundedCornerShape(22.dp),
                            maxLines = 6,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = c.border,
                                focusedContainerColor = c.bg,
                                unfocusedContainerColor = c.bg,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.size(8.dp))
                        val enabled = draft.isNotBlank() && !sending
                        Box(
                            Modifier
                                .padding(bottom = 4.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (enabled) MaterialTheme.colorScheme.primary else c.pill)
                                .clickable(enabled = enabled, onClick = onSend),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (sending) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(
                                    CvIcons.Send, "Invia",
                                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary else c.muted,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
