package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
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
 * Memgraph용 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * Memgraph는 Neo4j Bolt 프로토콜 + openCypher를 완전 호환하므로
 * [org.neo4j.driver.Driver]를 그대로 사용한다.
 *
 * Memgraph는 `elementId()` 대신 정수형 `id()`를 사용한다.
 * Cypher 쿼리에서 `id(n) = toInteger($id)` 형태로 노드를 조회한다.
 *
 * [ReactiveSession] + [Flow]를 사용한다.
 *
 * @param driver Neo4j Java Driver (외부 소유, Memgraph bolt URL로 생성)
 * @param database 데이터베이스 이름 (기본: "memgraph")
 */
class MemgraphGraphSuspendOperations(
    private val driver: Driver,
    private val database: String = "memgraph",
): GraphSuspendOperations {

    companion object: KLoggingChannel() {
        private val SAFE_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }

    private fun String.requireSafeIdentifier(paramName: String): String = apply {
        require(SAFE_IDENTIFIER.matches(this)) { "$paramName must be a valid identifier (alphanumeric/_): $this" }
    }

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
    ): Flow<T> = channelFlow {
        val s = session()
        try {
            val result = s.run(Query(cypher, params)).awaitSingle()
            result.records().asFlow().map(mapper).collect { send(it) }
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    // -- GraphSuspendSession --

    override suspend fun createGraph(name: String) {
        log.debug { "Memgraph graph session initialized for database: $name" }
    }

    override suspend fun dropGraph(name: String) {
        runQuery("MATCH (n) DETACH DELETE n") { it }
    }

    override suspend fun graphExists(name: String): Boolean {
        val s = session()
        return try {
            val result = s.run(Query("RETURN 1")).awaitSingle()
            result.records().awaitFirstOrNull() != null
        } catch (e: org.neo4j.driver.exceptions.ServiceUnavailableException) {
            log.warn(e) { "Memgraph service unavailable for database: $name" }
            false
        } catch (e: org.neo4j.driver.exceptions.DatabaseException) {
            false
        } catch (e: Exception) {
            log.warn(e) { "Unexpected error checking graphExists for: $name" }
            false
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    override fun close() { /* driver는 외부 소유 */
    }

    // -- GraphSuspendVertexRepository --

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val cypher = $$"CREATE (n:$$label$$propsClause) RETURN n"
        val params = if (properties.isEmpty()) emptyMap() else mapOf("props" to properties)
        return runQuery(cypher, params) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create vertex: $label")
    }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        return runQuery(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) RETURN n",
            mapOf("id" to id.value),
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }.firstOrNull()
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { $$"n.$$it = $$$it" }
        return flowQuery(
            $$"MATCH (n:$$label)$$whereClause RETURN n",
            filter,
        ) {
            MemgraphRecordMapper.recordToVertex(it)
        }
    }

    override suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        if (properties.isEmpty()) return findVertexById(label, id)
        val setClause = properties.keys.joinToString(", ") { $$"n.$$it = $$$it" }
        val params = properties + mapOf("id" to id.value)
        return runQuery(
            $$"MATCH (n:$$label) WHERE id(n) = toInteger($id) SET $$setClause RETURN n",
            params,
        ) { MemgraphRecordMapper.recordToVertex(it) }.firstOrNull()
    }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val s = session()
        return try {
            val result = s.run(
                Query($$"MATCH (n:$$label) WHERE id(n) = toInteger($id) DETACH DELETE n", mapOf("id" to id.value))
            ).awaitSingle()
            result.consume().awaitSingle().counters().nodesDeleted() > 0
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    override suspend fun countVertices(label: String): Long {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val s = session()
        return try {
            val result = s.run(Query($$"MATCH (n:$$label) RETURN count(n) AS cnt")).awaitSingle()
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
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val propsClause = if (properties.isEmpty()) "" else $$" $props"
        val params = mutableMapOf<String, Any?>("fromId" to fromId.value, "toId" to toId.value)
        if (properties.isNotEmpty()) params["props"] = properties
        return runQuery(
            $$"MATCH (a), (b) WHERE id(a) = toInteger($fromId) AND id(b) = toInteger($toId) " +
                    $$"CREATE (a)-[r:$$label$$propsClause]->(b) RETURN r",
            params,
        ) {
            MemgraphRecordMapper.recordToEdge(it)
        }.firstOrNull() ?: throw GraphQueryException("Failed to create edge: $label")
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val whereClause = if (filter.isEmpty()) "" else
            " WHERE " + filter.keys.joinToString(" AND ") { $$"r.$$it = $$$it" }
        return flowQuery(
            $$"MATCH ()-[r:$$label]->()$$whereClause RETURN r",
            filter,
        ) {
            MemgraphRecordMapper.recordToEdge(it)
        }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean {
        label.requireNotBlank("label").requireSafeIdentifier("label")
        val s = session()
        return try {
            val result = s.run(
                Query($$"MATCH ()-[r:$$label]->() WHERE id(r) = toInteger($id) DELETE r", mapOf("id" to id.value))
            ).awaitSingle()
            result.consume().awaitSingle().counters().relationshipsDeleted() > 0
        } finally {
            withContext(NonCancellable) { s.close<Void>().awaitFirstOrNull() }
        }
    }

    // -- GraphSuspendTraversalRepository --

    override fun neighbors(
        startId: GraphElementId,
        options: NeighborOptions,
    ): Flow<GraphVertex> {
        options.edgeLabel?.requireNotBlank("edgeLabel")
        val depthStr = if (options.maxDepth == 1) "" else $$"*1..$${ options.maxDepth }"
        val edgePart = if (options.edgeLabel != null) $$":$${ options.edgeLabel }$$depthStr" else depthStr
        val pattern = when (options.direction) {
            Direction.OUTGOING -> $$"(start)-[$$edgePart]->(neighbor)"
            Direction.INCOMING -> $$"(start)<-[$$edgePart]-(neighbor)"
            Direction.BOTH     -> $$"(start)-[$$edgePart]-(neighbor)"
        }
        return flowQuery(
            $$"MATCH $$pattern WHERE id(start) = toInteger($startId) RETURN DISTINCT neighbor",
            mapOf("startId" to startId.value),
        ) {
            MemgraphRecordMapper.recordToVertex(it, "neighbor")
        }
    }

    override suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        // Memgraph는 shortestPath() 미지원 → depth-limited MATCH + ORDER BY length(p) LIMIT 1 사용
        val relPattern = if (options.edgeLabel != null)
            ":" + options.edgeLabel + "*1.." + options.maxDepth
        else
            "*1.." + options.maxDepth
        return runQuery(
            "MATCH p = (a)-[$relPattern]-(b) " +
                    "WHERE id(a) = toInteger(\$fromId) AND id(b) = toInteger(\$toId) " +
                    "RETURN p ORDER BY length(p) LIMIT 1",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            MemgraphRecordMapper.recordToPath(it)
        }.firstOrNull()
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): Flow<GraphPath> {
        val relPattern = if (options.edgeLabel != null) $$":$${ options.edgeLabel }*1..$${ options.maxDepth }" else $$"*1..$${ options.maxDepth }"
        return flowQuery(
            $$"MATCH p = (a)-[$$relPattern]-(b) " +
                    $$"WHERE id(a) = toInteger($fromId) AND id(b) = toInteger($toId) RETURN p",
            mapOf("fromId" to fromId.value, "toId" to toId.value),
        ) {
            MemgraphRecordMapper.recordToPath(it)
        }
    }
}
