package io.bluetape4k.exposed.ignite3

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.exposed.ignite3.dialect.IgniteConnectionWrapper
import io.bluetape4k.exposed.ignite3.dialect.IgniteDialect
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.testcontainers.storage.Ignite3Server
import io.bluetape4k.utils.ShutdownQueue
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll

abstract class AbstractIgnite3Test {

    companion object : KLogging() {

        val ignite3: Ignite3Server by lazy {
            Ignite3Server().apply {
                start()
                ShutdownQueue.register(this)
            }
        }

        val dataSource: HikariDataSource by lazy {
            val jdbcUrl = "${IgniteDialect.JDBC_URL_PREFIX}://${ignite3.url}/PUBLIC"
            log.info { "Ignite 3 JDBC URL: $jdbcUrl" }

            HikariDataSource(
                HikariConfig().apply {
                    driverClassName = IgniteDialect.JDBC_DRIVER
                    this.jdbcUrl = jdbcUrl
                    username = ""
                    password = ""
                    maximumPoolSize = 5
                    isAutoCommit = false
                    transactionIsolation = "TRANSACTION_READ_COMMITTED"
                    validate()
                }
            ).also { ShutdownQueue.register(it) }
        }

        val db: Database by lazy {
            Database.connect(
                getNewConnection = { IgniteConnectionWrapper(dataSource.connection) },
                databaseConfig = DatabaseConfig {
                    defaultIsolationLevel = java.sql.Connection.TRANSACTION_READ_COMMITTED
                }
            )
        }

        @JvmStatic
        @BeforeAll
        fun setupDatabase() {
            // IgniteDialect 참조 시 companion object init 에서 드라이버/dialect/metadata 자동 등록
            db
            log.info { "Ignite 3 database connected: ${ignite3.url}" }
        }
    }

    /**
     * 지정한 테이블들을 DROP 후 CREATE 합니다.
     *
     * Ignite 3 DDL 제약: autocommit 모드 raw connection에서 실행해야 합니다.
     * SQL은 항상 Exposed [Table]에서만 생성 — 외부 문자열 입력 경로 없음.
     */
    protected fun dropAndCreateTables(vararg tables: Table) {
        // DROP/CREATE SQL을 모두 Exposed 트랜잭션 안에서 생성 (dialect 컨텍스트 보장)
        val dropSql = transaction(db) {
            db.dialectMetadata.resetCaches()
            tables.flatMap { it.dropStatement() }
        }
        runCatching { executeDdlStatements(dropSql) }

        val createSql = transaction(db) {
            SchemaUtils.createStatements(*tables)
        }
        executeDdlStatements(createSql)

        transaction(db) {
            tables.forEach { it.deleteAll() }
            db.dialectMetadata.resetCaches()
        }
    }

    // SQL은 항상 Exposed Table에서 생성된 것만 실행 — private으로 외부 주입 차단
    private fun executeDdlStatements(statements: List<String>) {
        if (statements.isEmpty()) return

        dataSource.connection.use { connection ->
            connection.autoCommit = true
            connection.createStatement().use { stmt ->
                statements.forEach { sql -> stmt.execute(sql) }
            }
        }
    }
}
