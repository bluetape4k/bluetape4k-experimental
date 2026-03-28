# exposed-bigquery

**Exposed SQL generator + BigQuery REST executor** 모듈.

Exposed DSL로 쿼리를 작성하고, `google-api-services-bigquery-v2` REST API로 실행합니다.
Simba JDBC 드라이버 없이 동작하며, Coroutines/Flow/Virtual Thread를 지원합니다.

## 포지셔닝

| 구분 | 내용 |
|------|------|
| **보장** | SELECT/WHERE/ORDER BY/GROUP BY/aggregate, INSERT/UPDATE/DELETE, 페이지네이션 자동 처리 |
| **제한** | SchemaUtils DDL 자동화, DAO 완전 호환, JDBC 트랜잭션 의미론 |
| **조건부** | join/alias(컬럼명 기준 접근), 집계 표현식은 `row["컬럼명"]` 사용 |

## 의존성/런타임 주의

- public API가 `com.google.api.services.bigquery.Bigquery` 타입을 직접 노출합니다.
- `BigQueryContext.create()`는 내부에서 H2(PostgreSQL 모드) `sqlGenDb`를 생성합니다.
- 따라서 이 모듈만 의존해도 BigQuery REST 클라이언트와 H2 드라이버가 함께 런타임 classpath에 있어야 합니다.
- `BigQueryContext(...)` 생성자를 직접 사용할 경우에는 호출자가 준비한 `sqlGenDb`를 주입할 수 있습니다.

## BigQueryContext DSL

### 생성

```kotlin
// create() 팩토리 — H2 sqlGenDb 자동 생성 (권장)
val context = BigQueryContext.create(
    bigquery = Bigquery.Builder(transport, json, credential)
        .setRootUrl("http://localhost:9050/")  // 에뮬레이터 또는 실제 BigQuery
        .setApplicationName("my-app")
        .build(),
    projectId = "my-project",
    datasetId = "my-dataset",
)

// Virtual Thread dispatcher 사용
val vtContext = BigQueryContext.create(
    bigquery = bigquery,
    projectId = "my-project",
    datasetId = "my-dataset",
    dispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher(),
)
```

### 동기 API

```kotlin
with(context) {
    // SELECT — 전체 페이지네이션 자동 처리
    val rows = Events.selectAll()
        .where { Events.region eq "kr" }
        .orderBy(Events.userId, SortOrder.DESC)
        .withBigQuery()
        .toList()

    val region: String      = rows[0][Events.region]
    val userId: Long        = rows[0][Events.userId]
    val amount: BigDecimal? = rows[0][Events.amount]  // nullable 컬럼

    // 단건 조회
    val row = Events.selectAll().where { Events.eventId eq 1L }.withBigQuery().singleOrNull()

    // INSERT
    Events.execInsert {
        it[eventId]   = 1L
        it[userId]    = 100L
        it[eventType] = "PURCHASE"
        it[region]    = "kr"
        it[amount]    = BigDecimal("9900.00")
        it[occurredAt] = Instant.now()
    }

    // UPDATE
    Events.execUpdate(Events.region eq "kr") { it[eventType] = "UPDATED" }

    // DELETE
    Events.execDelete(Events.region eq "us")

    // 원시 SQL
    runRawQuery("SELECT COUNT(*) FROM events WHERE region = 'kr'")
}
```

### 코루틴 API

`BigQueryContext.dispatcher` (기본값 `Dispatchers.IO`)를 사용해 블로킹 REST 호출을 비동기로 처리합니다.

```kotlin
with(context) {
    // suspend — 전체 결과 반환
    val rows = Events.selectAll()
        .where { Events.region eq "kr" }
        .withBigQuery()
        .toListSuspending()

    // Flow — 페이지 단위 스트리밍 (대용량 결과셋에 적합)
    Events.selectAll().withBigQuery().toFlow().collect { row ->
        println(row[Events.region])
    }

    // suspend DML
    Events.execInsertSuspending { it[eventId] = 1L; it[region] = "kr" }
    Events.execUpdateSuspending(Events.region eq "kr") { it[eventType] = "UPDATED" }
    Events.execDeleteSuspending(Events.region eq "us")
    runRawQuerySuspending("SELECT COUNT(*) FROM events")
}
```

`toListSuspending()`, `execInsertSuspending()`, `execUpdateSuspending()`, `execDeleteSuspending()`는
동일한 dispatcher 경계에서 SQL 생성과 BigQuery REST 호출을 수행합니다.

### BigQueryQueryExecutor 헬퍼

| 함수 | 설명 |
|------|------|
| `toList()` | 전체 결과 반환 (페이지네이션 자동 처리) |
| `toListSuspending()` | suspend 버전 |
| `toFlow()` | 페이지 단위 Flow 스트리밍 |
| `single()` | 정확히 1건, 아니면 예외 |
| `singleOrNull()` | 0건이면 null, 2건 이상이면 예외 |
| `firstOrNull()` | 첫 번째 행 또는 null |

### BigQueryResultRow 컬럼 타입 변환

| BigQuery 타입 | Exposed Column 타입 | Kotlin 타입 |
|--------------|---------------------|-------------|
| INT64 | `LongColumnType` | `Long` |
| STRING | `VarCharColumnType` | `String` |
| NUMERIC | `DecimalColumnType` | `BigDecimal` |
| TIMESTAMP | `JavaInstantColumnType` | `Instant` |
| 그 외 | — | `ColumnType.valueFromDB()` 위임 |

## 테스트 방법

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
dependencies {
    implementation(project(":exposed-bigquery"))
}
```

권장 진입점인 `BigQueryContext.create()`를 사용할 경우 추가로 H2를 직접 선언할 필요는 없습니다.

## 실제 BigQuery 연결 (프로덕션)

```kotlin
val credential = GoogleCredential.fromStream(FileInputStream("service-account.json"))
    .createScoped(listOf("https://www.googleapis.com/auth/bigquery"))

val bigquery = Bigquery.Builder(
    GoogleNetHttpTransport.newTrustedTransport(),
    GsonFactory.getDefaultInstance(),
    credential,
).setApplicationName("my-app").build()

val context = BigQueryContext.create(
    bigquery  = bigquery,
    projectId = "my-gcp-project",
    datasetId = "my_dataset",
    // Virtual Thread 사용 시:
    // dispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher(),
)
```

## 알려진 제한사항

- **Simba JDBC 1.6.5는 에뮬레이터 미지원**: `BigQueryEndpoint` 파라미터를 무시하고 항상 `https://bigquery.googleapis.com`으로 연결합니다. 이 모듈은 REST 클라이언트를 사용하므로 JDBC가 불필요합니다.
- **`DROP TABLE IF EXISTS` 미지원**: `goccy/bigquery-emulator`가 `IF EXISTS` 구문을 처리하지 못합니다. 테스트에서는 `runCatching`으로 오류를 무시합니다.
- **집계 표현식 Column 접근 불가**: `COUNT()`, `SUM()` 등 집계 표현식은 `row[Column]` 방식 대신 `row["컬럼명"]` 또는 원시 `QueryResponse`를 사용하세요.
