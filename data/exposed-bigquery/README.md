# exposed-bigquery

JetBrains Exposed ORM을 사용하여 Google BigQuery에 연결하는 모듈.
`BigQueryDialect`와 `BigQueryContext` DSL을 제공하며, JDBC 없이 REST API로 동작합니다.

## 지원/미지원 기능

| 기능 | 지원 여부 | 비고 |
|------|-----------|------|
| SELECT / WHERE / ORDER BY | ✅ | |
| GROUP BY / COUNT / SUM | ✅ | |
| INSERT / UPDATE / DELETE | ✅ | Exposed DSL 또는 원시 SQL |
| WINDOW FRAME GROUPS | ✅ | `supportsWindowFrameGroupsMode = true` |
| ALTER COLUMN TYPE | ❌ | `supportsColumnTypeChange = false` |
| SERIAL / SEQUENCE (auto-increment) | ❌ | BigQuery 미지원 |
| Multiple Generated Keys | ❌ | `supportsMultipleGeneratedKeys = false` |

## BigQueryContext DSL

`BigQueryContext`는 Exposed DSL과 유사한 방식으로 BigQuery REST API를 호출합니다.
JDBC 드라이버 없이 `google-api-services-bigquery-v2`를 사용합니다.

```kotlin
val context = BigQueryContext(
    bigquery = Bigquery.Builder(transport, json, credential)
        .setRootUrl("http://localhost:9050/")  // 에뮬레이터 또는 실제 BigQuery
        .build(),
    projectId = "my-project",
    datasetId  = "my-dataset",
    sqlGenDb   = Database.connect(             // Exposed → SQL 변환 전용 H2
        "jdbc:h2:mem:sqlgen;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "org.h2.Driver"
    ),
)

with(context) {
    // SELECT — Exposed Query를 그대로 사용, row[Column<T>] 타입 안전 접근
    val rows = Events.selectAll()
        .where { Events.region eq "kr" }
        .orderBy(Events.userId, SortOrder.DESC)
        .withBigQuery()
        .toList()

    val region: String      = rows[0][Events.region]
    val userId: Long        = rows[0][Events.userId]
    val amount: BigDecimal? = rows[0][Events.amount]  // nullable 컬럼

    // INSERT
    Events.execInsert {
        it[eventId]    = 1L
        it[userId]     = 100L
        it[eventType]  = "PURCHASE"
        it[region]     = "kr"
        it[amount]     = BigDecimal("9900.00")
        it[occurredAt] = Instant.now()
    }

    // UPDATE
    Events.execUpdate(Events.region eq "kr") {
        it[eventType] = "UPDATED"
    }

    // DELETE
    Events.execDelete(Events.region eq "us")

    // 원시 SQL (집계 등 DSL로 표현이 어려울 때)
    runRawQuery("SELECT COUNT(*) FROM events WHERE region = 'kr'")
}
```

### BigQueryResultRow 컬럼 타입 변환

| BigQuery 타입 | Exposed Column 타입 | Kotlin 타입 |
|--------------|---------------------|-------------|
| INT64 | `LongColumnType` | `Long` |
| STRING | `VarCharColumnType` | `String` |
| NUMERIC | `DecimalColumnType` | `BigDecimal` |
| TIMESTAMP | `JavaInstantColumnType` | `Instant` |

## 테스트 방법

테스트는 JDBC 드라이버 없이 `google-api-services-bigquery-v2` REST 클라이언트로
`goccy/bigquery-emulator`에 직접 연결합니다.

### 로컬 에뮬레이터 (권장)

```bash
# macOS (Homebrew)
brew install goccy/bigquery-emulator/bigquery-emulator

# 에뮬레이터 시작
bigquery-emulator --project=test --dataset=testdb --port=9050
```

`localhost:9050`에서 실행 중이면 테스트가 자동으로 감지하여 사용합니다.

### Docker (Testcontainers)

로컬 에뮬레이터가 없으면 Testcontainers가 Docker 컨테이너를 자동 시작합니다.

### 테스트 실행

```bash
./gradlew :exposed-bigquery:test
```

### AbstractBigQueryTest 상속

테스트에서 `AbstractBigQueryTest`를 상속하면 `withBigQuery()`, `execInsert()` 등을 바로 사용할 수 있습니다.

```kotlin
class MyTest : AbstractBigQueryTest() {

    @Test
    fun `이벤트 조회`() {
        withEventsTable {
            Events.execInsert { it[eventId] = 1L; it[region] = "kr" }

            val rows = Events.selectAll()
                .where { Events.region eq "kr" }
                .withBigQuery()
                .toList()

            rows.size shouldBeEqualTo 1
            rows[0][Events.region] shouldBeEqualTo "kr"
        }
    }
}
```

## 의존성

```kotlin
// build.gradle.kts
implementation(Libs.google_api_services_bigquery)  // Maven Central, 별도 설치 불필요
testImplementation(Libs.h2_v2)                     // SQL 생성 전용 (테스트에서 sqlGenDb 생성 시)
```

## 실제 BigQuery 연결 (프로덕션)

```kotlin
val credential = GoogleCredential.fromStream(FileInputStream("service-account.json"))
    .createScoped(listOf("https://www.googleapis.com/auth/bigquery"))

val bigquery = Bigquery.Builder(
    GoogleNetHttpTransport.newTrustedTransport(),
    GsonFactory.getDefaultInstance(),
    credential
).setApplicationName("my-app").build()

val context = BigQueryContext(
    bigquery   = bigquery,
    projectId  = "my-gcp-project",
    datasetId  = "my_dataset",
    sqlGenDb   = Database.connect("jdbc:h2:mem:sqlgen;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "org.h2.Driver"),
)
```

## 알려진 제한사항

- **Simba JDBC 1.6.5는 에뮬레이터 미지원**: `BigQueryEndpoint` 파라미터를 무시하고 항상 `https://bigquery.googleapis.com`으로 연결합니다. 이 모듈은 REST 클라이언트를 사용하므로 JDBC가 불필요합니다.
- **`DROP TABLE IF EXISTS` 미지원**: `goccy/bigquery-emulator`가 `IF EXISTS` 구문을 처리하지 못합니다. 테스트에서는 `runCatching`으로 오류를 무시합니다.
- **집계 표현식 Column 접근 불가**: `COUNT()`, `SUM()` 등 집계 표현식은 `row[Column]` 방식 대신 `row["컬럼명"]` 또는 원시 `QueryResponse`를 사용하세요.
