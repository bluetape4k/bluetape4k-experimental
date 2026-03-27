# Plan: exposed-mysql8-gis 모듈 구현

**날짜:** 2026-03-28
**Spec:** `docs/superpowers/specs/2026-03-28-exposed-mysql8-gis-design.md`
**모듈:** `data/exposed-mysql8-gis/`
**패키지:** `io.bluetape4k.exposed.mysql8.gis`

---

## 의존성 그래프

```
Task 0 (Libs.kt)
  │
  ├─→ Task 1 (모듈 골격)
  │     │
  │     ├─→ Task 0.5 (Spike: write path 검증) ─→ Task 2 결정에 영향
  │     │
  │     ├─→ Task 2 (MySqlWkbUtils)
  │     │     │
  │     │     └─→ Task 3 (GeometryColumnType)
  │     │           │
  │     │           ├─→ Task 4 (GeoExtensions)
  │     │           │
  │     │           ├─→ Task 5 (SpatialExpressions + SpatialFunctions)
  │     │           │
  │     │           └─→ Task 6 (JtsHelpers)
  │     │                 │
  │     │                 └─→ Task 7 (ColumnType CRUD 테스트)
  │     │                       │
  │     │                       ├─→ Task 8 (관계 함수 테스트)
  │     │                       ├─→ Task 9 (측정 함수 테스트)
  │     │                       └─→ Task 10 (변환/속성 함수 테스트)
  │     │
  │     └─→ Task 11 (README.md) — 모든 구현 완료 후
```

## 병렬 실행 가능 그룹

| 그룹 | 태스크 | 비고 |
|------|--------|------|
| **G0** | Task 0 | 선행 필수 |
| **G1** | Task 1 | Task 0 완료 후 |
| **G2** | Task 0.5 | Task 1 완료 후 독립 실행, 결과가 Task 2 설계에 영향 |
| **G3** | Task 2, Task 6 | Task 0.5 완료 후 병렬 |
| **G4** | Task 3 | Task 2 완료 후 |
| **G5** | Task 4, Task 5 | Task 3 완료 후 병렬 |
| **G6** | Task 7 | Task 4 + 5 + 6 완료 후 |
| **G7** | Task 8, Task 9, Task 10 | Task 7 완료 후 병렬 |
| **G8** | Task 11 | 모든 태스크 완료 후 |

---

## Task 0 — `Libs.kt` 의존성 추가

**complexity: low** | 의존성: 없음

### 작업 내용

`buildSrc/src/main/kotlin/Libs.kt`에 JTS 의존성 상수를 추가한다.

```kotlin
// JTS (Java Topology Suite) — MySQL 8.0 GIS 공간 데이터 (exposed-mysql8-gis)
// https://mvnrepository.com/artifact/org.locationtech.jts/jts-core
const val jts_core = "org.locationtech.jts:jts-core:1.20.0"
```

### 파일
- `buildSrc/src/main/kotlin/Libs.kt`

### 검증
- `./gradlew :exposed-mysql8-gis:dependencies` 실행 시 jts-core 확인 (모듈 생성 후)

---

## Task 0.5 — Spike: Write Path 검증

**complexity: high** | 의존성: Task 1

### 목적

MySQL Connector/J가 GEOMETRY 컬럼에 Internal Format ByteArray를 PreparedStatement로 직접 바인딩할 수 있는지 검증한다. 실패 시 WKT fallback 전략으로 전환한다.

### 작업 내용

Testcontainers `mysql:8.0`을 사용한 스파이크 테스트를 작성한다. Exposed ORM이 아닌 순수 JDBC로 테스트하여 바인딩 동작을 격리 검증한다.

### 파일
- `data/exposed-mysql8-gis/src/test/kotlin/io/bluetape4k/exposed/mysql8/gis/SpikeWritePathTest.kt`

### 테스트 시나리오 (4개)

```kotlin
class SpikeWritePathTest {
    companion object: KLogging() {
        @JvmStatic
        val mysqlContainer: MySQLContainer<*> = MySQLContainer(
            DockerImageName.parse("mysql:8.0")
        ).apply { start() }
    }

    @Test
    fun `ByteArray 직접 바인딩으로 Point 라운드트립`() {
        // 1. CREATE TABLE test_geo (id INT PRIMARY KEY, geom GEOMETRY SRID 4326)
        // 2. INSERT: PreparedStatement.setBytes(1, sridLE + wkb)
        // 3. SELECT: ResultSet.getBytes("geom") → parseMySqlInternalGeometry → Point 비교
    }

    @Test
    fun `SRID mismatch 동작 확인`() {
        // 컬럼 SRID 4326, 데이터 SRID 0 또는 3857 삽입 시 예외 또는 자동 보정 확인
    }

    @Test
    fun `axis-order 라운드트립 — lng-lat 순서 보존`() {
        // Point(126.9780, 37.5665) 삽입 → 조회 후 x=126.9780, y=37.5665 확인
        // nonNullValueToString의 axis-order=long-lat 옵션 검증
    }

    @Test
    fun `WKT fallback — ST_GeomFromText 방식 동작 확인`() {
        // INSERT via ST_GeomFromText('POINT(126.9780 37.5665)', 4326, 'axis-order=long-lat')
        // 라운드트립 확인
    }
}
```

### 결과에 따른 분기

| 결과 | Task 2~3 설계 영향 |
|------|-------------------|
| ByteArray 바인딩 성공 | **경로 A**: `notNullValueToDB()` → Internal Format ByteArray 반환 (Spec 기본안) |
| ByteArray 바인딩 실패 | **경로 B (WKT fallback)**: `notNullValueToDB()` → WKT String 반환, `parameterMarker()` 오버라이드 → `ST_GeomFromText(?, $srid, 'axis-order=long-lat')` |

