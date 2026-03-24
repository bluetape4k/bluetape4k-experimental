package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 그래프의 간선(Edge/Relationship).
 */
data class GraphEdge(
    val id: GraphElementId,
    val label: String,
    val startId: GraphElementId,
    val endId: GraphElementId,
    val properties: Map<String, Any?> = emptyMap(),
): Serializable {
    companion object {
        const val serializableUID = 1L
    }
}
