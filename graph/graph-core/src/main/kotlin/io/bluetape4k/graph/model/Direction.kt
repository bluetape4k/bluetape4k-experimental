package io.bluetape4k.graph.model

/**
 * 그래프 간선 탐색 방향.
 */
enum class Direction {
    /** 출발 정점 -> 도착 정점 방향 */
    OUTGOING,
    /** 도착 정점 -> 출발 정점 방향 */
    INCOMING,
    /** 양방향 */
    BOTH,
}
