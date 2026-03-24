package io.bluetape4k.graph.age.sql

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Kotlin 값을 Cypher 프로퍼티 문자열로 직렬화합니다.
 */
object AgePropertySerializer {

    fun toCypherProps(properties: Map<String, Any?>): String {
        if (properties.isEmpty()) return ""
        return properties.entries.joinToString(", ", "{", "}") { (key, value) ->
            "$key: ${toCypherValue(value)}"
        }
    }

    fun toCypherValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "'${value.replace("'", "\\'")}'"
        is Number -> value.toString()
        is Boolean -> value.toString()
        is LocalDate -> "'${value}'"
        is LocalDateTime -> "'${value}'"
        is List<*> -> value.joinToString(", ", "[", "]") { toCypherValue(it) }
        is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { (k, v) -> "$k: ${toCypherValue(v)}" }
        else -> "'${value.toString().replace("'", "\\'")}'"
    }
}
