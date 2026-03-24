package io.bluetape4k.graph.model

/**
 * 그래프 요소(Vertex, Edge)의 백엔드 독립 ID.
 *
 * - Apache AGE: Long 내부 ID -> GraphElementId("$longId") 변환
 * - Neo4j: elementId() (String) -> GraphElementId 직접 매핑
 */
@JvmInline
value class GraphElementId(val value: String) {
    companion object {
        fun of(value: String) = GraphElementId(value)
        fun of(value: Long) = GraphElementId(value.toString())
    }
}
