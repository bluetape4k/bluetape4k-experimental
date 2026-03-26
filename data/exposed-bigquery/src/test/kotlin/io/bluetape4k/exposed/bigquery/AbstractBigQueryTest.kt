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
 * Simba BigQuery JDBC 드라이버를 사용하여 에뮬레이터에 연결합니다.
 * - 로컬에 에뮬레이터가 실행 중이면 그대로 사용 (localhost:9050)
 * - 아니면 Testcontainers로 Docker 컨테이너 자동 시작
 *
 * ## 사전 조건
 *
 * Simba BigQuery JDBC 드라이버 JAR이 `data/exposed-bigquery/libs/` 에 있어야 합니다.
 * 다운로드: https://storage.googleapis.com/simba-bq-release/jdbc/SimbaJDBCDriverforGoogleBigQuery42_1.6.5.1002.zip
 *
 * ## H2 대안
 *
 * 에뮬레이터 없이 로컬 개발 시에는 [AbstractBigQueryH2Test]를 사용하세요.
 */
abstract class AbstractBigQueryTest {

    companion object : KLogging() {

        val dataSource: HikariDataSource by lazy {
            // Dialect 및 JDBC 드라이버 등록 (Database.connect() 이전에 반드시 먼저 등록)
            DatabaseApi.registerDialect(BigQueryDialect.dialectName) { BigQueryDialect() }
            Database.registerDialectMetadata(BigQueryDialect.dialectName) { PostgreSQLDialectMetadata() }
            Database.registerJdbcDriver(
                prefix = "jdbc:bigquery",
                driverClassName = BigQueryDialect.DRIVER_CLASS_NAME,
                dialect = BigQueryDialect.dialectName
            )

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
            Database.connect(
                datasource = dataSource,
                databaseConfig = DatabaseConfig {
                    defaultMaxAttempts = 1
                }
            )
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
