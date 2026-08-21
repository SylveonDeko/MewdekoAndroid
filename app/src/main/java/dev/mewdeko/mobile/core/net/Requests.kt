package dev.mewdeko.mobile.core.net

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Encodes a bare JSON string body, which several bot setters expect. */
fun jsonString(value: String): String = MewdekoJson.encodeToString(JsonPrimitive(value))

/** Encodes a bare JSON integer body. */
fun jsonInt(value: Int): String = value.toString()

/** Encodes a bare JSON boolean body. */
fun jsonBool(value: Boolean): String = value.toString()

/**
 * Builds a JSON object body, dropping null entries so optional fields are
 * omitted rather than sent as explicit nulls.
 */
fun jsonBody(vararg pairs: Pair<String, Any?>): String {
    val obj = buildJsonObject {
        pairs.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is JsonElement -> put(key, value)
                is String -> put(key, JsonPrimitive(value))
                is Int -> put(key, JsonPrimitive(value))
                is Long -> put(key, JsonPrimitive(value))
                is Double -> put(key, JsonPrimitive(value))
                is Boolean -> put(key, JsonPrimitive(value))
                else -> put(key, JsonPrimitive(value.toString()))
            }
        }
    }
    return MewdekoJson.encodeToString(JsonObject.serializer(), obj)
}

/** Parses a snowflake string into the numeric form some bot endpoints require. */
fun String.asSnowflakeNumber(): Long = toLongOrNull() ?: 0L
