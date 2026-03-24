package io.bluetape4k.graph.examples.code.service

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging

/**
 * 소스 코드 의존성 그래프 서비스.
 * 모듈/클래스/함수 간 관계를 AGE 그래프로 관리.
 */
class CodeGraphService(
    private val ops: GraphOperations,
    private val graphName: String = "code_graph",
) {
    companion object : KLogging()

    /** 그래프 초기화 */
    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info("Code graph '$graphName' created")
        }
    }

    /** 모듈 추가 */
    suspend fun addModule(
        name: String,
        path: String = "",
        version: String = "",
        language: String = "kotlin",
    ): GraphVertex = ops.createVertex(
        "Module",
        mapOf("name" to name, "path" to path, "version" to version, "language" to language)
    )

    /** 클래스 추가 */
    suspend fun addClass(
        name: String,
        qualifiedName: String,
        module: String = "",
        isAbstract: Boolean = false,
        isInterface: Boolean = false,
    ): GraphVertex = ops.createVertex(
        "Class",
        mapOf(
            "name" to name,
            "qualifiedName" to qualifiedName,
            "module" to module,
            "isAbstract" to isAbstract,
            "isInterface" to isInterface,
        )
    )

    /** 함수 추가 */
    suspend fun addFunction(
        name: String,
        signature: String,
        className: String = "",
        module: String = "",
        lineCount: Int = 0,
    ): GraphVertex = ops.createVertex(
        "Function",
        mapOf(
            "name" to name,
            "signature" to signature,
            "className" to className,
            "module" to module,
            "lineCount" to lineCount,
        )
    )

    /** 모듈 간 의존성 추가 */
    suspend fun addDependency(
        fromModuleId: GraphElementId,
        toModuleId: GraphElementId,
        dependencyType: String = "compile",
        version: String = "",
    ) {
        ops.createEdge(
            fromModuleId, toModuleId, "DEPENDS_ON",
            mapOf("dependencyType" to dependencyType, "version" to version)
        )
    }

    /** 클래스 상속 */
    suspend fun addExtends(childId: GraphElementId, parentId: GraphElementId) {
        ops.createEdge(childId, parentId, "EXTENDS", emptyMap())
    }

    /** 인터페이스 구현 */
    suspend fun addImplements(classId: GraphElementId, interfaceId: GraphElementId) {
        ops.createEdge(classId, interfaceId, "IMPLEMENTS", emptyMap())
    }

    /** 함수 호출 관계 */
    suspend fun addCall(
        callerFunctionId: GraphElementId,
        calleeFunctionId: GraphElementId,
        callCount: Int = 1,
        isRecursive: Boolean = false,
    ) {
        ops.createEdge(
            callerFunctionId, calleeFunctionId, "CALLS",
            mapOf("callCount" to callCount, "isRecursive" to isRecursive)
        )
    }

    /** 클래스/함수가 모듈에 속함 */
    suspend fun addBelongsTo(elementId: GraphElementId, moduleId: GraphElementId) {
        ops.createEdge(elementId, moduleId, "BELONGS_TO", emptyMap())
    }

    /** 특정 모듈이 의존하는 모듈 목록 */
    suspend fun getDependencies(moduleId: GraphElementId): List<GraphVertex> =
        ops.neighbors(moduleId, "DEPENDS_ON", Direction.OUTGOING, depth = 1)

    /** 특정 모듈에 의존하는 모듈 목록 (역방향) */
    suspend fun getDependents(moduleId: GraphElementId): List<GraphVertex> =
        ops.neighbors(moduleId, "DEPENDS_ON", Direction.INCOMING, depth = 1)

    /** 전이 의존성 (n단계) */
    suspend fun getTransitiveDependencies(moduleId: GraphElementId, maxDepth: Int = 5): List<GraphVertex> =
        ops.neighbors(moduleId, "DEPENDS_ON", Direction.OUTGOING, depth = maxDepth)

    /** 두 모듈 간 의존성 경로 탐색 */
    suspend fun findDependencyPath(fromId: GraphElementId, toId: GraphElementId): GraphPath? =
        ops.shortestPath(fromId, toId, "DEPENDS_ON", maxDepth = 10)

    /** 순환 의존성 탐지: A→B→C→A 패턴 (allPaths로 자기 자신으로 돌아오는 경로 탐색) */
    suspend fun detectCircularDependency(moduleId: GraphElementId): List<GraphPath> =
        ops.allPaths(moduleId, moduleId, "DEPENDS_ON", maxDepth = 5)

    /** 클래스 상속 계층 탐색 */
    suspend fun getInheritanceChain(classId: GraphElementId, depth: Int = 5): List<GraphVertex> =
        ops.neighbors(classId, "EXTENDS", Direction.OUTGOING, depth = depth)

    /** 함수 호출 체인 탐색 */
    suspend fun getCallChain(functionId: GraphElementId, maxDepth: Int = 5): List<GraphVertex> =
        ops.neighbors(functionId, "CALLS", Direction.OUTGOING, depth = maxDepth)

    /** 영향 범위 분석: 이 모듈이 변경되면 영향받는 모듈 */
    suspend fun getImpactedModules(moduleId: GraphElementId, depth: Int = 3): List<GraphVertex> =
        ops.neighbors(moduleId, "DEPENDS_ON", Direction.INCOMING, depth = depth)

    /** 모듈 이름으로 검색 */
    suspend fun findModuleByName(name: String): List<GraphVertex> =
        ops.findVerticesByLabel("Module", mapOf("name" to name))

    /** 클래스 이름으로 검색 */
    suspend fun findClassByName(name: String): List<GraphVertex> =
        ops.findVerticesByLabel("Class", mapOf("name" to name))
}
