# Code Review

- Review date: 2026-03-08
- Module: `infra/cache-lettuce-near`
- Test status: `./gradlew :cache-lettuce-near:test` passed
- Recommendation: REQUEST CHANGES

## Findings

- [MEDIUM] bulk write API가 이름과 다르게 사실상 순차 round-trip 입니다. [LettuceNearCache.kt](src/main/kotlin/io/bluetape4k/cache/nearcache/lettuce/LettuceNearCache.kt) 145-151행과 [LettuceNearSuspendCache.kt](src/main/kotlin/io/bluetape4k/cache/nearcache/lettuce/LettuceNearSuspendCache.kt) 137-143행은 `putAll()`에서 `setRedis()`를 항목별로 개별 호출하고 tracking용 `GET`도 매번 추가합니다. 대량 warm-up이나 region 재적재 시 Redis RTT가 항목 수만큼 누적되어 처리량이 급격히 떨어질 수 있습니다. async/pipeline 또는 batched `MSET`/Lua 기반 write-through로 바꾸고, 배치 성능 회귀 테스트를 두는 편이 좋습니다.
- [LOW] 공개 DSL/API 일부에 한글 KDoc이 비어 있습니다. [NearCacheConfig.kt](src/main/kotlin/io/bluetape4k/cache/nearcache/lettuce/NearCacheConfig.kt) 49-66행의 `NearCacheConfigBuilder`와 [LettuceNearSuspendCache.kt](src/main/kotlin/io/bluetape4k/cache/nearcache/lettuce/LettuceNearSuspendCache.kt) 120-314행의 공개 함수들은 사용 계약이 코드에만 있습니다. TTL, invalidation, close 이후 동작을 KDoc으로 고정하는 편이 라이브러리 안정성에 유리합니다.

## Notes

- 기능 정확도 테스트는 충분한 편이지만, bulk 경로의 성능/확장성은 현재 테스트 범위 밖입니다.
