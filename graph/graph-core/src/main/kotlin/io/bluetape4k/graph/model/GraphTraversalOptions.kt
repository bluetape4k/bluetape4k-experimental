package io.bluetape4k.graph.model

/**
 * 그래프 순회 옵션의 기반 sealed class.
 */
sealed class GraphTraversalOptions {
    abstract val maxDepth: Int
}

/**
 * [GraphTraversalRepository.neighbors] 호출 옵션.
 *
 * @param edgeLabel 탐색할 엣지 레이블. null이면 모든 레이블 탐색.
 * @param direction 탐색 방향 (OUTGOING, INCOMING, BOTH)
 * @param maxDepth 최대 탐색 깊이 (기본값: 1)
 */
data class NeighborOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.OUTGOING,
    override val maxDepth: Int = 1,
) : GraphTraversalOptions() {
    companion object {
        val Default = NeighborOptions()
    }
}

/**
 * [GraphTraversalRepository.shortestPath] / [GraphTraversalRepository.allPaths] 호출 옵션.
 *
 * @param edgeLabel 탐색할 엣지 레이블. null이면 모든 레이블 탐색.
 * @param maxDepth 최대 탐색 깊이 (기본값: 10)
 */
data class PathOptions(
    val edgeLabel: String? = null,
    override val maxDepth: Int = 10,
) : GraphTraversalOptions() {
    companion object {
        val Default = PathOptions()
    }
}
