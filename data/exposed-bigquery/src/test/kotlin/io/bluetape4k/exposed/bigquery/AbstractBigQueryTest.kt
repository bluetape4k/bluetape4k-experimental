package io.bluetape4k.exposed.bigquery

import io.bluetape4k.exposed.bigquery.dialect.BigQueryDialect
import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.DatabaseApi
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.vendors.PostgreSQLDialectMetadata

abstract class AbstractBigQueryTest {

    companion object : KLogging() {

        // BigQuery JDBC 드라이버는 Maven Central에 배포되지 않음.
        // 테스트에서는 H2 PostgreSQL 호환 모드를 사용하여 BigQueryDialect를 검증합니다.
        val db: Database by lazy {
            DatabaseApi.registerDialect(BigQueryDialect.dialectName) { BigQueryDialect() }
            Database.registerDialectMetadata(BigQueryDialect.dialectName) { PostgreSQLDialectMetadata() }
            Database.connect(
                url = "jdbc:h2:mem:bigquery_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
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
