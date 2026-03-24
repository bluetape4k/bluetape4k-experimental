package io.bluetape4k.graph.neo4j

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
 * Neo4j Driver [Record]를 Graph 모델로 변환합니다.
 */
object Neo4jRecordMapper : KLogging() {

    fun nodeToVertex(node: Node): GraphVertex {
        val id = GraphElementId(node.elementId())
        val label = node.labels().firstOrNull() ?: "Unknown"
        val properties = node.asMap()
        return GraphVertex(id, label, properties)
    }

    fun relationshipToEdge(rel: Relationship): GraphEdge {
        val id = GraphElementId(rel.elementId())
        val startId = GraphElementId(rel.startNodeElementId())
        val endId = GraphElementId(rel.endNodeElementId())
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