### 결과 기록 및 전달

- 테스트 결과를 `SpikeWritePathTest.kt` **상단 주석**에 기록한다:
  - 어느 경로(A/B)가 선택되었는지
  - ByteArray 바인딩 시 발생한 에러 메시지 (실패 시)
  - axis-order 보존 여부
- Task 3 담당자에게 **경로 A/B 중 어느 것을 구현할지 명시적으로 전달**한다.

### `parameterMarker()` 패턴 참조 (경로 B 선택 시)

`InetColumnTypes.kt`, `TstzRangeColumnType.kt`의 기존 패턴을 따른다:

```kotlin
// InetColumnTypes.kt 참조 — dialect별 parameterMarker 분기
override fun parameterMarker(value: T?): String =
    "ST_GeomFromText(?, $srid, 'axis-order=long-lat')"
```

### 검증
- 4개 테스트 통과 또는 fallback 전략 선택 완료
- `./gradlew :exposed-mysql8-gis:test --tests "*.SpikeWritePathTest"` 통과

---

## Task 1 — 모듈 골격 생성

**complexity: low** | 의존성: Task 0

### 작업 내용

모듈 디렉토리 구조 및 빌드 설정 파일을 생성한다.

### 디렉토리 구조

```
data/exposed-mysql8-gis/
├── build.gradle.kts
└── src/
    ├── main/kotlin/io/bluetape4k/exposed/mysql8/gis/
    └── test/
        ├── kotlin/io/bluetape4k/exposed/mysql8/gis/
        └── resources/
            ├── junit-platform.properties   ← 기존 모듈에서 복사
            └── logback-test.xml            ← 기존 모듈에서 복사
```

### `build.gradle.kts`

```kotlin
dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.bluetape4k_exposed_core)
    api(Libs.jts_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_exposed_jdbc_tests)
    testImplementation(Libs.mysql_connector_j)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_mysql)
}
```

### 파일
- `data/exposed-mysql8-gis/build.gradle.kts`
- `data/exposed-mysql8-gis/src/test/resources/junit-platform.properties` (복사)
- `data/exposed-mysql8-gis/src/test/resources/logback-test.xml` (복사)

### 검증
- `./gradlew :exposed-mysql8-gis:compileKotlin` 통과 (소스 없이도 빈 모듈 빌드 성공)

---

## Task 2 — `MySqlWkbUtils.kt` 구현

**complexity: high** | 의존성: Task 0.5 (결과에 따라 write 전략 결정)

### 작업 내용

MySQL Internal Geometry Format (4바이트 LE SRID + 표준 WKB) 의 파싱/직렬화 유틸리티를 구현한다.

### 파일
- `data/exposed-mysql8-gis/src/main/kotlin/io/bluetape4k/exposed/mysql8/gis/MySqlWkbUtils.kt`
- `data/exposed-mysql8-gis/src/test/kotlin/io/bluetape4k/exposed/mysql8/gis/MySqlWkbUtilsTest.kt`

### 함수 시그니처

```kotlin
package io.bluetape4k.exposed.mysql8.gis

import io.bluetape4k.logging.KLogging
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.ByteOrderValues
import org.locationtech.jts.io.WKBReader
import org.locationtech.jts.io.WKBWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** WGS 84 SRID 상수 */
const val SRID_WGS84: Int = 4326

/**
 * MySQL Internal Geometry Format 변환 유틸리티.
 *
 * MySQL 8.0은 공간 데이터를 `[4바이트 SRID (LE)] + [표준 WKB]` 형식으로 저장한다.
 */
object MySqlWkbUtils: KLogging() {

    /**
     * MySQL Internal Geometry Format ByteArray → JTS Geometry 변환.
     *
     * @param bytes MySQL Internal Format (4바이트 LE SRID + WKB)
     * @return JTS Geometry (SRID 설정됨)
     */
    fun parseMySqlInternalGeometry(bytes: ByteArray): Geometry {
        require(bytes.size >= 5) {
            "MySQL internal geometry format requires at least 5 bytes (4 SRID + 1 WKB), got ${bytes.size}"
        }
        val srid = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val wkb = bytes.copyOfRange(4, bytes.size)
        val geometry = WKBReader().read(wkb)
        geometry.srid = srid
        return geometry
    }

    /**
     * JTS Geometry → MySQL Internal Geometry Format ByteArray 변환.
     *
     * @param geometry JTS Geometry
     * @param srid SRID (기본: geometry.srid)
     * @return MySQL Internal Format ByteArray (4바이트 LE SRID + WKB)
     */
    fun buildMySqlInternalGeometry(geometry: Geometry, srid: Int = geometry.srid): ByteArray {
        val wkb = WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN).write(geometry)
        val sridBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(srid).array()
        return sridBytes + wkb
    }
}
```

### 단위 테스트 (MySqlWkbUtilsTest)

