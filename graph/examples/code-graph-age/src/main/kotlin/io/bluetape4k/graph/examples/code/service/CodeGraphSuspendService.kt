package io.bluetape4k.graph.examples.code.service

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.Flow

/**
 * 소스 코드 의존성 그래프 서비스 (코루틴 방식).
 * 모듈/클래스/함수 간 관계를 AGE 그래프로 관리.
 */
class CodeGraphSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "code_graph",
) {
    companion object : KLoggingChannel()

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
    fun getDependencies(moduleId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.OUTGOING, maxDepth = 1))

    /** 특정 모듈에 의존하는 모듈 목록 (역방향) */
    fun getDependents(moduleId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.INCOMING, maxDepth = 1))

    /** 전이 의존성 (n단계) */
    fun getTransitiveDependencies(moduleId: GraphElementId, maxDepth: Int = 5): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.OUTGOING, maxDepth = maxDepth))

    /** 두 모듈 간 의존성 경로 탐색 */
    suspend fun findDependencyPath(fromId: GraphElementId, toId: GraphElementId): GraphPath? =
        ops.shortestPath(fromId, toId, PathOptions(edgeLabel = "DEPENDS_ON", maxDepth = 10))

    /** 순환 의존성 탐지: A→B→C→A 패턴 */
    fun detectCircularDependency(moduleId: GraphElementId): Flow<GraphPath> =
        ops.allPaths(moduleId, moduleId, PathOptions(edgeLabel = "DEPENDS_ON", maxDepth = 5))

    /** 클래스 상속 계층 탐색 */
    fun getInheritanceChain(classId: GraphElementId, depth: Int = 5): Flow<GraphVertex> =
        ops.neighbors(classId, NeighborOptions(edgeLabel = "EXTENDS", direction = Direction.OUTGOING, maxDepth = depth))

    /** 함수 호출 체인 탐색 */
    fun getCallChain(functionId: GraphElementId, maxDepth: Int = 5): Flow<GraphVertex> =
        ops.neighbors(functionId, NeighborOptions(edgeLabel = "CALLS", direction = Direction.OUTGOING, maxDepth = maxDepth))

    /** 영향 범위 분석: 이 모듈이 변경되면 영향받는 모듈 */
    fun getImpactedModules(moduleId: GraphElementId, depth: Int = 3): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.INCOMING, maxDepth = depth))

    /** 모듈 이름으로 검색 */
    fun findModuleByName(name: String): Flow<GraphVertex> =
        ops.findVerticesByLabel("Module", mapOf("name" to name))

    /** 클래스 이름으로 검색 */
    fun findClassByName(name: String): Flow<GraphVertex> =
        ops.findVerticesByLabel("Class", mapOf("name" to name))
}
