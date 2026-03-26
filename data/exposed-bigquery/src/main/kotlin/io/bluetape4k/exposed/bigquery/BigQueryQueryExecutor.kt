package io.bluetape4k.exposed.bigquery

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DecimalColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.javatime.JavaInstantColumnType
import java.math.BigDecimal
import java.time.Instant

/**
 * Exposed [Query] 객체를 BigQuery REST API로 실행하는 실행기.
 *
 * [BigQueryContext.withBigQuery]를 통해 생성됩니다.
 *
 * ```kotlin
 * with(context) {
 *     val rows = Events.selectAll()
 *         .where { Events.region eq "kr" }
 *         .withBigQuery()
 *         .toList()
 *
 *     val region: String      = rows[0][Events.region]
 *     val userId: Long        = rows[0][Events.userId]
 *     val amount: BigDecimal? = rows[0][Events.amount]
 * }
 * ```
 */
class BigQueryQueryExecutor(
    private val query: Query,
    private val context: BigQueryContext,
) {
    /** 쿼리를 실행하고 결과를 [BigQueryResultRow] 목록으로 반환합니다. */
    fun toList(): List<BigQueryResultRow> {
        val sql = transaction(context.sqlGenDb) { query.prepareSQL(this, prepared = false) }
        val response = context.runRawQuery(sql)

        val fieldNames = response.schema?.fields?.map { it.name.lowercase() } ?: emptyList()
        return response.rows?.map { tableRow ->
            val data = fieldNames.zip(tableRow.f).associate { (name, cell) -> name to cell.v }
            BigQueryResultRow(data)
        } ?: emptyList()
    }
}

/**
 * BigQuery REST API 응답의 단일 행.
 *
 * Exposed [Column] 참조로 타입 안전하게 값을 읽을 수 있습니다.
 *
 * ```kotlin
 * val row: BigQueryResultRow = ...
 * val region: String      = row[Events.region]
 * val userId: Long        = row[Events.userId]
 * val amount: BigDecimal? = row[Events.amount]
 * ```
 */
class BigQueryResultRow(private val data: Map<String, Any?>) {

    /** Exposed [Column]으로 타입 변환된 값을 반환합니다. */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(column: Column<T>): T =
        convertValue(data[column.name.lowercase()], column) as T

    /** 컬럼 이름으로 원시값을 반환합니다. */
    operator fun get(name: String): Any? = data[name.lowercase()]

    @Suppress("UNCHECKED_CAST")
    private fun <T> convertValue(raw: Any?, column: Column<T>): T? {
        if (raw == null) return null
        val s = raw.toString()
        return when (column.columnType) {
            is LongColumnType    -> s.toLong()
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
