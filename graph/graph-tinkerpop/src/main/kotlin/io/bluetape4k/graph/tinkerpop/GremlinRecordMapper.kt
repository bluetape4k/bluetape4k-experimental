package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.process.traversal.Path as GremlinPath

/**
 * TinkerPop Gremlin 구조체를 Graph 모델로 변환합니다.
 */
object GremlinRecordMapper : KLogging() {

    fun vertexToGraphVertex(v: Vertex): GraphVertex {
        val id = GraphElementId(v.id().toString())
        val label = v.label()
        val properties = mutableMapOf<String, Any?>()
        v.properties<Any>().forEachRemaining { vp ->
            properties[vp.key()] = vp.value()
        }
        return GraphVertex(id, label, properties)
    }

    fun edgeToGraphEdge(e: Edge): GraphEdge {
        val id = GraphElementId(e.id().toString())
        val startId = GraphElementId(e.outVertex().id().toString())
        val endId = GraphElementId(e.inVertex().id().toString())
        val properties = mutableMapOf<String, Any?>()
        e.properties<Any>().forEachRemaining { ep ->
            properties[ep.key()] = ep.value()
        }
        return GraphEdge(id, e.label(), startId, endId, properties)
    }

    fun pathToGraphPath(p: GremlinPath): GraphPath {
        val steps = mutableListOf<PathStep>()
        p.objects().forEach { obj ->
            when (obj) {
                is Vertex -> steps.add(PathStep.VertexStep(vertexToGraphVertex(obj)))
                is Edge -> steps.add(PathStep.EdgeStep(edgeToGraphEdge(obj)))
            }
        }
        return GraphPath(steps)
    }
}
