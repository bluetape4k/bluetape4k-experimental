# Code Review

- Review date: 2026-03-08
- Module: `examples/hibernate-cache-lettuce-near-demo`
- Test status: `./gradlew :hibernate-cache-lettuce-near-demo:test` passed
- Recommendation: COMMENT

## Findings

- [LOW] cache 관리 endpoint의 의미가 route 이름만 보면 과하게 넓게 읽힐 수 있습니다. [CacheController.kt](src/main/kotlin/io/bluetape4k/examples/cache/lettuce/controller/CacheController.kt) 41-56행의 `/api/cache/evict*`는 실제로 Redis L2는 건드리지 않고 `clearLocal()`만 수행합니다. 응답 메시지에 local eviction이라고 적혀 있긴 하지만 운영 중에는 full eviction으로 오해하기 쉽습니다. route 또는 KDoc/README에서 L1 only 동작을 더 분명히 드러내는 편이 좋습니다.
- [LOW] 테스트가 cache 관리 API를 검증하지 않습니다. [DemoApplicationTest.kt](src/test/kotlin/io/bluetape4k/examples/cache/lettuce/DemoApplicationTest.kt) 40-88행은 컨텍스트 로드와 repository CRUD만 다루고 `/api/cache/stats`, `/api/cache/evict` 회귀를 잡지 못합니다. 최소한 stats 조회와 local eviction 동작을 확인하는 HTTP 테스트를 추가하는 편이 좋습니다.
- [LOW] 공개 controller 타입에 한글 KDoc이 없습니다. [CacheController.kt](src/main/kotlin/io/bluetape4k/examples/cache/lettuce/controller/CacheController.kt) 14-65행과 [ProductController.kt](src/main/kotlin/io/bluetape4k/examples/cache/lettuce/controller/ProductController.kt) 15-43행은 데모 endpoint 계약 설명이 없습니다.

## Notes

- 엔티티 캐시 기본 동작과 CRUD 예제는 안정적으로 보입니다.
