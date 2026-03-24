package io.bluetape4k.graph.examples.code

import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PostgreSQLAgeServer : PostgreSQLContainer(
    DockerImageName.parse("apache/age:latest").asCompatibleSubstituteFor("postgres")
) {

    init {
        withDatabaseName("code_graph_test")
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
