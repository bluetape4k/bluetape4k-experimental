package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging
import org.neo4j.driver.Record
import org.neo4j.driver.types.Node
import org.neo4j.driver.types.Path
import org.neo4j.driver.types.Relationship

/**
 * Neo4j Driver [Record]를 Graph 모델로 변환합니다 (Memgraph 용).
 *
 * Memgraph는 `elementId()` 대신 정수형 `id()`를 사용한다.
 * [GraphElementId.value]에는 정수 ID의 문자열 표현을 저장한다.
 */
object MemgraphRecordMapper : KLogging() {

    fun nodeToVertex(node: Node): GraphVertex {
        val id = GraphElementId(node.id().toString())
        val label = node.labels().firstOrNull() ?: "Unknown"
        val properties = node.asMap()
        return GraphVertex(id, label, properties)
    }

    fun relationshipToEdge(rel: Relationship): GraphEdge {
        val id = GraphElementId(rel.id().toString())
        val startId = GraphElementId(rel.startNodeId().toString())
        val endId = GraphElementId(rel.endNodeId().toString())
        return GraphEdge(id, rel.type(), startId, endId, rel.asMap())
    }

    fun pathToGraphPath(path: Path): GraphPath {
        val steps = mutableListOf<PathStep>()
        val nodes = path.nodes().toList()
        val rels = path.relationships().toList()

        nodes.forEachIndexed { index, node ->
            steps.add(PathStep.VertexStep(nodeToVertex(node)))
            if (index < rels.size) {
                steps.add(PathStep.EdgeStep(relationshipToEdge(rels[index])))
            }
        }
        return GraphPath(steps)
    }

    fun recordToVertex(record: Record, key: String = "n"): GraphVertex =
        nodeToVertex(record[key].asNode())

    fun recordToEdge(record: Record, key: String = "r"): GraphEdge =
        relationshipToEdge(record[key].asRelationship())

    fun recordToPath(record: Record, key: String = "p"): GraphPath =
        pathToGraphPath(record[key].asPath())
}
