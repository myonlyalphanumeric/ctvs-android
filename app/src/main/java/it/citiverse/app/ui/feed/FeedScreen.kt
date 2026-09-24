package it.citiverse.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.CategoryRef
import it.citiverse.app.data.FeedKind
import it.citiverse.app.data.FeedTerm
import it.citiverse.app.data.Topic
import it.citiverse.app.ui.Routes
import it.citiverse.app.ui.VoteState
import it.citiverse.app.ui.appViewModel
import it.citiverse.app.ui.components.AvatarRaw
import it.citiverse.app.ui.components.CommunityChip
import it.citiverse.app.ui.components.EmptyBox
import it.citiverse.app.ui.components.ErrorBox
import it.citiverse.app.ui.components.LoadingBox
import it.citiverse.app.ui.components.SectionLabel
import it.citiverse.app.ui.components.TabPill
import it.citiverse.app.ui.components.TopicCard
import it.citiverse.app.ui.components.shareLink
import it.citiverse.app.ui.components.toast
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Stato di una lista di discussioni a pagine (feed, categoria). */
data class TopicListUi(
    val topics: List<Topic> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
    val hasMore: Boolean = false,
)

data class CommunitiesRow(val title: String, val items: List<CategoryRef>)

class FeedViewModel(private val app: CitiverseApp) : ViewModel() {
    private val _ui = MutableStateFlow(TopicListUi())
    val ui: StateFlow<TopicListUi> = _ui.asStateFlow()

    private val _kind = MutableStateFlow(FeedKind.RECENT)
    val kind: StateFlow<FeedKind> = _kind.asStateFlow()
    private val _term = MutableStateFlow(FeedTerm.ALL)
    val term: StateFlow<FeedTerm> = _term.asStateFlow()

    private val _communities = MutableStateFlow<CommunitiesRow?>(null)
    val communities: StateFlow<CommunitiesRow?> = _communities.asStateFlow()

    private var job: Job? = null
    private var loadedFor: String? = null

    init {
        load(reset = true)
        loadCommunities()
    }

    fun select(kind: FeedKind) {
        if (kind == _kind.value) return
        _kind.value = kind
        load(reset = true)
    }

    fun selectTerm(term: FeedTerm) {
        if (term == _term.value) return
        _term.value = term
        load(reset = true)
    }

    fun refresh() {
        load(reset = true, pull = true)
        loadCommunities()
    }

