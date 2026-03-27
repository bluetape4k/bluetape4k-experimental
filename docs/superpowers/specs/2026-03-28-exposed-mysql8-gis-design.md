# exposed-mysql8-gis 설계 문서

> 작성일: 2026-03-28
> 모듈 경로: `data/exposed-mysql8-gis/`
> 패키지: `io.bluetape4k.exposed.mysql8.gis`

---

## 1. 개요

### 목적
MySQL 8.0의 네이티브 GIS/공간 데이터 기능을 Exposed ORM에서 타입-세이프하게 사용할 수 있는 모듈.

### 범위
- 8종 Geometry 타입 CRUD (Point, LineString, Polygon, MultiPoint, MultiLineString, MultiPolygon, GeometryCollection, Geometry)
- MySQL 8.0 ST_* 공간 함수 (관계, 측정, 변환)
- SRID 4326 (WGS 84) 기본, 사용자 지정 SRID 지원
- Table 확장 함수 (`geoPoint()`, `geoPolygon()` 등)

### 전제조건
- MySQL 8.0.24+ (SRID 완전 지원, `ST_Distance_Sphere`, `ST_SRID`, `axis-order` 옵션 지원)
- JTS (Java Topology Suite) `org.locationtech.jts:jts-core` — Kotlin 측 Geometry 표현
- Exposed v1 (`org.jetbrains.exposed.v1.*`)

---

## 2. 아키텍처

### 2.1 ColumnType 설계: 단일 Generic ColumnType

**결정: `GeometryColumnType<T : Geometry>` 단일 제네릭 클래스**

PostGIS 모듈은 타입별 ColumnType(GeoPointColumnType, GeoPolygonColumnType)을 개별 정의했으나, MySQL GIS 모듈에서는 **단일 제네릭 ColumnType**을 채택한다.

#### 근거
1. **MySQL WKB 프로토콜 통일성**: MySQL은 모든 Geometry 타입에 동일한 WKB(Well-Known Binary) 직렬화를 사용한다. `ST_GeomFromWKB()` / `ST_AsWKB()`가 타입과 무관하게 동작하므로, 변환 로직이 동일하다.
2. **코드 중복 제거**: 8종 타입에 대해 거의 동일한 `valueFromDB`/`notNullValueToDB` 로직을 반복할 필요가 없다.
3. **SQL 타입 분기**: `sqlType()`만 타입 파라미터에 따라 `"POINT"`, `"POLYGON"`, `"GEOMETRY"` 등으로 분기하면 충분하다.
4. **확장성**: 향후 새 Geometry 타입 추가 시 ColumnType 클래스 추가 없이 대응 가능.

#### PostGIS 대비 차이점
| 항목 | exposed-postgis | exposed-mysql8-gis |
|------|----------------|-------------------|
| Geometry 라이브러리 | net.postgis.jdbc (PGgeometry) | JTS (org.locationtech.jts) |
| 직렬화 | PGgeometry 문자열 래퍼 | WKB (4바이트 SRID prefix) |
| ColumnType 수 | 타입별 N개 | 단일 제네릭 1개 |
| SRID 처리 | PGgeometry 내부 | MySQL WKB 앞 4바이트 LE |

### 2.2 WKB 변환 전략

MySQL 8.0은 공간 데이터를 **Internal Geometry Format**으로 저장한다:
```
[4 bytes SRID (Little-Endian)] + [Standard WKB]
```

#### Write Path 설계 (notNullValueToDB)

**채택: MySQL Internal Format ByteArray 직접 바인딩 (접근 B)**

MySQL Connector/J는 GEOMETRY 컬럼에 Internal Format ByteArray를 직접 바인딩할 수 있다.
`notNullValueToDB()`에서 `4바이트 LE SRID + WKB` 형식의 `ByteArray`를 반환하면, PreparedStatement가 이를 그대로 바인딩한다.

