# Exposed CockroachDB Dialect — 구현 계획

- **날짜**: 2026-03-26 (rev4: Codex 4차 피드백 반영)
- **Spec**: `docs/superpowers/specs/2026-03-26-exposed-cockroachdb-design.md`
- **모듈**: `data/exposed-cockroachdb/` (`:exposed-cockroachdb`)

---

## 확정된 설계 결정

| 항목 | 결정 |
|------|------|
| `CockroachDataTypeProvider` | 제거 — `BIGSERIAL`을 CockroachDB가 수용, `PostgreSQLDataTypeProvider` 자동 사용 |
| `CockroachFunctionProvider` | 제거 — `PostgreSQLFunctionProvider` 자동 상속 |
| `createDatabase()` override | 제거 — CockroachDB가 `IF NOT EXISTS` 지원 |
| Dialect 등록 | `Database.registerDialect("postgresql") { CockroachDialect() }` **+** `Database.registerDialectMetadata("postgresql") { PostgreSQLDialectMetadata() }` 모두 필수 |
| `registerDialectMetadata()` | `exposed-jdbc/Database.kt`에 존재 확인 — schema introspection/migration 경로를 위해 함께 등록 |
| `Database.connect` 재시도 | `databaseConfig = DatabaseConfig { defaultMaxAttempts = 3 }` named parameter |
| Testcontainers | `CockroachServer.Launcher.cockroach` 싱글턴 (`bluetape4k-projects` 존재 확인, `bluetape4k_testcontainers` 의존) |
| 외부 테스트 모듈 의존 | 없음 — 독립 테스트 |
| Dialect 검증 테스트 | `DialectVerificationTest`: `db.dialect shouldBeInstanceOf CockroachDialect::class` 명시 검증 추가 |
| PkTest 기대값 | `unique_rowid()` 내부 구현 명시 금지 — "Long PK 자동 생성 + 양수 + 고유"만 단언 (`serial_normalization` 설정 영향 방지) |
| 재시도 테스트 | contention 재현 불안정 → 테스트 제외, README에 설정 예제로 대체 |

---

## 태스크 목록 (13개)

### Task 1: `build.gradle.kts` 작성
- **complexity**: low
- **의존성**: 없음
- **파일**: `data/exposed-cockroachdb/build.gradle.kts`
- **구현**:
  ```kotlin
  dependencies {
      api(Libs.exposed_core)
      api(Libs.exposed_dao)
      api(Libs.exposed_jdbc)
      api(Libs.exposed_java_time)
      api(Libs.exposed_json)
      compileOnly(Libs.postgresql_driver)

      testImplementation(Libs.bluetape4k_junit5)
      testImplementation(Libs.bluetape4k_testcontainers)
      testImplementation(Libs.postgresql_driver)
      testImplementation(Libs.testcontainers_cockroachdb)
      testImplementation(Libs.hikaricp)
  }
  ```
- **참조**: `data/exposed-ignite3/build.gradle.kts`

---

### Task 2: 테스트 리소스 복사
- **complexity**: low
- **의존성**: Task 1
- **파일**:
  - `data/exposed-cockroachdb/src/test/resources/junit-platform.properties`
  - `data/exposed-cockroachdb/src/test/resources/logback-test.xml`
- **소스**: `data/exposed-ignite3/src/test/resources/`에서 복사

---

### Task 3: `CockroachDialect` 구현
- **complexity**: high
- **의존성**: Task 1
- **파일**: `data/exposed-cockroachdb/src/main/kotlin/io/bluetape4k/exposed/cockroachdb/dialect/CockroachDialect.kt`
- **핵심 구현**:
  ```kotlin
  class CockroachDialect : PostgreSQLDialect() {
      companion object : KLogging() {
          const val dialectName = "CockroachDB"
      }
      override val name: String = dialectName
      // DataTypeProvider/FunctionProvider: PostgreSQLDialect 자동 상속
      override val supportsWindowFrameGroupsMode: Boolean = false
      override val supportsColumnTypeChange: Boolean = false
      override val supportsMultipleGeneratedKeys: Boolean = false
      override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> =
          if (columnDiff.typeChanged) emptyList() else super.modifyColumn(column, columnDiff)
  }
  ```
- **주의**: `supportsWindowFrameGroupsMode` 등 프로퍼티명을 Exposed `PostgreSQLDialect` 소스에서 확인 후 구현

---

