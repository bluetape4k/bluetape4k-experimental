package io.bluetape4k.graph.age.sql

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging

/**
 * Apache AGE의 agtype 결과를 파싱하여 Graph 모델로 변환합니다.
 */
object AgeTypeParser : KLogging() {

    fun parseVertex(agtype: String): GraphVertex {
        val json = agtype.removeSuffix("::vertex").trim()
        val map = parseJsonObject(json)
        val id = GraphElementId.of((map["id"] as Number).toLong())
        val label = map["label"] as String
        @Suppress("UNCHECKED_CAST")
        val properties = (map["properties"] as? Map<String, Any?>) ?: emptyMap()
        return GraphVertex(id, label, properties)
    }

    fun parseEdge(agtype: String): GraphEdge {
        val json = agtype.removeSuffix("::edge").trim()
        val map = parseJsonObject(json)
        val id = GraphElementId.of((map["id"] as Number).toLong())
        val label = map["label"] as String
        val startId = GraphElementId.of((map["start_id"] as Number).toLong())
        val endId = GraphElementId.of((map["end_id"] as Number).toLong())
        @Suppress("UNCHECKED_CAST")
        val properties = (map["properties"] as? Map<String, Any?>) ?: emptyMap()
        return GraphEdge(id, label, startId, endId, properties)
    }

    fun parsePath(agtype: String): GraphPath {
        val json = agtype.removeSuffix("::path").trim()
        // path는 [{...}::vertex, {...}::edge, ...] 형태의 agtype 배열
        val content = json.trim().removePrefix("[").removeSuffix("]").trim()
        val elements = parseAgtypeElements(content)
        val steps = elements.mapNotNull { element ->
            when {
                element.endsWith("::vertex") -> PathStep.VertexStep(parseVertex(element))
                element.endsWith("::edge") -> PathStep.EdgeStep(parseEdge(element))
                else -> null.also { log.warn("Unknown agtype element: $element") }
            }
        }
        return GraphPath(steps)
    }

    /**
     * agtype 배열 내용에서 각 요소를 `{...}::type` 형태의 문자열로 추출.
     * `parseJsonArray`는 `::type` suffix를 제거하므로 path 파싱에는 사용 불가.
     */
    private fun parseAgtypeElements(content: String): List<String> {
        val elements = mutableListOf<String>()
        var i = 0
        while (i < content.length) {
            while (i < content.length && (content[i] == ',' || content[i] == ' ')) i++
            if (i >= content.length) break
            if (content[i] == '{') {
                val end = findClosing(content, i, '{', '}')
                val jsonPart = content.substring(i, end + 1)
                var j = end + 1
                while (j < content.length && content[j] == ' ') j++
                if (j + 1 < content.length && content[j] == ':' && content[j + 1] == ':') {
                    var typeEnd = j + 2
                    while (typeEnd < content.length && content[typeEnd] != ',' && content[typeEnd] != ' ') typeEnd++
                    elements.add("$jsonPart${content.substring(j, typeEnd)}")
                    i = typeEnd
                } else {
                    elements.add(jsonPart)
                    i = end + 1
                }
            } else {
                i++
            }
        }
        return elements
    }

    fun isVertex(agtype: String): Boolean = agtype.trimEnd().endsWith("::vertex")
    fun isEdge(agtype: String): Boolean = agtype.trimEnd().endsWith("::edge")
    fun isPath(agtype: String): Boolean = agtype.trimEnd().endsWith("::path")

    /**
     * 단순 JSON 객체 파싱 (Jackson/Gson 의존성 없이 AGE agtype 처리용).
     * 복잡한 중첩 구조는 지원하지 않으므로, 필요 시 Jackson 도입 고려.
     */
    fun parseJsonObject(json: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val content = json.trim().removePrefix("{").removeSuffix("}").trim()
        if (content.isEmpty()) return result

        var i = 0
        while (i < content.length) {
            // key 파싱
            if (content[i] == '"') {
                val keyEnd = content.indexOf('"', i + 1)
                val key = content.substring(i + 1, keyEnd)
                i = keyEnd + 1
                // ':' 건너뜀
                while (i < content.length && (content[i] == ':' || content[i] == ' ')) i++
                // value 파싱
                val (value, nextIndex) = parseValue(content, i)
                result[key] = value
                i = nextIndex
                while (i < content.length && (content[i] == ',' || content[i] == ' ')) i++
            } else {
                i++
            }
        }
        return result
    }

    private fun parseValue(content: String, start: Int): Pair<Any?, Int> {
        var i = start
        return when {
            content[i] == '"' -> {
                val end = content.indexOf('"', i + 1)
                content.substring(i + 1, end) to end + 1
            }
            content[i] == '{' -> {
                val end = findClosing(content, i, '{', '}')
                parseJsonObject(content.substring(i, end + 1)) to end + 1
            }
            content[i] == '[' -> {
                val end = findClosing(content, i, '[', ']')
                parseJsonArray(content.substring(i, end + 1)) to end + 1
            }
            content.startsWith("null", i) -> null to i + 4
            content.startsWith("true", i) -> true to i + 4
            content.startsWith("false", i) -> false to i + 5
            else -> {
                val commaPos = content.indexOf(',', i)
                val bracePos = content.indexOf('}', i)
                val end = when {
                    commaPos < 0 && bracePos < 0 -> content.length
                    commaPos < 0 -> bracePos
                    bracePos < 0 -> commaPos
                    else -> minOf(commaPos, bracePos)
                }
                val numStr = content.substring(i, end).trim()
                val num = numStr.toLongOrNull() ?: numStr.toDoubleOrNull() ?: numStr
                num to end
            }
        }
    }

    fun parseJsonArray(json: String): List<Any?> {
        val content = json.trim().removePrefix("[").removeSuffix("]").trim()
        if (content.isEmpty()) return emptyList()
        val result = mutableListOf<Any?>()
        var i = 0
        while (i < content.length) {
            val (value, nextIndex) = parseValue(content, i)
            result.add(value)
            i = nextIndex
            while (i < content.length && (content[i] == ',' || content[i] == ' ')) i++
        }
        return result
    }

    private fun findClosing(content: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        for (i in start until content.length) {
            when (content[i]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return content.length - 1
    }
}
