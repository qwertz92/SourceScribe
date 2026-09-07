package app.sourcescribe.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Bound recursion before the library parser visits even unknown fields. Strings are opaque. */
fun Json.parseBounded(raw: String): JsonElement = parseToJsonElement(boundedJsonText(raw))

/** Also usable before platform JSON readers, with no second parsing pass. */
fun boundedJsonText(raw: String): String {
    var depth = 0
    var quoted = false
    var escaped = false
    for (character in raw) {
        if (quoted) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> quoted = false
            }
        } else when (character) {
            '"' -> quoted = true
            '[', '{' -> require(++depth <= 64) { "JSON_NESTING_LIMIT" }
            ']', '}' -> require(--depth >= 0) { "JSON_UNBALANCED" }
        }
    }
    require(!quoted && depth == 0) { "JSON_UNBALANCED" }
    return raw
}
