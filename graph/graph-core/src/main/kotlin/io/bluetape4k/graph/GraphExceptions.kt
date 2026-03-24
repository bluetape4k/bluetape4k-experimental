package io.bluetape4k.graph

/**
 * Graph 모듈 최상위 예외.
 */
open class GraphException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 정점 또는 간선을 찾을 수 없을 때 */
class GraphNotFoundException(message: String) : GraphException(message)

/** 그래프가 이미 존재할 때 */
class GraphAlreadyExistsException(message: String) : GraphException(message)

/** Cypher/SQL 쿼리 실행 오류 */
class GraphQueryException(message: String, cause: Throwable? = null) : GraphException(message, cause)

/** 그래프가 존재하지 않을 때 */
class GraphNotInitializedException(message: String) : GraphException(message)
