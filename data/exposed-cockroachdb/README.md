# exposed-cockroachdb

JetBrains Exposed ORM v1 기반 CockroachDB 방언(Dialect) 구현.

## 개요

PostgreSQL 호환 분산 SQL 데이터베이스인 CockroachDB를 Exposed와 함께 사용하기 위한 모듈입니다.

- **기반**: Exposed v1 Core, DAO, JDBC
- **드라이버**: PostgreSQL JDBC (CockroachDB는 PostgreSQL 프로토콜 지원)
- **언어**: Kotlin 2.3+
- **테스트**: Testcontainers CockroachDB

## 지원/미지원 기능

| 기능 | 지원 여부 | 비고 |
|------|----------|------|
| SELECT / INSERT / UPDATE / DELETE | ✅ 지원 | PostgreSQL 호환 |
| BATCH INSERT | ✅ 지원 | |
| UPSERT (ON CONFLICT DO UPDATE) | ✅ 지원 | |
| ON CONFLICT DO NOTHING | ✅ 지원 | upsert + onUpdateExclude 패턴 |
| AUTO-INCREMENT PK (LongIdTable) | ✅ 지원 | unique_rowid() 내부 사용 |
| UUID PK (UUIDTable) | ✅ 지원 | |
| JSONB 컬럼 | ✅ 지원 | exposed-json 필요 |
| JSONB @> contains 연산자 | ✅ 지원 | |
| Window 함수 (ROW_NUMBER, RANK, SUM OVER 등) | ✅ 지원 | |
| LAG / LEAD 함수 | ✅ 지원 | |
| WINDOW FRAME GROUPS 모드 | ✅ 지원 | v26.1+ 이상 필요 |
| ALTER COLUMN TYPE | ❌ 미지원 | CockroachDB 제한 |
| 다중 GENERATED KEYS | ❌ 미지원 | CockroachDB 제한 |
| R2DBC (비동기) | ❌ 미지원 | JDBC 전용 |

## Gradle 의존성 추가

```kotlin
dependencies {
    implementation("io.bluetape4k:exposed-cockroachdb")
    runtimeOnly("org.postgresql:postgresql")
}
```

## Dialect 등록

CockroachDB는 PostgreSQL JDBC 드라이버를 사용하므로, 데이터베이스 URL의 프리픽스는 `postgresql`입니다.
**`registerDialect`와 `registerDialectMetadata` 모두 등록**해야 DDL 생성과 schema introspection이 정상 작동합니다.

```kotlin
import io.bluetape4k.exposed.cockroachdb.dialect.CockroachDialect
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.vendors.PostgreSQLDialectMetadata

// 애플리케이션 시작 시 한 번만 호출
Database.registerDialect("postgresql") { CockroachDialect() }
Database.registerDialectMetadata("postgresql") { PostgreSQLDialectMetadata() }
```

## Database 설정

### 기본 연결

```kotlin
val db = Database.connect(
    url = "jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable",
    driver = "org.postgresql.Driver",
    user = "root",
    password = ""
)
```

### Retry 설정 (권장)

CockroachDB는 serializable isolation 사용 시 트랜잭션 충돌로 인해 재시도 가능한 에러(40001)를 반환할 수 있습니다.
`DatabaseConfig`로 자동 재시도를 활성화하세요.

```kotlin
import org.jetbrains.exposed.v1.core.DatabaseConfig

val db = Database.connect(
    datasource = dataSource,  // HikariCP 또는 다른 DataSource
    databaseConfig = DatabaseConfig {
        defaultMaxAttempts = 3
        defaultMinRetryDelay = 100L
        defaultMaxRetryDelay = 1000L
    }
)
```

Exposed는 최대 3회까지 자동으로 트랜잭션을 재시도합니다.

## 테이블 정의

### LongIdTable (AUTO-INCREMENT)

