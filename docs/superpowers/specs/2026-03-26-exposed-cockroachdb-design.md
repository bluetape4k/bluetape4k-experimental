# Exposed CockroachDB Dialect 설계 스펙

- **날짜**: 2026-03-26 (rev3: Codex P1/P2 2차 반영)
- **모듈**: `data/exposed-cockroachdb/` (Gradle task name: `:exposed-cockroachdb`)
- **패키지**: `io.bluetape4k.exposed.cockroachdb`

---

## 1. 배경 및 목적

CockroachDB는 PostgreSQL 호환 분산 SQL 데이터베이스이다. PostgreSQL wire protocol을 사용하지만 분산 아키텍처 특성상 일부 기능이 다르게 동작하거나 미지원된다. JetBrains Exposed ORM에서 CockroachDB를 사용하려면 전용 Dialect이 필요하다.

### 설계 원칙

- **PostgreSQLDialect 상속 + 최소 override**: CockroachDB는 PostgreSQL DDL을 수용하므로 `CockroachDataTypeProvider` 불필요
- **FunctionProvider 재사용**: `PostgreSQLDialect` 상속 시 `PostgreSQLFunctionProvider` 자동 사용. 별도 구현 불필요
- **실제로 다른 것만 override**: 추측으로 override 금지. 실제 동작 차이만 처리
- **CockroachServer.Launcher 활용**: `bluetape4k-testcontainers`의 기존 싱글턴 래퍼 사용

---

## 2. CockroachDB vs PostgreSQL 차이점

### 2.1 지원 기능 (PostgreSQL과 동일 — override 불필요)

| 기능 | 비고 |
|------|------|
| 기본 CRUD (INSERT, SELECT, UPDATE, DELETE) | 동일 |
| `ON CONFLICT DO UPDATE` (UPSERT) | 동일 문법 |
| Window 함수 (ROW_NUMBER, RANK, DENSE_RANK, LEAD, LAG) | 동일 |
| CTE (WITH ... AS) | 동일 |
| JSONB | 동일 연산자 (`@>`, `->`, `->>`) |
| UUID 네이티브 타입 | `gen_random_uuid()` 사용 |
| RETURNING 절 | INSERT/UPDATE/DELETE 모두 지원 |
| Partial Index | WHERE 절 포함 인덱스 |
| STRING_AGG | 동일 |
| Schema 생성 | 동일 |
| Sequence | `CREATE SEQUENCE` 지원 |
| `SELECT FOR UPDATE` | 분산 환경에서 성능 주의 |
| `NULLS FIRST/LAST` | 동일 |
| 정규식 (`~`, `~*`) | 동일 |
| `EXTRACT(field FROM ...)` | 동일 |
| Foreign Key | 동일 |
| `BIGSERIAL` / `SERIAL` | CockroachDB가 그대로 수용 (내부적으로 `unique_rowid()` 사용) |
| `CREATE DATABASE IF NOT EXISTS` | **지원함** — override 불필요 |

### 2.2 실제 차이점 (override 필요)

| 기능 | PostgreSQL | CockroachDB | Dialect 처리 |
|------|-----------|-------------|-------------|
| `WINDOW FRAME GROUPS` | O | X (v24.1 기준) | `supportsWindowFrameGroupsMode = false` |
| `ALTER COLUMN TYPE` | 유연 | 인덱스/체크 제약 있으면 불가 | `supportsColumnTypeChange = false` + `modifyColumn()` 타입 변경 차단 |
| Multiple Generated Keys | O | 제한적 | `supportsMultipleGeneratedKeys = false` |
| 트랜잭션 재시도 | 불필요 | serializable contention 시 필요 | `DatabaseConfig(defaultMaxAttempts = 3)` |

### 2.3 CockroachDB 고유 기능 (1단계 구현 제외)

| 기능 | 설명 |
|------|------|
| `unique_rowid()` | BIGSERIAL 내부 구현체. DDL에서 직접 사용 불필요 |
| `AS OF SYSTEM TIME` | 특정 시점 데이터 읽기 (follower read) |

---

## 3. 모듈 구조

