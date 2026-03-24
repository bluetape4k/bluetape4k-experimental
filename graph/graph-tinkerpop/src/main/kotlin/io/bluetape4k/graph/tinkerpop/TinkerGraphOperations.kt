package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.support.requireNotBlank
import org.apache.tinkerpop.gremlin.process.traversal.Traversal
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.`__` as AnonymousTraversal
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

/**
 * Apache TinkerPop TinkerGraph 기반 [GraphOperations] 구현체 (동기 방식).
 *
 * TinkerGraph는 in-memory JVM 그래프 데이터베이스이다.
 */
class TinkerGraphOperations : GraphOperations {

    companion object : KLogging()

    private val graph: TinkerGraph = TinkerGraph.open()
    private val g: GraphTraversalSource get() = graph.traversal()

    override fun close() {
        graph.close()
    }

    // -- GraphSession --

    override fun createGraph(name: String) {
        log.debug { "TinkerGraph session initialized for graph: $name" }
    }

    override fun dropGraph(name: String) {
        g.V().drop().iterate()
    }

    override fun graphExists(name: String): Boolean = true

    // -- GraphVertexRepository --

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label")
        val traversal = g.addV(label)
        properties.forEach { (key, value) ->
            if (value != null) traversal.property(key, value)
        }
        val v = traversal.next()
        return GremlinRecordMapper.vertexToGraphVertex(v)
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label")
        val idValue = id.value.toLongOrNull() ?: return null
        val optional = g.V(idValue).hasLabel(label).tryNext()
        return if (optional.isPresent) GremlinRecordMapper.vertexToGraphVertex(optional.get()) else null
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        label.requireNotBlank("label")
        val traversal = g.V().hasLabel(label)
        filter.forEach { (key, value) ->
            traversal.has(key, value)
        }
        return traversal.toList().map { GremlinRecordMapper.vertexToGraphVertex(it) }
    }

    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label")
        val idValue = id.value.toLongOrNull() ?: return null
        val optional = g.V(idValue).hasLabel(label).tryNext()
        if (!optional.isPresent) return null
        if (properties.isEmpty()) return GremlinRecordMapper.vertexToGraphVertex(optional.get())

        val traversal = g.V(idValue).hasLabel(label)
        properties.forEach { (key, value) ->
            traversal.property(key, value)
        }
        val v = traversal.next()
        return GremlinRecordMapper.vertexToGraphVertex(v)
    }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val idValue = id.value.toLongOrNull() ?: return false
        val optional = g.V(idValue).hasLabel(label).tryNext()
        if (!optional.isPresent) return false
        g.V(idValue).drop().iterate()
        return true
    }

    override fun countVertices(label: String): Long {
        label.requireNotBlank("label")
        return g.V().hasLabel(label).count().next()
    }

    // -- GraphEdgeRepository --

    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label")
        val fromIdValue = fromId.value.toLongOrNull()
            ?: throw GraphQueryException("Invalid fromId: ${fromId.value}")
        val toIdValue = toId.value.toLongOrNull()
            ?: throw GraphQueryException("Invalid toId: ${toId.value}")

        val traversal = g.V(fromIdValue).addE(label).to(AnonymousTraversal.V<Vertex>(toIdValue))
        properties.forEach { (key, value) ->
            if (value != null) traversal.property(key, value)
        }
        val e = traversal.next()
        return GremlinRecordMapper.edgeToGraphEdge(e)
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        label.requireNotBlank("label")
        val traversal = g.E().hasLabel(label)
        filter.forEach { (key, value) ->
            traversal.has(key, value)
        }
        return traversal.toList().map { GremlinRecordMapper.edgeToGraphEdge(it) }
    }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val idValue = id.value.toLongOrNull() ?: return false
        val optional = g.E(idValue).hasLabel(label).tryNext()
        if (!optional.isPresent) return false
        g.E(idValue).drop().iterate()
        return true
    }

    // -- GraphTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): List<GraphVertex> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val idValue = startId.value.toLongOrNull() ?: return emptyList()

        if (options.maxDepth == 1) {
            val traversal = when (options.direction) {
                Direction.OUTGOING -> if (options.edgeLabel != null) g.V(idValue).out(options.edgeLabel) else g.V(idValue).out()
                Direction.INCOMING -> if (options.edgeLabel != null) g.V(idValue).`in`(options.edgeLabel) else g.V(idValue).`in`()
                Direction.BOTH -> if (options.edgeLabel != null) g.V(idValue).both(options.edgeLabel) else g.V(idValue).both()
            }
            return traversal.dedup().toList().map { GremlinRecordMapper.vertexToGraphVertex(it) }
        }

        // depth > 1: repeat/times/emit
        @Suppress("UNCHECKED_CAST")
        val step: Traversal<*, Vertex> = when (options.direction) {
            Direction.OUTGOING -> if (options.edgeLabel != null) AnonymousTraversal.out(options.edgeLabel) else AnonymousTraversal.out()
            Direction.INCOMING -> if (options.edgeLabel != null) AnonymousTraversal.`in`(options.edgeLabel) else AnonymousTraversal.`in`()
            Direction.BOTH -> if (options.edgeLabel != null) AnonymousTraversal.both(options.edgeLabel) else AnonymousTraversal.both()
        } as Traversal<*, Vertex>
        return g.V(idValue)
            .repeat(step)
            .times(options.maxDepth)
            .emit()
            .dedup()
            .toList()
            .map { GremlinRecordMapper.vertexToGraphVertex(it) }
    }

    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val fromIdValue = fromId.value.toLongOrNull() ?: return null
        val toIdValue = toId.value.toLongOrNull() ?: return null

        @Suppress("UNCHECKED_CAST")
        val step = (if (options.edgeLabel != null) AnonymousTraversal.both(options.edgeLabel) else AnonymousTraversal.both())
            .simplePath() as Traversal<*, Vertex>

        val paths = try {
            g.V(fromIdValue)
                .repeat(step)
                .until(AnonymousTraversal.hasId<Any>(toIdValue))
                .path()
                .limit(1)
                .toList()
        } catch (_: Exception) {
            emptyList()
        }

        return paths.firstOrNull()?.let { GremlinRecordMapper.pathToGraphPath(it) }
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> {
        val fromIdValue = fromId.value.toLongOrNull() ?: return emptyList()
        val toIdValue = toId.value.toLongOrNull() ?: return emptyList()

        @Suppress("UNCHECKED_CAST")
        val step = (if (options.edgeLabel != null) AnonymousTraversal.both(options.edgeLabel) else AnonymousTraversal.both())
            .simplePath() as Traversal<*, Vertex>

        val paths = try {
            g.V(fromIdValue)
                .repeat(step)
                .until(AnonymousTraversal.hasId<Any>(toIdValue))
                .path()
                .toList()
        } catch (_: Exception) {
            emptyList()
        }

        return paths.map { GremlinRecordMapper.pathToGraphPath(it) }
    }
}
