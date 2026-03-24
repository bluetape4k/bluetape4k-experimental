package io.bluetape4k.graph.neo4j

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
import org.neo4j.driver.Driver
import org.neo4j.driver.Record
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig

/**
 * Neo4j Java Driver 기반 [GraphOperations] 구현체 (동기 방식).
 *
 * blocking [Session]을 사용한다.
 *
 * @param driver Neo4j Java Driver (외부 소유)
 * @param database 데이터베이스 이름 (기본: "neo4j")
 */
class Neo4jGraphOperations(
    private val driver: Driver,
    private val database: String = "neo4j",
): GraphOperations {

    companion object: KLogging()

    private fun session(): Session =
        driver.session(SessionConfig.builder().withDatabase(database).build())

    private fun <T> runQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): List<T> =
        session().use { s -> s.run(cypher, params).list(mapper) }

    // -- GraphSession --

    override fun createGraph(name: String) {
        log.debug { "Neo4j graph session initialized for database: $name" }
    }

    override fun dropGraph(name: String) {
        runQuery("MATCH (n) DETACH DELETE n") { it }
    }

    override fun graphExists(name: String): Boolean {
        return try {
            session().use { s ->
                s.run("RETURN 1")
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    override fun close() { /* driver는 외부 소유 */
    }

    // -- GraphVertexRepository --

    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label")
        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val cypher = $$"CREATE (n:$$label$$propsClause) RETURN n"
        val params = if (properties.isEmpty()) emptyMap() else mapOf("props" to properties)
        return runQuery(cypher, params) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label")
        return runQuery(
            $$"MATCH (n:$$label) WHERE elementId(n) = $id RETURN n",
            mapOf("id" to id.value),
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        label.requireNotBlank("label")
        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { $$"n.$$it = $$$it" }
        return runQuery(
            $$"MATCH (n:$$label)$$whereClause RETURN n",
            filter,
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }
    }

    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label")
        if (properties.isEmpty()) return findVertexById(label, id)
        val setClause = properties.keys.joinToString(", ") { $$"n.$$it = $$$it" }
        val params = properties + mapOf("id" to id.value)
        return runQuery(
            $$"MATCH (n:$$label) WHERE elementId(n) = $id SET $$setClause RETURN n",
            params,
        ) {
            Neo4jRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        return session().use { s ->
            val result = s.run(
                $$"MATCH (n:$$label) WHERE elementId(n) = $id DETACH DELETE n",
                mapOf("id" to id.value)
            )
            result.consume().counters().nodesDeleted() > 0
        }
    }

    override fun countVertices(label: String): Long {
        label.requireNotBlank("label")
        return session().use { s ->
            s.run($$"MATCH (n:$$label) RETURN count(n) AS cnt").single().get("cnt").asLong()
        }
    }

    // -- GraphEdgeRepository --

    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label")
        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val params = mutableMapOf<String, Any?>("fromId" to fromId.value, "toId" to toId.value)
        if (properties.isNotEmpty()) params["props"] = properties
        return runQuery(
            $$"MATCH (a), (b) WHERE elementId(a) = $fromId AND elementId(b) = $toId " +
                    $$"CREATE (a)-[r:$$label$$propsClause]->(b) RETURN r",
            params,
        ) {
            Neo4jRecordMapper.recordToEdge(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create edge: $label")
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        label.requireNotBlank("label")
        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { $$"r.$$it = $$$it" }
        return runQuery(
            $$"MATCH ()-[r:$$label]->()$$whereClause RETURN r",
            filter,
        ) { Neo4jRecordMapper.recordToEdge(it) }
    }

    override fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        return session().use { s ->
            val result = s.run(
                $$"MATCH ()-[r:$$label]->() WHERE elementId(r) = $id DELETE r",
                mapOf("id" to id.value)
            )
            result.consume().counters().relationshipsDeleted() > 0
        }
    }

    // -- GraphTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): List<GraphVertex> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val depthStr = if (options.maxDepth == 1) "" else $$"*1..${ options.maxDepth }"
        val edgePart = if (options.edgeLabel != null) $$":${ options.edgeLabel }$$depthStr" else depthStr
        val pattern = when (options.direction) {
            Direction.OUTGOING -> $$"(start)-[$$edgePart]->(neighbor)"
            Direction.INCOMING -> $$"(start)<-[$$edgePart]-(neighbor)"
            Direction.BOTH     -> $$"(start)-[$$edgePart]-(neighbor)"
        }
        return runQuery(
            $$"MATCH $$pattern WHERE elementId(start) = $startId RETURN DISTINCT neighbor",
            mapOf("startId" to startId.value),
        ) {
            Neo4jRecordMapper.recordToVertex(it, "neighbor")
        }
    }

    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val relPattern = if (options.edgeLabel != null) $$":${ options.edgeLabel }*1..${ options.maxDepth }" else $$"*1..${ options.maxDepth }"
        return runQuery(
            $$"MATCH p = shortestPath((a)-[$$relPattern]-(b)) " +
                    $$"WHERE elementId(a) = $fromId AND elementId(b) = $toId RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            Neo4jRecordMapper.recordToPath(it)
        }.firstOrNull()
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> {
        val relPattern = if (options.edgeLabel != null) $$":${ options.edgeLabel }*1..${ options.maxDepth }" else $$"*1..${ options.maxDepth }"
        return runQuery(
            $$"MATCH p = (a)-[$$relPattern]-(b) " +
                    $$"WHERE elementId(a) = $fromId AND elementId(b) = $toId RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            Neo4jRecordMapper.recordToPath(it)
        }
    }
}