```kotlin
class MySqlWkbUtilsTest {

    @Test
    fun `Point 라운드트립 — build 후 parse하면 동일한 좌표`() {
        val point = wgs84Point(126.9780, 37.5665)
        val bytes = MySqlWkbUtils.buildMySqlInternalGeometry(point)
        val parsed = MySqlWkbUtils.parseMySqlInternalGeometry(bytes)

        parsed.shouldBeInstanceOf<Point>()
        (parsed as Point).x.shouldBeEqualTo(126.9780)
        parsed.y.shouldBeEqualTo(37.5665)
        parsed.srid.shouldBeEqualTo(SRID_WGS84)
    }

    @Test
    fun `Polygon 라운드트립`() { ... }

    @Test
    fun `SRID가 Little-Endian으로 인코딩됨`() {
        val point = wgs84Point(0.0, 0.0)
        val bytes = MySqlWkbUtils.buildMySqlInternalGeometry(point, SRID_WGS84)
        // 4326 = 0x000010E6 → LE = E6 10 00 00
        bytes[0].shouldBeEqualTo(0xE6.toByte())
        bytes[1].shouldBeEqualTo(0x10.toByte())
        bytes[2].shouldBeEqualTo(0x00.toByte())
        bytes[3].shouldBeEqualTo(0x00.toByte())
    }

    @Test
    fun `빈 ByteArray 시 예외 발생`() {
        assertFailsWith<IllegalArgumentException> {
            MySqlWkbUtils.parseMySqlInternalGeometry(byteArrayOf())
        }
    }

    @Test
    fun `잘린 입력(4바이트 미만) 시 예외 발생`() {
        assertFailsWith<IllegalArgumentException> {
            MySqlWkbUtils.parseMySqlInternalGeometry(byteArrayOf(0x01, 0x02, 0x03))
        }
    }

    @Test
    fun `SRID만 있고 WKB 없는 4바이트 입력 시 예외 발생`() {
        assertFailsWith<IllegalArgumentException> {
            MySqlWkbUtils.parseMySqlInternalGeometry(byteArrayOf(0xE6.toByte(), 0x10, 0x00, 0x00))
        }
    }
}
```

### 검증
- `./gradlew :exposed-mysql8-gis:test --tests "*.MySqlWkbUtilsTest"` — 6개 테스트 통과

---

## Task 3 — `GeometryColumnType<T : Geometry>` 구현

**complexity: high** | 의존성: Task 2

### 작업 내용

Exposed `ColumnType<T>` 을 상속하여 MySQL 8.0 GIS 컬럼 타입을 구현한다. Task 0.5 결과에 따라 두 경로 중 하나를 구현한다:

- **경로 A (ByteArray 직접 바인딩)**: `notNullValueToDB()` → MySQL Internal Format ByteArray
- **경로 B (WKT fallback)**: `notNullValueToDB()` → WKT 문자열, `parameterMarker()` 오버라이드 → `ST_GeomFromText(?, $srid, 'axis-order=long-lat')`

### 파일
- `data/exposed-mysql8-gis/src/main/kotlin/io/bluetape4k/exposed/mysql8/gis/GeometryColumnType.kt`

### 클래스 시그니처

```kotlin
package io.bluetape4k.exposed.mysql8.gis

import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.ByteOrderValues
import org.locationtech.jts.io.WKBWriter
import kotlin.reflect.KClass

/**
 * MySQL 8.0 GIS Geometry 컬럼 타입.
 *
 * 8종 Geometry 타입을 단일 제네릭 클래스로 처리한다.
 * MySQL Internal Format (4바이트 LE SRID + WKB) 을 사용하여 직렬화/역직렬화한다.
 *
 * @param T JTS Geometry 서브타입
 * @param geometryType MySQL SQL 타입명 ("POINT", "POLYGON", "GEOMETRY" 등)
 * @param srid SRID (기본: 4326 WGS84)
 */
class GeometryColumnType<T : Geometry>(
    val geometryType: String,
    val srid: Int = SRID_WGS84,
) : ColumnType<T>() {

    companion object: KLogging() {
        /** JTS Geometry 타입 매핑 테이블 */
        private val GEOMETRY_TYPE_MAP: Map<String, KClass<out Geometry>> = mapOf(
            "point" to Point::class,
            "linestring" to LineString::class,
            "polygon" to Polygon::class,
            "multipoint" to MultiPoint::class,
            "multilinestring" to MultiLineString::class,
            "multipolygon" to MultiPolygon::class,
            "geometrycollection" to GeometryCollection::class,
        )
    }

    init {
        geometryType.requireNotBlank("geometryType")
        srid.requirePositiveNumber("srid")
    }

    override fun sqlType(): String {
        check(currentDialect is MysqlDialect) {
            "GeometryColumnType 은 MySQL dialect 에서만 지원됩니다."
        }
        return "$geometryType SRID $srid"
    }

    // ── 경로 A: ByteArray 직접 바인딩 (Task 0.5에서 성공 시) ──
    override fun notNullValueToDB(value: T): Any {
        if (value.srid == 0) value.srid = srid
        return MySqlWkbUtils.buildMySqlInternalGeometry(value, value.srid)
    }

    // ── 경로 B: WKT fallback (Task 0.5에서 ByteArray 바인딩 실패 시) ──
    // notNullValueToDB → WKT 문자열 반환
    // override fun notNullValueToDB(value: T): Any {
    //     if (value.srid == 0) value.srid = srid
    //     return value.toText()  // WKT 문자열: "POINT(126.978 37.5665)"
    // }
    //
    // parameterMarker 오버라이드 (InetColumnTypes.kt, TstzRangeColumnType.kt 패턴)
    // override fun parameterMarker(value: T?): String =
    //     "ST_GeomFromText(?, $srid, 'axis-order=long-lat')"

    override fun nonNullValueToString(value: T): String {
        if (value.srid == 0) value.srid = srid
        val wkb = WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN).write(value)
        val hex = wkb.joinToString("") { "%02x".format(it) }
        return "ST_GeomFromWKB(X'$hex', ${value.srid}, 'axis-order=long-lat')"
    }

    @Suppress("UNCHECKED_CAST")
    override fun valueFromDB(value: Any): T {
        val geometry = when (value) {
            is ByteArray -> MySqlWkbUtils.parseMySqlInternalGeometry(value)
            is Geometry  -> value
            else -> error("Unsupported value type: ${value::class.java}")
        }
        if (geometryType != "GEOMETRY") {
            val expectedClass = GEOMETRY_TYPE_MAP[geometryType.lowercase()]
                ?: error("Unknown geometry type: $geometryType")
            check(expectedClass.isInstance(geometry)) {
                "Expected $geometryType but got ${geometry.geometryType}"
            }
        }
        return geometry as T
    }
}
```

