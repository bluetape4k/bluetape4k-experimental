package io.bluetape4k.graph.age

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.age.sql.AgeSql
import io.bluetape4k.graph.age.sql.AgeTypeParser
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Apache AGE + PostgreSQL 기반 [GraphSuspendOperations] 구현체 (코루틴 방식).
 *
 * **AGE 초기화 전략:**
 * - `CREATE EXTENSION IF NOT EXISTS age`: PostgreSQLAgeServer 시작 시 1회 실행 (DB 수준)
 * - 매 connection: `LOAD 'age'` + `SET search_path` 는 HikariCP connectionInitSql로 처리
 *
 * **Dispatcher 경계:**
 * - 단일값 반환 `suspend fun`은 내부적으로 [Dispatchers.IO]에서 실행
 * - 컬렉션 반환 `fun ... : Flow<T>`는 `flow { }` 빌더 안에서 [Dispatchers.IO]로 감싸 실행
 *
 * @param database Exposed Database 인스턴스 (외부에서 주입, close 시 닫지 않음)
 * @param graphName AGE 그래프 이름
 */
class AgeGraphSuspendOperations(
    private val database: Database,
    private val graphName: String,
) : GraphSuspendOperations {

    companion object : KLoggingChannel()

    init {
        graphName.requireNotBlank("graphName")
    }

    override suspend fun createGraph(name: String): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            exec(AgeSql.loadAge())
            exec(AgeSql.setSearchPath())
            try {
                exec(AgeSql.createGraph(name))
            } catch (e: Exception) {
                // 이미 존재하는 경우 무시
                log.debug("Graph '$name' may already exist: ${e.message}")
            }
        }
    }

    override suspend fun dropGraph(name: String): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            exec(AgeSql.loadAge())
            exec(AgeSql.setSearchPath())
            exec(AgeSql.dropGraph(name))
        }
    }

    override suspend fun graphExists(name: String): Boolean = withContext(Dispatchers.IO) {
        transaction(database) {
            exec(AgeSql.loadAge())
            exec(AgeSql.setSearchPath())
            var count = 0L
            exec(AgeSql.graphExists(name)) { rs ->
                if (rs.next()) count = rs.getLong(1)
            }
            count > 0
        }
    }

    override fun close() {
        // database는 외부 소유이므로 닫지 않음
    }

    override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        label.requireNotBlank("label")
        return withContext(Dispatchers.IO) {
            transaction(database) {
                exec(AgeSql.loadAge())
                exec(AgeSql.setSearchPath())
                var vertex: GraphVertex? = null
                exec(AgeSql.createVertex(graphName, label, properties)) { rs ->
                    if (rs.next()) vertex = AgeTypeParser.parseVertex(rs.getString("v"))
                }
                vertex ?: throw GraphQueryException("Failed to create vertex with label=$label")
            }
        }
    }

    override suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        label.requireNotBlank("label")
        return withContext(Dispatchers.IO) {
            transaction(database) {
                exec(AgeSql.loadAge())
                exec(AgeSql.setSearchPath())
                val longId = id.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")
                var vertex: GraphVertex? = null
                exec(AgeSql.matchVertexById(graphName, label, longId)) { rs ->
                    if (rs.next()) vertex = AgeTypeParser.parseVertex(rs.getString("v"))
                }
                vertex
            }
        }
    }

    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
        label.requireNotBlank("label")
        return flow {
            val vertices = withContext(Dispatchers.IO) {
                transaction(database) {
                    exec(AgeSql.loadAge())
                    exec(AgeSql.setSearchPath())
                    val list = mutableListOf<GraphVertex>()
                    exec(AgeSql.matchVertices(graphName, label, filter)) { rs ->
                        while (rs.next()) list.add(AgeTypeParser.parseVertex(rs.getString("v")))
                    }
                    list
                }
            }
            vertices.forEach { emit(it) }
        }
    }

    override suspend fun updateVertex(
        label: String,
        id: GraphElementId,
        properties: Map<String, Any?>,
    ): GraphVertex? {
        label.requireNotBlank("label")
        return withContext(Dispatchers.IO) {
            transaction(database) {
                exec(AgeSql.loadAge())
                exec(AgeSql.setSearchPath())
                val longId = id.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")
                var vertex: GraphVertex? = null
                exec(AgeSql.updateVertex(graphName, label, longId, properties)) { rs ->
                    if (rs.next()) vertex = AgeTypeParser.parseVertex(rs.getString("v"))
                }
                vertex
            }
        }
    }

    override suspend fun deleteVertex(label: String, id: GraphElementId): Boolean =
        withContext(Dispatchers.IO) {
            label.requireNotBlank("label")
            transaction(database) {
                exec(AgeSql.loadAge())
                exec(AgeSql.setSearchPath())
                val longId = id.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")
                var deleted = false
                exec(AgeSql.deleteVertex(graphName, label, longId)) { rs ->
                    deleted = rs.next()
                }
                deleted
            }
        }

    override suspend fun countVertices(label: String): Long =
        withContext(Dispatchers.IO) {
            label.requireNotBlank("label")
            transaction(database) {
                exec(AgeSql.loadAge())
                exec(AgeSql.setSearchPath())
                var count = 0L
                exec(AgeSql.countVertices(graphName, label)) { rs ->
                    if (rs.next()) count = rs.getString("count").trim().toLongOrNull() ?: 0L
                }
                count
            }
        }

    override suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        label.requireNotBlank("label")
        return withContext(Dispatchers.IO) {
            transaction(database) {
                exec(AgeSql.loadAge())
                exec(AgeSql.setSearchPath())
                val from = fromId.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
                val to = toId.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")
                var edge: GraphEdge? = null
                exec(AgeSql.createEdge(graphName, from, to, label, properties)) { rs ->
                    if (rs.next()) edge = AgeTypeParser.parseEdge(rs.getString("e"))
                }
                edge ?: throw GraphQueryException("Failed to create edge: $label ($fromId -> $toId)")
            }
        }
    }

    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphEdge> {
        label.requireNotBlank("label")
        return flow {
            val edges = withContext(Dispatchers.IO) {
                transaction(database) {
                    exec(AgeSql.loadAge())
                    exec(AgeSql.setSearchPath())
                    val list = mutableListOf<GraphEdge>()
                    exec(AgeSql.matchEdgesByLabel(graphName, label, filter)) { rs ->
                        while (rs.next()) list.add(AgeTypeParser.parseEdge(rs.getString("e")))
                    }
                    list
                }
            }
            edges.forEach { emit(it) }
        }
    }

    override suspend fun deleteEdge(label: String, id: GraphElementId): Boolean =
        withContext(Dispatchers.IO) {
            label.requireNotBlank("label")
            transaction(database) {
                exec(AgeSql.loadAge())
                exec(AgeSql.setSearchPath())
                val longId = id.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")
                var deleted = false
                exec(AgeSql.deleteEdge(graphName, label, longId)) { rs ->
                    deleted = rs.next()
                }
                deleted
            }
        }

    override fun neighbors(
        startId: GraphElementId,
        edgeLabel: String,
        direction: Direction,
        depth: Int,
    ): Flow<GraphVertex> {
        edgeLabel.requireNotBlank("edgeLabel")
        return flow {
            val vertices = withContext(Dispatchers.IO) {
                transaction(database) {
                    exec(AgeSql.loadAge())
                    exec(AgeSql.setSearchPath())
                    val longId = startId.value.toLongOrNull()
                        ?: throw GraphQueryException("AGE requires numeric ID, got: ${startId.value}")
                    val list = mutableListOf<GraphVertex>()
                    exec(AgeSql.neighbors(graphName, longId, edgeLabel, direction.name, depth)) { rs ->
                        while (rs.next()) list.add(AgeTypeParser.parseVertex(rs.getString("neighbor")))
                    }
                    list
                }
            }
            vertices.forEach { emit(it) }
        }
    }

    override suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String?,
        maxDepth: Int,
    ): GraphPath? = withContext(Dispatchers.IO) {
        transaction(database) {
            exec(AgeSql.loadAge())
            exec(AgeSql.setSearchPath())
            val from = fromId.value.toLongOrNull()
                ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
            val to = toId.value.toLongOrNull()
                ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")
            var path: GraphPath? = null
            exec(AgeSql.shortestPath(graphName, from, to, edgeLabel, maxDepth)) { rs ->
                if (rs.next()) path = AgeTypeParser.parsePath(rs.getString("p"))
            }
            path
        }
    }

    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String?,
        maxDepth: Int,
    ): Flow<GraphPath> = flow {
        val paths = withContext(Dispatchers.IO) {
            transaction(database) {
                exec(AgeSql.loadAge())
                exec(AgeSql.setSearchPath())
                val from = fromId.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${fromId.value}")
                val to = toId.value.toLongOrNull()
                    ?: throw GraphQueryException("AGE requires numeric ID, got: ${toId.value}")
                val list = mutableListOf<GraphPath>()
                exec(AgeSql.allPaths(graphName, from, to, edgeLabel, maxDepth)) { rs ->
                    while (rs.next()) list.add(AgeTypeParser.parsePath(rs.getString("p")))
                }
                list
            }
        }
        paths.forEach { emit(it) }
    }
}
