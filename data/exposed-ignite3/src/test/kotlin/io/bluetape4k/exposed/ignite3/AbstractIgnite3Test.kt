package io.bluetape4k.exposed.ignite3

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.testcontainers.storage.Ignite3Server
import io.bluetape4k.utils.ShutdownQueue
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll

abstract class AbstractIgnite3Test {

    companion object : KLogging() {

        /** Ignite 3 JDBC 드라이버 클래스 */
        const val IGNITE_JDBC_DRIVER = "org.apache.ignite.internal.jdbc.IgniteJdbcDriver"

        val ignite3: Ignite3Server by lazy {
            Ignite3Server().apply {
                start()
                ShutdownQueue.register(this)
            }
        }

        val dataSource: HikariDataSource by lazy {
            val jdbcUrl = "jdbc:ignite:thin://${ignite3.url}/PUBLIC"
            log.info { "Ignite 3 JDBC URL: $jdbcUrl" }

            HikariDataSource(
                HikariConfig().apply {
                    driverClassName = IGNITE_JDBC_DRIVER
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
            Database.connect(dataSource, databaseConfig = DatabaseConfig {
                defaultIsolationLevel = java.sql.Connection.TRANSACTION_READ_COMMITTED
            })
        }

        @JvmStatic
        @BeforeAll
        fun setupDatabase() {
            // DB 연결 초기화 (lazy 초기화 강제)
            db
            log.info { "Ignite 3 database connected: ${ignite3.url}" }
        }
    }

    /**
     * 지정한 테이블들을 DROP 후 CREATE 합니다.
     */
    protected fun dropAndCreateTables(vararg tables: Table) {
        transaction(db) {
            runCatching { SchemaUtils.drop(*tables) }
            SchemaUtils.create(*tables)
        }
    }
}
