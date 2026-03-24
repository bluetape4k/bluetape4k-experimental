package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스의 통합 Facade.
 * AGE, Neo4j 등 각 백엔드가 이 인터페이스를 구현한다.
 */
interface GraphOperations :
    GraphSession,
    GraphVertexRepository,
    GraphEdgeRepository,
    GraphTraversalRepository
