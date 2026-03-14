# Code Review

- Review date: 2026-03-08
- Module: `infra/hibernate-cache-lettuce-near`
- Test status: `./gradlew :hibernate-cache-lettuce-near:test` passed
- Recommendation: REQUEST CHANGES

## Findings

- [HIGH] `StorageAccess.release()`가 region factory가 공유하는 cache 인스턴스를 닫아 버립니다. [LettuceNearCacheStorageAccess.kt](src/main/kotlin/io/bluetape4k/hibernate/cache/lettuce/LettuceNearCacheStorageAccess.kt) 49-50행은 `nearCache.close()`를 호출하지만, [LettuceNearCacheRegionFactory.kt](src/main/kotlin/io/bluetape4k/hibernate/cache/lettuce/LettuceNearCacheRegionFactory.kt) 45행과 92-96행은 같은 `LettuceNearCache`를 `cacheMap`에 보관한 채 재사용합니다. Hibernate가 region access를 release한 뒤 같은 region을 다시 열면 닫힌 Redis connection을 물고 재사용할 수 있어 캐시 read/write가 불안정해집니다. release에서는 공유 캐시를 닫지 말고 factory 수명주기(`releaseFromUse`)에서만 close 하도록 분리해야 합니다.
- [LOW] 공개 설정 API의 KDoc이 빠져 있습니다. [LettuceNearCacheProperties.kt](src/main/kotlin/io/bluetape4k/hibernate/cache/lettuce/LettuceNearCacheProperties.kt) 83-105행의 `createCodec`, `buildNearCacheConfig`는 외부 사용 가능한 진입점인데 동작/기본값 문서가 없습니다. codec fallback 규칙과 region TTL 우선순위를 KDoc으로 남기는 편이 좋습니다.

## Notes

- 현재 테스트는 캐시 hit/miss와 region eviction은 검증하지만, `release()` 이후 동일 region 재사용 경로는 다루지 않습니다.