```
JTS Geometry
  → SRID를 4바이트 Little-Endian으로 인코딩
  → WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN).write(geometry)  → WKB byte[]
  → [4바이트 LE SRID] + [WKB byte[]] 결합
  → ByteArray 반환 (PreparedStatement에 직접 바인딩)
```

> **비채택 (접근 A)**: `nonNullValueToString()`에서 `ST_GeomFromWKB(X'...hex...', 4326)` SQL 리터럴을 생성하는 방식.
> PreparedStatement 사용 불가, SQL injection 리스크, 성능 저하로 부적합.

**Fallback 전략**: 만약 MySQL Connector/J에서 Internal Format ByteArray 직접 바인딩이 동작하지 않는 경우, `nonNullValueToString()` 방식의 WKT 리터럴 `ST_GeomFromText('POINT(lng lat)', 4326, 'axis-order=long-lat')` 방식으로 fallback한다.

**`nonNullValueToString()` 오버라이드**: DDL/literal context에서 사용될 때를 위해 `ST_GeomFromWKB(X'hex', srid, 'axis-order=long-lat')` 형태의 SQL 리터럴을 반환한다.

> **WGS84 axis-order 주의**: MySQL 8.0은 SRID 4326에 대해 `axis-order=srid-defined`를 기본 적용하므로 lat/lng 순서가 뒤집힐 수 있다.
> MySQL 8.0.24+에서 `ST_GeomFromWKB()` / `ST_GeomFromText()`의 3번째 인자로 `'axis-order=long-lat'` 옵션을 지정하여 항상 longitude-first 좌표를 보장한다.
> `nonNullValueToString()`에서 반드시 이 옵션을 포함해야 한다.

#### Read Path (valueFromDB)
```
MySQL Internal Geometry (byte[])
  → 앞 4바이트: SRID (Little-Endian)
  → 나머지: 표준 WKB
  → WKBReader.read(wkb)
  → geometry.setSRID(srid)
  → JTS Geometry
```

#### Byte Order 처리
- **SRID prefix**: 항상 Little-Endian (MySQL Internal Format 규약)
- **WKB body**: JTS `WKBWriter`는 기본 Big-Endian이지만, MySQL 호환을 위해 `ByteOrderValues.LITTLE_ENDIAN`을 명시 지정
- **WKB 읽기**: `WKBReader`는 WKB 헤더의 byte order 플래그를 자동 감지하므로 별도 처리 불필요

### 2.3 SRID 처리

- 기본값: `SRID_WGS84 = 4326`
- `GeometryColumnType` 생성 시 `srid` 파라미터로 지정 가능
- `sqlType()` 반환: `"POINT SRID 4326"` (plain syntax, conditional comment 방식 미사용)
  - MySQL 8.0.3+ 에서 컬럼 레벨 SRID 제약조건 지원
- `notNullValueToDB`에서 Geometry의 SRID가 0이면 기본 SRID로 설정

### 2.4 MySQL Dialect Guard

모든 spatial 함수 확장 및 `GeometryColumnType.sqlType()`에 MySQL dialect 검증을 추가한다.
PostGIS 모듈(`exposed-postgis/GeoExtensions.kt`)의 `check(currentDialect is PostgreSQLDialect)` 패턴과 동일.

```kotlin
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect

// ColumnType 내
override fun sqlType(): String {
    check(currentDialect is MysqlDialect) {
        "GeometryColumnType 은 MySQL dialect 에서만 지원됩니다."
    }
    return "$geometryType SRID $srid"
}

// 확장 함수 내
fun <T : Geometry> Column<T>.stContains(other: Column<out Geometry>): Op<Boolean> {
    check(currentDialect is MysqlDialect) {
        "stContains 는 MySQL dialect 에서만 지원됩니다."
    }
    // ...
}
```

---

## 3. 지원 타입

### 3.1 GeometryColumnType<T : Geometry>

```kotlin
class GeometryColumnType<T : Geometry>(
    val geometryType: String,  // "POINT", "LINESTRING", "POLYGON", etc.
    val srid: Int = SRID_WGS84,
) : ColumnType<T>() {

    companion object: KLogging()

    init {
        geometryType.requireNotBlank("geometryType")
        srid.requirePositiveNumber("srid")
    }
}
```

