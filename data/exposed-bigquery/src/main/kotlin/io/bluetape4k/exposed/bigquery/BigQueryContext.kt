package io.bluetape4k.exposed.bigquery

import com.google.api.services.bigquery.Bigquery
import com.google.api.services.bigquery.model.DatasetReference
import com.google.api.services.bigquery.model.QueryRequest
import com.google.api.services.bigquery.model.QueryResponse
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.DeleteStatement
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * BigQuery REST API 실행 컨텍스트.
 *
 * Exposed DSL과 유사한 방식으로 BigQuery 에뮬레이터 또는 실제 BigQuery에 쿼리를 실행합니다.
 * JDBC 드라이버 없이 `google-api-services-bigquery-v2` REST 클라이언트를 사용합니다.
 *
 * ## 사용 예
 *
 * ```kotlin
 * val context = BigQueryContext(bigquery, projectId, datasetId, sqlGenDb)
 *
 * with(context) {
 *     // SELECT
 *     val rows = Events.selectAll().where { Events.region eq "kr" }.withBigQuery().toList()
 *     val region: String = rows[0][Events.region]
 *
 *     // INSERT
 *     Events.execInsert {
 *         it[eventId]   = 1L
 *         it[region]    = "kr"
 *     }
 *
 *     // UPDATE / DELETE
 *     Events.execUpdate(Events.region eq "kr") { it[eventType] = "UPDATED" }
 *     Events.execDelete(Events.region eq "us")
 *
 *     // 원시 SQL
 *     runRawQuery("SELECT COUNT(*) FROM events")
 * }
 * ```
 *
 * @param bigquery BigQuery REST API 클라이언트
 * @param projectId BigQuery 프로젝트 ID
 * @param datasetId BigQuery 데이터셋 ID
 * @param sqlGenDb Exposed Statement → SQL 변환 전용 데이터베이스 (PostgreSQL 모드 권장)
 */
class BigQueryContext(
    val bigquery: Bigquery,
    val projectId: String,
    val datasetId: String,
    val sqlGenDb: Database,
) {

    // ── RAW SQL ───────────────────────────────────────────────────────────────

    /** 원시 SQL 문자열을 BigQuery에서 실행합니다. */
    fun runRawQuery(sql: String): QueryResponse {
        val request = QueryRequest()
            .setQuery(sql.trimIndent().trim())
            .setUseLegacySql(false)
            .setDefaultDataset(
                DatasetReference()
                    .setProjectId(projectId)
                    .setDatasetId(datasetId)
            )
            .setTimeoutMs(30_000L)

        return bigquery.jobs().query(projectId, request).execute()
            .also { response ->
                if (response.errors?.isNotEmpty() == true) {
                    val msg = response.errors.joinToString("; ") { it.message ?: it.reason ?: "unknown" }
                    throw RuntimeException("BigQuery 쿼리 오류: $msg\nSQL: ${sql.take(200)}")
                }
            }
    }

    // ── SELECT ────────────────────────────────────────────────────────────────

    /** Exposed [Query]를 SQL로 변환한 뒤 실행하고 [com.google.api.services.bigquery.model.QueryResponse]를 반환합니다. */
    fun runQuery(query: Query): QueryResponse {
        val sql = transaction(sqlGenDb) { query.prepareSQL(this, prepared = false) }
        return runRawQuery(sql)
    }

    /**
     * Exposed [Query]를 [BigQueryQueryExecutor]로 래핑합니다.
     *
     * ```kotlin
     * with(context) {
     *     val rows = Events.selectAll().where { Events.region eq "kr" }.withBigQuery().toList()
     *     val region: String = rows[0][Events.region]
     * }
     * ```
     */
    fun Query.withBigQuery(): BigQueryQueryExecutor =
        BigQueryQueryExecutor(this, this@BigQueryContext)

    // ── INSERT ────────────────────────────────────────────────────────────────

    /**
     * Exposed INSERT DSL을 BigQuery에서 실행합니다.
     *
     * ```kotlin
     * with(context) {
     *     Events.execInsert {
     *         it[eventId]   = 1L
     *         it[userId]    = 100L
     *         it[eventType] = "PURCHASE"
     *         it[region]    = "kr"
     *     }
     * }
     * ```
     */
    fun <T : Table> T.execInsert(body: T.(InsertStatement<Number>) -> Unit): QueryResponse {
        val stmt = InsertStatement<Number>(this)
        body(stmt)
        val sql = transaction(sqlGenDb) { stmt.prepareSQL(this, prepared = false) }
        return runRawQuery(sql)
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Exposed UPDATE DSL을 BigQuery에서 실행합니다.
     *
     * ```kotlin
     * with(context) {
     *     Events.execUpdate(Events.region eq "kr") { it[eventType] = "UPDATED" }
     * }
     * ```
     */
    fun <T : Table> T.execUpdate(
        where: Op<Boolean>,
        body: T.(UpdateStatement) -> Unit,
    ): QueryResponse {
        val stmt = UpdateStatement(this, limit = null, where = where)
        body(stmt)
        val sql = transaction(sqlGenDb) { stmt.prepareSQL(this, prepared = false) }
        return runRawQuery(sql)
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    /**
     * Exposed DELETE DSL을 BigQuery에서 실행합니다.
     *
     * ```kotlin
     * with(context) {
     *     Events.execDelete(Events.region eq "us")
     * }
     * ```
     */
    fun <T : Table> T.execDelete(where: Op<Boolean>): QueryResponse {
        val stmt = DeleteStatement(this, where = where)
        val sql = transaction(sqlGenDb) { stmt.prepareSQL(this, prepared = false) }
        return runRawQuery(sql)
    }
}
