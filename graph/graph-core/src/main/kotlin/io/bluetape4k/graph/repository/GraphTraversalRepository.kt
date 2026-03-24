package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex

/**
 * 그래프 순회(Traversal) 저장소.
 */
interface GraphTraversalRepository {
    suspend fun neighbors(
        startId: GraphElementId,
        edgeLabel: String,
        direction: Direction = Direction.OUTGOING,
        depth: Int = 1,
    ): List<GraphVertex>

    suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String? = null,
        maxDepth: Int = 10,
    ): GraphPath?

    suspend fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String? = null,
        maxDepth: Int = 5,
    ): List<GraphPath>
}
