package io.bluetape4k.graph.age

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AgeGraphOperationsTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private lateinit var ops: AgeGraphOperations

    private val graphName = "test_graph"

    @BeforeAll
    fun setup() {
        val server = PostgreSQLAgeServer.instance
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        database = Database.connect(dataSource)
        ops = AgeGraphOperations(database, graphName)
    }

    @AfterAll
    fun teardown() {
        dataSource.close()
    }

    @BeforeEach
    fun resetGraph() = runSuspendIO {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        ops.createGraph(graphName)
    }

    // ───────────────────────── 그래프 생성/삭제/존재 여부 ─────────────────────────

    @Test
    @Order(10)
    fun `그래프를 생성하면 존재 여부가 true 반환`() = runSuspendIO {
        ops.graphExists(graphName).shouldBeTrue()
    }

    @Test
    @Order(11)
    fun `그래프를 삭제하면 존재 여부가 false 반환`() = runSuspendIO {
        ops.dropGraph(graphName)
        ops.graphExists(graphName).shouldBeFalse()
    }

    // ───────────────────────── 정점(Vertex) CRUD ─────────────────────────

    @Test
    @Order(20)
    fun `정점을 생성하면 id가 부여된다`() = runSuspendIO {
        val vertex = ops.createVertex("Person")
        vertex.id.shouldNotBeNull()
        vertex.id.value.shouldNotBeNull()
    }

    @Test
    @Order(21)
    fun `label과 properties로 정점을 생성한다`() = runSuspendIO {
        val vertex = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
        vertex.id.shouldNotBeNull()
        vertex.label shouldBeEqualTo "Person"
        vertex.properties["name"] shouldBeEqualTo "Alice"
        vertex.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    @Order(22)
    fun `id로 정점을 조회한다`() = runSuspendIO {
        val created = ops.createVertex("Person", mapOf("name" to "Bob"))
        val found = ops.findVertexById("Person", created.id)
        found.shouldNotBeNull()
        found.id shouldBeEqualTo created.id
        found.properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    @Order(23)
    fun `존재하지 않는 id로 조회하면 null 반환`() = runSuspendIO {
        val fakeId = GraphElementId.of(999999999L)
        val found = ops.findVertexById("Person", fakeId)
        found.shouldBeNull()
    }

    @Test
    @Order(24)
    fun `label로 정점 목록을 조회한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))
        val vertices = ops.findVerticesByLabel("Person")
        vertices.shouldNotBeEmpty()
        vertices.size shouldBeGreaterThan 1
    }

    @Test
    @Order(25)
    fun `filter 조건으로 정점을 조회한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice", "city" to "Seoul"))
        ops.createVertex("Person", mapOf("name" to "Bob", "city" to "Busan"))
        val vertices = ops.findVerticesByLabel("Person", mapOf("city" to "Seoul"))
        vertices.shouldNotBeEmpty()
        vertices.all { it.properties["city"] == "Seoul" }.shouldBeTrue()
    }

    @Test
    @Order(26)
    fun `정점의 properties를 업데이트한다`() = runSuspendIO {
        val created = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 25))
        val updated = ops.updateVertex("Person", created.id, mapOf("name" to "Alice", "age" to 26))
        updated.shouldNotBeNull()
        updated.properties["age"] shouldBeEqualTo 26L
    }

    @Test
    @Order(27)
    fun `정점을 삭제한다`() = runSuspendIO {
        val created = ops.createVertex("Person", mapOf("name" to "ToDelete"))
        val deleted = ops.deleteVertex("Person", created.id)
        deleted.shouldBeTrue()
        val found = ops.findVertexById("Person", created.id)
        found.shouldBeNull()
    }

    @Test
    @Order(28)
    fun `정점 개수를 조회한다`() = runSuspendIO {
        ops.createVertex("Person", mapOf("name" to "Alice"))
        ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createVertex("Person", mapOf("name" to "Carol"))
        val count = ops.countVertices("Person")
        count shouldBeEqualTo 3L
    }

    // ───────────────────────── 간선(Edge) CRUD ─────────────────────────

    @Test
    @Order(30)
    fun `두 정점 사이에 간선을 생성한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val edge = ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to "2023-01-01"))
        edge.id.shouldNotBeNull()
        edge.label shouldBeEqualTo "KNOWS"
        edge.properties["since"] shouldBeEqualTo "2023-01-01"
    }

    @Test
    @Order(31)
    fun `label로 간선 목록을 조회한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(alice.id, carol.id, "KNOWS")
        val edges = ops.findEdgesByLabel("KNOWS")
        edges.shouldNotBeEmpty()
        edges.size shouldBeGreaterThan 1
    }

    @Test
    @Order(32)
    fun `간선을 삭제한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val edge = ops.createEdge(alice.id, bob.id, "KNOWS")
        val deleted = ops.deleteEdge("KNOWS", edge.id)
        deleted.shouldBeTrue()
        val edges = ops.findEdgesByLabel("KNOWS")
        edges.none { it.id == edge.id }.shouldBeTrue()
    }

    // ───────────────────────── 그래프 탐색 (Traversal) ─────────────────────────

    @Test
    @Order(40)
    fun `이웃 정점을 조회한다 - OUTGOING`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 1))
        neighbors.shouldNotBeEmpty()
        neighbors.any { it.properties["name"] == "Bob" }.shouldBeTrue()
    }

    @Test
    @Order(41)
    fun `이웃 정점을 조회한다 - INCOMING`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        val neighbors = ops.neighbors(bob.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING, maxDepth = 1))
        neighbors.shouldNotBeEmpty()
        neighbors.any { it.properties["name"] == "Alice" }.shouldBeTrue()
    }

    @Test
    @Order(42)
    fun `이웃 정점을 조회한다 - BOTH`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(carol.id, alice.id, "KNOWS")
        val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH, maxDepth = 1))
        neighbors.shouldNotBeEmpty()
        neighbors.size shouldBeGreaterThan 1
    }

    @Test
    @Order(43)
    fun `depth=2로 2단계 이웃을 조회한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(bob.id, carol.id, "KNOWS")
        val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2))
        neighbors.shouldNotBeEmpty()
        neighbors.any { it.properties["name"] == "Carol" }.shouldBeTrue()
    }

    @Test
    @Order(50)
    fun `최단 경로를 탐색한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(bob.id, carol.id, "KNOWS")
        val path = ops.shortestPath(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
        path.shouldNotBeNull()
        path.length shouldBeGreaterThan 0
    }

    @Test
    @Order(51)
    fun `연결되지 않은 경우 shortestPath는 null 반환`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val dave = ops.createVertex("Person", mapOf("name" to "Dave"))
        // 간선 없음 - alice와 dave는 연결되지 않음
        val path = ops.shortestPath(alice.id, dave.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 10))
        path.shouldBeNull()
    }

    @Test
    @Order(52)
    fun `모든 경로를 탐색한다`() = runSuspendIO {
        val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
        val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
        val carol = ops.createVertex("Person", mapOf("name" to "Carol"))
        // alice -> bob -> carol (직접 경로)
        ops.createEdge(alice.id, bob.id, "KNOWS")
        ops.createEdge(bob.id, carol.id, "KNOWS")
        // alice -> carol (우회 경로)
        ops.createEdge(alice.id, carol.id, "KNOWS")
        val paths = ops.allPaths(alice.id, carol.id, PathOptions(edgeLabel = "KNOWS", maxDepth = 5))
        paths.shouldNotBeEmpty()
        paths.size shouldBeGreaterThan 1
    }
}
