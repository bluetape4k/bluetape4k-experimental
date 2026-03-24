package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex

/**
 * 그래프 정점(Vertex) CRUD 저장소 (동기 방식).
 */
interface GraphVertexRepository {
    fun createVertex(label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex
    fun findVertexById(label: String, id: GraphElementId): GraphVertex?
    fun findVerticesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): List<GraphVertex>
    fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex?
    fun deleteVertex(label: String, id: GraphElementId): Boolean
    fun countVertices(label: String): Long
}
