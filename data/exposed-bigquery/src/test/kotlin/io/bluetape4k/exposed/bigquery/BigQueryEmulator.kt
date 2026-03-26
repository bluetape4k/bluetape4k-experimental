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

    val host: String get() = container.host
    val port: Int get() = container.getMappedPort(HTTP_PORT)

    /**
     * BigQuery JDBC 연결 URL
     * - `http://HOST:PORT` 형식으로 에뮬레이터 엔드포인트를 지정
     * - OAuthType=2 + 더미 AccessToken — 에뮬레이터는 인증을 검증하지 않음
     */
    val jdbcUrl: String
        get() = "jdbc:bigquery://http://$host:$port;ProjectId=$PROJECT_ID;OAuthType=2;OAuthAccessToken=FAKE_TOKEN_FOR_EMULATOR"
}
