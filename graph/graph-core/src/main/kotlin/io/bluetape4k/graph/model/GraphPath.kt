package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 그래프 경로의 단계 (정점 또는 간선).
 */
sealed class PathStep {
    data class VertexStep(val vertex: GraphVertex): PathStep()
    data class EdgeStep(val edge: GraphEdge): PathStep()
}

/**
 * 그래프 경로. steps는 [VertexStep, EdgeStep, VertexStep, ...] 교차 순서.
 */
data class GraphPath(
    val steps: List<PathStep>,
): Serializable {
    val vertices: List<GraphVertex>
        get() = steps.filterIsInstance<PathStep.VertexStep>().map { it.vertex }
    val edges: List<GraphEdge>
        get() = steps.filterIsInstance<PathStep.EdgeStep>().map { it.edge }
    val length: Int
        get() = edges.size
    val isEmpty: Boolean
        get() = steps.isEmpty()

    companion object {
        const val serializableUID = 1L

        val EMPTY = GraphPath(emptyList())

        fun of(vararg vertices: GraphVertex): GraphPath =
            GraphPath(vertices.map { PathStep.VertexStep(it) })
    }
}
