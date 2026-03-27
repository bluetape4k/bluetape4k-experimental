# Spec: Exposed 커스텀 컬럼 타입 모듈 5개 추가

**날짜:** 2026-03-27  
**프로젝트:** bluetape4k-experimental  
**상태:** 승인 대기

---

## 1. 배경 및 목적

bluetape4k-experimental의 `data/` 디렉토리에는 DuckDB, BigQuery, CockroachDB 등 비표준 DB dialect들이 있다.
같은 맥락에서, 실무에서 자주 쓰이지만 Exposed 공식 지원이 없는 **특수 컬럼 타입** 5개를 모듈로 추가한다.

목표:
- Exposed 코어(`org.jetbrains.exposed.v1.core`)의 `ColumnWithTransform` / `ColumnTransformer` 패턴을 따라 일관된 구현 (bluetape4k-exposed-core의 기존 구현체 참고)
- 각 모듈은 독립적으로 사용 가능 (opt-in)
- PostgreSQL 네이티브 기능 우선, H2 fallback 제공 (SQLite는 미지원)

---

## 2. 대상 모듈

### 2-1. `exposed-phone`
전화번호를 E.164 형식으로 자동 정규화하여 저장하는 컬럼 타입.

**기술 선택:** Google `libphonenumber` (de facto standard)

**핵심 클래스:**
- `PhoneNumberTransformer(defaultRegion: String = "KR")` — `ColumnTransformer<String, PhoneNumber>`
- `PhoneNumberColumnType(defaultRegion)` — `ColumnWithTransform<String, PhoneNumber>` → VARCHAR(20)
- `PhoneNumberStringColumnType(defaultRegion)` — `ColumnWithTransform<String, String>` (String → E.164 정규화 → String)
- `Table.phoneNumber(name, defaultRegion)` factory
- `Table.phoneNumberString(name, defaultRegion)` factory

**저장 형식:** VARCHAR(20), E.164 (`+821012345678`)

**예외:** `NumberParseException` → `IllegalArgumentException`으로 래핑

**테스트 DB:** H2 (컨테이너 불필요)

---

### 2-2. `exposed-inet`
IP 주소(IPv4/IPv6)와 CIDR 블록을 저장하는 컬럼 타입.

**기술 선택:** JDK 표준 `java.net.InetAddress`

**핵심 클래스:**
- `InetAddressColumnType` — `InetAddress` 저장
  - PostgreSQL: `INET` 네이티브 타입
  - H2/기타: `VARCHAR(45)` fallback
- `CidrColumnType` — CIDR String (`"192.168.0.0/24"`) 저장
  - PostgreSQL: `CIDR` 네이티브 타입
  - H2/기타: `VARCHAR(50)` fallback
- `Table.inetAddress(name)` factory
- `Table.cidr(name)` factory
- PostgreSQL 전용 확장 함수: `Column<InetAddress>.isContainedBy(cidr)` (`<<` 연산자)

**테스트 DB:** H2 (기본) + Testcontainers PostgreSQL (PostgreSQL 전용 기능 테스트)

---

### 2-3. `exposed-tsrange`
시작/종료 Instant로 표현되는 시간 범위를 저장하는 컬럼 타입.

**기술 선택:** PostgreSQL `TSTZRANGE`, H2 fallback은 range literal VARCHAR

**핵심 클래스:**
- `data class TimestampRange(val start: Instant, val end: Instant, val lowerInclusive: Boolean = true, val upperInclusive: Boolean = false) : Serializable`
  - `companion object { private const val serialVersionUID = 1L }`
- `TstzRangeColumnType` — `TimestampRange` ↔ PostgreSQL `TSTZRANGE`
  - H2 fallback: `VARCHAR(100)`, range literal 포맷: `[2024-01-01T00:00:00Z,2024-12-31T23:59:59Z)` (ISO8601 Instant, 대괄호=inclusive, 소괄호=exclusive)
  - 외부 직렬화 라이브러리 미사용 — 직접 파싱/포맷
- `Table.tstzRange(name)` factory
- SQL 연산자 확장 함수 (PostgreSQL 전용):
  - `Column<TimestampRange>.overlaps(other: Column<TimestampRange>)` — `&&`
  - `Column<TimestampRange>.contains(instant: Expression<Instant>)` — `@>`
  - `Column<TimestampRange>.containsRange(other: Column<TimestampRange>)` — `@>`
  - `Column<TimestampRange>.isAdjacentTo(other: Column<TimestampRange>)` — `-|-`

**테스트 DB:** H2 (기본) + Testcontainers PostgreSQL (range 연산자 테스트)

---

### 2-4. `exposed-pgvector`
AI/ML 임베딩 벡터를 PostgreSQL `pgvector` 확장으로 저장하는 컬럼 타입.

**기술 선택:** `pgvector/pgvector:pg16` Docker 이미지, `com.pgvector:pgvector` JDBC 어댑터

**핵심 클래스:**
- `VectorColumnType(dimension: Int)` — `FloatArray` ↔ `pgvector`
  - `dimension` 은 `requirePositiveNumber` 로 검증
  - DDL: `VECTOR($dimension)`
  - `notNullValueToDB`: `PGvector(floatArray)` 로 변환
  - `valueFromDB`: `PGvector` → `FloatArray` 변환
- `Table.vector(name, dimension)` factory
- SQL 거리 함수 확장 (PostgreSQL 전용):
  - `Column<FloatArray>.cosineDistance(other)` — `<=>` 연산자
  - `Column<FloatArray>.l2Distance(other)` — `<->` 연산자
  - `Column<FloatArray>.innerProduct(other)` — `<#>` 연산자