| JTS 타입 | geometryType | MySQL SQL 타입 |
|----------|-------------|---------------|
| `Point` | `"POINT"` | `POINT SRID 4326` |
| `LineString` | `"LINESTRING"` | `LINESTRING SRID 4326` |
| `Polygon` | `"POLYGON"` | `POLYGON SRID 4326` |
| `MultiPoint` | `"MULTIPOINT"` | `MULTIPOINT SRID 4326` |
| `MultiLineString` | `"MULTILINESTRING"` | `MULTILINESTRING SRID 4326` |
| `MultiPolygon` | `"MULTIPOLYGON"` | `MULTIPOLYGON SRID 4326` |
| `GeometryCollection` | `"GEOMETRYCOLLECTION"` | `GEOMETRYCOLLECTION SRID 4326` |
| `Geometry` | `"GEOMETRY"` | `GEOMETRY SRID 4326` |

### 3.2 핵심 메서드

```kotlin
override fun sqlType(): String {
    check(currentDialect is MysqlDialect) {
        "GeometryColumnType 은 MySQL dialect 에서만 지원됩니다."
    }
    return "$geometryType SRID $srid"
}

/**
 * PreparedStatement 바인딩용.
 * MySQL Internal Format (4바이트 LE SRID + WKB) ByteArray를 반환한다.
 * MySQL Connector/J가 GEOMETRY 컬럼에 직접 바인딩한다.
 */
override fun notNullValueToDB(value: T): Any {
    if (value.srid == 0) value.srid = srid
    val wkb = WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN).write(value)
    return buildMySqlInternalGeometry(value.srid, wkb)
}

/**
 * DDL/literal context용.
 * ST_GeomFromWKB(X'hex', srid, 'axis-order=long-lat') 형태의 SQL 리터럴을 반환한다.
 * axis-order=long-lat 옵션으로 WGS84(SRID 4326)에서 좌표 순서 뒤집힘을 방지한다.
 * MySQL 8.0.24+ 필요.
 */
override fun nonNullValueToString(value: T): String {
    if (value.srid == 0) value.srid = srid
    val wkb = WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN).write(value)
    val hex = wkb.joinToString("") { "%02x".format(it) }
    return "ST_GeomFromWKB(X'$hex', ${value.srid}, 'axis-order=long-lat')"
}

override fun valueFromDB(value: Any): T {
    val geometry = when (value) {
        is ByteArray -> parseMySqlInternalGeometry(value)
        is Geometry  -> value
        else -> error("Unsupported value type: ${value::class.java}")
    }
    // KClass 기반 타입 검증 — type erasure 우회
    if (geometryType != "GEOMETRY") {
        val expectedClass = GEOMETRY_TYPE_MAP[geometryType.lowercase()]
            ?: error("Unknown geometry type: $geometryType")
        check(expectedClass.isInstance(geometry)) {
            "Expected $geometryType but got ${geometry.geometryType}"
        }
    }
    @Suppress("UNCHECKED_CAST")
    return geometry as T
}

companion object: KLogging() {
    /** JTS Geometry 타입 매핑 테이블 (대소문자 무관 비교용) */
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
```

> **Trade-off**: `valueFromDB`의 타입 검증은 `KClass.isInstance()` 기반으로 수행하여 type erasure를 우회한다.
> `geometryType` 문자열 비교는 `lowercase()`로 정규화하여 대소문자 혼재(`"Point"` vs `"POINT"`) 문제를 해결한다.
> `Geometry` 범용 컬럼(`geometryType == "GEOMETRY"`)에서는 모든 서브타입을 허용해야 하므로 검증을 건너뛴다.

---

## 4. 공간 함수

### 4.1 관계 함수 (Op<Boolean>)

