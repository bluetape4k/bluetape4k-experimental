# Code Review

- Review date: 2026-03-08
- Module: `spring-data/exposed-r2dbc-spring-data`
- Test status: `./gradlew :exposed-r2dbc-spring-data:test` passed
- Recommendation: COMMENT

## Findings

- [MEDIUM] `Flow` 반환 API가 실제로는 전체 결과를 먼저 materialize 합니다. [SimpleCoroutineExposedRepository.kt](src/main/kotlin/io/bluetape4k/spring/data/exposed/r2dbc/repository/support/SimpleCoroutineExposedRepository.kt) 65-71행은 `selectAll().toList().map(::toDomain).asFlow()` 구조라서 back-pressure 없이 모든 row를 한 번에 메모리에 올립니다. 데이터가 커지면 WebFlux/coroutine 소비자가 기대하는 streaming 이점이 사라집니다. 가능하면 Exposed R2DBC row stream을 직접 `Flow`로 노출하거나, 최소한 문서와 이름으로 eager materialization임을 명시해야 합니다.
- [LOW] 테스트가 대용량/streaming 특성을 검증하지 않습니다. [SimpleCoroutineExposedRepositoryTest.kt](src/test/kotlin/io/bluetape4k/spring/data/exposed/r2dbc/SimpleCoroutineExposedRepositoryTest.kt) 49-147행은 소량 CRUD 위주여서 `findAll()` 메모리 사용과 `save()` update 경로 회귀를 잡지 못합니다. streaming 또는 대량 row 시나리오 테스트를 추가하는 편이 좋습니다.

## Notes

- 현재 모듈에서 즉시 깨지는 기능 버그는 보지 못했습니다.
- 다만 API 이름이 암시하는 비동기 streaming 성격과 실제 구현 사이 간극은 분명합니다.