### 8종 타입별 편의 팩토리 함수 (같은 파일 하단)

```kotlin
fun PointColumnType(srid: Int = SRID_WGS84) = GeometryColumnType<Point>("POINT", srid)
fun LineStringColumnType(srid: Int = SRID_WGS84) = GeometryColumnType<LineString>("LINESTRING", srid)
fun PolygonColumnType(srid: Int = SRID_WGS84) = GeometryColumnType<Polygon>("POLYGON", srid)
fun MultiPointColumnType(srid: Int = SRID_WGS84) = GeometryColumnType<MultiPoint>("MULTIPOINT", srid)
fun MultiLineStringColumnType(srid: Int = SRID_WGS84) = GeometryColumnType<MultiLineString>("MULTILINESTRING", srid)
fun MultiPolygonColumnType(srid: Int = SRID_WGS84) = GeometryColumnType<MultiPolygon>("MULTIPOLYGON", srid)
fun GeometryCollectionColumnType(srid: Int = SRID_WGS84) = GeometryColumnType<GeometryCollection>("GEOMETRYCOLLECTION", srid)
fun GenericGeometryColumnType(srid: Int = SRID_WGS84) = GeometryColumnType<Geometry>("GEOMETRY", srid)
```

### 검증
- `./gradlew :exposed-mysql8-gis:compileKotlin` 통과
- Task 7 에서 통합 테스트로 검증

---

## Task 4 — `GeoExtensions.kt` Table 확장 함수

**complexity: low** | 의존성: Task 3

### 작업 내용

`Table` 확장 함수로 Geometry 컬럼을 등록하는 편의 함수를 제공한다.

### 파일
- `data/exposed-mysql8-gis/src/main/kotlin/io/bluetape4k/exposed/mysql8/gis/GeoExtensions.kt`

### 함수 시그니처

```kotlin
package io.bluetape4k.exposed.mysql8.gis

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import org.locationtech.jts.geom.*

fun Table.geoPoint(name: String, srid: Int = SRID_WGS84): Column<Point> =
    registerColumn(name, PointColumnType(srid))

fun Table.geoLineString(name: String, srid: Int = SRID_WGS84): Column<LineString> =
    registerColumn(name, LineStringColumnType(srid))

fun Table.geoPolygon(name: String, srid: Int = SRID_WGS84): Column<Polygon> =
    registerColumn(name, PolygonColumnType(srid))

fun Table.geoMultiPoint(name: String, srid: Int = SRID_WGS84): Column<MultiPoint> =
    registerColumn(name, MultiPointColumnType(srid))

fun Table.geoMultiLineString(name: String, srid: Int = SRID_WGS84): Column<MultiLineString> =
    registerColumn(name, MultiLineStringColumnType(srid))

fun Table.geoMultiPolygon(name: String, srid: Int = SRID_WGS84): Column<MultiPolygon> =
    registerColumn(name, MultiPolygonColumnType(srid))

fun Table.geoGeometryCollection(name: String, srid: Int = SRID_WGS84): Column<GeometryCollection> =
    registerColumn(name, GeometryCollectionColumnType(srid))

fun Table.geometry(name: String, srid: Int = SRID_WGS84): Column<Geometry> =
    registerColumn(name, GenericGeometryColumnType(srid))
```

### 검증
- `./gradlew :exposed-mysql8-gis:compileKotlin` 통과

---

## Task 5 — `SpatialExpressions.kt` + `SpatialFunctions.kt`

**complexity: medium** | 의존성: Task 3

### 작업 내용

MySQL 8.0 ST_* 공간 함수를 Exposed Expression/Op 클래스와 Column 확장 함수로 구현한다.

### 파일
- `data/exposed-mysql8-gis/src/main/kotlin/io/bluetape4k/exposed/mysql8/gis/SpatialExpressions.kt`
- `data/exposed-mysql8-gis/src/main/kotlin/io/bluetape4k/exposed/mysql8/gis/SpatialFunctions.kt`

### `SpatialExpressions.kt` — Expression/Op 클래스