```kotlin
import org.jetbrains.exposed.v1.dao.id.LongIdTable
import org.jetbrains.exposed.v1.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.sql.javatime.javaInstant

object Users : LongIdTable("users") {
    val username = varchar("username", 100)
    val email = varchar("email", 200).uniqueIndex()
    val age = integer("age").nullable()
    val createdAt = javaInstant("created_at").defaultExpression(CurrentTimestamp)
}
```

### UUIDTable

```kotlin
import org.jetbrains.exposed.v1.dao.id.UUIDTable

object Products : UUIDTable("products") {
    val sku = varchar("sku", 50).uniqueIndex()
    val stock = integer("stock").default(0)
}
```

## 기본 연산

### INSERT

```kotlin
import org.jetbrains.exposed.v1.jdbc.transaction

transaction(db) {
    val id = Users.insertAndGetId {
        it[username] = "홍길동"
        it[email] = "hong@example.com"
        it[age] = 30
    }
}
```

### SELECT

```kotlin
transaction(db) {
    val users = Users.selectAll().map { row ->
        Triple(row[Users.id], row[Users.username], row[Users.email])
    }
}
```

### UPDATE

```kotlin
transaction(db) {
    Users.update({ Users.id eq 1 }) {
        it[age] = 31
    }
}
```

### DELETE

```kotlin
transaction(db) {
    Users.deleteWhere { id eq 1 }
}
```

## BATCH INSERT

```kotlin
transaction(db) {
    Users.batchInsert(
        listOf(
            Triple("유저1", "user1@example.com", 25),
            Triple("유저2", "user2@example.com", 28),
            Triple("유저3", "user3@example.com", 32)
        )
    ) { (username, email, age) ->
        this[Users.username] = username
        this[Users.email] = email
        this[Users.age] = age
    }
}
```

## UPSERT (ON CONFLICT DO UPDATE)

```kotlin
transaction(db) {
    Products.upsert(Products.sku) {
        it[sku] = "ITEM-001"
        it[stock] = 100
    }
}
```

## ON CONFLICT DO NOTHING (insertOrIgnore 패턴)

```kotlin
transaction(db) {
    Products.upsert(
        keys = arrayOf(Products.sku),
        onUpdateExclude = Products.columns
    ) {
        it[sku] = "ITEM-001"
        it[stock] = 100
    }
}
```

## JSONB 컬럼

exposed-json 의존성 필요.

```kotlin
import org.jetbrains.exposed.v1.sql.json.jsonb
import org.jetbrains.exposed.v1.sql.json.Json

object UserProfiles : LongIdTable("user_profiles") {
    val userId = long("user_id")
    val metadata = jsonb<Map<String, Any>>("metadata", Json.Default)
}

transaction(db) {
    UserProfiles.insertAndGetId {
        it[userId] = 1
        it[metadata] = mapOf("theme" to "dark", "language" to "ko")
    }
}
```

## JSONB contains 연산자

```kotlin
transaction(db) {
    val profiles = UserProfiles.selectAll()
        .where(UserProfiles.metadata.contains(mapOf("theme" to "dark")))
        .toList()
}
```

## Window 함수

```kotlin
import org.jetbrains.exposed.v1.sql.function.RankingWindowFunction

transaction(db) {
    val query = Users.select(
        Users.username,
        Users.age,
        RankingWindowFunction.rowNumber().over(orderBy = Users.age.desc()) as "rank"
    )
}
```

## Testcontainers 사용

테스트 클래스는 `AbstractCockroachDBTest`를 상속하고 `withTables` 헬퍼 메서드를 사용하세요.

```kotlin
import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import org.junit.jupiter.api.Test
import org.jetbrains.exposed.v1.dao.id.LongIdTable

class UserRepositoryTest : AbstractCockroachDBTest() {

    object Users : LongIdTable("users") {
        val username = varchar("username", 100)
        val email = varchar("email", 200)
    }

    @Test
    fun `사용자 저장 및 조회`() = withTables(Users) {
        val id = Users.insertAndGetId {
            it[username] = "테스트유저"
            it[email] = "test@example.com"
        }

        val user = Users.selectAll().where(Users.id eq id).single()
        assert(user[Users.username] == "테스트유저")
    }
}
```

