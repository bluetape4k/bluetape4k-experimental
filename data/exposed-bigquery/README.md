# exposed-bigquery

JetBrains Exposed ORM을 사용하여 Google BigQuery에 연결하는 모듈.
`BigQueryDialect`를 제공하며, H2 PostgreSQL 모드 기반 테스트를 지원합니다.

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

## JDBC 드라이버 설정

BigQuery JDBC 드라이버는 Maven Central에 배포되지 않아 수동 설치가 필요합니다.

### Simba JDBC 드라이버 설치

```bash
# 1. 드라이버 다운로드
curl -O https://storage.googleapis.com/simba-bq-release/jdbc/SimbaJDBCDriverforGoogleBigQuery42_1.6.5.1002.zip

# 2. 압축 해제
unzip SimbaJDBCDriverforGoogleBigQuery42_1.6.5.1002.zip -d simba-bq-jdbc

# 3. 모든 JAR을 모듈 libs/ 디렉토리에 복사
cp simba-bq-jdbc/*.jar data/exposed-bigquery/libs/
```

> `libs/` 디렉토리는 `.gitignore`에 포함되어 있어 저장소에 커밋되지 않습니다.

### 알려진 제한사항: 에뮬레이터 미지원

**Simba BigQuery JDBC 드라이버(1.6.5)는 `BigQueryEndpoint` 파라미터를 무시하고 항상 `https://bigquery.googleapis.com`으로 요청을 보냅니다.**
`goccy/bigquery-emulator`와 JDBC로 연결하는 것은 현재 불가능합니다.

이에 따라 테스트는 **H2 PostgreSQL 모드**를 사용합니다.

## 테스트 방법

### H2 모드 (기본, CI/CD 환경)

`AbstractBigQueryH2Test`를 상속하면 H2 인메모리 DB에서 PostgreSQL 호환 모드로 테스트합니다.
BigQueryDialect가 등록되며 DDL/DML이 정상 동작합니다.

```bash
./gradlew :exposed-bigquery:test
```

### 실제 BigQuery 연결 (프로덕션)

```kotlin
val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = "jdbc:bigquery://;ProjectId=my-project;OAuthType=0;OAuthServiceAcctEmail=...;OAuthPvtKeyPath=..."
    driverClassName = BigQueryDialect.DRIVER_CLASS_NAME
})

val database = Database.connect(datasource = dataSource)
DatabaseApi.registerDialect("bigquery") { BigQueryDialect() }
Database.registerDialectMetadata("bigquery") { PostgreSQLDialectMetadata() }
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
        this[Events.eventId]    = f.eventId
        this[Events.userId]     = f.userId
        this[Events.eventType]  = f.eventType
        this[Events.region]     = f.region
        this[Events.amount]     = f.amount
        this[Events.occurredAt] = f.occurredAt
    }
}
```
