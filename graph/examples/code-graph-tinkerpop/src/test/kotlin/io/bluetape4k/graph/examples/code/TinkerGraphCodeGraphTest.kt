package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.examples.code.service.CodeGraphService
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphCodeGraphTest {

    private val ops = TinkerGraphOperations()
    private val service = CodeGraphService(ops)

    @BeforeEach
    fun cleanGraph() {
        ops.dropGraph("default")
    }

    @Test
    fun `모듈 추가 및 의존성 관계 구성`() {
        val core = service.addModule("core", "graph/graph-core", "1.0.0")
        val tinkerpop = service.addModule("graph-tinkerpop", "graph/graph-tinkerpop", "1.0.0")
        val app = service.addModule("code-graph-tinkerpop", "examples/code-graph-tinkerpop", "1.0.0")

        service.addDependency(tinkerpop.id, core.id, "compile")
        service.addDependency(app.id, tinkerpop.id, "compile")

        val tinkerpopDeps = service.getDependencies(tinkerpop.id)
        tinkerpopDeps.shouldNotBeEmpty()

        val appDeps = service.getTransitiveDependencies(app.id, maxDepth = 3)
        appDeps.shouldNotBeEmpty()
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
        path.vertices.size shouldBeGreaterThan 1
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
    }

    @Test
    fun `의존성 없는 경우 경로 null`() {
        val isolated = service.addModule("isolated", path = "", version = "1.0.0")
        val other = service.addModule("other", path = "", version = "1.0.0")

        val path = service.findDependencyPath(isolated.id, other.id)
        path.shouldBeNull()
    }
}
