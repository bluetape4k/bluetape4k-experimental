# exposed-bigquery

JetBrains Exposed ORM을 사용하여 Google BigQuery에 연결하는 모듈.
BigQuery JDBC 드라이버(`bigquery-connector-jdbc`)와 BigQuery 에뮬레이터(`bigquery-emulator`)를 활용한 테스트를 지원합니다.

## 지원/미지원 기능

| 기능 | 지원 여부 | 비고 |
|------|-----------|------|
| SELECT / WHERE / ORDER BY | ✅ | |
| GROUP BY / COUNT / SUM | ✅ | |
| batchInsert | ✅ | BigQuery DML |
| WINDOW FRAME GROUPS | ✅ | BigQuery 지원 |
| ALTER COLUMN TYPE | ❌ | BigQuery 미지원 |
| SERIAL / SEQUENCE (auto-increment) | ❌ | BigQuery 미지원 |
| Multiple Generated Keys | ❌ | BigQuery 미지원 |

## 의존성 설정

```kotlin
dependencies {
    implementation("com.google.cloud.bigquery:bigquery-connector-jdbc:1.6.4")
}
```

## 사용 예제

### Dialect 등록

```kotlin
val database = Database.connect(
    datasource = dataSource,
    databaseConfig = DatabaseConfig { defaultMaxAttempts = 1 }
)
DatabaseApi.registerDialect("bigquery") { BigQueryDialect() }
Database.registerDialectMetadata("bigquery") { PostgreSQLDialectMetadata() }
```

### SELECT

```kotlin
transaction(db) {
    val rows = Events.selectAll()
        .where { Events.region eq "kr" }
        .orderBy(Events.userId, SortOrder.DESC)
        .toList()
}
```

### batchInsert

```kotlin
transaction(db) {
    Events.batchInsert(fixtures) { f ->
        this[Events.eventId]   = f.eventId
        this[Events.userId]    = f.userId
        this[Events.eventType] = f.eventType
        this[Events.region]    = f.region
        this[Events.amount]    = f.amount
        this[Events.occurredAt] = f.occurredAt
    }
}
```

## 테스트 방법

BigQuery 에뮬레이터(`ghcr.io/goccy/bigquery-emulator:0.6.3`)를 Testcontainers로 자동 실행합니다.
Docker가 실행 중인 환경에서 아래 명령으로 테스트합니다.

```bash
./gradlew :exposed-bigquery:test
```
