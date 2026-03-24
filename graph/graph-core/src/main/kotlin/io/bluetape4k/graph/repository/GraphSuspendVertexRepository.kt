package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 정점(Vertex) CRUD 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 *
 * @see GraphVertexRepository 동기(blocking) 방식
 */
interface GraphSuspendVertexRepository {
    suspend fun createVertex(label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex
    suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex?
    fun findVerticesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): Flow<GraphVertex>
    suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex?
    suspend fun deleteVertex(label: String, id: GraphElementId): Boolean
    suspend fun countVertices(label: String): Long
}
