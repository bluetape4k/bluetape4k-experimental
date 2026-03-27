package io.bluetape4k.exposed.mysql8.gis

import io.bluetape4k.exposed.tests.AbstractExposedTest
import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

abstract class AbstractMySqlGisTest : AbstractExposedTest() {

    companion object : KLogging() {
        @JvmStatic
        val mysqlContainer: MySQLContainer<*> = MySQLContainer(
            DockerImageName.parse("mysql:8.0")
        ).apply { start() }

        @JvmStatic
        val db: Database by lazy {
            Database.connect(
                url = mysqlContainer.jdbcUrl + "?allowPublicKeyRetrieval=true&useSSL=false",
                driver = "com.mysql.cj.jdbc.Driver",
                user = mysqlContainer.username,
                password = mysqlContainer.password,
            )
        }
    }

    /**
     * 테이블 생성 → 테스트 실행 → 테이블 삭제 패턴 (drop/create/finally drop).
     */
    protected fun withGeoTables(vararg tables: Table, statement: JdbcTransaction.() -> Unit) {
        transaction(db) {
            runCatching { SchemaUtils.drop(*tables) }
            SchemaUtils.create(*tables)
        }
        try {
            transaction(db) {
                statement()
            }
        } finally {
            transaction(db) {
                runCatching { SchemaUtils.drop(*tables) }
            }
        }
    }
}