| Kotlin 함수 | MySQL SQL | 설명 |
|-------------|----------|------|
| `stEquals(other)` | `ST_Equals(a, b)` | 공간적으로 동일 |
| `stIntersects(other)` | `ST_Intersects(a, b)` | 교차 여부 |
| `stDisjoint(other)` | `ST_Disjoint(a, b)` | 완전 분리 여부 |
| `stContains(other)` | `ST_Contains(a, b)` | a가 b를 포함 |
| `stWithin(other)` | `ST_Within(a, b)` | a가 b 내부 |
| `stOverlaps(other)` | `ST_Overlaps(a, b)` | 부분 겹침 |
| `stTouches(other)` | `ST_Touches(a, b)` | 경계만 접촉 |
| `stCrosses(other)` | `ST_Crosses(a, b)` | 교차 (차원 다름) |

### 4.2 측정 함수 (Expression<Double>)

> **단위 참고**: MySQL 8.0.18+에서 geographic SRS(SRID 4326)의 측정값은 모두 **미터 / 제곱미터** 단위이다.
> `ST_Distance()` — 미터 (geodetic, 측지 거리), `ST_Distance_Sphere()` — 미터 (구면 근사), `ST_Length()` — 미터, `ST_Area()` — 제곱미터.
> `ST_Distance()`와 `ST_Distance_Sphere()`의 차이: 둘 다 SRID 4326에서 미터를 반환하지만, `ST_Distance()`는 정확한 측지(geodetic) 알고리즘을, `ST_Distance_Sphere()`는 구면(spherical) 근사를 사용한다.

| Kotlin 함수 | MySQL SQL | 수신자 타입 제한 | 설명 |
|-------------|----------|---------------|------|
| `stDistance(other)` | `ST_Distance(a, b)` | `Column<out Geometry>` | 측지 거리 (SRID 4326: 미터) |
| `stDistanceSphere(other)` | `ST_Distance_Sphere(a, b)` | `Column<Point>` | 구면 거리 (미터) |
| `stArea()` | `ST_Area(a)` | `Column<out Polygonal>` | 넓이 (SRID 4326: 제곱미터) |
| `stLength()` | `ST_Length(a)` | `Column<out Lineal>` | 길이 (SRID 4326: 미터) |

### 4.3 변환/생성 함수 (Expression<Geometry>)

| Kotlin 함수 | MySQL SQL | 설명 |
|-------------|----------|------|
| `stBuffer(distance)` | `ST_Buffer(a, d)` | 버퍼 영역 생성 (geographic SRS에서는 Point만 안전) |
| ~~`stCentroid()`~~ | ~~`ST_Centroid(a)`~~ | ~~중심점~~ — `@Deprecated("geographic SRS(4326) 미지원")` ⚠️ MySQL 8.0 geographic SRS에서 `ER_NOT_IMPLEMENTED_FOR_GEOGRAPHIC_SRS` 에러 발생 |
| ~~`stEnvelope()`~~ | ~~`ST_Envelope(a)`~~ | ~~최소 경계 사각형~~ — `@Deprecated("geographic SRS(4326) 미지원")` ⚠️ MySQL 8.0 geographic SRS에서 `ER_NOT_IMPLEMENTED_FOR_GEOGRAPHIC_SRS` 에러 발생 |
| `stUnion(other)` | `ST_Union(a, b)` | 합집합 |
| `stIntersection(other)` | `ST_Intersection(a, b)` | 교집합 |
| `stDifference(other)` | `ST_Difference(a, b)` | 차집합 |
| `stConvexHull()` | `ST_ConvexHull(a)` | 볼록 껍질 |

### 4.4 속성 함수 (Expression<*>)

| Kotlin 함수 | MySQL SQL | 반환 | 설명 |
|-------------|----------|------|------|
| `stSrid()` | `ST_SRID(a)` | `Int` | SRID 조회 |
| `stAsText()` | `ST_AsText(a)` | `String` | WKT 문자열 |
| `stAsBinary()` | `ST_AsBinary(a)` | `ByteArray` | WKB 바이너리 |
| `stIsEmpty()` | `ST_IsEmpty(a)` | `Boolean` | 빈 geometry 여부 |
| `stIsValid()` | `ST_IsValid(a)` | `Boolean` | 유효성 검증 |
| `stDimension()` | `ST_Dimension(a)` | `Int` | 차원 (0/1/2) |
| `stGeometryType()` | `ST_GeometryType(a)` | `String` | 타입 문자열 |
| `stNumPoints()` | `ST_NumPoints(a)` | `Int` | 포인트 수 (LineString 전용) |

