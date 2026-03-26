package io.bluetape4k.exposed.bigquery

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.bigquery.Bigquery
import com.google.api.services.bigquery.model.DatasetReference
import com.google.api.services.bigquery.model.QueryRequest
import com.google.api.services.bigquery.model.QueryResponse
import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.DeleteStatement
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * BigQuery 에뮬레이터(goccy/bigquery-emulator)를 사용하는 테스트 기반 클래스.
 *
 * JDBC 드라이버 없이 `google-api-services-bigquery-v2` REST 클라이언트로 에뮬레이터에 직접 연결합니다.
 * - 로컬 에뮬레이터 실행 중: localhost:9050 사용
 * - 미실행 시: Testcontainers Docker 컨테이너 자동 시작
 *
 * ## 사전 조건
 *
 * ```bash
 * brew install goccy/bigquery-emulator/bigquery-emulator
 * bigquery-emulator --project=test --dataset=testdb --port=9050
 * ```
 *
 * ## 사용 예
 *
 * ```kotlin
 * // SELECT — Exposed Query 객체로 실행
 * val rows = Events.selectAll().where { Events.region eq "kr" }.withBigQuery().toList()
 * val region: String = rows[0][Events.region]
 *
 * // INSERT — Exposed DSL로 실행
 * Events.execInsert { it[eventId] = 1L; it[region] = "kr" }
 *
 * // UPDATE / DELETE
 * Events.execUpdate(Events.region eq "kr") { it[eventType] = "UPDATED" }
 * Events.execDelete(Events.region eq "us")
 *
 * // 원시 SQL 직접 실행
 * runRawQuery("SELECT COUNT(*) FROM events")
 * ```
 */
abstract class AbstractBigQueryTest {

    companion object : KLogging() {

        /** BigQuery REST API 클라이언트. 에뮬레이터 HTTP 엔드포인트를 setRootUrl 로 지정합니다. */
        val bigquery: Bigquery by lazy {
            val transport = GoogleNetHttpTransport.newTrustedTransport()
            val json = GsonFactory.getDefaultInstance()
            val credential = GoogleCredential().setAccessToken("emulator-fake-token")
            Bigquery.Builder(transport, json, credential)
                .setRootUrl("http://${BigQueryEmulator.host}:${BigQueryEmulator.port}/")
                .setApplicationName("exposed-bigquery-test")
                .build()
        }

        /** Exposed Statement → SQL 변환 전용 H2 연결. 실제 데이터 저장 없이 SQL 생성만 담당합니다. */
        val sqlGenDb: Database by lazy {
            Database.connect(
                url = "jdbc:h2:mem:sqlgen;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        }

        /** 원시 SQL 문자열을 에뮬레이터에서 실행합니다. */
        fun runRawQuery(sql: String): QueryResponse {
            val request = QueryRequest()
                .setQuery(sql.trimIndent().trim())
                .setUseLegacySql(false)
                .setDefaultDataset(
                    DatasetReference()
                        .setProjectId(BigQueryEmulator.PROJECT_ID)
                        .setDatasetId(BigQueryEmulator.DATASET)
                )
                .setTimeoutMs(30_000L)

            return bigquery.jobs().query(BigQueryEmulator.PROJECT_ID, request).execute()
                .also { response ->
                    if (response.errors?.isNotEmpty() == true) {
                        val msg = response.errors.joinToString("; ") { it.message ?: it.reason ?: "unknown" }
                        throw RuntimeException("BigQuery 쿼리 오류: $msg\nSQL: ${sql.take(200)}")
                    }
                }
        }

        /** Exposed [Query] 객체를 SQL로 변환한 뒤 에뮬레이터에서 실행합니다. */
        fun runQuery(query: Query): QueryResponse {
            val sql = transaction(sqlGenDb) { query.prepareSQL(this, prepared = false) }
            return runRawQuery(sql)
        }
    }

    // ── SELECT ────────────────────────────────────────────────────────────────

    /**
     * Exposed [Query]를 BigQuery 에뮬레이터에서 실행하는 [BigQueryQueryExecutor]를 반환합니다.
     *
     * ```kotlin
     * val rows = Events.selectAll().where { Events.region eq "kr" }.withBigQuery().toList()
     * val region: String = rows[0][Events.region]
     * ```
     */
    protected fun Query.withBigQuery(): BigQueryQueryExecutor =
        BigQueryQueryExecutor(this, bigquery, BigQueryEmulator.PROJECT_ID, BigQueryEmulator.DATASET, sqlGenDb)

    // ── INSERT ────────────────────────────────────────────────────────────────

    /**
     * Exposed INSERT DSL을 BigQuery 에뮬레이터에서 실행합니다.
     *
     * ```kotlin
     * Events.execInsert {
     *     it[eventId]    = 1L
     *     it[userId]     = 100L
     *     it[eventType]  = "PURCHASE"
     *     it[region]     = "kr"
     *     it[amount]     = BigDecimal("9900.00")
     *     it[occurredAt] = Instant.now()
     * }
     * ```
     */
    protected fun <T : Table> T.execInsert(body: T.(InsertStatement<Number>) -> Unit): QueryResponse {
        val stmt = InsertStatement<Number>(this)
        body(stmt)
        val sql = transaction(sqlGenDb) { stmt.prepareSQL(this, prepared = false) }
        return runRawQuery(sql)
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    /**
     * Exposed UPDATE DSL을 BigQuery 에뮬레이터에서 실행합니다.
     *
     * ```kotlin
     * Events.execUpdate(Events.region eq "kr") {
     *     it[eventType] = "UPDATED"
     * }
     * ```
     */
    protected fun <T : Table> T.execUpdate(
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
     * Exposed DELETE DSL을 BigQuery 에뮬레이터에서 실행합니다.
     *
     * ```kotlin
     * Events.execDelete(Events.region eq "us")
     * ```
     */
    protected fun <T : Table> T.execDelete(
        where: Op<Boolean>,
    ): QueryResponse {
        val stmt = DeleteStatement(this, where = where)
        val sql = transaction(sqlGenDb) { stmt.prepareSQL(this, prepared = false) }
        return runRawQuery(sql)
    }

    // ── TABLE LIFECYCLE ───────────────────────────────────────────────────────

    /**
     * events 테이블을 생성하고 테스트 블록 실행 후 삭제합니다.
     *
     * NOTE: bigquery-emulator 가 `DROP TABLE IF EXISTS` 를 지원하지 않아 runCatching 으로 무시합니다.
     */
    protected fun withEventsTable(block: () -> Unit) {
        runCatching { runRawQuery("DROP TABLE events") }
        runRawQuery(
            """
            CREATE TABLE events (
                event_id    INT64     NOT NULL,
                user_id     INT64     NOT NULL,
                event_type  STRING    NOT NULL,
                region      STRING    NOT NULL,
                amount      NUMERIC,
                occurred_at TIMESTAMP NOT NULL
            )
            """.trimIndent()
        )
        try {
            block()
        } finally {
            runCatching { runRawQuery("DROP TABLE events") }
        }
    }
}
