package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스의 통합 Facade (코루틴 방식).
 * AGE, Neo4j 등 각 백엔드가 이 인터페이스를 구현한다.
 *
 * @see GraphOperations 동기(blocking) 방식
 */
interface GraphSuspendOperations :
    GraphSuspendSession,
    GraphSuspendVertexRepository,
    GraphSuspendEdgeRepository,
    GraphSuspendTraversalRepository