---

## 5. 확장 함수

### 5.1 Table 확장 (컬럼 등록)

```kotlin
// 타입별 편의 함수 — 내부적으로 GeometryColumnType<T> 사용
fun Table.geoPoint(name: String, srid: Int = SRID_WGS84): Column<Point>
fun Table.geoLineString(name: String, srid: Int = SRID_WGS84): Column<LineString>
fun Table.geoPolygon(name: String, srid: Int = SRID_WGS84): Column<Polygon>
fun Table.geoMultiPoint(name: String, srid: Int = SRID_WGS84): Column<MultiPoint>
fun Table.geoMultiLineString(name: String, srid: Int = SRID_WGS84): Column<MultiLineString>
fun Table.geoMultiPolygon(name: String, srid: Int = SRID_WGS84): Column<MultiPolygon>
fun Table.geoGeometryCollection(name: String, srid: Int = SRID_WGS84): Column<GeometryCollection>
fun Table.geometry(name: String, srid: Int = SRID_WGS84): Column<Geometry>
```

### 5.2 Column 확장 (공간 함수 호출)

```kotlin
// ── 관계 함수 — Op<Boolean> (모든 Geometry 서브타입 허용) ──
fun Column<out Geometry>.stContains(other: Column<out Geometry>): Op<Boolean>
fun Column<out Geometry>.stWithin(other: Column<out Geometry>): Op<Boolean>
fun Column<out Geometry>.stIntersects(other: Column<out Geometry>): Op<Boolean>
fun Column<out Geometry>.stEquals(other: Column<out Geometry>): Op<Boolean>
fun Column<out Geometry>.stDisjoint(other: Column<out Geometry>): Op<Boolean>
fun Column<out Geometry>.stOverlaps(other: Column<out Geometry>): Op<Boolean>
fun Column<out Geometry>.stTouches(other: Column<out Geometry>): Op<Boolean>
fun Column<out Geometry>.stCrosses(other: Column<out Geometry>): Op<Boolean>

// ── 측정 함수 — Expression<Double> (서브타입별 제한) ──
fun Column<out Geometry>.stDistance(other: Column<out Geometry>): Expression<Double>
fun Column<Point>.stDistanceSphere(other: Column<Point>): Expression<Double>

// ST_Area: Polygon, MultiPolygon만 유효 (JTS Polygonal 인터페이스)
fun Column<out Polygon>.stArea(): Expression<Double>
fun Column<out MultiPolygon>.stArea(): Expression<Double>

// ST_Length: LineString, MultiLineString만 유효 (JTS Lineal 인터페이스)
fun Column<out LineString>.stLength(): Expression<Double>
fun Column<out MultiLineString>.stLength(): Expression<Double>

// ST_NumPoints: LineString 전용
fun Column<out LineString>.stNumPoints(): Expression<Int>

// ── 변환/생성 함수 — Expression<Geometry> ──
fun Column<out Geometry>.stBuffer(distance: Double): Expression<Geometry>
@Deprecated("ST_Centroid는 geographic SRS(4326)에서 지원되지 않습니다. Cartesian SRS 전용.")
fun Column<out Geometry>.stCentroid(): Expression<Point>
@Deprecated("ST_Envelope은 geographic SRS(4326)에서 지원되지 않습니다. Cartesian SRS 전용.")
fun Column<out Geometry>.stEnvelope(): Expression<Geometry>
fun Column<out Geometry>.stUnion(other: Column<out Geometry>): Expression<Geometry>
fun Column<out Geometry>.stIntersection(other: Column<out Geometry>): Expression<Geometry>
fun Column<out Geometry>.stDifference(other: Column<out Geometry>): Expression<Geometry>
fun Column<out Geometry>.stConvexHull(): Expression<Geometry>

// ── 속성 함수 ──
fun Column<out Geometry>.stSrid(): Expression<Int>
fun Column<out Geometry>.stAsText(): Expression<String>
fun Column<out Geometry>.stAsBinary(): Expression<ByteArray>
fun Column<out Geometry>.stIsEmpty(): Op<Boolean>
fun Column<out Geometry>.stIsValid(): Op<Boolean>
fun Column<out Geometry>.stDimension(): Expression<Int>
fun Column<out Geometry>.stGeometryType(): Expression<String>
```

