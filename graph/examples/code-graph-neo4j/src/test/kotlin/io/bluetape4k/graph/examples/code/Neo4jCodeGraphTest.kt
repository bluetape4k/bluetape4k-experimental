package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.examples.code.service.CodeGraphService
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.servers.Neo4jServer
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

class Neo4jCodeGraphTest {

    companion object: KLogging()

    private lateinit var driver: Driver
    private lateinit var ops: Neo4jGraphOperations
    private lateinit var service: CodeGraphService

    @BeforeAll
    fun setup() {
        val server = Neo4jServer.instance
        driver = GraphDatabase.driver(server.boltUrl, AuthTokens.none())
        ops = Neo4jGraphOperations(driver)
        service = CodeGraphService(ops)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @BeforeEach
    fun cleanGraph() {
        // Neo4j는 그래프 자체가 DB이므로 모든 노드/관계 삭제
        ops.dropGraph("code_graph")
    }

    @Test
    fun `모듈 추가 및 의존성 관계 구성`() {
        val core = service.addModule("core", "graph/graph-core", "1.0.0")
        val neo4j = service.addModule("graph-neo4j", "graph/graph-neo4j", "1.0.0")
        val app = service.addModule("code-graph-neo4j", "examples/code-graph-neo4j", "1.0.0")

        service.addDependency(neo4j.id, core.id, "compile")
        service.addDependency(app.id, neo4j.id, "compile")

        val neo4jDeps = service.getDependencies(neo4j.id)
        neo4jDeps.shouldNotBeEmpty()

        val appDeps = service.getTransitiveDependencies(app.id, maxDepth = 3)
        appDeps.shouldNotBeEmpty()
        appDeps.forEach { vertex ->
            log.debug { "vertex=$vertex" }
        }
    }

    @Test
    fun `의존성 경로 탐색`() {
        val core = service.addModule("core", path = "", version = "1.0.0")
        val mid = service.addModule("middle", path = "", version = "1.0.0")
        val top = service.addModule("top", path = "", version = "1.0.0")

        service.addDependency(mid.id, core.id)
        service.addDependency(top.id, mid.id)

        val path = service.findDependencyPath(top.id, core.id)
        path.shouldNotBeNull()
        path.length shouldBeGreaterThan 0
        path.vertices.forEach { vertex ->
            log.debug { "vertex=$vertex" }
        }
    }

    @Test
    fun `영향 범위 분석 - 역방향 탐색`() {
        val core = service.addModule("core", path = "", version = "1.0.0")
        val moduleA = service.addModule("moduleA", path = "", version = "1.0.0")
        val moduleB = service.addModule("moduleB", path = "", version = "1.0.0")

        service.addDependency(moduleA.id, core.id)
        service.addDependency(moduleB.id, core.id)

        val impacted = service.getImpactedModules(core.id, depth = 1)
        impacted.shouldNotBeEmpty()
        impacted.forEach { vertex ->
            log.debug { "vertex=$vertex" }
        }
    }

    @Test
    fun `클래스 상속 계층 탐색`() {
        val baseClass = service.addClass("Animal", "io.example.Animal")
        val midClass = service.addClass("Mammal", "io.example.Mammal")
        val leafClass = service.addClass("Dog", "io.example.Dog")

        service.addExtends(midClass.id, baseClass.id)
        service.addExtends(leafClass.id, midClass.id)

        val chain = service.getInheritanceChain(leafClass.id, depth = 3)
        chain.shouldNotBeEmpty()
        chain.forEach { vertex ->
            log.debug { "vertex=$vertex" }
        }
    }

    @Test
    fun `함수 호출 체인 분석`() {
        val funcA = service.addFunction("processOrder", "fun processOrder(orderId: Long)")
        val funcB = service.addFunction("validateOrder", "fun validateOrder(orderId: Long)")
        val funcC = service.addFunction("saveOrder", "fun saveOrder(order: Order)")

        service.addCall(funcA.id, funcB.id, callCount = 1)
        service.addCall(funcA.id, funcC.id, callCount = 1)
        service.addCall(funcB.id, funcC.id, callCount = 2)

        val callChain = service.getCallChain(funcA.id, maxDepth = 3)
        callChain.shouldNotBeEmpty()
        callChain.forEach { vertex ->
            log.debug { "vertex=$vertex" }
        }
    }

    @Test
    fun `의존성 없는 경우 경로 null`() {
        val isolated = service.addModule("isolated", path = "", version = "1.0.0")
        val other = service.addModule("other", path = "", version = "1.0.0")

        val path = service.findDependencyPath(isolated.id, other.id)
        path.shouldBeNull()
    }
}