`withTables` 메서드는 다음을 수행합니다:
1. 전달된 테이블들을 생성
2. 테스트 블록 실행
3. 테스트 완료 후 테이블 삭제

## CockroachDB 특이사항

### 1. unique_rowid() 자동 생성

`LongIdTable` 사용 시 PK는 CockroachDB의 내장 함수 `unique_rowid()`로 자동 생성됩니다.

### 2. Serializable Isolation 충돌

CockroachDB는 serializable isolation level을 기본으로 사용하여, 동시 트랜잭션 충돌 시 에러 40001을 반환합니다.
`DatabaseConfig`의 `defaultMaxAttempts` 설정으로 자동 재시도하세요.

### 3. ALTER COLUMN TYPE 미지원

CockroachDB는 스키마 마이그레이션 시 컬럼 타입 변경을 지원하지 않습니다.
대신 새 컬럼을 생성하고 데이터를 마이그레이션한 후 기존 컬럼을 삭제하는 방식을 권장합니다.

### 4. Window Frame GROUPS 지원 (v26.1+)

CockroachDB v26.1부터 `GROUPS` 프레임 모드를 지원합니다. `ROWS`, `RANGE`, `GROUPS` 세 가지 모드 모두 사용 가능합니다.

## 설정 예제 (Spring Boot)

```kotlin
@Configuration
class DatabaseConfiguration {

    @Bean
    fun hikariDataSource(
        @Value("\${spring.datasource.url}") url: String,
        @Value("\${spring.datasource.username}") username: String,
        @Value("\${spring.datasource.password}") password: String
    ): HikariDataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url
        this.username = username
        this.password = password
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
    })

    @Bean
    fun database(dataSource: HikariDataSource): Database {
        Database.registerDialect("postgresql") { CockroachDialect() }
        Database.registerDialectMetadata("postgresql") { PostgreSQLDialectMetadata() }

        return Database.connect(
            datasource = dataSource,
            databaseConfig = DatabaseConfig {
                defaultMaxAttempts = 3
                defaultMinRetryDelay = 100L
                defaultMaxRetryDelay = 1000L
            }
        )
    }
}
```

## CockroachDB 장점

### 1. 수평 확장 (Horizontal Scalability)

노드를 추가하기만 하면 자동으로 데이터를 재분산합니다. 단일 노드 한계를 넘는 데이터 볼륨과 트래픽을 처리할 수 있습니다.

### 2. 고가용성 (High Availability)

Raft 합의 알고리즘 기반 데이터 복제로 노드 장애 시에도 자동 failover합니다. 기본 3-way replication으로 2개 노드가 동시에 장애 나도 서비스가 유지됩니다.

### 3. 지리적 분산 (Geo-Distribution)

멀티 리전 클러스터를 구성하여 데이터를 사용자에 가깝게 배치할 수 있습니다. `REGIONAL BY ROW` 테이블로 행 단위 지역 고정을 지원합니다.

### 4. Serializable Isolation 기본 보장

기본 격리 수준이 `SERIALIZABLE`이므로 데이터 정합성을 최고 수준으로 보장합니다. 직렬화 이상(write skew, phantom read 등)이 원천 차단됩니다.

### 5. PostgreSQL 호환성

PostgreSQL 프로토콜 및 SQL 문법을 지원하여 기존 PostgreSQL 드라이버, ORM, 툴을 그대로 사용할 수 있습니다.

### 6. 온라인 스키마 변경

`CREATE INDEX`, `ADD COLUMN` 등 스키마 변경이 서비스 중단 없이 온라인으로 실행됩니다.

