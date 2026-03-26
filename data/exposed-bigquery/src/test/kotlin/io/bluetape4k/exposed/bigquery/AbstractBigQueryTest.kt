package io.bluetape4k.exposed.bigquery

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.bigquery.Bigquery
import com.google.api.services.bigquery.model.DatasetReference
import com.google.api.services.bigquery.model.QueryRequest
import com.google.api.services.bigquery.model.QueryResponse
import io.bluetape4k.logging.KLogging
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
 * bigquery-emulator 설치:
 * ```bash
 * brew install goccy/bigquery-emulator/bigquery-emulator
 * bigquery-emulator --project=test --dataset=testdb --port=9050
 * ```
 */
abstract class AbstractBigQueryTest {

    companion object : KLogging() {

        /**
         * BigQuery REST API 클라이언트 (google-api-services-bigquery-v2).
         * 에뮬레이터 HTTP 엔드포인트를 setRootUrl 로 지정합니다.
         */
        val bigquery: Bigquery by lazy {
            val transport = GoogleNetHttpTransport.newTrustedTransport()
            val json = GsonFactory.getDefaultInstance()
            val credential = GoogleCredential().setAccessToken("emulator-fake-token")
            Bigquery.Builder(transport, json, credential)
                .setRootUrl("http://${BigQueryEmulator.host}:${BigQueryEmulator.port}/")
                .setApplicationName("exposed-bigquery-test")
                .build()
        }

        /**
         * Exposed Query → SQL 변환 전용 H2 연결.
         * BigQueryDialect 가 PostgreSQLDialect 를 상속하므로 기본 DML/DQL SQL은 호환됩니다.
         */
        private val sqlGenDb: Database by lazy {
            Database.connect(
                url = "jdbc:h2:mem:sqlgen;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
        }

        /**
         * 원시 SQL 문자열을 에뮬레이터에서 실행합니다.
         */
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

        /**
         * Exposed [Query] 객체를 SQL 문자열로 변환한 뒤 에뮬레이터에서 실행합니다.
         *
         * H2(PostgreSQL 모드) 연결을 통해 SQL을 생성하므로 BigQuery 전용 함수는 지원하지 않습니다.
         */
        fun runQuery(query: Query): QueryResponse {
            val sql = transaction(sqlGenDb) {
                query.prepareSQL(this, prepared = false)
            }
            return runRawQuery(sql)
        }
    }

    /**
     * events 테이블을 생성하고 테스트 블록 실행 후 삭제합니다.
     *
     * NOTE: bigquery-emulator 가 `DROP TABLE IF EXISTS` 를 지원하지 않아 runCatching 으로 무시합니다.
     */
    protected fun withEventsTable(block: () -> Unit) {
        // 이전 테스트 잔여 테이블 정리 (테이블이 없어도 에러 무시)
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
