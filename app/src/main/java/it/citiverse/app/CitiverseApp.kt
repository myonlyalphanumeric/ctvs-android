package it.citiverse.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import it.citiverse.app.data.Category
import it.citiverse.app.data.NodeBBApi
import it.citiverse.app.data.PersistentCookieJar
import it.citiverse.app.data.Prefs
import it.citiverse.app.push.Notifier
import it.citiverse.app.ui.VoteStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Punto unico per le dipendenze dell'app (niente framework di DI: l'app è piccola). */
class CitiverseApp : Application(), ImageLoaderFactory {

    lateinit var api: NodeBBApi
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var votes: VoteStore
        private set

    /** Numero di notifiche non lette, per il pallino nella barra in basso. */
    val unreadCount = MutableStateFlow(0)

    /** Cresce quando l'utente segue o smette di seguire una comunità: il feed "Seguite" si aggiorna. */
    val followsVersion = MutableStateFlow(0)

    /** Cresce quando si tocca di nuovo "Home" già aperta: il feed torna in cima. */
    val homeReselect = MutableStateFlow(0)

    private val catLock = Mutex()
    private var catCache: List<Category>? = null
    private var catCacheAt = 0L

    /** Tutte le categorie (/api/v3/categories), tenute in memoria per 10 minuti. */
    suspend fun categories(force: Boolean = false): List<Category> = catLock.withLock {
        val cached = catCache
        if (!force && cached != null && System.currentTimeMillis() - catCacheAt < 10 * 60 * 1000) return cached
        val fresh = api.allCategories()
        catCache = fresh
        catCacheAt = System.currentTimeMillis()
        fresh
    }

    /** Dopo il login o il logout. */
    fun resetUserData() {
        votes.clear()
        unreadCount.value = 0
        catCache = null
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = NodeBBApi(PersistentCookieJar(this))
        votes = VoteStore(api)
        Notifier.ensureChannels(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(api.http)
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(80L * 1024 * 1024).build() }
            .build()
}
