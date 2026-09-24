package it.citiverse.app.data

import kotlinx.serialization.json.JsonObject

const val BASE_URL = "https://citiverse.it"
const val SITE_HOST = "citiverse.it"

/** Rende assoluto un percorso relativo del forum ("/assets/..."). */
fun absUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    return when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> BASE_URL + path
        else -> "$BASE_URL/$path"
    }
}

data class UserRef(
    val uid: String,
    val username: String,
    val displayName: String,
    val userslug: String?,
    val picture: String?,
    val iconText: String,
    val iconBg: String?,
    val isLocal: Boolean,
) {
    /** Dominio dell'istanza per gli utenti che scrivono da altre istanze del fediverso. */
    val remoteDomain: String? get() = remoteDomainOf(uid, username, isLocal)

    companion object {
        fun from(o: JsonObject?): UserRef {
            if (o == null) return UserRef("0", "Ospite", "Ospite", null, null, "?", null, true)
            val uid = o.str("uid") ?: "0"
            val username = o.str("username") ?: "Ospite"
            val display = o.str("displayname") ?: username
            val isLocal = if (o.containsKey("isLocal")) o.bool("isLocal") else uid.all { it.isDigit() }
            return UserRef(
                uid = uid,
                username = username,
                displayName = textFromHtml(display),
                userslug = o.str("userslug"),
                picture = absUrl(o.str("picture")),
                iconText = o.str("icon:text") ?: username.take(1).uppercase(),
                iconBg = o.str("icon:bgColor"),
                isLocal = isLocal,
            )
        }
    }
}

/** Gli utenti remoti hanno un uid non numerico e un nome del tipo "nome@istanza". */
fun remoteDomainOf(uid: String?, username: String?, isLocal: Boolean = false): String? {
    if (uid == null || (isLocal && uid.all { it.isDigit() })) return null
    if (uid.all { it.isDigit() }) return null
    val at = username?.lastIndexOf('@') ?: -1
    if (username != null && at > 0 && at < username.length - 1) return username.substring(at + 1)
    return runCatching { java.net.URI(uid).host }.getOrNull()
}

data class CategoryRef(
    val cid: String,
    val name: String,
    val slug: String?,
    val icon: String?,
    val bgColor: String?,
    val color: String?,
    val backgroundImage: String?,
) {
    /** Identificatore da usare in /api/category/{id}. */
    val routeId: String get() = cid

    companion object {
        fun from(o: JsonObject?): CategoryRef? {
            if (o == null) return null
            val cid = o.str("cid") ?: return null
            return CategoryRef(
                cid = cid,
                name = textFromHtml(o.str("name") ?: ""),
                slug = o.str("slug"),
                icon = o.str("icon"),
                bgColor = o.str("bgColor"),
                color = o.str("color"),
                backgroundImage = absUrl(o.str("backgroundImage")),
            )
        }
    }
}

data class Topic(
    val tid: String,
    val title: String,
    val slug: String?,
    val mainPid: String?,
    val cid: String?,
    val category: CategoryRef?,
    val user: UserRef,
    val timestamp: Long,
    val lastPostTime: Long,
    val postCount: Int,
    val votes: Int,
    val thumb: String?,
    val numThumbs: Int,
    val pinned: Boolean,
    val locked: Boolean,
    val unread: Boolean,
    val tags: List<String>,
) {
    val replies: Int get() = (postCount - 1).coerceAtLeast(0)
    val webUrl: String get() = "$BASE_URL/topic/${slug ?: tid}"

    companion object {
        fun from(o: JsonObject): Topic? {
            val tid = o.str("tid") ?: return null
            val thumbs = o.objects("thumbs")
            return Topic(
                tid = tid,
                title = textFromHtml(o.str("titleRaw") ?: o.str("title") ?: ""),
                slug = o.str("slug"),
                mainPid = o.str("mainPid"),
                cid = o.str("cid"),
                category = CategoryRef.from(o.obj("category")),
                user = UserRef.from(o.obj("user")),
                timestamp = o.long("timestamp"),
                lastPostTime = o.long("lastposttime"),
                postCount = o.int("postcount"),
                votes = o.int("votes"),
                thumb = absUrl(thumbs.firstOrNull()?.let { it.str("url") ?: it.str("path") }),
                numThumbs = o.int("numThumbs"),
                pinned = o.bool("pinned"),
                locked = o.bool("locked"),
                unread = o.bool("unread"),
                tags = o.objects("tags").mapNotNull { it.str("value") },
            )
        }
    }
}

data class TopicPage(
    val topics: List<Topic>,
    val page: Int,
    val pageCount: Int,
) {
    val hasMore: Boolean get() = page < pageCount
}

