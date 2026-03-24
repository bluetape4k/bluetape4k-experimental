package io.bluetape4k.graph.examples.code

import org.testcontainers.containers.Neo4jContainer

/**
 * Neo4j 테스트 컨테이너.
 *
 * - 이미지: `neo4j:5` (Neo4j 5.x LTS)
 * - 싱글턴 패턴으로 테스트 간 컨테이너 재사용 (빠른 테스트)
 */
class Neo4jServer private constructor() : Neo4jContainer<Neo4jServer>("neo4j:5") {

    companion object {
        val instance: Neo4jServer by lazy {
            Neo4jServer().apply { start() }
        }
    }

    init {
        withoutAuthentication()
    }
}
