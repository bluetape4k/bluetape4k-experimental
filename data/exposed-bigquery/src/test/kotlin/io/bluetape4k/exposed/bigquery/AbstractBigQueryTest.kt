package io.bluetape4k.exposed.bigquery

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.exposed.bigquery.dialect.BigQueryDialect
import io.bluetape4k.logging.KLogging
import io.bluetape4k.utils.ShutdownQueue
import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.PostgreSQLDialectMetadata

/**
 * BigQuery 에뮬레이터(goccy/bigquery-emulator)를 사용하는 테스트 기반 클래스.
 *
 * [BigQueryEmulator] 컨테이너를 시작하고 HikariCP DataSource로 연결합니다.
 *
 * ## H2 대안
 *
 * 에뮬레이터 없이 로컬 개발 시에는 [AbstractBigQueryH2Test]를 사용하세요.
 */
abstract class AbstractBigQueryTest {

    companion object : KLogging() {

        val dataSource: HikariDataSource by lazy {
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = BigQueryEmulator.jdbcUrl
                    driverClassName = BigQueryDialect.DRIVER_CLASS_NAME
                    maximumPoolSize = 3
                    connectionTimeout = 30_000
                    isAutoCommit = true
                }
            ).also { ShutdownQueue.register(it) }
        }

        val db: Database by lazy {
            val database = Database.connect(
                datasource = dataSource,
                databaseConfig = DatabaseConfig {
                    defaultMaxAttempts = 1
                }
            )
            DatabaseApi.registerDialect("bigquery") { BigQueryDialect() }
            Database.registerDialectMetadata("bigquery") { PostgreSQLDialectMetadata() }
            database
        }
    }

    fun withTables(vararg tables: Table, block: Transaction.() -> Unit) {
        transaction(db) { SchemaUtils.create(*tables) }
        try {
            transaction(db) { block() }
        } finally {
            transaction(db) { SchemaUtils.drop(*tables) }
        }
    }
}
