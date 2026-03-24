package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext
import org.neo4j.driver.Driver
import org.neo4j.driver.Query
import org.neo4j.driver.Record
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.reactivestreams.ReactiveSession

/**
 * Neo4j Java Driver 기반 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * [ReactiveSession] + [Flow]를 사용한다.
 *
 * @param driver Neo4j Java Driver (외부 소유)
 * @param database 데이터베이스 이름 (기본: "neo4j")
 */
class Neo4jGraphSuspendOperations(
    private val driver: Driver,
    private val database: String = "neo4j",
) : GraphSuspendOperations {

    companion object : KLoggingChannel()

    private fun session(): ReactiveSession =
        driver.session(
            ReactiveSession::class.java,
            SessionConfig.builder().withDatabase(database).build(),
        )

    /**
     * 단일값/삭제 등 suspend 메서드용 쿼리 헬퍼.
     */
    private suspend fun <T> runQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): List<T> {
        val s = session()
        return try {
            val result = s.run(Query(cypher, params)).awaitSingle()
            result.records().asFlow().toList().map(mapper)
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    /**
     * 컬렉션 [Flow] 반환용 쿼리 헬퍼.
     *
     * 취소 시에도 세션이 안전하게 닫히도록 [NonCancellable]을 사용한다.
     */
    private fun <T> flowQuery(
        cypher: String,
        params: Map<String, Any?> = emptyMap(),
        mapper: (Record) -> T,
    ): Flow<T> = flow {
        val s = session()
        try {
            val result = s.run(Query(cypher, params)).awaitSingle()
            emitAll(result.records().asFlow().map(mapper))
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    // -- GraphSuspendSession --

    override suspend fun createGraph(name: String) {
        log.debug { "Neo4j graph session initialized for database: $name" }
    }

    override suspend fun dropGraph(name: String) {
        runQuery("MATCH (n) DETACH DELETE n") { it }
    }

    override suspend fun graphExists(name: String): Boolean {
        val s = session()
        return try {
            val result = s.run(Query("RETURN 1")).awaitSingle()
            result.records().awaitFirstOrNull() != null
        } catch (e: Exception) {
            false
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    override fun close() { /* driver는 외부 소유 */ }

    // -- GraphSuspendVertexRepository --

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label")
        val propsClause = if (properties.isEmpty()) "" else " \$props"
        val cypher = "CREATE (n:$label$propsClause) RETURN n"
        val params = if (properties.isEmpty()) emptyMap() else mapOf("props" to properties)
        return runQuery(cypher, params) { Neo4jRecordMapper.recordToVertex(it) }
            .firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label")
        return runQuery(
            "MATCH (n:$label) WHERE elementId(n) = \$id RETURN n",
            mapOf("id" to id.value),
        ) { Neo4jRecordMapper.recordToVertex(it) }.firstOrNull()
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
        label.requireNotBlank("label")
        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { "n.$it = \$$it" }
        return flowQuery(
            "MATCH (n:$label)$whereClause RETURN n",
            filter,
        ) { Neo4jRecordMapper.recordToVertex(it) }
    }

    override suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label")
        if (properties.isEmpty()) return findVertexById(label, id)
        val setClause = properties.keys.joinToString(", ") { "n.$it = \$$it" }
        val params = properties + mapOf("id" to id.value)
        return runQuery(
            "MATCH (n:$label) WHERE elementId(n) = \$id SET $setClause RETURN n",
            params,
        ) { Neo4jRecordMapper.recordToVertex(it) }.firstOrNull()
    }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val s = session()
        return try {
            val result = s.run(
                Query("MATCH (n:$label) WHERE elementId(n) = \$id DETACH DELETE n", mapOf("id" to id.value))
            ).awaitSingle()
            result.consume().awaitSingle().counters().nodesDeleted() > 0
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    override suspend fun countVertices(label: String): Long {
        label.requireNotBlank("label")
        val s = session()
        return try {
            val result = s.run(Query("MATCH (n:$label) RETURN count(n) AS cnt")).awaitSingle()
            result.records().awaitFirstOrNull()?.get("cnt")?.asLong() ?: 0L
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    // -- GraphSuspendEdgeRepository --

    override suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label")
        val propsClause = if (properties.isEmpty()) "" else " \$props"
        val params = mutableMapOf<String, Any?>("fromId" to fromId.value, "toId" to toId.value)
        if (properties.isNotEmpty()) params["props"] = properties
        return runQuery(
            "MATCH (a), (b) WHERE elementId(a) = \$fromId AND elementId(b) = \$toId " +
                "CREATE (a)-[r:$label$propsClause]->(b) RETURN r",
            params,
        ) { Neo4jRecordMapper.recordToEdge(it) }
            .firstOrNull() ?: throw GraphQueryException("Failed to create edge: $label")
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
        label.requireNotBlank("label")
        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { "r.$it = \$$it" }
        return flowQuery(
            "MATCH ()-[r:$label]->()$whereClause RETURN r",
            filter,
        ) { Neo4jRecordMapper.recordToEdge(it) }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label")
        val s = session()
        return try {
            val result = s.run(
                Query("MATCH ()-[r:$label]->() WHERE elementId(r) = \$id DELETE r", mapOf("id" to id.value))
            ).awaitSingle()
            result.consume().awaitSingle().counters().relationshipsDeleted() > 0
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    // -- GraphSuspendTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        edgeLabel: String,
        direction: Direction,
        depth: Int,
    ): Flow<GraphVertex> {
        edgeLabel.requireNotBlank("edgeLabel")
        val depthStr = if (depth == 1) "" else "*1..$depth"
        val pattern = when (direction) {
            Direction.OUTGOING -> "(start)-[:$edgeLabel$depthStr]->(neighbor)"
            Direction.INCOMING -> "(start)<-[:$edgeLabel$depthStr]-(neighbor)"
            Direction.BOTH -> "(start)-[:$edgeLabel$depthStr]-(neighbor)"
        }
        return flowQuery(
            "MATCH $pattern WHERE elementId(start) = \$startId RETURN DISTINCT neighbor",
            mapOf("startId" to startId.value),
        ) { Neo4jRecordMapper.recordToVertex(it, "neighbor") }
    }

    override suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String?,
        maxDepth: Int,
    ): GraphPath? {
        val relPattern = if (edgeLabel != null) ":$edgeLabel*1..$maxDepth" else "*1..$maxDepth"
        return runQuery(
            "MATCH p = shortestPath((a)-[$relPattern]-(b)) " +
                "WHERE elementId(a) = \$fromId AND elementId(b) = \$toId RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) { Neo4jRecordMapper.recordToPath(it) }.firstOrNull()
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String?,
        maxDepth: Int,
    ): Flow<GraphPath> {
        val relPattern = if (edgeLabel != null) ":$edgeLabel*1..$maxDepth" else "*1..$maxDepth"
        return flowQuery(
            "MATCH p = (a)-[$relPattern]-(b) " +
                "WHERE elementId(a) = \$fromId AND elementId(b) = \$toId RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) { Neo4jRecordMapper.recordToPath(it) }
    }
}