### 7. JSONB 네이티브 지원

PostgreSQL과 동일한 JSONB 타입과 `@>`, `->`, `->>`, `jsonb_path_exists()` 연산자를 지원합니다.
v26.1부터 `jsonb_path_exists()` 필터에 inverted index 가속이 적용됩니다.

---

## CockroachDB 한계점

### 1. ALTER COLUMN TYPE 제한

인덱스에 포함된 컬럼, CHECK 제약조건이 있는 컬럼, SEQUENCE를 소유한 컬럼의 타입 변경이 **불가**합니다.
스키마 마이그레이션 시 새 컬럼 생성 → 데이터 복사 → 기존 컬럼 삭제 패턴을 사용해야 합니다.

```sql
-- ❌ 불가 (인덱스 포함 컬럼)
ALTER TABLE orders ALTER COLUMN status TYPE VARCHAR(100);

-- ✅ 우회 방법
ALTER TABLE orders ADD COLUMN status_new VARCHAR(100);
UPDATE orders SET status_new = status::VARCHAR(100);
ALTER TABLE orders DROP COLUMN status;
ALTER TABLE orders RENAME COLUMN status_new TO status;
```

### 2. Serializable 충돌 재시도 필요

고동시성 환경에서 트랜잭션 충돌 시 `40001` 에러가 발생합니다. 애플리케이션 레벨에서 반드시 재시도 로직을 구현해야 합니다. Exposed의 `DatabaseConfig.defaultMaxAttempts`로 자동 재시도를 설정하세요.

### 3. 다중 Generated Keys 미지원 (`RETURNING *` 제한)

JDBC의 `getGeneratedKeys()`는 단일 키만 반환합니다. 삽입된 여러 컬럼 값이 필요한 경우 별도 SELECT 쿼리를 실행해야 합니다.

### 4. 명시적 트랜잭션 내 DDL 제한

명시적 트랜잭션(`BEGIN ... COMMIT`) 내에서 DDL과 DML을 함께 실행하면 DDL 실패 시 전체 트랜잭션이 취소됩니다. DDL과 DML은 별도 트랜잭션으로 분리해야 합니다.

### 5. 순차 PK (SERIAL) 성능 한계

분산 환경에서 순차 증가 PK는 핫스팟을 유발합니다. CockroachDB는 내부적으로 `unique_rowid()`(랜덤 분산 값)를 사용하므로, 순서를 보장하는 AUTO_INCREMENT와 동작이 다릅니다.

### 6. PostgreSQL 완전 호환 아님

일부 PostgreSQL 전용 기능은 미지원입니다:

| 기능 | 상태 |
|------|------|
| `pg_catalog` 직접 접근 | 일부 제한 (v26.1+에서 더 강화됨) |
| Stored Procedure (PL/pgSQL) | 미지원 (UDF는 지원) |
| `LISTEN` / `NOTIFY` | 미지원 |
| `COPY FROM STDIN` 스트리밍 | 미지원 |
| Advisory Lock | 미지원 |

### 7. Innovation Release 주기 유의

CockroachDB는 LTS(Long-Term Support) 버전과 Innovation 버전으로 나뉩니다.

| 버전 | 종류 | 특징 |
|------|------|------|
| v25.2.x | **LTS** | 장기 지원, 안정성 우선 |
| v26.1.x | Innovation | 신기능 포함, 6개월 지원 |
| v26.2.x (예정) | LTS | 차기 장기 지원 |

프로덕션 환경에서는 LTS 버전 사용을 권장합니다.

---

## 참고

- [CockroachDB 공식 문서](https://www.cockroachlabs.com/docs/)
- [CockroachDB v26.1 릴리스 노트](https://www.cockroachlabs.com/docs/releases/v26.1)
- [Exposed 공식 문서](https://github.com/jetbrains/exposed)
- [PostgreSQL JDBC 드라이버](https://jdbc.postgresql.org/)
