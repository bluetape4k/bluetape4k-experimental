# Plan: Exposed 커스텀 컬럼 타입 모듈 5개 구현

**날짜:** 2026-03-27
**Spec:** `docs/superpowers/specs/2026-03-27-exposed-dialects-design.md`
**작업 순서:** 단순한 것부터 → 복잡한 것 순

---

## Task 0 — `Libs.kt` 외부 라이브러리 상수 추가
**complexity: low**

`buildSrc/src/main/kotlin/Libs.kt` 에 아래 3개 상수가 이미 추가되어 있음을 확인한다 (이미 작업 완료):

```kotlin
const val libphonenumber = "com.googlecode.libphonenumber:libphonenumber:8.13.52"
const val pgvector = "com.pgvector:pgvector:0.1.6"
const val postgis_jdbc = "net.postgis:postgis-jdbc:2023.1.0"
```

---

## Task 1 — `exposed-phone` 모듈 골격 생성
**complexity: low**

`data/exposed-phone/` 디렉토리 및 기본 파일 생성.

```
data/exposed-phone/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/kotlin/io/bluetape4k/exposed/phone/
    └── test/
        ├── kotlin/io/bluetape4k/exposed/phone/
        └── resources/
            ├── junit-platform.properties   ← data/exposed-duckdb/src/test/resources/ 에서 복사
            └── logback-test.xml            ← data/exposed-duckdb/src/test/resources/ 에서 복사
```

`build.gradle.kts`:
```kotlin
dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.bluetape4k_exposed_core)
    api(Libs.libphonenumber)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.h2_v2)
}
```

---

## Task 2 — `PhoneNumberTransformer`, `PhoneNumberColumnType` 구현
**complexity: medium**

파일: `src/main/kotlin/io/bluetape4k/exposed/phone/PhoneNumberColumnType.kt`

구현:
- `PhoneNumberTransformer(defaultRegion: String = "KR")` — `ColumnTransformer<String, PhoneNumber>`
  - `companion object: KLogging()`
  - init 블록: `defaultRegion.requireNotBlank("defaultRegion")`
  - `wrap(value: String): PhoneNumber` — `PhoneNumberUtil.parse(value, defaultRegion)` 호출
    - `NumberParseException` 은 반드시 `IllegalArgumentException("전화번호 파싱 실패: $value", e)` 로 래핑
  - `unwrap(value: PhoneNumber): String` — `PhoneNumberUtil.format(value, E164)` 로 E.164 직렬화
- `PhoneNumberColumnType(defaultRegion: String = "KR")` — `ColumnWithTransform<String, PhoneNumber>` (VARCHAR(20))
- `PhoneNumberStringColumnType(defaultRegion: String = "KR")` — `ColumnWithTransform<String, String>` (VARCHAR(20))
  - wrap: E.164 정규화 후 String 반환
- `Table.phoneNumber(name, defaultRegion)` factory 확장 함수
- `Table.phoneNumberString(name, defaultRegion)` factory 확장 함수
- KDoc 한국어 필수 (모든 public class/function)

---

## Task 3 — `exposed-phone` 테스트 작성 및 빌드 검증
**complexity: medium**

파일: `src/test/kotlin/io/bluetape4k/exposed/phone/PhoneNumberColumnTypeTest.kt`

테스트 (H2 인메모리 DB):
- `010-1234-5678` → DB 저장 → `+821012345678` 조회 (`shouldBeEqualTo`)
- 미국 번호 `+1-650-253-0000` 파싱 → `+16502530000` 저장 검증
- 잘못된 번호 입력 시 `IllegalArgumentException` 발생 (`assertFailsWith<IllegalArgumentException>`)
- `phoneNumberString` 컬럼: E.164 정규화 후 저장, 동일 값 조회
- Kluent assertions 사용 (`shouldBeEqualTo`, `shouldBeNull`, `shouldNotBeNull`)

빌드 검증:
```bash
./gradlew :exposed-phone:build
```

---