> **JTS 타입 계층 활용**: `Polygonal` 인터페이스(Polygon, MultiPolygon), `Lineal` 인터페이스(LineString, MultiLineString), `Puntal` 인터페이스(Point, MultiPoint)를 활용하여 함수 시그니처를 서브타입별로 제한한다.
> 실제 구현 시 JTS의 `Polygonal`/`Lineal` 인터페이스를 직접 사용할 수 있으나, Exposed Column 타입 파라미터 호환성을 위해 구체 타입별 오버로드를 제공한다.

### 5.3 JTS 헬퍼 함수

> **좌표 순서 규약**: 모든 헬퍼 함수는 **longitude-first (X=lng, Y=lat)** 순서를 사용한다.
> JTS `Coordinate(x, y)` = `Coordinate(lng, lat)`. 이는 WKT 표준(`POINT(lng lat)`)과 일치한다.
> MySQL 8.0의 SRID 4326 기본 axis-order(lat-first)와 다르므로, DB 바인딩 시 `axis-order=long-lat` 옵션이 필수이다.

```kotlin
/**
 * WGS84 Point 생성.
 * @param lng 경도 (longitude, X축) — -180..180
 * @param lat 위도 (latitude, Y축) — -90..90
 * 좌표 순서: longitude-first (JTS/WKT 표준)
 */
fun wgs84Point(lng: Double, lat: Double): Point

/** WGS84 Polygon 생성 (좌표 배열, Coordinate(lng, lat) 순서) */
fun wgs84Polygon(vararg coordinates: Coordinate): Polygon

/** WGS84 사각형 Polygon 생성 */
fun wgs84Rectangle(minLng: Double, minLat: Double, maxLng: Double, maxLat: Double): Polygon
```

---

## 6. 테스트 전략

### 6.1 인프라
- **Testcontainers**: `mysql:8.0` Docker 이미지
- MySQL 8.0은 GIS가 내장이므로 PostGIS처럼 별도 확장 설치 불필요
- `AbstractExposedTest` 상속

### 6.2 테스트 시나리오

#### ColumnType CRUD (GeometryColumnTypeTest)
| 시나리오 | 설명 |
|---------|------|
| Point 저장/조회 | WGS84 좌표 CRUD |
| LineString 저장/조회 | 다중 좌표 라인 |
| Polygon 저장/조회 | 닫힌 링 폴리곤 |
| MultiPoint 저장/조회 | 복수 포인트 |
| MultiLineString 저장/조회 | 복수 라인 |
| MultiPolygon 저장/조회 | 복수 폴리곤 |
| GeometryCollection 저장/조회 | 혼합 geometry |
| Geometry (범용) 저장/조회 | 타입 무관 저장 |
| SRID 보존 | 저장 후 SRID 4326 유지 확인 |

#### 공간 관계 함수 (SpatialRelationTest)
| 시나리오 | 설명 |
|---------|------|
| ST_Contains | 외부 폴리곤이 내부 폴리곤 포함 |
| ST_Within | 포인트가 폴리곤 내부 |
| ST_Intersects / ST_Disjoint | 교차/분리 |
| ST_Overlaps | 부분 겹침 (포함 아님) |
| ST_Touches | 경계 접촉 |
| ST_Crosses | 교차 (차원이 다른 geometry 간) |
| ST_Equals | 동일 geometry |

