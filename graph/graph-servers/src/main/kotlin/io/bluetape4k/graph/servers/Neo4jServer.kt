package io.bluetape4k.graph.servers

import io.bluetape4k.graph.servers.Neo4jServer.neo4j
import org.testcontainers.neo4j.Neo4jContainer
import org.testcontainers.utility.DockerImageName

/**
 * Neo4j 테스트 컨테이너.
 *
 * - 이미지: `neo4j:5` (Neo4j 5.x LTS)
 * - 인증 없음 (테스트 전용)
 * - 싱글턴 패턴으로 테스트 간 컨테이너 재사용
 *
 * **사용 예시:**
 * ```kotlin
 * val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
 * val ops = Neo4jGraphOperations(driver)
 * ```
 */
object Neo4jServer {

    val neo4j: Neo4jContainer by lazy {
        Neo4jContainer(DockerImageName.parse("neo4j:5"))
            .withoutAuthentication()
            .apply { start() }
    }

    /** [neo4j] 컨테이너의 alias — `Neo4jServer.instance` 패턴 하위 호환용 */
    val instance get() = neo4j

    val boltUrl: String get() = neo4j.boltUrl
}
