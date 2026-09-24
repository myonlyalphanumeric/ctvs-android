package it.citiverse.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * NodeBB non è coerente con i tipi: gli id sono numeri per i contenuti locali e
 * URL/UUID per quelli del fediverso, i flag sono a volte 0/1 e a volte true/false.
 * Per questo leggiamo il JSON a mano con questi helper tolleranti.
 */
val JsonParser = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())

fun JsonObject.objects(key: String): List<JsonObject> = arr(key).mapNotNull { it as? JsonObject }

/** Stringa (anche da numero). Null se assente, null o vuota. */
fun JsonObject.str(key: String): String? = this[key].asStr()

fun JsonElement?.asStr(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    val s = p.content
    return s.ifEmpty { null }
}

fun JsonObject.long(key: String): Long {
    val p = this[key] as? JsonPrimitive ?: return 0L
    if (p is JsonNull) return 0L
    return p.content.toLongOrNull() ?: p.content.toDoubleOrNull()?.toLong() ?: 0L
}

fun JsonObject.int(key: String): Int = long(key).toInt()

fun JsonObject.bool(key: String): Boolean {
    val p = this[key] as? JsonPrimitive ?: return false
    if (p is JsonNull) return false
    return when (p.content.lowercase()) {
        "true", "1", "on", "yes" -> true
        else -> false
    }
}
