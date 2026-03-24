package io.bluetape4k.graph.utils

import io.bluetape4k.logging.KLogging

/**
 * 그래프 속성(Properties) 변환 유틸리티.
 */
object GraphProperties : KLogging() {

    /**
     * Map을 Cypher 속성 문자열로 변환. 예: `{name: 'Alice', age: 30}`
     */
    fun toCypherProps(properties: Map<String, Any?>): String {
        if (properties.isEmpty()) return ""
        return properties.entries.joinToString(", ", "{", "}") { (key, value) ->
            "$key: ${toCypherValue(value)}"
        }
    }

    /**
     * 값을 Cypher 리터럴로 변환.
     */
    fun toCypherValue(value: Any?): String = when (value) {
        null -> "null"
        is String -> "'${value.replace("'", "\\'")}'"
        is Number -> value.toString()
        is Boolean -> value.toString()
        is List<*> -> value.joinToString(", ", "[", "]") { toCypherValue(it) }
        else -> "'${value.toString().replace("'", "\\'")}'"
    }
}
