package io.bluetape4k.graph.examples.code

import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.testcontainers.containers.GenericContainer

/**
 * Memgraph 테스트 컨테이너.
 *
 * - 이미지: `memgraph/memgraph:latest`
 * - 싱글턴 패턴으로 테스트 간 컨테이너 재사용 (빠른 테스트)
 * - Memgraph는 인증 없이 접속 가능 (기본 설정)
 *
 * **Driver 생성 예시:**
 * ```kotlin
 * GraphDatabase.driver(
 *     MemgraphServer.boltUrl,
 *     AuthTokens.none()
 * )
 * ```
 */
object MemgraphServer {

    val memgraph: GenericContainer<*> by lazy {
        GenericContainer("memgraph/memgraph:latest")
            .withExposedPorts(7687)
            .apply { start() }
    }

    val boltUrl: String
        get() = "bolt://${memgraph.host}:${memgraph.getMappedPort(7687)}"

    val driver: Driver by lazy {
        GraphDatabase.driver(boltUrl, AuthTokens.none())
    }
}
