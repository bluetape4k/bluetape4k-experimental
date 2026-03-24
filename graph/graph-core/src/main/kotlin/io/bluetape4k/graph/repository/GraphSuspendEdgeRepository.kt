package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 간선(Edge) CRUD 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 *
 * @see GraphEdgeRepository 동기(blocking) 방식
 */
interface GraphSuspendEdgeRepository {
    suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): GraphEdge

    fun findEdgesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): Flow<GraphEdge>
    suspend fun deleteEdge(label: String, id: GraphElementId): Boolean
}
