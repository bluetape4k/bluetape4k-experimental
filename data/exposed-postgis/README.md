# exposed-postgis

Exposed ORM용 PostGIS 지리 좌표 컬럼 타입 모듈.

PostgreSQL + PostGIS 확장 환경에서 `GEOMETRY(POINT, 4326)`, `GEOMETRY(POLYGON, 4326)` 타입을 Exposed 테이블에 매핑한다.

## 의존성

```kotlin
dependencies {
    implementation("io.bluetape4k:bluetape4k-exposed-postgis:$version")
}
```

내부적으로 `net.postgis:postgis-jdbc`를 사용한다.

## 사용법

### 테이블 정의

```kotlin
object Locations : LongIdTable("locations") {
    val name = varchar("name", 255)
    val point = geoPoint("point")       // GEOMETRY(POINT, 4326)
    val area = geoPolygon("area")       // GEOMETRY(POLYGON, 4326)
}
```

### Point 저장/조회

```kotlin
import net.postgis.jdbc.geometry.Point

transaction {
    SchemaUtils.create(Locations)

    val seoul = Point(126.9780, 37.5665).apply { srid = 4326 }
    Locations.insert {
        it[name] = "서울"
        it[point] = seoul
    }

    val row = Locations.selectAll().single()
    val result = row[Locations.point]  // Point
}
```

### 공간 함수

```kotlin
// ST_DWithin - 지정 거리 이내 검색 (도 단위)
Locations.selectAll()
    .where { Locations.point.stDWithin(otherColumn, 1.0) }

// ST_Within - 폴리곤 내부 포인트 검색
Regions.selectAll()
    .where { Regions.point.stWithin(Regions.area) }

// ST_Distance - 두 geometry 간 거리 계산
Locations.selectAll()
    .orderBy(Locations.point.stDistance(otherColumn))
```

## 주의사항

- **PostgreSQL 전용**: H2 등 다른 DB에서는 사용할 수 없다.
- **PostGIS 확장 필수**: `CREATE EXTENSION IF NOT EXISTS postgis` 를 먼저 실행해야 한다.
- **좌표 순서**: PostGIS는 `Point(x=경도, y=위도)` 순서를 사용한다.
- **SRID**: 기본 SRID 4326 (WGS 84) 좌표계를 사용한다.
