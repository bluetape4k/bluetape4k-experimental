package io.bluetape4k.graph.servers

import io.bluetape4k.utils.ShutdownQueue
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Apache AGE 확장이 설치된 PostgreSQL 테스트 컨테이너.
 *
 * - 이미지: `apache/age:latest`
 * - 컨테이너 시작 시 `CREATE EXTENSION IF NOT EXISTS age` 자동 실행
 * - 싱글턴 패턴으로 테스트 간 컨테이너 재사용 (빠른 테스트)
 *
 * **HikariCP 설정 예시:**
 * ```kotlin
 * HikariConfig().apply {
 *     jdbcUrl = PostgreSQLAgeServer.instance.jdbcUrl
 *     username = PostgreSQLAgeServer.instance.username
 *     password = PostgreSQLAgeServer.instance.password
 *     connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
 * }
 * ```
 */
class PostgreSQLAgeServer private constructor():
    PostgreSQLContainer(
        DockerImageName.parse("apache/age:latest").asCompatibleSubstituteFor("postgres")
    ) {

    companion object {
        val instance: PostgreSQLAgeServer by lazy {
            PostgreSQLAgeServer().apply {
                start()
                ShutdownQueue.register(this)
            }
        }
    }

    init {
        withDatabaseName("age_test")
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
