# exposed-duckdb

JetBrains Exposed ORM과 DuckDB JDBC를 연동하는 모듈.

## 개요

DuckDB는 인메모리 또는 파일 기반의 분석용 OLAP 데이터베이스입니다.
이 모듈은 Exposed DSL/DAO를 DuckDB와 함께 사용할 수 있도록 연결 팩토리와 코루틴 헬퍼를 제공합니다.

## 의존성

```kotlin
dependencies {
    implementation("io.bluetape4k:exposed-duckdb")
}
```

## 사용법

### 연결 생성

```kotlin
// 인메모리 (연결별 독립 DB — 단일 연결에서 사용)
val db = DuckDBDatabase.inMemory()

// 파일 기반 (여러 트랜잭션/연결에 걸쳐 데이터 유지)
val db = DuckDBDatabase.file("/tmp/analytics.db")

// 읽기 전용
val db = DuckDBDatabase.readOnly("/data/warehouse.db")
```

### 기본 CRUD

```kotlin
object Events : Table("events") {
    val eventId = long("event_id")
    val userId = long("user_id")
    val region = varchar("region", 10)
    val occurredAt = timestamp("occurred_at")
    override val primaryKey = PrimaryKey(eventId)
}

val db = DuckDBDatabase.file("/tmp/events.db")

transaction(db) {
    SchemaUtils.create(Events)

    // INSERT
    Events.insert {
        it[eventId] = 1L
        it[userId] = 100L
        it[region] = "kr"
        it[occurredAt] = Instant.now()
    }

    // SELECT
    val rows = Events.selectAll()
        .where { Events.region eq "kr" }
        .orderBy(Events.userId)
        .toList()

    val region = rows[0][Events.region]  // "kr"
}
```

### 코루틴 지원

```kotlin
val db = DuckDBDatabase.file("/tmp/events.db")

// suspend 트랜잭션
val rows = suspendTransaction(db) {
    Events.selectAll().where { Events.region eq "kr" }.toList()
}

// Virtual Thread 사용
val vtDispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
val rows = suspendTransaction(db, vtDispatcher) {
    Events.selectAll().toList()
}

// Flow 스트리밍
queryFlow(db) {
    Events.selectAll()
}.collect { row ->
    println(row[Events.eventId])
}
```

## 주의사항

### 인메모리 DB와 연결 격리

DuckDB 인메모리 데이터베이스는 **연결별로 독립적**입니다.
`jdbc:duckdb:` URL로 생성된 각 연결은 별도의 인메모리 DB에 접근합니다.

여러 트랜잭션에 걸쳐 데이터를 유지해야 하는 경우:
- **파일 기반 DB** (`DuckDBDatabase.file(...)`) 사용 권장
- 또는 HikariCP에서 `maximumPoolSize = 1` 설정

### 테스트 패턴

인메모리 DuckDB를 여러 트랜잭션에서 공유하려면 `DuckDBConnection.duplicate()`를 사용합니다.
`jdbc:duckdb:` URL은 연결마다 독립된 DB를 생성하지만, `duplicate()`는 같은 인메모리 DB를 공유합니다.

```kotlin
abstract class AbstractDuckDBTest {
    companion object {
        private val rootConn: DuckDBConnection by lazy {
            Class.forName(DuckDBDatabase.DRIVER)  // 다이얼렉트 등록 트리거
            DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection
        }

        val db: Database by lazy {
            Database.connect(getNewConnection = { DuckDBConnectionWrapper(rootConn.duplicate()) })
        }
    }
}

class MyTest : AbstractDuckDBTest() {
    @BeforeEach
    fun setUp() {
        transaction(db) {
            SchemaUtils.create(Events)   // createMissingTablesAndColumns 대신 사용
            Events.deleteAll()
        }
    }
}
```

> **참고**: `createMissingTablesAndColumns` 대신 `SchemaUtils.create`를 사용합니다.
> DuckDB JDBC 1.1.3은 `getImportedKeys` 및 `ALTER TABLE ADD PRIMARY KEY` 구문을 지원하지 않습니다.
