# exposed-ignite3

Apache Ignite 3 용 JetBrains Exposed ORM 커스텀 Dialect 구현 모듈입니다.

## 개요

Apache Ignite 3는 분산 SQL DB로 자리매김했습니다. 이 모듈은 JetBrains Exposed ORM과 Ignite 3를 연동하기 위한 커스텀 Dialect를 제공합니다.

## Ignite 3 제약사항

| 기능 | 지원 여부 | 대안 |
|------|-----------|------|
| AUTO_INCREMENT | ❌ | `TimebasedUUIDTable`, `SnowflakeIdTable` 사용 |
| FOREIGN KEY | ❌ | 애플리케이션 레벨 무결성 관리 |
| TEXT / BLOB | ❌ | `VARCHAR(65536)` / `VARBINARY` 사용 |
| UNIQUE INDEX | ✅ | `CREATE UNIQUE INDEX` 사용 (ALTER TABLE ADD CONSTRAINT 아님) |
| UUID 네이티브 타입 | ✅ | `UUID` 타입 직접 사용 |
| TIMESTAMP | ✅ | DATETIME 대신 TIMESTAMP 사용 |
| Sequence | ❌ | 클라이언트 측 ID 생성 |
| Read/Write-Through | ❌ | Ignite 3 설계 변경으로 제거됨 |

## 구성 요소

### IgniteDialect

Exposed `VendorDialect` 구현체. 다음을 오버라이드합니다:

- `createIndex()` — UNIQUE 인덱스를 `CREATE UNIQUE INDEX` 문법으로 생성
- `dropIndex()` — `DROP INDEX` 문법 사용 (ALTER TABLE DROP CONSTRAINT 아님)
- `modifyColumn()` — Ignite 3 미지원으로 빈 목록 반환

### IgniteDataTypeProvider

Ignite 3 호환 SQL 타입 매핑:

| Exposed 타입 | Ignite 3 타입 |
|-------------|--------------|
| `integerAutoincType()` | `INT` (AUTO_INCREMENT 제거) |
| `longAutoincType()` | `BIGINT` |
| `textType()` | `VARCHAR(65536)` |
| `blobType()` | `VARBINARY` |
| `uuidType()` | `UUID` |
| `dateTimeType()` | `TIMESTAMP` |
| `binaryType()` | `VARBINARY` |

### IgniteFunctionProvider

Ignite 3 SQL 함수 구현:

- `random()` — `RAND()` 사용
- `charLength()` — `LENGTH()` 사용

## 사용 예시

### 테이블 정의

```kotlin
// UUID v7 기본키 (Ignite 3 UUID 네이티브 타입 사용)
object Users : TimebasedUUIDTable("users") {
    val username = varchar("username", 100)
    val email = varchar("email", 255)
    val age = integer("age").nullable()
}

// Snowflake Long 기본키
object Events : SnowflakeIdTable("events") {
    val name = varchar("name", 100)
    val createdAt = timestamp("created_at")
}
```

### DB 연결

```kotlin
val db = Database.connect(
    url = "jdbc:ignite:thin://localhost:10800/PUBLIC",
    driver = "org.apache.ignite.internal.jdbc.IgniteJdbcDriver",
    databaseConfig = DatabaseConfig {
        defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
    }
)
```

### CRUD

```kotlin
transaction(db) {
    SchemaUtils.create(Users)
}

transaction(db) {
    Users.insert {
        it[username] = "debop"
        it[email] = "debop@example.com"
    }

    val user = Users.selectAll().where { Users.username eq "debop" }.single()
    println(user[Users.email])
}
```

## 테스트

Testcontainers를 이용하여 Docker로 Ignite 3 서버를 자동 시작합니다.

```bash
./gradlew :exposed-ignite3:test
```

## 의존성

```kotlin
// build.gradle.kts
dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.bluetape4k_exposed_jdbc)
    api(Libs.ignite3_client)
    api(Libs.ignite3_jdbc)
}
```

## Ignite 3 Production 도입 검토

### 장점

**분산 SQL + 수평 확장**
- 노드 추가만으로 읽기/쓰기 자동 분산 (sharding 내장)
- PostgreSQL 같은 단일 노드 DB와 달리 데이터가 자동으로 파티셔닝

**인메모리 속도 + 디스크 영속성**
- 데이터를 메모리에 유지하면서 디스크에도 저장 (Native Persistence)
- 캐시와 DB를 별도로 운영할 필요 없음

**분산 ACID 트랜잭션**
- 여러 노드에 걸친 분산 트랜잭션 지원
- Redis Cluster와 달리 트랜잭션 보장

**SQL 표준 인터페이스**
- JDBC/ODBC 표준으로 기존 도구 재사용 가능
- Exposed, JOOQ, MyBatis 등 ORM 연동 가능

### 단점 / 현실적 한계

| 항목 | 현실 |
|------|------|
| FK / AUTO_INCREMENT 미지원 | 애플리케이션 레벨에서 직접 처리 필요 |
| Read/Write-Through 제거 | Ignite를 Primary Store로 봐야 함 |
| 운영 복잡도 | 클러스터 초기화, 파티션 관리, 메모리 튜닝 필요 |
| 생태계 미성숙 | Spring Cache, Hibernate L2 캐시 통합 없음 |
| 툴링 부족 | DBeaver 지원 불완전, Flyway 메타데이터 테이블 생성 문제 |

> **Flyway 사용 시 주의**: Flyway는 내부적으로 `flyway_schema_history` 테이블을 `INT AUTO_INCREMENT`로 생성합니다.
> Ignite 3는 AUTO_INCREMENT를 지원하지 않으므로 Flyway 사용이 불가합니다.
> 테스트 환경에서는 `SchemaUtils.create()`를 사용하세요.

### 도입 적합 여부

**적합한 경우:**
- 대용량 데이터의 실시간 분석 (HTAP: Hybrid Transactional/Analytical)
- 지역 분산 데이터 처리가 필요한 경우
- Redis + RDB를 단일 레이어로 통합하고 싶을 때

**부적합한 경우:**
- 일반적인 CRUD 위주 서비스 → PostgreSQL이 더 적합
- 캐시 레이어가 필요한 경우 → Redis + RDB 조합이 현실적
- 소규모 팀 → 운영 부담이 큼

**결론:** Ignite 3는 "분산 SQL DB"로 포지셔닝은 맞지만, 대부분의 서비스에서는 PostgreSQL + Redis 조합이 더 성숙하고 생태계가 풍부합니다. 특수 목적(대규모 인메모리 분산 처리)이 아니면 도입 근거가 약합니다.

## 참고

- [Apache Ignite 3 공식 문서](https://ignite.apache.org/docs/ignite3/latest/)
- [JetBrains Exposed ORM](https://github.com/JetBrains/Exposed)
- Near Cache 도입 검토 결과: [brainstorming 문서](../../docs/brainstorming/2026-03-26-apache-ignite3-near-cache-investigation.md)