### Task 4: 테스트 도메인 정의
- **complexity**: low
- **의존성**: Task 1
- **파일**: `data/exposed-cockroachdb/src/test/kotlin/io/bluetape4k/exposed/cockroachdb/domain/`
  - `Users.kt` — `LongIdTable("users")`: username, email, age, metadata(jsonb), createdAt
  - `UserUUIDs.kt` — `UUIDTable("user_uuids")`: username
  - `Products.kt` — `LongIdTable("products")`: sku(unique), name, price, stock
  - `Orders.kt` — `LongIdTable("orders")`: userId, amount, status, orderedAt

---

### Task 5: `AbstractCockroachDBTest` 작성
- **complexity**: medium
- **의존성**: Task 3
- **파일**: `data/exposed-cockroachdb/src/test/kotlin/io/bluetape4k/exposed/cockroachdb/AbstractCockroachDBTest.kt`
- **핵심 구현**:
  ```kotlin
  abstract class AbstractCockroachDBTest {
      companion object : KLogging() {
          init {
              // registerDialect + registerDialectMetadata 모두 등록 필수
              Database.registerDialect("postgresql") { CockroachDialect() }
              Database.registerDialectMetadata("postgresql") { PostgreSQLDialectMetadata() }
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

      fun withTables(vararg tables: Table, block: Transaction.() -> Unit) {
          transaction(db) {
              SchemaUtils.create(*tables)
              try { block() } finally { SchemaUtils.drop(*tables) }
          }
      }
  }
  ```
- **주의**: `DatabaseConfig { }` 빌더 람다 문법 Exposed 소스에서 확인

---

### Task 6: `DialectVerificationTest` 작성
- **complexity**: medium
- **의존성**: Task 5
- **파일**: `data/exposed-cockroachdb/src/test/kotlin/io/bluetape4k/exposed/cockroachdb/schema/DialectVerificationTest.kt`
- **테스트 항목**:
  - `db dialect is CockroachDialect` — `db.dialect shouldBeInstanceOf CockroachDialect::class`
  - `supportsWindowFrameGroupsMode is false`
  - `supportsColumnTypeChange is false`
  - `supportsMultipleGeneratedKeys is false`
- **목적**: Dialect 등록이 실제 동작하는지 검증. 없으면 `PostgreSQLDialect`로 조용히 동작해도 통과

---

### Task 7: `CockroachSchemaTest` 작성
- **complexity**: medium
- **의존성**: Task 4, Task 5
- **파일**: `data/exposed-cockroachdb/src/test/kotlin/io/bluetape4k/exposed/cockroachdb/schema/CockroachSchemaTest.kt`
- **테스트 항목**:
  - `createMissingTablesAndColumns` — Users 테이블 생성 성공
  - `dropAndCreate` — DROP 후 재생성
  - `ifNotExists` — 중복 CREATE 오류 없음
  - `modifyColumn with typeChanged returns empty` — `modifyColumn()` 직접 호출 검증

---

### Task 8: `PkTest` 작성
- **complexity**: medium
- **의존성**: Task 4, Task 5
- **파일**: `data/exposed-cockroachdb/src/test/kotlin/io/bluetape4k/exposed/cockroachdb/schema/PkTest.kt`
- **테스트 항목**:
  - `LongIdTable INSERT returns auto-generated positive id` — Long PK가 양수이고 고유함만 단언 (`unique_rowid()` 명시 금지)
  - `UUIDTable INSERT returns valid uuid` — UUID 형식 유효성만 확인
  - `multiple inserts have unique ids` — 복수 삽입 시 PK 고유성
- **주의**: `serial_normalization` 설정에 무관한 단언만 사용

---

### Task 9: `CrudTest` 작성
- **complexity**: medium
- **의존성**: Task 4, Task 5
- **파일**: `data/exposed-cockroachdb/src/test/kotlin/io/bluetape4k/exposed/cockroachdb/crud/CrudTest.kt`
- **테스트 항목**:
  - `insert and select by id`
  - `select with WHERE (eq, like, greaterThan)`
  - `update single row`
  - `delete single row`
  - `count and limit offset`
  - `batch insert`

---

### Task 10: `UpsertTest` 작성
- **complexity**: medium
- **의존성**: Task 4, Task 5
- **파일**: `data/exposed-cockroachdb/src/test/kotlin/io/bluetape4k/exposed/cockroachdb/upsert/UpsertTest.kt`
- **테스트 항목**:
  - `insertOrIgnore — ON CONFLICT DO NOTHING`
  - `upsert — ON CONFLICT DO UPDATE (단일 unique key)`
  - `upsert with composite unique key`

