package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId

/**
 * 그래프 간선(Edge) CRUD 저장소 (동기 방식).
 */
interface GraphEdgeRepository {
    fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): GraphEdge

    fun findEdgesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): List<GraphEdge>
    fun deleteEdge(label: String, id: GraphElementId): Boolean
}
