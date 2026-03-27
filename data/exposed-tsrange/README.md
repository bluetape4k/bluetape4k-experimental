# exposed-tsrange

## 개요
시작/종료 `Instant`로 표현되는 시간 범위를 Exposed 커스텀 컬럼 타입으로 저장하는 모듈.
PostgreSQL에서는 네이티브 `TSTZRANGE` 타입을, H2에서는 VARCHAR(100) + range literal 포맷을 사용한다.

## 주요 기능
- `TimestampRange` — 시간 범위 값 객체 (`Serializable`, `contains()`, `overlaps()`)
- `TstzRangeColumnType` — PostgreSQL: `TSTZRANGE`, H2: `VARCHAR(100)`
- `Table.tstzRange(name)` factory 확장 함수
- PostgreSQL 전용 SQL 연산자:
  - `Column<TimestampRange>.overlaps(other)` — `&&`
  - `Column<TimestampRange>.contains(instant)` — `@>`
  - `Column<TimestampRange>.containsRange(other)` — `@>`
  - `Column<TimestampRange>.isAdjacentTo(other)` — `-|-`

## 사용 예제
```kotlin
object Events : LongIdTable("events") {
    val period = tstzRange("period")
}

transaction {
    val range = TimestampRange(
        start = Instant.parse("2024-01-01T00:00:00Z"),
        end = Instant.parse("2024-12-31T23:59:59Z"),
    )
    Events.insert { it[period] = range }

    // PostgreSQL 전용: 겹치는 이벤트 조회
    Events.selectAll()
        .where { Events.period.overlaps(Events.period) }
}
```

## 의존성
```kotlin
testImplementation(project(":exposed-tsrange"))
```

## 테스트 DB
- H2 인메모리 (fallback 동작 확인)
- PostgreSQL Testcontainers (TSTZRANGE 네이티브 + 연산자 쿼리)
