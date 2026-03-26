# exposed-bigquery

JetBrains Exposed ORM을 사용하여 Google BigQuery에 연결하는 모듈.
`BigQueryDialect`를 제공하며, `goccy/bigquery-emulator`를 이용한 로컬 테스트를 지원합니다.

## 지원/미지원 기능

| 기능 | 지원 여부 | 비고 |
|------|-----------|------|
| SELECT / WHERE / ORDER BY | ✅ | |
| GROUP BY / COUNT / SUM | ✅ | |
| batchInsert | ✅ | BigQuery DML |
| WINDOW FRAME GROUPS | ✅ | `supportsWindowFrameGroupsMode = true` |
| ALTER COLUMN TYPE | ❌ | `supportsColumnTypeChange = false` |
| SERIAL / SEQUENCE (auto-increment) | ❌ | BigQuery 미지원 |
| Multiple Generated Keys | ❌ | `supportsMultipleGeneratedKeys = false` |

## 테스트 방법

테스트는 **Simba JDBC 드라이버 없이** `google-api-services-bigquery-v2` REST 클라이언트로
`goccy/bigquery-emulator`에 직접 연결합니다. 별도 설치 없이 바로 실행 가능합니다.

### 로컬 에뮬레이터 (권장)

```bash
# macOS (Homebrew)
brew install goccy/bigquery-emulator/bigquery-emulator

# 에뮬레이터 시작
bigquery-emulator --project=test --dataset=testdb --port=9050
```

에뮬레이터가 `localhost:9050`에서 실행 중이면 테스트가 자동으로 이를 사용합니다.

### Docker (Testcontainers)

로컬 에뮬레이터가 없으면 Testcontainers가 Docker 컨테이너를 자동 시작합니다.
Docker가 설치되어 있으면 별도 설정 없이 동작합니다.

### 테스트 실행

```bash
./gradlew :exposed-bigquery:test
```

## 의존성

`google-api-services-bigquery`는 Maven Central에서 제공됩니다. 별도 로컬 설치 불필요.

```kotlin
// build.gradle.kts
testImplementation(Libs.google_api_services_bigquery)
```

## 실제 BigQuery 연결 (프로덕션)

실제 환경에서는 Simba JDBC 드라이버와 HikariCP를 사용합니다.
드라이버는 Maven Central에 없으므로 수동 설치가 필요합니다.

### Simba JDBC 드라이버 설치

```bash
# 1. 드라이버 다운로드
# https://storage.googleapis.com/simba-bq-release/jdbc/
curl -O https://storage.googleapis.com/simba-bq-release/jdbc/SimbaJDBCDriverforGoogleBigQuery42_1.6.5.1002.zip

# 2. 로컬 Maven 저장소에 설치
unzip SimbaJDBCDriverforGoogleBigQuery42_1.6.5.1002.zip -d simba-bq-jdbc
mvn install:install-file \
  -Dfile=simba-bq-jdbc/GoogleBigQueryJDBC42.jar \
  -DgroupId=com.simba.googlebigquery \
  -DartifactId=googlebigquery-jdbc42 \
  -Dversion=1.6.5.1002 \
  -Dpackaging=jar
```

### Dialect 등록 및 연결

```kotlin
val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = "jdbc:bigquery://;ProjectId=my-project;OAuthType=0;" +
              "OAuthServiceAcctEmail=...;OAuthPvtKeyPath=..."
    driverClassName = "com.simba.googlebigquery.jdbc.Driver"
})

val database = Database.connect(datasource = dataSource)
DatabaseApi.registerDialect("bigquery") { BigQueryDialect() }
Database.registerDialectMetadata("bigquery") { PostgreSQLDialectMetadata() }
```

## 알려진 제한사항

- **Simba JDBC 1.6.5는 에뮬레이터 미지원**: `BigQueryEndpoint` 파라미터를 무시하고 항상 `https://bigquery.googleapis.com`으로 연결합니다. 로컬 테스트에는 REST 클라이언트를 사용합니다.
- **`DROP TABLE IF EXISTS` 미지원**: `goccy/bigquery-emulator`가 `IF EXISTS` 구문을 처리하지 못합니다. 테스트에서는 `runCatching`으로 오류를 무시합니다.
