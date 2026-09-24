package it.citiverse.app.data

import it.citiverse.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class FeedKind(val path: String, val label: String) {
    /** Discussioni recenti delle sole comunità seguite (stessa pagina /api/recent, filtrata per cid). */
    FOLLOWED("recent", "Seguite"),
    RECENT("recent", "Recenti"),
    POPULAR("popular", "Popolari"),
    TOP("top", "Più votati"),
}

enum class FeedTerm(val key: String, val label: String) {
    ALL("alltime", "Sempre"),
    DAY("daily", "Oggi"),
    WEEK("weekly", "Settimana"),
    MONTH("monthly", "Mese"),
}

/**
 * Accesso alle API ufficiali di NodeBB:
 * - lettura: /api/... (le stesse pagine del sito in JSON)
 * - scrittura: /api/v3/... (con il token CSRF della sessione nell'header x-csrf-token)
 */
sealed class LoginResult {
    data class Done(val me: Me) : LoginResult()

    /** Password giusta, ma l'account chiede anche il codice della verifica in due passaggi. */
    data object SecondFactor : LoginResult()
}

/** Chiavi di errore di NodeBB che conviene spiegare meglio. */
private val KNOWN_ERRORS = mapOf(
    "invalid-login-credentials" to "Nome utente o password errati.",
    "invalid-username-or-password" to "Nome utente o password errati.",
    "account-locked" to "Account bloccato temporaneamente per troppi tentativi: riprova più tardi.",
    "second-factor-required" to "Serve il codice della verifica in due passaggi.",
    "email-not-confirmed" to "Devi prima confermare l'indirizzo email.",
    "user-banned" to "Questo account è sospeso.",
    "too-many-posts" to "Stai scrivendo troppo in fretta: aspetta qualche secondo.",
    "content-too-short" to "Il testo è troppo corto.",
    "title-too-short" to "Il titolo è troppo corto.",
    "no-privileges" to "Non hai i permessi per questa azione.",
)

/**
 * Risposta di /api/notifications subito dopo una password accettata.
 * Il plugin 2factor risponde 401 con code "not-authorised" e un messaggio già tradotto nella
 * lingua del forum ("È richiesta l'autenticazione a due fattori…"), mai con la chiave
 * "second-factor-required": dopo un login riuscito, un 401 vuol dire solo 2FA in sospeso.
 */
internal fun isSecondFactorPending(code: Int): Boolean = code == 401

class NodeBBApi(val cookies: PersistentCookieJar) {

    private val userAgent = "CitiverseAndroid/${BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.RELEASE})"