**pgvector JDBC 타입 등록:** `PGvector.addVectorType(connection)` 을 매 JDBC 연결마다 호출해야 한다.
테스트에서는 `Database.connect()` 직후 `transaction { connection.connection.unwrap(PGConnection::class.java).let { PGvector.addVectorType(it) } }` 패턴으로 초기화한다.
애플리케이션 환경(HikariCP 등)에서는 `connectionInitSql` 또는 커넥션 팩토리 훅에서 처리한다.

**H2 fallback 없음** (pgvector는 PostgreSQL 전용)

**테스트 DB:** Testcontainers `pgvector/pgvector:pg16` (필수)

---

### 2-5. `exposed-postgis`
지리 좌표(Point, Polygon)를 PostGIS `GEOMETRY` 타입으로 저장하는 컬럼 타입.
기존 `bluetape4k-geo` 모듈과 연계 가능.

**기술 선택:** `net.postgis:postgis-jdbc`, `postgis/postgis:16-3.4` Docker 이미지

**타입 모델:** `PGpoint`/`PGpolygon`은 PostgreSQL 기본 geometric 타입용으로 PostGIS GEOMETRY와 직접 호환되지 않는다.
PostGIS GEOMETRY는 WKT(Well-Known Text) 또는 WKB(Well-Known Binary) 기반이므로 다음 타입을 사용한다:
- 저장/조회 도메인 타입: `org.postgis.Point`, `org.postgis.Polygon` (`net.postgis:postgis-jdbc` 제공)
- JDBC 변환: `PGgeometry` 래퍼를 통해 WKT 직렬화/역직렬화

**핵심 클래스:**
- `GeoPointColumnType` — `org.postgis.Point` ↔ `GEOMETRY(POINT, 4326)`
  - 저장: `PGgeometry(point)` 변환 후 JDBC PreparedStatement 바인딩
  - 조회: `PGgeometry` → `.geometry as Point` 추출
- `GeoPolygonColumnType` — `org.postgis.Polygon` ↔ `GEOMETRY(POLYGON, 4326)`
- `Table.geoPoint(name)` factory
- `Table.geoPolygon(name)` factory
- SQL 공간 함수 확장 (PostgreSQL/PostGIS 전용):
  - `Column<Point>.stDistance(other: Column<Point>)` — `ST_Distance`
  - `Column<Point>.stDWithin(other: Column<Point>, distance: Double)` — `ST_DWithin`
  - `Column<Point>.stWithin(polygon: Column<Polygon>)` — `ST_Within`

**H2 fallback 없음** (PostGIS는 PostgreSQL 전용)

**테스트 DB:** Testcontainers `postgis/postgis:16-3.4` (필수)

---

## 2-6. PostgreSQL 전용 연산자/함수 Dialect Gating 정책

`<<`, `@>`, `&&`, `-|-`, `<=>`, `ST_Distance` 등 PostgreSQL/PostGIS 전용 확장 함수는 **런타임 SQL 오류에 의존하지 않고** Exposed Expression 빌드 시점에 `UnsupportedOperationException` 을 발생시킨다.

구현 패턴:
```kotlin
fun Column<InetAddress>.isContainedBy(cidr: Expression<String>): Op<Boolean> {
    check(currentDialect is PostgreSQLDialect) {
        "isContainedBy (<<) 는 PostgreSQL dialect 에서만 지원됩니다."
    }
    return InetContainedByOp(this, cidr)
}
```

---

## 3. 공통 구현 규칙

| 항목 | 규칙 |
|------|------|
| 패키지 | `io.bluetape4k.exposed.{module}` |
| 기반 클래스 | `bluetape4k-exposed-core`의 `ColumnWithTransform`, `ColumnTransformer` |
| KDoc | 한국어, 모든 public class/interface/function 필수 |
| 테스트 | JUnit 5 + Kluent assertions |
| 비동기 | 필요 시 Coroutines (`suspend`, `Flow`) |
| 커밋 | 한국어, prefix (feat/fix/test/chore) |
| 모듈 등록 | `settings.gradle.kts` `includeModules("data", ...)` 로 자동 포함 |

---

## 4. 의존성 매핑

| 모듈 | 추가 의존성 |
|------|------------|
| `exposed-phone` | `com.googlecode.libphonenumber:libphonenumber:8.13.52` |
| `exposed-inet` | 없음 (JDK 표준) |
| `exposed-tsrange` | 없음 (JDK 표준, JSON 직렬화는 직접 구현) |
| `exposed-pgvector` | `com.pgvector:pgvector:0.1.6` |
| `exposed-postgis` | `net.postgis:postgis-jdbc:2023.1.0` |

모두 공통: `Libs.exposed_core`, `Libs.bluetape4k_exposed_core`, `Libs.postgresql_driver`(test), `Libs.bluetape4k_junit5`(test)

---

## 5. 디렉토리 구조 (각 모듈 동일)

```
data/exposed-{name}/
├── build.gradle.kts
└── src/
    ├── main/kotlin/io/bluetape4k/exposed/{name}/
    │   ├── {Name}ColumnType.kt
    │   ├── {Name}Transformer.kt      (필요 시)
    │   └── ExposedExtensions.kt      (Table factory + SQL 확장 함수)
    └── test/kotlin/io/bluetape4k/exposed/{name}/
        └── {Name}ColumnTypeTest.kt
```
