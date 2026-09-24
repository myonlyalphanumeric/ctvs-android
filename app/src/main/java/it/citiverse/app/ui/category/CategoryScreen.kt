package it.citiverse.app.ui.category

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.Category
import it.citiverse.app.data.CategoryDetail
import it.citiverse.app.data.Topic
import it.citiverse.app.data.fmtNum
import it.citiverse.app.ui.Routes
import it.citiverse.app.ui.VoteState
import it.citiverse.app.ui.appViewModel
import it.citiverse.app.ui.components.CategoryIcon
import it.citiverse.app.ui.components.EmptyBox
import it.citiverse.app.ui.components.ErrorBox
import it.citiverse.app.ui.components.LoadingBox
import it.citiverse.app.ui.components.TopicCard
import it.citiverse.app.ui.components.copyText
import it.citiverse.app.ui.components.shareLink
import it.citiverse.app.ui.components.toast
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons
import it.citiverse.app.ui.theme.parseHexColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryUi(
    val detail: CategoryDetail? = null,
    val topics: List<Topic> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val watched: Boolean = false,
    val watchBusy: Boolean = false,
)

class CategoryViewModel(private val app: CitiverseApp, private val id: String) : ViewModel() {
    private val _ui = MutableStateFlow(CategoryUi())
    val ui: StateFlow<CategoryUi> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        load(reset = true)
    }

    fun refresh() = load(reset = true, pull = true)

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        load(reset = false)
    }

    private fun load(reset: Boolean, pull: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            val page = if (reset) 1 else _ui.value.page + 1
            _ui.update {
                if (reset) it.copy(loading = !pull, refreshing = pull, error = null) else it.copy(loadingMore = true, error = null)
            }
            try {
                val d = app.api.category(id, page)
                d.topics.topics.forEach { t -> t.mainPid?.let { app.votes.seed(it, t.votes) } }
                _ui.update {
                    val merged = if (reset) d.topics.topics else it.topics + d.topics.topics
                    it.copy(
                        detail = d,
                        topics = merged.distinctBy { t -> t.tid },
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        page = d.topics.page,
                        hasMore = d.topics.hasMore,
                        watched = if (reset) d.isWatched else it.watched,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, refreshing = false, loadingMore = false, error = e.message ?: "Errore") }
            }
        }
    }

    fun toggleWatch(onError: (String) -> Unit) {
        val s = _ui.value
        val cid = s.detail?.category?.ref?.cid ?: return
        if (s.watchBusy) return
        val next = !s.watched
        _ui.update { it.copy(watched = next, watchBusy = true) }
        viewModelScope.launch {
            try {
                app.api.setCategoryWatched(cid, next)
                _ui.update { it.copy(watchBusy = false) }
                app.followsVersion.value++
            } catch (e: Exception) {
                _ui.update { it.copy(watched = !next, watchBusy = false) }
                onError(e.message ?: "Operazione non riuscita")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(id: String, navigate: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val app = ctx.applicationContext as CitiverseApp
    val vm = appViewModel(key = "cat:$id") { CategoryViewModel(it, id) }
    val ui by vm.ui.collectAsState()
    val me by app.prefs.me.collectAsState()
    val votes by app.votes.states.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val cat = ui.detail?.category

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, ui.hasMore) { if (nearEnd) vm.loadMore() }

    fun vote(t: Topic, up: Boolean) {
        val pid = t.mainPid ?: return
        if (me == null) return navigate(Routes.LOGIN)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch { app.votes.toggle(pid, up).onFailure { toast(ctx, it.message ?: "Voto non riuscito") } }
    }

    Scaffold(
        containerColor = Cv.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text(cat?.ref?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
                },
                actions = {
                    if (cat != null) {
                        IconButton(onClick = {
                            shareLink(ctx, cat.ref.name, "https://citiverse.it/category/${cat.ref.slug ?: cat.ref.cid}")
                        }) { Icon(CvIcons.Share, "Condividi", modifier = Modifier.size(20.dp)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Cv.colors.bg),
            )
        },
        floatingActionButton = {
            if (ui.detail?.canPost == true && me != null) {
                ExtendedFloatingActionButton(
                    onClick = { navigate(Routes.newTopic(cat!!.ref.cid)) },
                    icon = { Icon(CvIcons.Plus, null) },
                    text = { Text("Nuova discussione") },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = padding.calculateBottomPadding() + 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val d = ui.detail
                if (d != null) {
                    item(key = "header") {
                        CategoryHeader(
                            d = d,
                            loggedIn = me != null,
                            watched = ui.watched,
                            onWatch = { vm.toggleWatch { toast(ctx, it) } },
                            onCopyHandle = { h ->
                                copyText(ctx, h, "Indirizzo copiato: cercalo su Mastodon o su un altro social del fediverso")
                            },
                        )
                    }
                    if (d.children.isNotEmpty()) {
                        item(key = "subcats") {
                            Subcategories(d.children) { child ->
                                child.openableId?.let { navigate(Routes.category(it)) }
                            }
                        }
                    }
                }
                when {
                    ui.loading && d == null -> item(key = "loading") { LoadingBox() }
                    ui.error != null && d == null -> item(key = "error") { ErrorBox(ui.error!!, onRetry = { vm.refresh() }) }
                    !ui.loading && ui.topics.isEmpty() && d != null -> item(key = "empty") {
                        EmptyBox("Ancora nessuna discussione in questa comunità.")
                    }
                }
                items(ui.topics, key = { "t:" + it.tid }) { t ->
                    val pid = t.mainPid
                    if (pid != null && me != null) LaunchedEffect(pid) { app.votes.ensureLoaded(pid) }
                    TopicCard(
                        topic = t,
                        vote = pid?.let { votes[it] } ?: VoteState(score = t.votes),
                        // dentro la comunità la riga mostra l'autore, non di nuovo la comunità
                        showCategory = t.cid != null && t.cid != cat?.ref?.cid,
                        showUnread = me != null,
                        votesEnabled = true,
                        onOpen = { navigate(Routes.topic(t.tid)) },
                        onCategory = { navigate(Routes.category(it.routeId)) },
                        onUp = { vote(t, true) },
                        onDown = { vote(t, false) },
                        onShare = { shareLink(ctx, t.title, t.webUrl) },
                    )
                }
                if (ui.loadingMore) item(key = "more") { LoadingBox() }
                if (ui.error != null && d != null) item(key = "moreErr") { ErrorBox(ui.error!!, onRetry = { vm.loadMore() }) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryHeader(
    d: CategoryDetail,
    loggedIn: Boolean,
    watched: Boolean,
    onWatch: () -> Unit,
    onCopyHandle: (String) -> Unit,
) {
    val c = Cv.colors
    val cat = d.category
    val color = parseHexColor(cat.ref.bgColor, MaterialTheme.colorScheme.primary)
    val shape = RoundedCornerShape(20.dp)
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(c.card, color, 0.16f), c.card, c.card)))
            .border(1.dp, lerp(c.border, color, 0.25f), shape)
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(cat.ref, 52.dp, RoundedCornerShape(14.dp))
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cat.ref.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp, color = c.text, lineHeight = 26.sp)
                Text(
                    "${fmtNum(cat.topicCount)} discussioni · ${fmtNum(cat.postCount)} post",
                    fontSize = 13.sp, color = c.muted,
                )
            }
        }
        if (cat.description.isNotBlank()) {
            Text(
                cat.description,
                color = c.muted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (loggedIn) {
                if (watched) {
                    OutlinedButton(onClick = onWatch, shape = CircleShape, contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Icon(CvIcons.Check, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Segui già")
                    }
                } else {
                    Button(
                        onClick = onWatch,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.text, contentColor = c.card),
                    ) { Text("Segui", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        val handle = d.handleFull?.let { "@" + it.removePrefix("@") }
        if (handle != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .clip(CircleShape)
                        .background(c.fediBg)
                        .border(1.dp, c.fedi.copy(alpha = 0.35f), CircleShape)
                        .clickable { onCopyHandle(handle) }
                        .height(34.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(CvIcons.Globe, null, tint = c.fedi, modifier = Modifier.size(15.dp))
                    Text(
                        handle, color = c.fedi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(CvIcons.Copy, "Copia l'indirizzo", tint = c.fedi.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                }
                Text("Segui questa comunità anche da Mastodon e dal fediverso", fontSize = 12.sp, color = c.muted)
            }
        }
    }
}

/** Pannello compatto delle sottocategorie: le prime 5, poi "Mostra tutte". */
@Composable
private fun Subcategories(children: List<Category>, onOpen: (Category) -> Unit) {
    val c = Cv.colors
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    var showAll by rememberSaveable { mutableStateOf(false) }
    val sorted = remember(children) { children.sortedByDescending { if (it.link.isNullOrBlank()) it.postCount else -1 } }
    val shown = if (showAll || sorted.size <= 6) sorted else sorted.take(5)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.dp, lerp(c.border, primary, 0.18f), shape)
            .padding(8.dp)
            .animateContentSize(),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(CvIcons.Folder, null, tint = c.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text("Sottocategorie", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.text)
            Spacer(Modifier.size(8.dp))
            Text(
                children.size.toString(),
                color = primary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(CircleShape).background(primary.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
        shown.forEach { child ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = child.openableId != null) { onOpen(child) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryIcon(child.ref, 34.dp, RoundedCornerShape(10.dp))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(child.ref.name, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (!child.link.isNullOrBlank()) "comunità del fediverso"
                        else "${fmtNum(child.topicCount)} discussioni · ${fmtNum(child.postCount)} post",
                        fontSize = 12.sp, color = c.muted, maxLines = 1,
                    )
                }
            }
        }
        if (sorted.size > 6) {
            Text(
                if (showAll) "Mostra meno" else "Mostra tutte le ${sorted.size}",
                color = primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                modifier = Modifier
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.10f))
                    .clickable { showAll = !showAll }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
