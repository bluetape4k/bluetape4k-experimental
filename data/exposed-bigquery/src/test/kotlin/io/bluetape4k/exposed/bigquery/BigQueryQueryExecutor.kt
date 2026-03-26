package io.bluetape4k.exposed.bigquery

import com.google.api.services.bigquery.Bigquery
import com.google.api.services.bigquery.model.DatasetReference
import com.google.api.services.bigquery.model.QueryRequest
import com.google.api.services.bigquery.model.QueryResponse
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DecimalColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.javatime.JavaInstantColumnType
import java.math.BigDecimal
import java.time.Instant

/**
 * Exposed [Query] 객체를 BigQuery REST API로 실행하는 실행기.
 *
 * ```kotlin
 * val rows: List<BigQueryResultRow> = Events.selectAll()
 *     .where { Events.region eq "kr" }
 *     .withBigQuery()
 *     .toList()
 *
 * val region: String = rows[0][Events.region]
 * val userId: Long   = rows[0][Events.userId]
 * ```
 */
class BigQueryQueryExecutor(
    private val query: Query,
    private val bigquery: Bigquery,
    private val projectId: String,
    private val datasetId: String,
    private val sqlGenDb: Database,
) {
    fun toList(): List<BigQueryResultRow> {
        val sql = transaction(sqlGenDb) { query.prepareSQL(this, prepared = false) }
        val response = execute(sql)

        val fieldNames = response.schema?.fields?.map { it.name.lowercase() } ?: emptyList()
        return response.rows?.map { tableRow ->
            val data = fieldNames.zip(tableRow.f).associate { (name, cell) -> name to cell.v }
            BigQueryResultRow(data)
        } ?: emptyList()
    }

    private fun execute(sql: String): QueryResponse {
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
}

/**
 * BigQuery REST API 응답의 단일 행(Row).
 *
 * Exposed [Column] 참조로 타입 안전하게 값을 읽을 수 있습니다.
 *
 * ```kotlin
 * val row: BigQueryResultRow = ...
 * val region: String     = row[Events.region]
 * val amount: BigDecimal? = row[Events.amount]
 * ```
 */
class BigQueryResultRow(private val data: Map<String, Any?>) {

    /** Exposed [Column]으로 타입 변환된 값을 반환합니다. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(column: Column<T>): T =
        convertValue(data[column.name.lowercase()], column) as T

    /** 컬럼 이름으로 원시값(String?)을 반환합니다. */
    operator fun get(name: String): Any? = data[name.lowercase()]

    @Suppress("UNCHECKED_CAST")
    private fun <T> convertValue(raw: Any?, column: Column<T>): T? {
        if (raw == null) return null
        val s = raw.toString()
        return when (column.columnType) {
            is LongColumnType -> s.toLong()
            is IntegerColumnType -> s.toInt()
            is VarCharColumnType -> s
            is DecimalColumnType -> BigDecimal(s)
            is JavaInstantColumnType -> {
                // BigQuery REST API: TIMESTAMP = 초 단위 float 문자열 (예: "1.704067200E9")
                Instant.ofEpochMilli((s.toDouble() * 1000).toLong())
            }
            else -> s
        } as T?
    }

    override fun toString(): String = data.toString()
}