```kotlin
package io.bluetape4k.exposed.mysql8.gis

import org.jetbrains.exposed.v1.core.*
import org.locationtech.jts.geom.Geometry

// ── 관계 함수 (Op<Boolean>) ──

/** ST_Contains(a, b) */
class StContainsOp(
    val left: Expression<out Geometry>,
    val right: Expression<out Geometry>,
): Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder { append("ST_Contains(", left, ", ", right, ")") }
    }
}

/** ST_Within(a, b) */
class StWithinOp(...)  // 동일 패턴

/** ST_Intersects(a, b) */
class StIntersectsOp(...)

/** ST_Disjoint(a, b) */
class StDisjointOp(...)

/** ST_Overlaps(a, b) */
class StOverlapsOp(...)

/** ST_Touches(a, b) */
class StTouchesOp(...)

/** ST_Crosses(a, b) */
class StCrossesOp(...)

/** ST_Equals(a, b) */
class StEqualsOp(...)

// ── 측정 함수 (Expression<Double>) ──

/** ST_Distance(a, b) — SRID 4326: 미터 */
class StDistanceExpr(
    val left: Expression<out Geometry>,
    val right: Expression<out Geometry>,
): Expression<Double>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder { append("ST_Distance(", left, ", ", right, ")") }
    }
    override val columnType = DoubleColumnType()
}

/** ST_Distance_Sphere(a, b) — 구면 거리, 미터 */
class StDistanceSphereExpr(...)

/** ST_Area(a) — SRID 4326: 제곱미터 */
class StAreaExpr(...)

/** ST_Length(a) — SRID 4326: 미터 */
class StLengthExpr(...)

// ── 변환/생성 함수 ──

/** ST_Buffer(a, distance) — geographic SRS(4326)에서는 Point 전용 */
class StBufferExpr(...)

/**
 * ST_Centroid(a)
 * ⚠️ MySQL 8.0 geographic SRS(SRID 4326)에서는 ER_NOT_IMPLEMENTED_FOR_GEOGRAPHIC_SRS 에러 발생.
 * Cartesian SRS 전용이므로 SRID 4326 환경에서는 사용 금지.
 */
@Deprecated("ST_Centroid는 geographic SRS(4326)에서 지원되지 않습니다. Cartesian SRS 전용.")
class StCentroidExpr(...)

/**
 * ST_Envelope(a)
 * ⚠️ MySQL 8.0 geographic SRS(SRID 4326)에서는 ER_NOT_IMPLEMENTED_FOR_GEOGRAPHIC_SRS 에러 발생.
 * Cartesian SRS 전용이므로 SRID 4326 환경에서는 사용 금지.
 */
@Deprecated("ST_Envelope은 geographic SRS(4326)에서 지원되지 않습니다. Cartesian SRS 전용.")
class StEnvelopeExpr(...)

/** ST_Union(a, b) */
class StUnionExpr(...)

/** ST_Intersection(a, b) */
class StIntersectionExpr(...)

/** ST_Difference(a, b) */
class StDifferenceExpr(...)

/** ST_ConvexHull(a) */
class StConvexHullExpr(...)

// ── 속성 함수 ──

/** ST_AsText(a) */
class StAsTextExpr(...)

/** ST_AsBinary(a) */
class StAsBinaryExpr(...)

/** ST_SRID(a) */
class StSridExpr(...)

/** ST_IsEmpty(a) */
class StIsEmptyOp(...)

/** ST_IsValid(a) */
class StIsValidOp(...)

/** ST_Dimension(a) */
class StDimensionExpr(...)

/** ST_GeometryType(a) */
class StGeometryTypeExpr(...)

/** ST_NumPoints(a) */
class StNumPointsExpr(...)
```

### `SpatialFunctions.kt` — Column 확장 함수

```kotlin
package io.bluetape4k.exposed.mysql8.gis

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.locationtech.jts.geom.*

// ── MysqlDialect guard ──
@Suppress("NOTHING_TO_INLINE")
private inline fun mysqlDialectGuard(functionName: String) {
    check(currentDialect is MysqlDialect) {
        "$functionName 는 MySQL dialect 에서만 지원됩니다."
    }
}

// ── 관계 함수 — Op<Boolean> ──

fun Column<out Geometry>.stContains(other: Column<out Geometry>): Op<Boolean> {
    mysqlDialectGuard("stContains")
    return StContainsOp(this, other)
}

fun Column<out Geometry>.stWithin(other: Column<out Geometry>): Op<Boolean> { ... }
fun Column<out Geometry>.stIntersects(other: Column<out Geometry>): Op<Boolean> { ... }
fun Column<out Geometry>.stDisjoint(other: Column<out Geometry>): Op<Boolean> { ... }
fun Column<out Geometry>.stOverlaps(other: Column<out Geometry>): Op<Boolean> { ... }
fun Column<out Geometry>.stTouches(other: Column<out Geometry>): Op<Boolean> { ... }
fun Column<out Geometry>.stCrosses(other: Column<out Geometry>): Op<Boolean> { ... }
fun Column<out Geometry>.stEquals(other: Column<out Geometry>): Op<Boolean> { ... }

// ── 측정 함수 — Expression<Double> ──

fun Column<out Geometry>.stDistance(other: Column<out Geometry>): Expression<Double> { ... }
fun Column<Point>.stDistanceSphere(other: Column<Point>): Expression<Double> { ... }

// ST_Area — Polygon, MultiPolygon만 허용
fun Column<out Polygon>.stArea(): Expression<Double> { ... }
@JvmName("stAreaMultiPolygon")
fun Column<out MultiPolygon>.stArea(): Expression<Double> { ... }

// ST_Length — LineString, MultiLineString만 허용
fun Column<out LineString>.stLength(): Expression<Double> { ... }
@JvmName("stLengthMultiLineString")
fun Column<out MultiLineString>.stLength(): Expression<Double> { ... }

// ST_NumPoints — LineString 전용
fun Column<out LineString>.stNumPoints(): Expression<Int> { ... }

// ── 변환/생성 함수 ──

/** ST_Buffer — geographic SRS(4326)에서는 Point에서만 안전. Point 전용으로 제한. */
fun Column<out Point>.stBuffer(distance: Double): Expression<Geometry> { ... }

/** ST_Centroid — geographic SRS(4326)에서 ER_NOT_IMPLEMENTED_FOR_GEOGRAPHIC_SRS 에러 발생. */
@Deprecated("ST_Centroid는 geographic SRS(4326)에서 지원되지 않습니다. Cartesian SRS 전용.")
fun Column<out Geometry>.stCentroid(): Expression<Point> { ... }

/** ST_Envelope — geographic SRS(4326)에서 ER_NOT_IMPLEMENTED_FOR_GEOGRAPHIC_SRS 에러 발생. */
@Deprecated("ST_Envelope은 geographic SRS(4326)에서 지원되지 않습니다. Cartesian SRS 전용.")
fun Column<out Geometry>.stEnvelope(): Expression<Geometry> { ... }
fun Column<out Geometry>.stUnion(other: Column<out Geometry>): Expression<Geometry> { ... }
fun Column<out Geometry>.stIntersection(other: Column<out Geometry>): Expression<Geometry> { ... }
fun Column<out Geometry>.stDifference(other: Column<out Geometry>): Expression<Geometry> { ... }
fun Column<out Geometry>.stConvexHull(): Expression<Geometry> { ... }

// ── 속성 함수 ──

fun Column<out Geometry>.stSrid(): Expression<Int> { ... }
fun Column<out Geometry>.stAsText(): Expression<String> { ... }
fun Column<out Geometry>.stAsBinary(): Expression<ByteArray> { ... }
fun Column<out Geometry>.stIsEmpty(): Op<Boolean> { ... }
fun Column<out Geometry>.stIsValid(): Op<Boolean> { ... }
fun Column<out Geometry>.stDimension(): Expression<Int> { ... }
fun Column<out Geometry>.stGeometryType(): Expression<String> { ... }
```