    /** Se nel frattempo l'utente ha fatto login/logout, ricarica. */
    fun onUserChanged(uid: String?) {
        val key = uid ?: "0"
        if (loadedFor != null && loadedFor != key) refresh()
        loadedFor = key
    }

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
                if (reset) it.copy(loading = !pull || it.topics.isEmpty(), refreshing = pull, error = null, topics = if (pull) it.topics else emptyList())
                else it.copy(loadingMore = true, error = null)
            }
            try {
                val res = app.api.feed(_kind.value, _term.value, page)
                res.topics.forEach { t -> t.mainPid?.let { app.votes.seed(it, t.votes) } }
                _ui.update {
                    val merged = if (reset) res.topics else (it.topics + res.topics).distinctBy { t -> t.tid }
                    TopicListUi(topics = merged.distinctBy { t -> t.tid }, loading = false, page = res.page, hasMore = res.hasMore)
                }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, refreshing = false, loadingMore = false, error = e.message ?: "Errore") }
            }
        }
    }

    private fun loadCommunities() {
        viewModelScope.launch {
            try {
                val all = app.categories().filter { it.openableId != null && it.link.isNullOrBlank() }
                val me = app.prefs.me.value
                val watched = if (me != null) runCatching { app.api.watchedCategoryIds(me.userslug) }.getOrDefault(emptySet()) else emptySet()
                val row = if (watched.isNotEmpty()) {
                    CommunitiesRow("Le tue comunità", all.filter { it.ref.cid in watched }.sortedByDescending { it.postCount }.take(20).map { it.ref })
                } else {
                    CommunitiesRow("Comunità più attive", all.sortedByDescending { it.postCount }.take(20).map { it.ref })
                }
                _communities.value = row.takeIf { it.items.isNotEmpty() }
            } catch (_: Exception) {
                // la fila delle comunità è un di più: se non arriva, il feed resta
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    navigate: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as CitiverseApp
    val vm = appViewModel { FeedViewModel(it) }
    val ui by vm.ui.collectAsState()
    val kind by vm.kind.collectAsState()
    val term by vm.term.collectAsState()
    val communities by vm.communities.collectAsState()
    val me by app.prefs.me.collectAsState()
    val votes by app.votes.states.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(me?.uid) { vm.onUserChanged(me?.uid) }

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
        if (me == null) {
            navigate(Routes.LOGIN)
            return
        }
        scope.launch { app.votes.toggle(pid, up).onFailure { toast(ctx, it.message ?: "Voto non riuscito") } }
    }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        HomeTopBar(
            loggedIn = me != null,
            picture = me?.picture,
            iconText = me?.iconText ?: "?",
            iconBg = me?.iconBg,
            onLogin = { navigate(Routes.LOGIN) },
            onProfile = { navigate(Routes.SETTINGS) },
        )
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "tabs") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { TabPill("Recenti", CvIcons.Clock, kind == FeedKind.RECENT) { vm.select(FeedKind.RECENT) } }
                        item { TabPill("Popolari", CvIcons.Fire, kind == FeedKind.POPULAR) { vm.select(FeedKind.POPULAR) } }
                        item { TabPill("Più votati", CvIcons.Trend, kind == FeedKind.TOP) { vm.select(FeedKind.TOP) } }
                    }
                }
                if (kind != FeedKind.RECENT) {
                    item(key = "terms") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(FeedTerm.entries) { t ->
                                FilterChip(
                                    selected = term == t,
                                    onClick = { vm.selectTerm(t) },
                                    label = { Text(t.label, fontSize = 13.sp) },
                                    shape = CircleShape,
                                )
                            }
                        }
                    }
                }
                communities?.let { row ->
                    item(key = "communities") {
                        Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                            SectionLabel(row.title, action = "Tutte", onAction = { navigate(Routes.COMMUNITIES) })
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(row.items, key = { it.cid }) { cat ->
                                    CommunityChip(cat) { navigate(Routes.category(cat.routeId)) }
                                }
                            }
                        }
                    }
                }
                when {
                    ui.loading && ui.topics.isEmpty() -> item(key = "loading") { LoadingBox() }
                    ui.error != null && ui.topics.isEmpty() -> item(key = "error") { ErrorBox(ui.error!!, onRetry = { vm.refresh() }) }
                    !ui.loading && ui.topics.isEmpty() -> item(key = "empty") { EmptyBox("Nessuna discussione qui, per ora.") }
                }
                items(ui.topics, key = { "t:" + it.tid }) { t ->
                    val pid = t.mainPid
                    if (pid != null && me != null) {
                        LaunchedEffect(pid) { app.votes.ensureLoaded(pid) }
                    }
                    TopicCard(
                        topic = t,
                        vote = pid?.let { votes[it] } ?: VoteState(score = t.votes),
                        showCategory = true,
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
                if (ui.error != null && ui.topics.isNotEmpty()) {
                    item(key = "moreError") { ErrorBox(ui.error!!, onRetry = { vm.loadMore() }) }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    loggedIn: Boolean,
    picture: String?,
    iconText: String,
    iconBg: String?,
    onLogin: () -> Unit,
    onProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Citiverse",
            color = Cv.colors.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.weight(1f),
        )
        if (loggedIn) {
            Row(Modifier.clip(CircleShape).clickable(onClick = onProfile).padding(4.dp)) {
                AvatarRaw(picture, iconText, iconBg, 32.dp)
            }
        } else {
            TextButton(onClick = onLogin) { Text("Accedi", fontWeight = FontWeight.SemiBold) }
        }
    }
}
