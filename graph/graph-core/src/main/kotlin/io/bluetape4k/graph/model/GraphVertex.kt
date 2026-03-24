package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 그래프의 정점(Vertex/Node).
 */
data class GraphVertex(
    val id: GraphElementId,
    val label: String,
    val properties: Map<String, Any?> = emptyMap(),
): Serializable {
    companion object {
        const val serializableUID = 1L
    }
}
