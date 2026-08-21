package dev.mewdeko.mobile.core.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shared JSON codec for every dashboard payload.
 *
 * The bot serialises some responses with PascalCase keys and others with
 * camelCase, and tolerates absent fields throughout. That is absorbed once
 * by [normalizeKeys] plus lenient decoder settings, so model classes stay
 * plain camelCase data classes.
 */
val MewdekoJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
    allowSpecialFloatingPointValues = true
}

/**
 * Recursively lowercases the first character of every object key so that
 * PascalCase server payloads decode into camelCase model properties.
 */
fun JsonElement.normalizeKeys(): JsonElement = when (this) {
    is JsonObject -> JsonObject(
        entries.associate { (key, value) ->
            key.replaceFirstChar { it.lowercaseChar() } to value.normalizeKeys()
        }
    )

    is JsonArray -> JsonArray(map { it.normalizeKeys() })
    else -> this
}

/**
 * The first array in a payload that may be either a bare array or an object
 * wrapping one.
 *
 * Several bot endpoints return a C# tuple, which serialises as an object whose
 * fields carry the collection, rather than the bare array the name suggests.
 */
fun JsonElement.firstArrayOrNull(): JsonArray? = when (this) {
    is JsonArray -> this
    is JsonObject -> values.firstNotNullOfOrNull { it as? JsonArray }
    else -> null
}

/**
 * Pulls snowflakes out of a collection whose elements are either bare ids or
 * objects carrying one under a `roleId`-style key.
 */
fun JsonElement.snowflakeIds(): List<String> {
    val array = firstArrayOrNull() ?: return emptyList()
    return array.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.content.takeIf { it.isNotBlank() && it != "0" }
            is JsonObject -> element.entries
                .firstOrNull { (key, _) -> key.endsWith("roleId", ignoreCase = true) || key.equals("id", true) }
                ?.value
                ?.let { (it as? JsonPrimitive)?.content }
                ?.takeIf { it.isNotBlank() && it != "0" }

            else -> null
        }
    }
}
