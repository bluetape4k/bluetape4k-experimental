package io.bluetape4k.graph.model

/**
 * 그래프의 정점(Vertex/Node).
 */
data class GraphVertex(
    val id: GraphElementId,
    val label: String,
    val properties: Map<String, Any?> = emptyMap(),
)
