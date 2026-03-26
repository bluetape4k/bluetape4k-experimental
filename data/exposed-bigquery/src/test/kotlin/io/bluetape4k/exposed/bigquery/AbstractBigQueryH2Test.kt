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

/**
 * H2 PostgreSQL 호환 모드를 사용하는 BigQuery 테스트 기반 클래스.
 *
 * BigQuery JDBC 드라이버(Maven Central 미배포)가 없는 환경에서 로컬 개발/CI 테스트용으로 사용합니다.
 * BigQueryDialect DSL 코드 생성과 타입 매핑을 검증할 수 있습니다.
 *
 * 실제 BigQuery 에뮬레이터 연결이 필요하면 [AbstractBigQueryTest]를 사용하세요.
 */
abstract class AbstractBigQueryH2Test {

    companion object : KLogging() {

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
