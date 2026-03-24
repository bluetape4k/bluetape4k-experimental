package io.bluetape4k.graph.examples.linkedin

import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Apache AGE 확장이 설치된 PostgreSQL 테스트 컨테이너.
 */
class PostgreSQLAgeServer: PostgreSQLContainer(
    DockerImageName.parse("apache/age:latest").asCompatibleSubstituteFor("postgres")
) {

    init {
        withDatabaseName("linkedin_graph_test")
        withUsername("test")
        withPassword("test")
    }

    override fun start() {
        super.start()
        createConnection("").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("CREATE EXTENSION IF NOT EXISTS age")
            }
        }
    }
}
