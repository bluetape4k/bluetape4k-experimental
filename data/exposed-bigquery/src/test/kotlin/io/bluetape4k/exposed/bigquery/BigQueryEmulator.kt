package io.bluetape4k.exposed.bigquery

import io.bluetape4k.logging.KLogging
import io.bluetape4k.utils.ShutdownQueue
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * BigQuery 에뮬레이터 컨테이너 (goccy/bigquery-emulator)
 *
 * ## 사전 조건
 *
 * BigQuery JDBC 드라이버가 클래스패스에 있어야 합니다.
 * Maven Central에 배포되지 않으므로 아래에서 수동 다운로드 후 로컬 Maven 저장소에 설치하세요:
 *
 * ```
 * https://storage.googleapis.com/simba-bq-release/jdbc/
 * ```
 *
 * 설치 방법:
 * ```bash
 * mvn install:install-file \
 *   -Dfile=BigQueryJDBC42.jar \
 *   -DgroupId=com.simba.googlebigquery \
 *   -DartifactId=googlebigquery-jdbc42 \
 *   -Dversion=1.5.4 \
 *   -Dpackaging=jar
 * ```
 *
 * ## 사용 예
 *
 * ```kotlin
 * val jdbcUrl = BigQueryEmulator.jdbcUrl
 * val db = Database.connect(
 *     url = jdbcUrl,
 *     driver = BigQueryDialect.DRIVER_CLASS_NAME,
 * )
 * ```
 */
object BigQueryEmulator : KLogging() {

    const val PROJECT_ID = "test"
    const val DATASET = "testdb"
    const val IMAGE = "ghcr.io/goccy/bigquery-emulator:0.6.3"
    const val HTTP_PORT = 9050

    /** brew install goccy/bigquery-emulator/bigquery-emulator 로 설치된 로컬 에뮬레이터 확인 */
    private fun isLocalRunning(): Boolean = runCatching {
        java.net.Socket("localhost", HTTP_PORT).use { true }
    }.getOrDefault(false)

    val container: GenericContainer<*> by lazy {
        GenericContainer(IMAGE)
            .withExposedPorts(HTTP_PORT)
            .withCommand("--project=$PROJECT_ID", "--dataset=$DATASET")
            .waitingFor(
                Wait.forHttp("/discovery/v1/apis/bigquery/v2/rest")
                    .forPort(HTTP_PORT)
                    .forStatusCode(200)
            )
            .also {
                it.start()
                ShutdownQueue.register { it.stop() }
            }
    }

    private val useLocal: Boolean by lazy {
        isLocalRunning().also { local ->
            if (local) log.info("로컬 BigQuery 에뮬레이터 사용 (localhost:$HTTP_PORT)")
            else log.info("Testcontainers BigQuery 에뮬레이터 시작")
        }
    }

    val host: String by lazy { if (useLocal) "localhost" else container.host }
    val port: Int by lazy { if (useLocal) HTTP_PORT else container.getMappedPort(HTTP_PORT) }

    /**
     * BigQuery 에뮬레이터 JDBC 연결 URL (Simba BigQuery JDBC 드라이버 사용)
     *
     * - OAuthType=0: 인증 없음 (에뮬레이터 테스트용)
     * - BigQueryEndpoint: 에뮬레이터 HTTP 엔드포인트
     * - DefaultDataset: 기본 데이터셋
     */
    val jdbcUrl: String
        get() = "jdbc:bigquery://;ProjectId=$PROJECT_ID;OAuthType=0;BigQueryEndpoint=http://$host:$port;DefaultDataset=$DATASET"
}