```
data/exposed-cockroachdb/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/kotlin/io/bluetape4k/exposed/cockroachdb/
    │   └── dialect/
    │       └── CockroachDialect.kt        # PostgreSQLDialect 상속, supportXXX 플래그
    └── test/
        ├── kotlin/io/bluetape4k/exposed/cockroachdb/
        │   ├── AbstractCockroachDBTest.kt  # CockroachServer.Launcher + registerDialect + DatabaseConfig
        │   ├── schema/
        │   │   ├── CockroachSchemaTest.kt  # 테이블 생성/삭제, 타입 변경 미지원 확인
        │   │   └── PkTest.kt              # Long/UUID PK 자동 생성 확인
        │   ├── crud/
        │   │   └── CrudTest.kt            # INSERT/SELECT/UPDATE/DELETE
        │   ├── upsert/
        │   │   └── UpsertTest.kt          # ON CONFLICT DO NOTHING/UPDATE
        │   ├── json/
        │   │   └── JsonbTest.kt           # JSONB 컬럼, @> 연산자
        │   ├── window/
        │   │   └── WindowFunctionTest.kt  # ROW_NUMBER, RANK, LEAD, LAG
        │   └── domain/
        │       ├── Users.kt               # LongIdTable
        │       ├── Products.kt            # LongIdTable (UPSERT용)
        │       └── Orders.kt             # LongIdTable (Window 함수용)
        └── resources/
            ├── junit-platform.properties  # exposed-ignite3에서 복사
            └── logback-test.xml           # exposed-ignite3에서 복사
```

**제거된 파일**:
- `CockroachDataTypeProvider.kt` — `BIGSERIAL`은 CockroachDB가 그대로 수용하므로 불필요
- `CockroachFunctionProvider.kt` — `PostgreSQLDialect` 상속으로 자동 해결

---

## 4. 클래스 설계

### 4.1 CockroachDialect

```kotlin
package io.bluetape4k.exposed.cockroachdb.dialect

import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect

class CockroachDialect : PostgreSQLDialect() {

    companion object : KLogging() {
        const val dialectName = "CockroachDB"
    }

    override val name: String = dialectName

    // DataTypeProvider: PostgreSQLDataTypeProvider 그대로 사용
    //   → BIGSERIAL은 CockroachDB가 수용하므로 override 불필요

    // FunctionProvider: PostgreSQLFunctionProvider 그대로 사용
    //   → PostgreSQLDialect 상속 시 자동 주입

    // --- supportXXX 플래그 (실제로 다른 것만) ---

    /** CockroachDB v24.1 기준 WINDOW FRAME GROUPS 미지원 */
    override val supportsWindowFrameGroupsMode: Boolean = false

    /**
     * ALTER COLUMN TYPE은 인덱스/체크 제약 있으면 불가.
     * "미지원"으로 확정 — modifyColumn()에서 타입 변경 차단
     */
    override val supportsColumnTypeChange: Boolean = false

    /** CockroachDB에서 multiple generated keys 제한적 */
    override val supportsMultipleGeneratedKeys: Boolean = false

    // --- 상속 유지 (PostgreSQLDialect과 동일, 변경 없음) ---
    // supportsSubqueryUnions = true
    // supportsOrderByNullsFirstLast = true
    // supportsCreateSequence = true
    // supportsCreateSchema = true
    // supportsOnUpdate = true
    // supportsSetDefaultReferenceOption = true
    // supportsIfNotExists = true        ← CREATE DATABASE IF NOT EXISTS 지원
    // requiresAutoCommitOnCreateDrop = true

    // createDatabase() — override 불필요 (CockroachDB는 IF NOT EXISTS 지원)

    /**
     * 컬럼 타입 변경 미지원 확정.
     * typeChanged인 경우 SQL을 생성하지 않는다.
     */
    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> =
        if (columnDiff.typeChanged) emptyList() else super.modifyColumn(column, columnDiff)
}
```

**상속 구조**: `CockroachDialect` → `PostgreSQLDialect` → `VendorDialect` → `DatabaseDialect`

### 4.2 Dialect 등록

`Database.registerDialect(prefix, factory)`는 JDBC URL prefix로 dialect를 매핑한다.
CockroachDB는 `jdbc:postgresql://` URL을 사용하므로 prefix = `"postgresql"`.

```kotlin
// AbstractCockroachDBTest companion object init 블록에서 호출
Database.registerDialect("postgresql") { CockroachDialect() }
```

> **참고**: `registerDialectMetadata()`는 Exposed 1.0+ API에 존재하지 않는다. `registerDialect()`만으로 충분하다.

### 4.3 트랜잭션 재시도

CockroachDB serializable isolation에서 write contention 발생 시 재시도가 필요하다. Exposed `DatabaseConfig.defaultMaxAttempts`를 사용한다.

```kotlin
// ✅ 올바른 사용법 — databaseConfig named parameter
val db = Database.connect(
    datasource = dataSource,
    databaseConfig = DatabaseConfig {
        defaultMaxAttempts = 3
        defaultMinRetryDelay = 100L
        defaultMaxRetryDelay = 1000L
    }
)

// ❌ trailing lambda는 setupConnection: (Connection) -> Unit 자리
// Database.connect(dataSource) { defaultMaxAttempts = 3 }  // 잘못됨
```

