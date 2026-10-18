package it.citiverse.app.ui.communities

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.citiverse.app.CitiverseApp
import it.citiverse.app.data.Category
import it.citiverse.app.data.fmtNum
import it.citiverse.app.data.norm
import it.citiverse.app.ui.Routes
import it.citiverse.app.ui.appViewModel
import it.citiverse.app.ui.components.CategoryIcon
import it.citiverse.app.ui.components.ErrorBox
import it.citiverse.app.ui.components.LoadingBox
import it.citiverse.app.ui.theme.Cv
import it.citiverse.app.ui.theme.CvIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommunityItem(
    val cat: Category,
    val routeId: String,
    val path: String,
    val nName: String,
    val nPath: String,
    val nDesc: String,
)

enum class CommunitySort(val label: String) { POSTS("Più post"), TOPICS("Più discussioni"), NAME("Nome A-Z") }

class CommunitiesViewModel(private val app: CitiverseApp) : ViewModel() {
    private val _items = MutableStateFlow<List<CommunityItem>?>(null)
    val items: StateFlow<List<CommunityItem>?> = _items.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load(false)
    }

    fun load(force: Boolean) {
        _error.value = null
        viewModelScope.launch {
            try {
                val all = app.categories(force)
                val byCid = all.associateBy { it.ref.cid }
                _items.value = all.mapNotNull { c ->
                    val id = c.openableId ?: return@mapNotNull null
                    val path = generateSequence(byCid[c.parentCid]) { byCid[it.parentCid] }
                        .take(10).map { it.ref.name }.toList().reversed().joinToString(" › ")
                    CommunityItem(c, id, path, norm(c.ref.name), norm(path), norm(c.description))
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Errore"
            }
        }
    }
}

/** Ordine come nel "Trova una comunità" del sito: prima il nome che inizia, poi contiene, poi percorso e descrizione. */
fun filterCommunities(items: List<CommunityItem>, query: String, sort: CommunitySort): List<CommunityItem> {
    val cmp: Comparator<CommunityItem> = when (sort) {
        CommunitySort.NAME -> compareBy<CommunityItem> { it.nName }
        CommunitySort.TOPICS -> compareByDescending { it.cat.topicCount }
        CommunitySort.POSTS -> compareByDescending { it.cat.postCount }
    }
    val q = norm(query.trim())
    if (q.isEmpty()) return items.sortedWith(cmp)
    fun rank(it: CommunityItem) = when {
        it.nName.startsWith(q) -> 0
        it.nName.contains(q) -> 1
        it.nPath.contains(q) -> 2
        it.nDesc.contains(q) -> 3
        else -> -1
    }
    return items.map { it to rank(it) }
        .filter { it.second >= 0 }
        .sortedWith(compareBy<Pair<CommunityItem, Int>> { it.second }.thenComparator { a, b -> cmp.compare(a.first, b.first) })
        .map { it.first }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunitiesScreen(navigate: (String) -> Unit, contentPadding: PaddingValues) {
    val vm = appViewModel { CommunitiesViewModel(it) }
    val items by vm.items.collectAsState()
    val error by vm.error.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(CommunitySort.POSTS) }
    val shown = remember(items, query, sort) { items?.let { filterCommunities(it, query, sort) } ?: emptyList() }
    val c = Cv.colors
    val primary = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "finder") {
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(lerp(c.card, primary, 0.10f), c.card, c.card)))
                    .border(1.dp, lerp(c.border, primary, 0.22f), shape)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(lerp(c.card, primary, 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(CvIcons.Compass, null, tint = primary, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text("Trova una comunità", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text)
                        Text(
                            "Gruppi locali e tematici: cerca per città, argomento o descrizione",
                            fontSize = 12.5.sp, color = c.muted, lineHeight = 16.sp,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Cerca tra ${items?.size ?: "le"} comunità: Matera, Linux, cucina…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(CvIcons.Search, null, modifier = Modifier.size(20.dp)) },
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = c.card,
                        unfocusedContainerColor = c.card,
                        unfocusedBorderColor = c.border,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CommunitySort.entries) { s ->
                        FilterChip(
                            selected = sort == s,
                            onClick = { sort = s },
                            label = { Text(s.label, fontSize = 13.sp) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        when {
            items == null && error != null -> item(key = "err") { ErrorBox(error!!, onRetry = { vm.load(true) }) }
            items == null -> item(key = "loading") { LoadingBox() }
            shown.isEmpty() -> item(key = "none") {
                Text(
                    "Nessuna comunità trovata per \"${query.trim()}\".",
                    color = c.muted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        // Chiave sul cid, non su routeId: più categorie-link possono puntare alla stessa comunità
        // (es. "Basket (poliverso.org)" e "Basket: la comunità…"), e due chiavi uguali visibili insieme chiudono l'app.
        items(shown, key = { "c:" + it.cat.ref.cid }) { item ->
            CommunityRow(item) { navigate(Routes.category(item.routeId)) }
        }
    }
}

@Composable
private fun CommunityRow(item: CommunityItem, onClick: () -> Unit) {
    val c = Cv.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(item.cat.ref, 40.dp, RoundedCornerShape(10.dp))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.cat.ref.name, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.path.isNotEmpty()) {
                Text(item.path, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val meta = if (!item.cat.link.isNullOrBlank()) "comunità del fediverso"
            else "${fmtNum(item.cat.topicCount)} discussioni · ${fmtNum(item.cat.postCount)} post"
            Text(meta, fontSize = 12.sp, color = c.muted, maxLines = 1)
        }
    }
}