#### 스파이크 테스트 — Write Path 검증 (Task 0.5, SpikeWritePathTest)
| 시나리오 | 설명 |
|---------|------|
| ByteArray 바인딩 라운드트립 | PreparedStatement를 통한 insert/update/select 라운드트립 — Internal Format ByteArray 직접 바인딩이 MySQL Connector/J에서 동작하는지 검증 |
| SRID mismatch 테스트 | 컬럼 SRID(4326)와 삽입 데이터 SRID(0 또는 3857)가 다를 때 동작 확인 |
| axis-order 라운드트립 | `nonNullValueToString()`의 `axis-order=long-lat` 옵션이 정확한 좌표로 저장/조회되는지 확인 |
| WKT fallback 테스트 | `ST_GeomFromText('POINT(lng lat)', 4326, 'axis-order=long-lat')` 방식이 정상 동작하는지 확인 |

> **목적**: 본격 구현 전에 MySQL Connector/J의 ByteArray 바인딩 동작과 axis-order 처리를 검증한다.
> 만약 ByteArray 직접 바인딩이 실패하면, `nonNullValueToString()` 기반 WKT 리터럴 방식으로 fallback한다.

#### 측정 함수 (SpatialMeasurementTest)

> **단위 참고**: SRID 4326(geographic SRS)에서 `ST_Distance()`, `ST_Length()`는 **미터**, `ST_Area()`는 **제곱미터**를 반환한다.

| 시나리오 | 설명 |
|---------|------|
| ST_Distance | 측지 거리 (SRID 4326: 미터 단위) |
| ST_Distance_Sphere | 서울-부산 구면 거리 (약 325km, 미터 단위) |
| ST_Distance vs ST_Distance_Sphere 비교 | 동일 좌표에 대해 두 함수의 결과 차이 확인 (geodetic vs spherical) |
| ST_Area | Polygon 넓이 (SRID 4326: 제곱미터 단위) — Polygon/MultiPolygon 컬럼만 허용 |
| ST_Length | LineString 길이 (SRID 4326: 미터 단위) — LineString/MultiLineString 컬럼만 허용 |

#### 변환/속성 함수 (SpatialFunctionTest)
| 시나리오 | 설명 |
|---------|------|
| ST_Buffer | 포인트 주변 버퍼 생성 |
| ST_Union / ST_Intersection | 합집합/교집합 |
| ST_AsText / ST_AsBinary | 변환 확인 |
| ST_IsValid | 유효성 검증 |
| ST_SRID | SRID 조회 |

> **ST_Centroid / ST_Envelope 제외**: MySQL 8.0 geographic SRS(SRID 4326)에서 `ER_NOT_IMPLEMENTED_FOR_GEOGRAPHIC_SRS` 에러가 발생하므로 테스트 대상에서 제외. (`@Deprecated` 처리됨)

---

## 7. build.gradle.kts

```kotlin
dependencies {
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.bluetape4k_exposed_core)
    api(Libs.jts_core)  // org.locationtech.jts:jts-core

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_exposed_jdbc_tests)
    testImplementation(Libs.mysql_connector_j)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_mysql)
}
```

### Libs.kt 추가 (Task 0 — 선행 필수)

> **주의**: `Libs.jts_core`는 현재 `buildSrc/src/main/kotlin/Libs.kt`에 정의되어 있지 않다.
> 구현 시 **가장 먼저** 아래 항목을 추가해야 한다.

```kotlin
// JTS (Java Topology Suite) — MySQL 8.0 GIS 공간 데이터 (exposed-mysql8-gis)
// https://mvnrepository.com/artifact/org.locationtech.jts/jts-core
const val jts_core = "org.locationtech.jts:jts-core:1.20.0"
```

---

## 8. 패키지 구조