## Task 4 — `exposed-inet` 모듈 생성 및 구현
**complexity: medium**

`data/exposed-inet/` 생성.

```
data/exposed-inet/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/kotlin/io/bluetape4k/exposed/inet/
    └── test/
        ├── kotlin/io/bluetape4k/exposed/inet/
        └── resources/
            ├── junit-platform.properties   ← data/exposed-duckdb 에서 복사
            └── logback-test.xml            ← data/exposed-duckdb 에서 복사
```

`build.gradle.kts`:
```kotlin
dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.bluetape4k_exposed_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.postgresql_driver)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_postgresql)
}
```

구현 (`InetColumnTypes.kt`):
- `InetAddressColumnType` — `companion object: KLogging()`
  - PostgreSQL: `INET` DDL
  - H2/기타: `VARCHAR(45)` DDL
  - `notNullValueToDB`: `InetAddress.getHostAddress()` 문자열로 변환
  - `valueFromDB`: `InetAddress.getByName(value)` 변환
- `CidrColumnType` — PostgreSQL: `CIDR` DDL / H2: `VARCHAR(50)` DDL
- `Table.inetAddress(name)` factory
- `Table.cidr(name)` factory
- PostgreSQL 전용 확장 함수 (`InetExtensions.kt`):
  ```kotlin
  fun Column<InetAddress>.isContainedBy(cidr: Expression<String>): Op<Boolean> {
      check(currentDialect is PostgreSQLDialect) { "isContainedBy (<<) 는 PostgreSQL dialect 에서만 지원됩니다." }
      return InetContainedByOp(this, cidr)
  }
  ```

테스트 (`InetColumnTypeTest.kt`):
- H2: IPv4 (`192.168.1.1`), IPv6 (`::1`) 저장/조회
- PostgreSQL Testcontainers: `INET` 네이티브 타입 저장/조회, `isContainedBy` 연산자 쿼리
- 비-PG dialect에서 `isContainedBy` 호출 시 예외 발생 확인

빌드 검증:
```bash
./gradlew :exposed-inet:build
```

---

## Task 5 — `exposed-tsrange` 모듈 생성 및 구현
**complexity: high**

`data/exposed-tsrange/` 생성.

```
data/exposed-tsrange/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/kotlin/io/bluetape4k/exposed/tsrange/
    └── test/
        ├── kotlin/io/bluetape4k/exposed/tsrange/
        └── resources/
            ├── junit-platform.properties   ← data/exposed-duckdb 에서 복사
            └── logback-test.xml            ← data/exposed-duckdb 에서 복사
```

`build.gradle.kts` (외부 JSON 라이브러리 미사용 — 직접 파싱):
```kotlin
dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.bluetape4k_exposed_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.postgresql_driver)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_postgresql)
}
```

구현:

**`TimestampRange.kt`:**
```kotlin
data class TimestampRange(
    val start: Instant,
    val end: Instant,
    val lowerInclusive: Boolean = true,
    val upperInclusive: Boolean = false,
) : Serializable {
    companion object : KLogging() {
        private const val serialVersionUID = 1L
    }
    fun contains(instant: Instant): Boolean { ... }
    fun overlaps(other: TimestampRange): Boolean { ... }
}
```

**`TstzRangeColumnType.kt`:**
- PostgreSQL: `TSTZRANGE` DDL, PgSQL range 리터럴 직접 파싱/생성
- H2 fallback: `VARCHAR(100)`, range literal 포맷 `[2024-01-01T00:00:00Z,2024-12-31T23:59:59Z)` (외부 라이브러리 미사용)
  - 파싱 로직: 첫 글자(`[`/`(`)로 lowerInclusive 결정, 마지막 글자(`]`/`)`)로 upperInclusive 결정
  - Instant는 `Instant.parse()`로 역직렬화

**`TstzRangeExtensions.kt` (PostgreSQL 전용):**
- `Column<TimestampRange>.overlaps(other)` — `&&`
  - `check(currentDialect is PostgreSQLDialect)` dialect gating