### 검증
- `./gradlew :exposed-mysql8-gis:compileKotlin` 통과

---

## Task 6 — `JtsHelpers.kt` 헬퍼 함수

**complexity: low** | 의존성: Task 1

### 작업 내용

JTS Geometry 생성 편의 함수를 제공한다. 모든 함수는 **longitude-first** 좌표 순서를 사용한다.

### 파일
- `data/exposed-mysql8-gis/src/main/kotlin/io/bluetape4k/exposed/mysql8/gis/JtsHelpers.kt`

### 함수 시그니처

```kotlin
package io.bluetape4k.exposed.mysql8.gis

import org.locationtech.jts.geom.*

/** SRID 4326 GeometryFactory 싱글턴 */
val WGS84_FACTORY: GeometryFactory = GeometryFactory(PrecisionModel(), SRID_WGS84)

/**
 * WGS84 Point 생성.
 * @param lng 경도 (longitude, X축) — -180..180
 * @param lat 위도 (latitude, Y축) — -90..90
 */
fun wgs84Point(lng: Double, lat: Double): Point =
    WGS84_FACTORY.createPoint(Coordinate(lng, lat))

/**
 * WGS84 Polygon 생성.
 * @param coordinates 좌표 배열 (Coordinate(lng, lat) 순서). 첫 점과 끝 점이 다르면 자동으로 닫는다.
 */
fun wgs84Polygon(vararg coordinates: Coordinate): Polygon {
    val coords = coordinates.toMutableList()
    if (coords.first() != coords.last()) {
        coords.add(coords.first())
    }
    return WGS84_FACTORY.createPolygon(coords.toTypedArray())
}

/**
 * WGS84 사각형 Polygon 생성.
 */
fun wgs84Rectangle(minLng: Double, minLat: Double, maxLng: Double, maxLat: Double): Polygon =
    wgs84Polygon(
        Coordinate(minLng, minLat),
        Coordinate(maxLng, minLat),
        Coordinate(maxLng, maxLat),
        Coordinate(minLng, maxLat),
        Coordinate(minLng, minLat),
    )

/**
 * WGS84 LineString 생성.
 * @param coordinates 좌표 배열 (Coordinate(lng, lat) 순서)
 */
fun wgs84LineString(vararg coordinates: Coordinate): LineString =
    WGS84_FACTORY.createLineString(coordinates)

/**
 * WGS84 MultiPoint 생성.
 */
fun wgs84MultiPoint(vararg points: Point): MultiPoint =
    WGS84_FACTORY.createMultiPoint(points)

/**
 * WGS84 MultiLineString 생성.
 */
fun wgs84MultiLineString(vararg lineStrings: LineString): MultiLineString =
    WGS84_FACTORY.createMultiLineString(lineStrings)

/**
 * WGS84 MultiPolygon 생성.
 */
fun wgs84MultiPolygon(vararg polygons: Polygon): MultiPolygon =
    WGS84_FACTORY.createMultiPolygon(polygons)

/**
 * WGS84 GeometryCollection 생성.
 */
fun wgs84GeometryCollection(vararg geometries: Geometry): GeometryCollection =
    WGS84_FACTORY.createGeometryCollection(geometries)
```

### 검증
- `./gradlew :exposed-mysql8-gis:compileKotlin` 통과

---

## Task 7 — `AbstractMySqlGisTest.kt` + `GeometryColumnTypeTest.kt`

**complexity: medium** | 의존성: Task 3, Task 4, Task 5, Task 6

### 작업 내용

Testcontainers 기반 테스트 베이스 클래스와 8종 Geometry 타입 CRUD 테스트를 구현한다.

### 파일
- `data/exposed-mysql8-gis/src/test/kotlin/io/bluetape4k/exposed/mysql8/gis/AbstractMySqlGisTest.kt`
- `data/exposed-mysql8-gis/src/test/kotlin/io/bluetape4k/exposed/mysql8/gis/GeometryColumnTypeTest.kt`

### `AbstractMySqlGisTest.kt`

```kotlin
package io.bluetape4k.exposed.mysql8.gis

import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

abstract class AbstractMySqlGisTest {

    companion object: KLogging() {
        /**
         * MySQLServer 싱글턴이 프로젝트에 존재하지 않으므로 MySQLContainer 직접 사용.
         * (exposed-postgis/GeoColumnTypeTest.kt의 PostgreSQLContainer 패턴 참조)
         */
        @JvmStatic
        val mysqlContainer: MySQLContainer<*> = MySQLContainer(
            DockerImageName.parse("mysql:8.0")
        ).apply { start() }

        @JvmStatic
        lateinit var db: Database

        @JvmStatic
        @BeforeAll
        fun setupDatabase() {
            db = Database.connect(
                url = mysqlContainer.jdbcUrl,
                driver = mysqlContainer.driverClassName,
                user = mysqlContainer.username,
                password = mysqlContainer.password,
            )
        }
    }

    /**
     * drop/create/finally drop 패턴 (exposed-postgis/GeoColumnTypeTest.kt 참조).
     * DDL 회귀를 감추지 않기 위해 createMissingTablesAndColumns + deleteAll 대신 사용.
     */
    fun withGeoTables(vararg tables: Table, block: JdbcTransaction.() -> Unit) {
        transaction(db) {
            runCatching { SchemaUtils.drop(*tables) }
            SchemaUtils.create(*tables)
        }
        try {
            transaction(db) { block() }
        } finally {
            transaction(db) { runCatching { SchemaUtils.drop(*tables) } }
        }
    }
}
```