    val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", userAgent)
                    .header("Accept-Language", "it")
                    .build()
            )
        })
        .build()

    /** Client per il WebSocket: nessun timeout di lettura (il keepalive lo gestisce Engine.IO). */
    val wsHttp: OkHttpClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()

    /** Per il secondo passo della 2FA: serve leggere il redirect, non seguirlo. */
    private val noRedirectHttp: OkHttpClient = http.newBuilder().followRedirects(false).followSslRedirects(false).build()

    private val json = "application/json; charset=utf-8".toMediaType()
    private val csrfLock = Mutex()

    @Volatile
    private var csrf: String? = null

    private fun url(vararg segments: String, query: Map<String, String?> = emptyMap()): HttpUrl {
        val b = BASE_URL.toHttpUrl().newBuilder()
        segments.forEach { b.addPathSegment(it) }
        query.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build()
    }

    private suspend fun execute(req: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).execute().use { res -> res.code to (res.body?.string() ?: "") }
        } catch (e: IOException) {
            throw ApiException("Connessione non riuscita. Controlla la rete e riprova.")
        }
    }

    private fun parseObj(body: String): JsonObject =
        runCatching { JsonParser.parseToJsonElement(body).jsonObject }.getOrElse { JsonObject(emptyMap()) }

    private fun errorMessage(code: Int, body: JsonObject, raw: String = ""): String {
        // A volte NodeBB risponde con testo semplice, per esempio "[[error:invalid-login-credentials]]".
        val msg = body.obj("status")?.str("message") ?: body.str("error") ?: body.str("message")
            ?: raw.trim().takeIf { it.startsWith("[[") && it.length < 200 }
        KNOWN_ERRORS.entries.firstOrNull { msg?.contains(it.key) == true }?.let { return it.value }
        return when {
            !msg.isNullOrBlank() && !msg.startsWith("[[") -> msg
            code == 401 || code == 403 -> "Non hai i permessi per questa azione, oppure la sessione è scaduta."
            code == 404 -> "Contenuto non trovato."
            code == 429 -> "Troppe richieste: aspetta un momento e riprova."
            code >= 500 -> "Il server ha avuto un problema ($code)."
            else -> "Errore $code"
        }
    }

    /** GET su un'API di lettura: restituisce l'oggetto JSON della pagina. */
    suspend fun getRead(u: HttpUrl): JsonObject {
        val (code, body) = execute(Request.Builder().url(u).header("Accept", "application/json").build())
        val obj = parseObj(body)
        if (code !in 200..299) throw ApiException(errorMessage(code, obj, body), code)
        return obj
    }

    /** Chiamata alle API v3: restituisce il campo "response". */
    private suspend fun v3(method: String, u: HttpUrl, body: JsonObject? = null, retry: Boolean = true): JsonObject {
        val token = csrfToken()
        val rb = body?.toString()?.toRequestBody(json)
            ?: if (method == "GET") null else "{}".toRequestBody(json)
        val req = Request.Builder().url(u)
            .header("Accept", "application/json")
            .apply { if (token != null) header("x-csrf-token", token) }
            .method(method, rb)
            .build()
        val (code, text) = execute(req)
        val obj = parseObj(text)
        if (code == 403 && retry && text.contains("csrf", ignoreCase = true)) {
            // Il token scade con la sessione: lo rinnoviamo e riproviamo una volta.
            csrf = null
            return v3(method, u, body, retry = false)
        }
        if (code !in 200..299) throw ApiException(errorMessage(code, obj, text), code)
        return obj.obj("response") ?: JsonObject(emptyMap())
    }

    /* ---------- sessione ---------- */

    suspend fun config(): SessionInfo {
        val o = getRead(url("api", "config"))
        val info = SessionInfo(
            loggedIn = o.bool("loggedIn"),
            uid = o.str("uid") ?: "0",
            csrf = o.str("csrf_token"),
        )
        csrf = info.csrf
        return info
    }

    suspend fun csrfToken(): String? {
        csrf?.let { return it }
        return csrfLock.withLock {
            csrf ?: runCatching { config().csrf }.getOrNull()
        }
    }

    fun invalidateCsrf() {
        csrf = null
    }

    /**
     * Primo passo del login (nome utente e password).
     * Se l'account ha la verifica in due passaggi (plugin 2factor di NodeBB) la sessione
     * resta "a metà" finché non si invia il codice con [submitSecondFactor].
     */
    suspend fun login(username: String, password: String): LoginResult {
        cookies.clear()
        csrf = null
        v3(
            "POST", url("api", "v3", "utilities", "login"),
            buildJsonObject {
                put("username", username)
                put("password", password)
            },
        )
        // Dopo il login la sessione cambia: serve un token CSRF nuovo.
        csrf = null
        if (needsSecondFactor()) return LoginResult.SecondFactor
        return LoginResult.Done(finishLogin())
    }

    /** Con la 2FA in sospeso /api/config dice già loggedIn, ma le pagine dell'utente rispondono 401. */
    private suspend fun needsSecondFactor(): Boolean {
        val (code, _) = execute(Request.Builder().url(url("api", "notifications")).header("Accept", "application/json").build())
        return isSecondFactorPending(code)
    }

    private suspend fun finishLogin(): Me {
        csrf = null
        val info = config()
        if (!info.loggedIn || info.uid == "0") throw ApiException("Accesso non riuscito: controlla nome utente e password.")
        return me(info.uid)
    }

    /**
     * Secondo passo: codice a 6 cifre dell'app di autenticazione (TOTP) o codice di backup.
     * È lo stesso modulo della pagina /login/2fa/totp del sito: se il codice è giusto il server
     * reindirizza alla home, altrimenti di nuovo alla pagina del codice.
     */
    suspend fun submitSecondFactor(code: String, backup: Boolean): Me {
        val token = csrfToken()
        val path = if (backup) "backup" else "totp"
        val form = okhttp3.FormBody.Builder()
            .add("code", code.filterNot { it.isWhitespace() })
            .apply { if (token != null) add("_csrf", token) }
            .build()
        val req = Request.Builder().url(url("login", "2fa", path))
            .apply { if (token != null) header("x-csrf-token", token) }
            .post(form)
            .build()
        val location = withContext(Dispatchers.IO) {
            try {
                noRedirectHttp.newCall(req).execute().use { res ->
                    if (res.code == 429) throw ApiException("Troppi tentativi: aspetta qualche secondo e riprova.", 429)
                    res.header("Location") ?: ""
                }
            } catch (e: IOException) {
                throw ApiException("Connessione non riuscita. Controlla la rete e riprova.")
            }
        }
        if (location.contains("/login/2fa") || needsSecondFactor()) {
            throw ApiException(if (backup) "Codice di backup non valido." else "Codice non valido o scaduto: riprova con quello nuovo.")
        }
        return finishLogin()
    }

    suspend fun me(uid: String): Me {
        val u = v3("GET", url("api", "v3", "users", uid))
        val ref = UserRef.from(u)
        return Me(
            uid = ref.uid,
            username = ref.username,
            userslug = ref.userslug ?: ref.username,
            displayName = ref.displayName,
            picture = ref.picture,
            iconText = ref.iconText,
            iconBg = ref.iconBg,
        )
    }

    suspend fun logout() {
        runCatching {
            val token = csrfToken()
            val req = Request.Builder().url(url("logout"))
                .apply { if (token != null) header("x-csrf-token", token) }
                .post("{}".toRequestBody(json))
                .build()
            execute(req)
        }
        cookies.clear()
        csrf = null
    }

    /* ---------- feed e categorie ---------- */

    private fun topicPage(o: JsonObject, page: Int): TopicPage {
        val topics = o.objects("topics").mapNotNull { Topic.from(it) }
        val pag = o.obj("pagination")
        val current = pag?.int("currentPage")?.takeIf { it > 0 } ?: page
        val count = pag?.int("pageCount")?.takeIf { it > 0 } ?: current
        return TopicPage(topics, current, count)
    }

    /** [cids]: se non vuoto, solo le discussioni di queste comunità (?cid=1&cid=2…). */
    suspend fun feed(kind: FeedKind, term: FeedTerm?, page: Int, cids: Collection<String> = emptyList()): TopicPage {
        val q = mutableMapOf<String, String?>("page" to page.toString())
        if (kind != FeedKind.RECENT && kind != FeedKind.FOLLOWED && term != null) q["term"] = term.key
        val u = url("api", kind.path, query = q).newBuilder()
            .apply { cids.forEach { addQueryParameter("cid", it) } }
            .build()
        return topicPage(getRead(u), page)
    }

    suspend fun allCategories(): List<Category> {
        val r = v3("GET", url("api", "v3", "categories"))
        return r.objects("categories").mapNotNull { Category.from(it) }
    }

    suspend fun category(id: String, page: Int): CategoryDetail {
        val o = getRead(url("api", "category", id, query = mapOf("page" to page.toString())))
        val cat = Category.from(o) ?: throw ApiException("Comunità non trovata.", 404)
        return CategoryDetail(
            category = cat,
            handleFull = o.str("handleFull"),
            children = o.objects("children").mapNotNull { Category.from(it) },
            topics = topicPage(o, page),
            isWatched = o.bool("isWatched"),
            canPost = o.obj("privileges")?.bool("topics:create") ?: false,
        )
    }

    /** Categorie seguite dall'utente (pagina "Categorie" del profilo). */
    suspend fun watchedCategoryIds(userslug: String): Set<String> {
        val found = linkedSetOf<String>()
        fun walk(list: List<JsonObject>) {
            list.forEach { c ->
                if (c.bool("isWatched") && c.str("link").isNullOrBlank()) c.str("cid")?.let { found.add(it) }
                walk(c.objects("children"))
            }
        }
        var page = 1
        while (page <= 10) {
            val o = getRead(url("api", "user", userslug, "categories", query = mapOf("page" to page.toString())))
            walk(o.objects("categories"))
            val count = o.obj("pagination")?.int("pageCount") ?: 1
            if (page >= count) break
            page++
        }
        return found
    }

    suspend fun setCategoryWatched(cid: String, watched: Boolean) {
        v3(
            "PUT", url("api", "v3", "categories", cid, "watch"),
            buildJsonObject { put("state", if (watched) "watching" else "notwatching") },
        )
    }

    /* ---------- discussioni ---------- */

    suspend fun topic(tid: String, page: Int): TopicDetail {
        val o = getRead(url("api", "topic", tid, query = mapOf("page" to page.toString())))
        val priv = o.obj("privileges")
        val pag = o.obj("pagination")
        val current = pag?.int("currentPage")?.takeIf { it > 0 } ?: page
        return TopicDetail(
            tid = o.str("tid") ?: tid,
            title = textFromHtml(o.str("titleRaw") ?: o.str("title") ?: ""),
            slug = o.str("slug"),
            ownerUid = o.str("uid"),
            category = CategoryRef.from(o.obj("category")),
            tags = o.objects("tags").mapNotNull { it.str("value") },
            posts = o.objects("posts").mapNotNull { Post.from(it) },
            page = current,
            pageCount = pag?.int("pageCount")?.takeIf { it > 0 } ?: current,
            canReply = priv?.bool("topics:reply") ?: false,
            canUpvote = priv?.bool("posts:upvote") ?: false,
            canDownvote = priv?.bool("posts:downvote") ?: false,
            downvoteDisabled = o.bool("downvote:disabled"),
            reputationDisabled = o.bool("reputation:disabled"),
            locked = o.bool("locked"),
            isFollowing = o.bool("isFollowing"),
        )
    }

    suspend fun post(pid: String): Post? = Post.from(v3("GET", url("api", "v3", "posts", pid)))

    /** delta: 1 voto su, -1 voto giù, 0 toglie il voto. */
    suspend fun vote(pid: String, delta: Int) {
        val u = url("api", "v3", "posts", pid, "vote")
        if (delta == 0) v3("DELETE", u) else v3("PUT", u, buildJsonObject { put("delta", delta) })
    }

    suspend fun reply(tid: String, content: String, toPid: String?): Post? {
        val r = v3(
            "POST", url("api", "v3", "topics", tid),
            buildJsonObject {
                put("content", content)
                if (toPid != null) put("toPid", toPid)
            },
        )
        return Post.from(r)
    }

    suspend fun newTopic(cid: String, title: String, content: String): String {
        val r = v3(
            "POST", url("api", "v3", "topics"),
            buildJsonObject {
                put("cid", cid.toLongOrNull()?.let { kotlinx.serialization.json.JsonPrimitive(it) }
                    ?: kotlinx.serialization.json.JsonPrimitive(cid))
                put("title", title)
                put("content", content)
            },
        )
        return r.str("tid") ?: throw ApiException("Discussione creata, ma il server non ha restituito l'indirizzo.")
    }

    suspend fun setTopicFollowed(tid: String, follow: Boolean) {
        val u = url("api", "v3", "topics", tid, "follow")
        if (follow) v3("PUT", u) else v3("DELETE", u)
    }

    /* ---------- notifiche ---------- */

    suspend fun notifications(): List<NotificationItem> =
        getRead(url("api", "notifications")).objects("notifications").mapNotNull { NotificationItem.from(it) }

    /** Testi in italiano di NodeBB (es. notifications.json), per tradurre "[[notifications:...]]". */
    suspend fun languageFile(namespace: String): JsonObject =
        getRead(url("assets", "language", "it", "$namespace.json"))
}
