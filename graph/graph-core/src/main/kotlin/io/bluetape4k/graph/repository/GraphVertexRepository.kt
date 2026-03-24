package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex

/**
 * 그래프 정점(Vertex) CRUD 저장소.
 */
interface GraphVertexRepository {
    suspend fun createVertex(label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex
    suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex?
    suspend fun findVerticesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): List<GraphVertex>
    suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex?
    suspend fun deleteVertex(label: String, id: GraphElementId): Boolean
    suspend fun countVertices(label: String): Long
}