data class Category(
    val ref: CategoryRef,
    val description: String,
    val parentCid: String?,
    val topicCount: Int,
    val postCount: Int,
    val handle: String?,
    val link: String?,
    val isSection: Boolean,
    val disabled: Boolean,
    val order: Int,
) {
    /**
     * Le categorie "link" che puntano a una comunità remota ospitata qui
     * (https://citiverse.it/category/nome@istanza) si possono aprire nell'app.
     */
    val openableId: String?
        get() {
            if (disabled || isSection) return null
            if (link.isNullOrBlank()) return ref.cid.takeIf { it != "-1" && it != "0" }
            val prefix = "$BASE_URL/category/"
            return if (link.startsWith(prefix)) link.removePrefix(prefix).trimEnd('/') else null
        }

    companion object {
        fun from(o: JsonObject): Category? {
            val ref = CategoryRef.from(o) ?: return null
            return Category(
                ref = ref,
                description = textFromHtml(o.str("descriptionParsed") ?: o.str("description") ?: ""),
                parentCid = o.str("parentCid"),
                topicCount = maxOf(o.int("totalTopicCount"), o.int("topic_count")),
                postCount = maxOf(o.int("totalPostCount"), o.int("post_count")),
                handle = o.str("handle"),
                link = o.str("link"),
                isSection = o.bool("isSection"),
                disabled = o.bool("disabled"),
                order = o.int("order"),
            )
        }
    }
}

data class CategoryDetail(
    val category: Category,
    val handleFull: String?,
    val children: List<Category>,
    val topics: TopicPage,
    val isWatched: Boolean,
    val canPost: Boolean,
)

data class Post(
    val pid: String,
    val tid: String,
    val index: Int,
    val content: String,
    val user: UserRef,
    val timestamp: Long,
    val edited: Long,
    val votes: Int,
    val upvoted: Boolean,
    val downvoted: Boolean,
    val deleted: Boolean,
    val toPid: String?,
    val parentUser: String?,
    val url: String?,
    val replyCount: Int,
) {
    companion object {
        fun from(o: JsonObject): Post? {
            val pid = o.str("pid") ?: return null
            return Post(
                pid = pid,
                tid = o.str("tid") ?: "",
                index = o.int("index"),
                content = o.str("content") ?: "",
                user = UserRef.from(o.obj("user")),
                timestamp = o.long("timestamp"),
                edited = o.long("edited"),
                votes = o.int("votes"),
                upvoted = o.bool("upvoted"),
                downvoted = o.bool("downvoted"),
                deleted = o.bool("deleted"),
                toPid = o.str("toPid"),
                parentUser = o.obj("parent")?.obj("user")?.let { u ->
                    u.str("displayname") ?: u.str("username")
                },
                url = o.str("url"),
                replyCount = o.obj("replies")?.int("count") ?: 0,
            )
        }
    }
}

data class TopicDetail(
    val tid: String,
    val title: String,
    val slug: String?,
    val ownerUid: String?,
    val category: CategoryRef?,
    val tags: List<String>,
    val posts: List<Post>,
    val page: Int,
    val pageCount: Int,
    val canReply: Boolean,
    val canUpvote: Boolean,
    val canDownvote: Boolean,
    val downvoteDisabled: Boolean,
    val reputationDisabled: Boolean,
    val locked: Boolean,
    val isFollowing: Boolean,
) {
    val webUrl: String get() = "$BASE_URL/topic/${slug ?: tid}"
}

data class SessionInfo(
    val loggedIn: Boolean,
    val uid: String,
    val csrf: String?,
)

data class Me(
    val uid: String,
    val username: String,
    val userslug: String,
    val displayName: String,
    val picture: String?,
    val iconText: String,
    val iconBg: String?,
)

data class NotificationItem(
    val nid: String,
    val type: String,
    val bodyShort: String,
    val bodyLong: String,
    val path: String?,
    val tid: String?,
    val pid: String?,
    val datetime: Long,
    val read: Boolean,
    val from: UserRef?,
) {
    companion object {
        fun from(o: JsonObject): NotificationItem? {
            val nid = o.str("nid") ?: return null
            return NotificationItem(
                nid = nid,
                type = o.str("type") ?: "",
                bodyShort = o.str("bodyShort") ?: "",
                bodyLong = o.str("bodyLong") ?: "",
                path = o.str("path"),
                tid = o.str("tid"),
                pid = o.str("pid"),
                datetime = o.long("datetime"),
                read = o.bool("read"),
                from = o.obj("user")?.let { UserRef.from(it) },
            )
        }
    }
}

class ApiException(message: String, val code: Int = 0) : Exception(message)
