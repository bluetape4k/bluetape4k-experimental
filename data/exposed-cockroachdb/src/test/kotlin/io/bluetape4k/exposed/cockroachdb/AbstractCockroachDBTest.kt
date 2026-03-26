package io.bluetape4k.exposed.cockroachdb

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.exposed.cockroachdb.dialect.CockroachDialect
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.database.CockroachServer
import io.bluetape4k.utils.ShutdownQueue
import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.PostgreSQLDialectMetadata

abstract class AbstractCockroachDBTest {

    companion object : KLogging() {

        val cockroach: CockroachServer by lazy { CockroachServer.Launcher.cockroach }

        val dataSource: HikariDataSource by lazy {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = cockroach.url
                username = cockroach.username
                password = cockroach.password
                driverClassName = CockroachServer.DRIVER_CLASS_NAME
                maximumPoolSize = 5
            }).also { ShutdownQueue.register(it) }
        }

        val db: Database by lazy {
            // Database.connect() triggers Database.Companion.init{} which registers PostgreSQLDialect
            val database = Database.connect(
                datasource = dataSource,
                databaseConfig = DatabaseConfig {
                    defaultMaxAttempts = 3
                    defaultMinRetryDelay = 100L
                    defaultMaxRetryDelay = 1000L
                }
            )
            // Re-register AFTER Database.Companion.init{} has run, so CockroachDialect is not overridden
            DatabaseApi.registerDialect("postgresql") { CockroachDialect() }
            Database.registerDialectMetadata("postgresql") { PostgreSQLDialectMetadata() }
            database
        }
    }

    // DDL과 DML을 별도 트랜잭션으로 분리
    // CockroachDB에서 트랜잭션 내 오류 시 DDL(DROP)이 함께 실패하는 문제 방지
    fun withTables(vararg tables: Table, block: Transaction.() -> Unit) {
        transaction(db) { SchemaUtils.create(*tables) }
        try {
            transaction(db) { block() }
        } finally {
            transaction(db) { SchemaUtils.drop(*tables) }
        }
    }
}