### `GeometryColumnTypeTest.kt` — 테스트 시나리오

```kotlin
class GeometryColumnTypeTest: AbstractMySqlGisTest() {

    // 8종 타입별 테스트 테이블 정의
    object GeoPoints: Table("geo_points") {
        val id = integer("id").autoIncrement()
        val name = varchar("name", 100)
        val location = geoPoint("location")
        override val primaryKey = PrimaryKey(id)
    }
    // GeoLineStrings, GeoPolygons, GeoMultiPoints, ... 동일 패턴

    @Test
    fun `Point 저장 및 조회`() {
        withGeoTables(GeoPoints) {
            val seoul = wgs84Point(126.9780, 37.5665)
            GeoPoints.insert {
                it[name] = "서울시청"
                it[location] = seoul
            }
            val row = GeoPoints.selectAll().single()
            val point = row[GeoPoints.location]
            point.x.shouldBeEqualTo(126.9780)
            point.y.shouldBeEqualTo(37.5665)
            point.srid.shouldBeEqualTo(SRID_WGS84)
        }
    }

    @Test fun `LineString 저장 및 조회`() { ... }
    @Test fun `Polygon 저장 및 조회`() { ... }
    @Test fun `MultiPoint 저장 및 조회`() { ... }
    @Test fun `MultiLineString 저장 및 조회`() { ... }
    @Test fun `MultiPolygon 저장 및 조회`() { ... }
    @Test fun `GeometryCollection 저장 및 조회`() { ... }
    @Test fun `Geometry 범용 컬럼 — 다양한 타입 저장`() { ... }
    @Test fun `SRID 보존 검증`() { ... }
    @Test fun `axis-order 검증 — lng-lat 순서 보존`() { ... }
}
```

### 검증
- `./gradlew :exposed-mysql8-gis:test --tests "*.GeometryColumnTypeTest"` — 10개 이상 테스트 통과

---

## Task 8 — `SpatialRelationTest.kt`

**complexity: medium** | 의존성: Task 7

### 작업 내용

ST_* 관계 함수(Op<Boolean>)의 통합 테스트를 구현한다.

### 파일
- `data/exposed-mysql8-gis/src/test/kotlin/io/bluetape4k/exposed/mysql8/gis/SpatialRelationTest.kt`

### 테스트 시나리오

```kotlin
class SpatialRelationTest: AbstractMySqlGisTest() {

    // 테스트 테이블: Points + Polygons

    @Test
    fun `ST_Contains — 외부 폴리곤이 내부 포인트를 포함`() {
        // 서울 시내 사각형 Polygon → 서울시청 Point 포함 여부
        // Polygons.polygon.stContains(GeoPoints.location)
    }

    @Test
    fun `ST_Within — 포인트가 폴리곤 내부`() {
        // GeoPoints.location.stWithin(Polygons.polygon)
    }

    @Test
    fun `ST_Intersects — 두 폴리곤 교차`() {
        // 겹치는 두 사각형
    }

    @Test
    fun `ST_Disjoint — 두 폴리곤 완전 분리`() {
        // 서울 사각형 vs 부산 사각형
    }

    @Test
    fun `ST_Overlaps — 부분 겹침`() {
        // 부분만 겹치는 두 폴리곤
    }

    @Test
    fun `ST_Touches — 경계 접촉`() {
        // 한 변을 공유하는 두 사각형
    }

    @Test
    fun `ST_Crosses — 라인이 폴리곤을 가로지름`() {
        // LineString이 Polygon을 교차
    }

    @Test
    fun `ST_Equals — 동일 Geometry`() {
        // 좌표가 같은 두 Polygon
    }
}
```

### 검증
- `./gradlew :exposed-mysql8-gis:test --tests "*.SpatialRelationTest"` — 8개 테스트 통과

---

## Task 9 — `SpatialMeasurementTest.kt`

**complexity: medium** | 의존성: Task 7

### 작업 내용

ST_* 측정 함수(Expression<Double>)의 통합 테스트를 구현한다. SRID 4326에서 미터/제곱미터 단위를 검증한다.

### 파일
- `data/exposed-mysql8-gis/src/test/kotlin/io/bluetape4k/exposed/mysql8/gis/SpatialMeasurementTest.kt`

### 테스트 시나리오

```kotlin
class SpatialMeasurementTest: AbstractMySqlGisTest() {

    @Test
    fun `ST_Distance — 서울-부산 측지 거리 약 325km`() {
        // 서울 (126.9780, 37.5665) — 부산 (129.0756, 35.1796)
        // ST_Distance → 약 325,000m (±5,000m)
        // distance.shouldBeInRange(320_000.0..330_000.0)
    }

    @Test
    fun `ST_Distance_Sphere — 서울-부산 구면 거리 약 325km`() {
        // stDistanceSphere → 약 325,000m
    }

    @Test
    fun `ST_Distance vs ST_Distance_Sphere 비교`() {
        // 동일 좌표에 대해 두 함수 결과 차이 확인
        // 차이가 1% 이내인지 검증
    }

    @Test
    fun `ST_Area — Polygon 넓이 제곱미터`() {
        // 서울 시내 약 1km x 1km 사각형 → 면적 약 1,000,000 m²
    }

    @Test
    fun `ST_Length — LineString 길이 미터`() {
        // 서울-수원 직선 LineString → 약 30km
    }
}
```

