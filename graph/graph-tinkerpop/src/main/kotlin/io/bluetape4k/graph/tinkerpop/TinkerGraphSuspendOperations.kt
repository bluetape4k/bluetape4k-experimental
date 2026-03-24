package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Apache TinkerPop TinkerGraph 기반 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * TinkerGraph는 in-process이므로 [Dispatchers.IO]로 래핑한다.
 *
 * @param delegate 동기 방식 [TinkerGraphOperations] (내부 위임)
 */
class TinkerGraphSuspendOperations(
    private val delegate: TinkerGraphOperations = TinkerGraphOperations(),
) : GraphSuspendOperations {

    companion object : KLoggingChannel()

    override fun close() {
        delegate.close()
    }

    // -- GraphSuspendSession --

    override suspend fun createGraph(name: String) = withContext(Dispatchers.IO) {
        delegate.createGraph(name)
    }

    override suspend fun dropGraph(name: String) = withContext(Dispatchers.IO) {
        delegate.dropGraph(name)
    }

    override suspend fun graphExists(name: String): Boolean = withContext(Dispatchers.IO) {
        delegate.graphExists(name)
    }

    // -- GraphSuspendVertexRepository --

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
        withContext(Dispatchers.IO) {
            delegate.createVertex(label, properties)
        }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? =
        withContext(Dispatchers.IO) {
            delegate.findVertexById(label, id)
        }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.findVerticesByLabel(label, filter)
        }
        list.forEach { emit(it) }
    }

    override suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        withContext(Dispatchers.IO) {
            delegate.updateVertex(label, id, properties)
        }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean =
        withContext(Dispatchers.IO) {
            delegate.deleteVertex(label, id)
        }

    override suspend fun countVertices(label: String): Long =
        withContext(Dispatchers.IO) {
            delegate.countVertices(label)
        }

    // -- GraphSuspendEdgeRepository --

    override suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge = withContext(Dispatchers.IO) {
        delegate.createEdge(fromId, toId, label, properties)
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.findEdgesByLabel(label, filter)
        }
        list.forEach { emit(it) }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean =
        withContext(Dispatchers.IO) {
            delegate.deleteEdge(label, id)
        }

    // -- GraphSuspendTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): Flow<GraphVertex> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.neighbors(startId, options)
        }
        list.forEach { emit(it) }
    }

    override suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? = withContext(Dispatchers.IO) {
        delegate.shortestPath(fromId, toId, options)
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): Flow<GraphPath> = flow {
        val list = withContext(Dispatchers.IO) {
            delegate.allPaths(fromId, toId, options)
        }
        list.forEach { emit(it) }
    }
}
