package io.bluetape4k.exposed.bigquery

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.bigquery.Bigquery
import com.google.api.services.bigquery.model.QueryResponse
import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.Query

/**
 * BigQuery 에뮬레이터(goccy/bigquery-emulator)를 사용하는 테스트 기반 클래스.
 *
 * [BigQueryContext]를 통해 Exposed DSL과 유사한 방식으로 쿼리를 실행합니다.
 * - 로컬 에뮬레이터 실행 중 (localhost:9050): 자동 감지하여 사용
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
 * // SELECT
 * val rows = Events.selectAll().where { Events.region eq "kr" }.withBigQuery().toList()
 * val region: String = rows[0][Events.region]
 *
 * // INSERT
 * Events.execInsert { it[eventId] = 1L; it[region] = "kr" }
 *
 * // UPDATE / DELETE
 * Events.execUpdate(Events.region eq "kr") { it[eventType] = "UPDATED" }
 * Events.execDelete(Events.region eq "us")
 *
 * // 원시 SQL
 * runRawQuery("SELECT COUNT(*) FROM events")
 * ```
 */
abstract class AbstractBigQueryTest {

    companion object : KLogging() {

        /** H2(PostgreSQL 모드) SQL 생성 전용 연결. Exposed Statement → SQL 변환에 사용합니다. */
        private val sqlGenDb: Database by lazy {
            Database.connect(
                url = "jdbc:h2:mem:sqlgen;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        }

        /** BigQuery REST API 클라이언트. 에뮬레이터 HTTP 엔드포인트를 setRootUrl 로 지정합니다. */
        private val bigqueryClient: Bigquery by lazy {
            val transport = GoogleNetHttpTransport.newTrustedTransport()
            val json = GsonFactory.getDefaultInstance()
            val credential = GoogleCredential().setAccessToken("emulator-fake-token")
            Bigquery.Builder(transport, json, credential)
                .setRootUrl("http://${BigQueryEmulator.host}:${BigQueryEmulator.port}/")
                .setApplicationName("exposed-bigquery-test")
                .build()
        }

        /** BigQuery 실행 컨텍스트. [BigQueryContext]의 DSL 함수를 직접 사용하려면 `with(bqContext) { }` 블록을 사용합니다. */
        val bqContext: BigQueryContext by lazy {
            BigQueryContext(
                bigquery = bigqueryClient,
                projectId = BigQueryEmulator.PROJECT_ID,
                datasetId = BigQueryEmulator.DATASET,
                sqlGenDb = sqlGenDb,
            )
        }

        /** 원시 SQL 문자열을 에뮬레이터에서 실행합니다. */
        fun runRawQuery(sql: String): QueryResponse = bqContext.runRawQuery(sql)

        /** Exposed [Query]를 SQL로 변환한 뒤 에뮬레이터에서 실행합니다. */
        fun runQuery(query: Query): QueryResponse = bqContext.runQuery(query)
    }

    // ── SELECT ────────────────────────────────────────────────────────────────

    protected fun Query.withBigQuery(): BigQueryQueryExecutor {
        val q = this
        return with(bqContext) { q.withBigQuery() }
    }

    // ── INSERT ────────────────────────────────────────────────────────────────

    protected fun <T : Table> T.execInsert(body: T.(InsertStatement<Number>) -> Unit): QueryResponse {
        val t = this
        return with(bqContext) { t.execInsert(body) }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    protected fun <T : Table> T.execUpdate(
        where: Op<Boolean>,
        body: T.(UpdateStatement) -> Unit,
    ): QueryResponse {
        val t = this
        return with(bqContext) { t.execUpdate(where, body) }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    protected fun <T : Table> T.execDelete(where: Op<Boolean>): QueryResponse {
        val t = this
        return with(bqContext) { t.execDelete(where) }
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
