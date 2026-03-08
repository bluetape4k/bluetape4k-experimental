# Code Review

- Review date: 2026-03-08
- Module: `spring-data/exposed-spring-data`
- Test status: `./gradlew :exposed-spring-data:test` passed
- Recommendation: REQUEST CHANGES

## Findings

- [HIGH] 메서드명에 선언된 정적 정렬(`OrderBy...`)이 실행 경로에 반영되지 않습니다. [PartTreeExposedQuery.kt](src/main/kotlin/io/bluetape4k/spring/data/exposed/repository/query/PartTreeExposedQuery.kt) 34-46행은 정렬 소스로 `Pageable`/런타임 `Sort`만 합치고 `PartTree` 자체의 sort를 포함하지 않으며, 58-61행의 `executeLimiting()`도 `limit()`만 적용합니다. 그래서 `findTop3ByOrderByAgeDesc()` 같은 쿼리가 DB 정렬 없이 임의 순서를 반환할 수 있습니다. [PartTreeExposedQueryTest.kt](src/test/kotlin/io/bluetape4k/spring/data/exposed/PartTreeExposedQueryTest.kt) 108-113행 테스트도 첫 두 원소 비교만 해서 이 회귀를 놓치고 있습니다. `partTree.sort`를 항상 병합하고, top/first 계열 정렬 회귀 테스트를 보강해야 합니다.
- [MEDIUM] Query-by-example 구현이 `ExampleMatcher`와 FluentQuery 계약을 대부분 무시합니다. [SimpleExposedRepository.kt](src/main/kotlin/io/bluetape4k/spring/data/exposed/repository/support/SimpleExposedRepository.kt) 145-156행, 178-202행은 matcher 설정 없이 필드 equality만 만들고, 232-237행의 `sortBy()`/`project()`는 no-op 입니다. ignore-case, string matcher, projection, sort callback을 기대하는 Spring Data 호출자가 있으면 결과가 조용히 달라질 수 있습니다. 지원 범위를 명시적으로 제한하거나 matcher/sort/projection을 실제로 반영해야 합니다.
- [MEDIUM] Query-by-example paging은 전체 결과를 메모리로 올린 뒤 정렬/페이지를 자릅니다. [SimpleExposedRepository.kt](src/main/kotlin/io/bluetape4k/spring/data/exposed/repository/support/SimpleExposedRepository.kt) 151-156행, 178-184행 구조상 매칭 건수가 커지면 메모리 사용량과 응답 시간이 선형으로 증가합니다. 가능한 조건/정렬/offset/limit을 Exposed query로 내리는 편이 좋습니다.

## Notes

- 기본 CRUD와 raw SQL 바인딩 테스트는 좋습니다.
- matcher, fluent query, static order-by 회귀를 잡는 테스트는 현재 빠져 있습니다.