---

## 5. 의존성 (build.gradle.kts)

```kotlin
dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.exposed_json)

    // CockroachDB는 PostgreSQL JDBC 드라이버 사용
    compileOnly(Libs.postgresql_driver)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)  // CockroachServer.Launcher 포함

    testImplementation(Libs.postgresql_driver)
    testImplementation(Libs.testcontainers_cockroachdb)  // CockroachContainer
    testImplementation(Libs.hikaricp)
}
```

> **주의**: `project(":exposed-jdbc-tests")`는 이 저장소에 없음. 독립 테스트로 구성.

---

## 6. 테스트 설계

### 6.1 AbstractCockroachDBTest

```kotlin
abstract class AbstractCockroachDBTest {
    companion object : KLogging() {

        init {
            Database.registerDialect("postgresql") { CockroachDialect() }
        }

        val cockroach: CockroachServer by lazy { CockroachServer.Launcher.cockroach }

        val dataSource: HikariDataSource by lazy {
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = cockroach.url
                username = cockroach.username
                password = cockroach.password
                driverClassName = CockroachServer.DRIVER_CLASS_NAME
                maximumPoolSize = 5
            }).also { ShutdownQueue.register(it) }
        }

        val db: Database by lazy {
            Database.connect(
                datasource = dataSource,
                databaseConfig = DatabaseConfig {
                    defaultMaxAttempts = 3
                    defaultMinRetryDelay = 100L
                    defaultMaxRetryDelay = 1000L
                }
            )
        }
    }

    /** 테이블 생성/삭제를 자동으로 처리하는 헬퍼 */
    fun withTables(vararg tables: Table, block: Transaction.() -> Unit) {
        transaction(db) {
            SchemaUtils.create(*tables)
            try {
                block()
            } finally {
                SchemaUtils.drop(*tables)
            }
        }
    }
}
```

### 6.2 테스트 목록

`~/work/bluetape4k/bluetape4k-projects/data/exposed-jdbc-tests` 의 테스트 패턴을 참조하여
CockroachDB에서 가능한 것/불가능한 것을 분류한다.

| 테스트 클래스 | 테스트 항목 | 지원 여부 |
|-------------|-----------|---------|
| `CockroachSchemaTest` | CREATE/DROP TABLE, `IF NOT EXISTS`, 컬럼 타입 변경 차단 확인 | 생성/삭제 O, 타입 변경 X |
| `PkTest` | `LongIdTable` (BIGSERIAL), `UUIDTable` (gen_random_uuid) | O |
| `CrudTest` | INSERT, SELECT, WHERE, UPDATE, DELETE, COUNT, LIMIT/OFFSET, batch | O |
| `UpsertTest` | ON CONFLICT DO NOTHING, ON CONFLICT DO UPDATE, 복합 키 | O |
| `JsonbTest` | JSONB 컬럼, `@>`, `->`, `->>` | O |
| `WindowFunctionTest` | ROW_NUMBER, RANK, DENSE_RANK, LEAD, LAG, SUM/AVG OVER | O (GROUPS 제외) |

### 6.3 테스트 도메인

```kotlin
// Long PK — BIGSERIAL (CockroachDB 내부적으로 unique_rowid())
object Users : LongIdTable("users") {
    val username = varchar("username", 100).uniqueIndex()
    val email = varchar("email", 255)
    val age = integer("age").nullable()
    val metadata = jsonb<Map<String, Any>>("metadata", Json.Default).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

// UUID PK — gen_random_uuid()
object UserUUIDs : UUIDTable("user_uuids") {
    val username = varchar("username", 100).uniqueIndex()
}

// UPSERT 테스트용
object Products : LongIdTable("products") {
    val sku = varchar("sku", 50).uniqueIndex()
    val name = varchar("name", 200)
    val price = decimal("price", 10, 2)
    val stock = integer("stock").default(0)
}

// Window 함수 테스트용
object Orders : LongIdTable("orders") {
    val userId = long("user_id")
    val amount = decimal("amount", 10, 2)
    val status = varchar("status", 20)
    val orderedAt = timestamp("ordered_at").defaultExpression(CurrentTimestamp)
}
```

---

## 7. README 내용 요약

