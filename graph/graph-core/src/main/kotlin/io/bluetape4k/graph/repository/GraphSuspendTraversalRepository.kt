package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 순회(Traversal) 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 *
 * @see GraphTraversalRepository 동기(blocking) 방식
 */
interface GraphSuspendTraversalRepository {
    fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions = NeighborOptions.Default,
    ): Flow<GraphVertex>

    suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): GraphPath?

    fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): Flow<GraphPath>
}
