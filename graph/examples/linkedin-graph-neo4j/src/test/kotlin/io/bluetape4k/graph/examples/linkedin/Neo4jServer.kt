package io.bluetape4k.graph.examples.linkedin

import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.utility.DockerImageName

/**
 * Neo4j 테스트 컨테이너 싱글턴.
 *
 * - 이미지: `neo4j:5` (Neo4j 5.x LTS)
 * - 인증 없음 (테스트 전용)
 * - 테스트 간 컨테이너 재사용으로 빠른 테스트
 */
object Neo4jServer {
    val neo4j: Neo4jContainer<*> by lazy {
        Neo4jContainer(DockerImageName.parse("neo4j:5"))
            .withoutAuthentication()
            .apply { start() }
    }
}