```
data/exposed-mysql8-gis/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/kotlin/io/bluetape4k/exposed/mysql8/gis/
    │   ├── GeometryColumnType.kt      # 단일 제네릭 ColumnType<T : Geometry>
    │   ├── MySqlWkbUtils.kt           # WKB 변환 유틸 (Internal Format 생성/파싱)
    │   ├── GeoExtensions.kt           # Table 확장 (geoPoint, geoPolygon, ...)
    │   ├── SpatialExpressions.kt      # ST_* Expression/Op 클래스 모음
    │   ├── SpatialFunctions.kt        # Column 확장 (stDistance, stContains, ...)
    │   └── JtsHelpers.kt              # wgs84Point, wgs84Polygon 등 헬퍼
    └── test/
        ├── kotlin/io/bluetape4k/exposed/mysql8/gis/
        │   ├── AbstractMySqlGisTest.kt     # Testcontainers 공통 설정
        │   ├── SpikeWritePathTest.kt       # Task 0.5: ByteArray 바인딩 / axis-order 스파이크
        │   ├── GeometryColumnTypeTest.kt   # 8종 타입 CRUD
        │   ├── SpatialRelationTest.kt      # 관계 함수 테스트
        │   ├── SpatialMeasurementTest.kt   # 측정 함수 테스트
        │   └── SpatialFunctionTest.kt      # 변환/속성 함수 테스트
        └── resources/
            ├── junit-platform.properties
            └── logback-test.xml
```

### 파일별 역할

| 파일 | 역할 |
|------|------|
| `GeometryColumnType.kt` | `ColumnType<T : Geometry>` 구현. `sqlType()`, `valueFromDB()`, `notNullValueToDB()`. WKB 직렬화/역직렬화 |
| `MySqlWkbUtils.kt` | MySQL Internal Geometry Format 변환 (4바이트 SRID LE + WKB). `parseMySqlInternalGeometry()`, `buildMySqlInternalGeometry()` |
| `GeoExtensions.kt` | `Table.geoPoint()`, `Table.geoPolygon()` 등 8종 확장 함수 |
| `SpatialExpressions.kt` | `StDistanceExpr`, `StContainsOp`, `StDistanceSphereExpr` 등 Expression/Op 클래스 |
| `SpatialFunctions.kt` | `Column<T>.stDistance()`, `Column<T>.stContains()` 등 Column 확장 함수 |
| `JtsHelpers.kt` | `wgs84Point()`, `wgs84Polygon()`, `wgs84Rectangle()`, `GeometryFactory` 싱글턴 |

### 테스트 파일별 역할

| 파일 | 역할 |
|------|------|
| `SpikeWritePathTest.kt` | Task 0.5: ByteArray 바인딩 라운드트립, SRID mismatch, axis-order, WKT fallback 스파이크 검증 |

---

## 부록: MySQL WKB Internal Format 상세

```
Offset  Length  내용
0       4       SRID (uint32, Little-Endian)
4       1       Byte Order (0x01 = LE)
5       4       WKB Type (uint32)
9       ...     WKB Payload (좌표 데이터)
```

JTS `WKBReader`는 offset 4부터의 표준 WKB만 인식하므로, 반드시 앞 4바이트를 잘라서 전달해야 한다.

```kotlin
/** MySQL Internal Format → JTS Geometry (읽기) */
fun parseMySqlInternalGeometry(bytes: ByteArray): Geometry {
    val srid = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
    val wkb = bytes.copyOfRange(4, bytes.size)
    val geometry = WKBReader().read(wkb)
    geometry.srid = srid
    return geometry
}

/** JTS Geometry → MySQL Internal Format (쓰기) */
fun buildMySqlInternalGeometry(srid: Int, wkb: ByteArray): ByteArray {
    val buffer = ByteBuffer.allocate(4 + wkb.size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(srid)
    buffer.put(wkb)
    return buffer.array()
}
```

> **주의**: JTS `WKBWriter`는 기본 Big-Endian을 사용한다. MySQL Connector/J가 Internal Format ByteArray를 직접 바인딩할 때 WKB body의 byte order는 무관하지만(WKB 헤더에 byte order 플래그 포함), 일관성을 위해 `WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN)`을 사용한다.