---

### Task 11: `JsonbTest` 작성
- **complexity**: medium
- **의존성**: Task 4, Task 5
- **파일**: `data/exposed-cockroachdb/src/test/kotlin/io/bluetape4k/exposed/cockroachdb/json/JsonbTest.kt`
- **테스트 항목**:
  - `insert and select jsonb column`
  - `@> contains operator`
  - `-> element access operator`
  - `->> text element access operator`
- **참조**: `bluetape4k-projects/data/exposed-jackson2` JSON 테스트 패턴

---

### Task 12: `WindowFunctionTest` 작성
- **complexity**: medium
- **의존성**: Task 4, Task 5
- **파일**: `data/exposed-cockroachdb/src/test/kotlin/io/bluetape4k/exposed/cockroachdb/window/WindowFunctionTest.kt`
- **테스트 항목**:
  - `ROW_NUMBER() OVER (ORDER BY amount DESC)`
  - `RANK() OVER (PARTITION BY status ORDER BY amount)`
  - `DENSE_RANK()`
  - `LEAD(amount) OVER (ORDER BY orderedAt)`
  - `LAG(amount) OVER (ORDER BY orderedAt)`
  - `SUM(amount) OVER (PARTITION BY userId)`
- **참조**: Exposed `WindowFunction` / `Over` DSL

---

### Task 13: `README.md` 작성
- **complexity**: low
- **의존성**: Task 3
- **파일**: `data/exposed-cockroachdb/README.md`
- **내용**:
  - 모듈 소개
  - 지원/미지원 기능 표
  - Dialect 등록 방법 (`registerDialect` + `registerDialectMetadata` 모두)
  - Gradle 의존성 추가 방법
  - `LongIdTable` / `UUIDTable` 사용 예제
  - `DatabaseConfig` 트랜잭션 재시도 설정 예제
  - 재시도 테스트 비포함 이유: serializable contention 재현이 CI 환경에서 불안정

---

## 의존 관계 그래프

```
Task 1 (build.gradle.kts)
├── Task 2 (테스트 리소스)    ─┐
├── Task 3 (CockroachDialect)  ─┤ 병렬
└── Task 4 (테스트 도메인)    ─┘
            │
            └─ Task 5 (AbstractCockroachDBTest) ← Task 3 필요
                   │
                   ├─ Task 6  (DialectVerificationTest)  ─┐
                   ├─ Task 7  (CockroachSchemaTest) ← T4  │
                   ├─ Task 8  (PkTest)              ← T4  │ 병렬
                   ├─ Task 9  (CrudTest)            ← T4  │ (최대 7개)
                   ├─ Task 10 (UpsertTest)          ← T4  │
                   ├─ Task 11 (JsonbTest)           ← T4  │
                   └─ Task 12 (WindowTest)          ← T4  ─┘
                              └─ Task 13 (README)
```

---

## 병렬 실행 그룹

### Phase 1
| Task | 설명 | complexity | 모델 |
|------|------|-----------|------|
| 1 | `build.gradle.kts` | low | Haiku |

### Phase 2 (병렬)
| Task | 설명 | complexity | 모델 |
|------|------|-----------|------|
| 2 | 테스트 리소스 복사 | low | Haiku |
| 3 | `CockroachDialect` | **high** | **Opus** |
| 4 | 테스트 도메인 | low | Haiku |

### Phase 3
| Task | 설명 | complexity | 모델 |
|------|------|-----------|------|
| 5 | `AbstractCockroachDBTest` | medium | Sonnet |

### Phase 4 (병렬, 최대 7개)
| Task | 설명 | complexity | 모델 |
|------|------|-----------|------|
| 6 | `DialectVerificationTest` | medium | Sonnet |
| 7 | `CockroachSchemaTest` | medium | Sonnet |
| 8 | `PkTest` | medium | Sonnet |
| 9 | `CrudTest` | medium | Sonnet |
| 10 | `UpsertTest` | medium | Sonnet |
| 11 | `JsonbTest` | medium | Sonnet |
| 12 | `WindowFunctionTest` | medium | Sonnet |

### Phase 5
| Task | 설명 | complexity | 모델 |
|------|------|-----------|------|
| 13 | `README.md` | low | Haiku |