- `Column<TimestampRange>.contains(instant)` — `@>`
- `Column<TimestampRange>.containsRange(other)` — `@>`
- `Column<TimestampRange>.isAdjacentTo(other)` — `-|-`

테스트:
- H2: 범위 저장/조회, `TimestampRange.contains()`, `overlaps()` Kotlin 로직 (`shouldBeTrue`/`shouldBeFalse`)
- PostgreSQL Testcontainers: `TSTZRANGE` 네이티브 저장/조회, `&&`/`@>` 연산자 쿼리

빌드 검증:
```bash
./gradlew :exposed-tsrange:build
```

---

## Task 6 — `exposed-pgvector` 모듈 생성 및 구현
**complexity: high**

`data/exposed-pgvector/` 생성.

```
data/exposed-pgvector/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/kotlin/io/bluetape4k/exposed/pgvector/
    └── test/
        ├── kotlin/io/bluetape4k/exposed/pgvector/
        └── resources/
            ├── junit-platform.properties   ← data/exposed-duckdb 에서 복사
            └── logback-test.xml            ← data/exposed-duckdb 에서 복사
```

`build.gradle.kts`:
```kotlin
dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.bluetape4k_exposed_core)
    api(Libs.pgvector)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.postgresql_driver)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_postgresql)
}
```

구현 (`VectorColumnType.kt`):
- `VectorColumnType(dimension: Int)`:
  - `companion object: KLogging()`
  - init: `dimension.requirePositiveNumber("dimension")`
  - DDL: `VECTOR($dimension)`
  - `notNullValueToDB`: `PGvector(floatArray)` 변환
  - `valueFromDB`: `PGvector` → `.toArray()` → `FloatArray` 변환
  - `sqlType()`: `"VECTOR($dimension)"`

**pgvector 타입 등록 패턴 (`VectorExtensions.kt`)**:
```kotlin
// 테스트에서 호출
fun Connection.registerVectorType() {
    PGvector.addVectorType(unwrap(PGConnection::class.java))
}
```
- `Database.connect()` 직후 `transaction { connection.connection.registerVectorType() }` 로 초기화

거리 함수 (`VectorDistanceFunctions.kt`, PostgreSQL 전용):
- `Column<FloatArray>.cosineDistance(other)` → `<=>` op
  - `check(currentDialect is PostgreSQLDialect)` dialect gating
- `Column<FloatArray>.l2Distance(other)` → `<->` op
- `Column<FloatArray>.innerProduct(other)` → `<#>` op

테스트 (Testcontainers `pgvector/pgvector:pg16`):
- `CREATE EXTENSION IF NOT EXISTS vector` 실행
- `registerVectorType()` 초기화
- 벡터 저장/조회 (`shouldBeEqualTo` — float 비교는 delta 허용)
- 코사인 거리 기준 유사도 검색 (`ORDER BY cosineDistance(...)`)
- 차원 불일치 시 `IllegalArgumentException` 확인

빌드 검증:
```bash
./gradlew :exposed-pgvector:build
```

---

## Task 7 — `exposed-postgis` 모듈 생성 및 구현
**complexity: high**

`data/exposed-postgis/` 생성.

```
data/exposed-postgis/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/kotlin/io/bluetape4k/exposed/postgis/
    └── test/
        ├── kotlin/io/bluetape4k/exposed/postgis/
        └── resources/
            ├── junit-platform.properties   ← data/exposed-duckdb 에서 복사
            └── logback-test.xml            ← data/exposed-duckdb 에서 복사
```

`build.gradle.kts`:
```kotlin
dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.bluetape4k_exposed_core)
    api(Libs.postgis_jdbc)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.postgresql_driver)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_postgresql)
}
```

구현 (`GeoColumnTypes.kt`):
- 도메인 타입: `org.postgis.Point`, `org.postgis.Polygon` (net.postgis:postgis-jdbc 제공)
- `GeoPointColumnType`:
  - `companion object: KLogging()`
  - DDL: `GEOMETRY(POINT, 4326)`
  - 저장: `PGgeometry(point)` 래핑 후 JDBC 바인딩
  - 조회: `(value as PGgeometry).geometry as Point`