### 지원 기능
- 기본 CRUD (INSERT, SELECT, UPDATE, DELETE)
- UPSERT (ON CONFLICT DO UPDATE / DO NOTHING)
- JSONB 타입 및 연산자 (`@>`, `->`, `->>`)
- Window 함수 (ROW_NUMBER, RANK, DENSE_RANK, LEAD, LAG, SUM/AVG OVER)
- Sequence, Schema, Partial Index
- UUID 네이티브 타입 (`gen_random_uuid()`)
- RETURNING 절
- CTE (Common Table Expressions)
- NULLS FIRST/LAST 정렬
- `CREATE DATABASE IF NOT EXISTS`
- `BIGSERIAL`/`SERIAL` (내부적으로 `unique_rowid()` 사용)
- 트랜잭션 재시도 (`DatabaseConfig.defaultMaxAttempts`)

### 미지원 / 제한 사항
- `WINDOW FRAME GROUPS` 미지원 (v24.1 기준)
- `ALTER COLUMN TYPE` 미지원 — 타입 변경 SQL이 생성되지 않음
- Multiple Generated Keys 제한
- 트랜잭션 재시도가 필요할 수 있음 (serializable isolation 사용 시)

---

## 8. 구현 태스크 목록

| # | 태스크 | complexity | 설명 |
|---|--------|-----------|------|
| 1 | `build.gradle.kts` 작성 | low | 의존성 선언 |
| 2 | 테스트 리소스 복사 | low | exposed-ignite3에서 복사 |
| 3 | `CockroachDialect` 구현 | **high** | `PostgreSQLDialect` 상속, `supportXXX` 플래그, `modifyColumn` override |
| 4 | 테스트 도메인 (Users, UserUUIDs, Products, Orders) | low | LongIdTable, UUIDTable 정의 |
| 5 | `AbstractCockroachDBTest` 작성 | medium | `registerDialect`, `CockroachServer.Launcher`, `DatabaseConfig` |
| 6 | `CockroachSchemaTest` 작성 | medium | 테이블 생성/삭제, 타입 변경 차단 확인 |
| 7 | `PkTest` 작성 | medium | Long PK (BIGSERIAL), UUID PK |
| 8 | `CrudTest` 작성 | medium | INSERT, SELECT, UPDATE, DELETE, COUNT |
| 9 | `UpsertTest` 작성 | medium | ON CONFLICT DO NOTHING/UPDATE |
| 10 | `JsonbTest` 작성 | medium | JSONB 컬럼, @> 연산자, extract_path |
| 11 | `WindowFunctionTest` 작성 | medium | ROW_NUMBER, RANK, LEAD, LAG, SUM OVER |
| 12 | `README.md` 작성 | low | 지원/미지원 기능 표, 사용법 |

### 의존 관계

```
1 (build.gradle.kts)
├── 2 (테스트 리소스)   ─ 병렬
├── 3 (CockroachDialect)  ─ 병렬
└── 4 (테스트 도메인)   ─ 병렬
    │
    └── 5 (AbstractCockroachDBTest) ← 3 필요
        ├── 6  (SchemaTest)   ─ 병렬
        ├── 7  (PkTest)       ─ 병렬
        ├── 8  (CrudTest)     ─ 병렬
        ├── 9  (UpsertTest)   ─ 병렬
        ├── 10 (JsonbTest)    ─ 병렬
        └── 11 (WindowTest)   ─ 병렬
            └── 12 (README)
```

### 복잡도별 모델 라우팅

| complexity | Task # | 모델 |
|------------|--------|------|
| **high** | 3 | **Opus** |
| **medium** | 5, 6, 7, 8, 9, 10, 11 | **Sonnet** |
| **low** | 1, 2, 4, 12 | **Haiku** |

---

## 9. 최종 결정 사항

| 항목 | 결정 | 근거 |
|------|------|------|
| `CockroachDataTypeProvider` | **제거** | CockroachDB가 `BIGSERIAL` 수용. `PostgreSQLDataTypeProvider` 그대로 사용 |
| `CockroachFunctionProvider` | **제거** | `PostgreSQLFunctionProvider` 자동 상속 |
| `createDatabase()` | **override 제거** | CockroachDB는 `CREATE DATABASE IF NOT EXISTS` 지원 |
| Dialect 등록 | `Database.registerDialect("postgresql") { CockroachDialect() }` | Exposed 1.0+ 유일 API |
| `registerDialectMetadata()` | **해당 없음** | Exposed 1.0+에 존재하지 않는 API |
| `Database.connect` 재시도 설정 | `databaseConfig = DatabaseConfig { defaultMaxAttempts = 3 }` named parameter 사용 | trailing lambda는 `setupConnection` 자리 |
| `project(":exposed-jdbc-tests")` | **제거** | 이 저장소에 없음. 독립 테스트로 구성 |