### 검증
- `./gradlew :exposed-mysql8-gis:test --tests "*.SpatialMeasurementTest"` — 5개 테스트 통과

---

## Task 10 — `SpatialFunctionTest.kt`

**complexity: medium** | 의존성: Task 7

### 작업 내용

ST_* 변환/속성 함수의 통합 테스트를 구현한다.

### 파일
- `data/exposed-mysql8-gis/src/test/kotlin/io/bluetape4k/exposed/mysql8/gis/SpatialFunctionTest.kt`

### 테스트 시나리오

```kotlin
class SpatialFunctionTest: AbstractMySqlGisTest() {

    @Test
    fun `ST_AsText — WKT 문자열 반환`() {
        // Point(126.9780, 37.5665) → "POINT(126.978 37.5665)"
    }

    @Test
    fun `ST_SRID — SRID 조회`() {
        // stSrid() → 4326
    }

    // ST_Centroid 테스트 제거 — geographic SRS(4326)에서 ER_NOT_IMPLEMENTED_FOR_GEOGRAPHIC_SRS 에러
    // @Deprecated 처리됨. Cartesian SRS 전용이므로 SRID 4326 환경에서는 테스트 불가

    @Test
    fun `ST_Buffer — Point 전용, 포인트 주변 버퍼 영역`() {
        // Point에 buffer 적용 → Polygon 반환 확인
        // ⚠️ stBuffer()는 Column<out Point> 전용 (geographic SRS 4326 제한)
    }

    // ST_Envelope 테스트 제거 — geographic SRS(4326)에서 ER_NOT_IMPLEMENTED_FOR_GEOGRAPHIC_SRS 에러
    // @Deprecated 처리됨. Cartesian SRS 전용이므로 SRID 4326 환경에서는 테스트 불가

    @Test
    fun `ST_Union — 두 Polygon 합집합`() {
        // 겹치는 두 사각형 → 합집합 Polygon
    }

    @Test
    fun `ST_Difference — 두 Polygon 차집합`() {
        // 겹치는 두 사각형 → 차집합 Polygon
    }

    @Test
    fun `ST_IsValid — 유효한 Polygon`() {
        // 정상 Polygon → true
    }

    @Test
    fun `ST_Dimension — Point는 0, LineString은 1, Polygon은 2`() {
        // 각 타입별 차원 검증
    }

    @Test
    fun `ST_GeometryType — 타입 문자열 반환`() {
        // ⚠️ MySQL 실제 반환값: "POINT", "POLYGON", "LINESTRING" (전체 대문자, ST_ 접두사 없음)
        // Point → "POINT", Polygon → "POLYGON", LineString → "LINESTRING"
    }
}
```

### 검증
- `./gradlew :exposed-mysql8-gis:test --tests "*.SpatialFunctionTest"` — 7개 테스트 통과 (ST_Centroid + ST_Envelope 제거됨)

---

## Task 11 — README.md 작성

**complexity: low** | 의존성: Task 0 ~ Task 10 모두 완료

### 작업 내용

모듈 README 문서를 작성한다.

### 파일
- `data/exposed-mysql8-gis/README.md`

### 목차

1. **개요** — MySQL 8.0 GIS + Exposed ORM 타입-세이프 모듈
2. **주요 기능** — 8종 Geometry CRUD, ST_* 함수, SRID 4326 기본
3. **의존성** — `build.gradle.kts` 예제
4. **사용 예제**
   - 테이블 정의 (`geoPoint()`, `geoPolygon()`)
   - 데이터 삽입 (`wgs84Point()`, `wgs84Polygon()`)
   - 공간 쿼리 (`stContains()`, `stDistance()`, `stDistanceSphere()`)
5. **좌표 순서 규약** — longitude-first
6. **MySQL 버전 요구사항** — 8.0.24+

### 검증
- README.md 존재 확인
- 코드 예제 컴파일 가능 여부 (문서 내 코드 블록)

---

## 전체 태스크 요약

| # | 태스크 | complexity | 의존성 | 파일 수 |
|---|--------|-----------|--------|---------|
| 0 | Libs.kt `jts_core` 추가 | **low** | 없음 | 1 |
| 0.5 | Spike: write path 검증 | **high** | T1 | 1 |
| 1 | 모듈 골격 생성 | **low** | T0 | 4 |
| 2 | MySqlWkbUtils.kt | **high** | T0.5 | 2 |
| 3 | GeometryColumnType.kt | **high** | T2 | 1 |
| 4 | GeoExtensions.kt | **low** | T3 | 1 |
| 5 | SpatialExpressions + Functions | **medium** | T3 | 2 |
| 6 | JtsHelpers.kt | **low** | T1 | 1 |
| 7 | AbstractMySqlGisTest + CRUD 테스트 | **medium** | T3,4,5,6 | 2 |
| 8 | SpatialRelationTest | **medium** | T7 | 1 |
| 9 | SpatialMeasurementTest | **medium** | T7 | 1 |
| 10 | SpatialFunctionTest | **medium** | T7 | 1 |
| 11 | README.md | **low** | 전체 | 1 |

### complexity 분포
- **high**: 3개 (Task 0.5, 2, 3) — WKB 파싱, write path 검증, ColumnType 핵심 로직
- **medium**: 5개 (Task 5, 7, 8, 9, 10) — Expression 클래스, 통합 테스트
- **low**: 5개 (Task 0, 1, 4, 6, 11) — 설정, 보일러플레이트, 확장 함수, 문서

### 모델 라우팅

| 모델 | 태스크 |
|------|--------|
| **Opus** | Task 0.5, Task 2, Task 3 |
| **Sonnet** | Task 5, Task 7, Task 8, Task 9, Task 10 |
| **Haiku** | Task 0, Task 1, Task 4, Task 6, Task 11 |