- `GeoPolygonColumnType` — 동일 패턴, `Polygon` 타입
- `Table.geoPoint(name)` factory
- `Table.geoPolygon(name)` factory

공간 함수 (`GeoExtensions.kt`, PostGIS 전용):
- `check(currentDialect is PostgreSQLDialect)` dialect gating 적용
- `Column<Point>.stDistance(other: Column<Point>)` → `ST_Distance(?, ?)`
- `Column<Point>.stDWithin(other: Column<Point>, distance: Double)` → `ST_DWithin(?, ?, ?)`
- `Column<Point>.stWithin(polygon: Column<Polygon>)` → `ST_Within(?, ?)`

테스트 (Testcontainers `postgis/postgis:16-3.4`):
- `CREATE EXTENSION IF NOT EXISTS postgis` 실행
- 서울 좌표 `Point(37.5665, 126.9780)` 저장/조회 (SRID 4326)
- 반경 10km 이내 포인트 검색 (`ST_DWithin`)
- 폴리곤 내 포인트 확인 (`ST_Within`)

빌드 검증:
```bash
./gradlew :exposed-postgis:build
```

---

## Task 8 — 전체 커밋
**complexity: low**

모든 모듈 빌드 통과 후 각 모듈별 커밋:

```bash
git add data/exposed-phone
git commit -m "feat(exposed-phone): 전화번호 E.164 정규화 컬럼 타입 모듈 추가"

git add data/exposed-inet
git commit -m "feat(exposed-inet): IP 주소/CIDR 컬럼 타입 모듈 추가"

git add data/exposed-tsrange
git commit -m "feat(exposed-tsrange): 타임스탬프 범위 컬럼 타입 모듈 추가"

git add data/exposed-pgvector
git commit -m "feat(exposed-pgvector): pgvector 임베딩 벡터 컬럼 타입 모듈 추가"

git add data/exposed-postgis
git commit -m "feat(exposed-postgis): PostGIS 지리 좌표 컬럼 타입 모듈 추가"

git add buildSrc/src/main/kotlin/Libs.kt
git commit -m "chore(libs): libphonenumber, pgvector, postgis-jdbc 상수 추가"
```

---

## 실행 순서 요약

```
Task 0 (low)    → Libs.kt 상수 확인 (완료)
Task 1 (low)    → exposed-phone 골격
Task 2 (medium) → PhoneNumberColumnType 구현
Task 3 (medium) → exposed-phone 테스트 + 빌드 ✓

병렬 실행 가능 (Tasks 4~7 독립적):
Task 4 (medium) → exposed-inet 구현 + 빌드 ✓
Task 5 (high)   → exposed-tsrange 구현 + 빌드 ✓
Task 6 (high)   → exposed-pgvector 구현 + 빌드 ✓
Task 7 (high)   → exposed-postgis 구현 + 빌드 ✓

Task 8 (low)    → 모듈별 커밋
```

---

## bluetape4k-patterns 체크리스트 (모든 구현에 적용)

- [ ] 인자 검증: `requireNotBlank`, `requirePositiveNumber`, `requireNotNull` 사용 (stdlib `require()` 금지)
- [ ] 로깅: `companion object: KLogging()` 패턴
- [ ] Value Object: `Serializable` + `serialVersionUID = 1L`
- [ ] 예외 래핑: 외부 라이브러리 예외 → `IllegalArgumentException`/`IllegalStateException`
- [ ] Dialect Gating: PostgreSQL 전용 함수는 `check(currentDialect is PostgreSQLDialect)` 적용
- [ ] 테스트 Assertion: Kluent 우선 (`shouldBeEqualTo`, `shouldBeNull`, `assertFailsWith`)
- [ ] test/resources: `junit-platform.properties`, `logback-test.xml` 복사 확인
- [ ] README.md: 각 모듈에 작성
