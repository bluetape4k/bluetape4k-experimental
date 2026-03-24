package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions

/**
 * 그래프 순회(Traversal) 저장소 (동기 방식).
 */
interface GraphTraversalRepository {
    fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions = NeighborOptions.Default,
    ): List<GraphVertex>

    fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): GraphPath?

    fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): List<GraphPath>
}
