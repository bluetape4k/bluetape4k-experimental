# hibernate-cache-lettuce-h6

Hibernate 6.6.x용 2nd Level Cache — Lettuce Near Cache (Caffeine L1 + Redis L2) 구현체.

Hibernate 7.x 버전은 `:hibernate-cache-lettuce` 모듈을 사용하세요.

## 설정

```properties
hibernate.cache.use_second_level_cache=true
hibernate.cache.region.factory_class=io.bluetape4k.hibernate.cache.lettuce.h6.LettuceNearCacheRegionFactory
hibernate.cache.lettuce.redis_uri=redis://localhost:6379
hibernate.cache.lettuce.codec=lz4fory
hibernate.cache.lettuce.local.max_size=10000
hibernate.cache.lettuce.local.expire_after_write=30m
hibernate.cache.lettuce.redis_ttl.default=120s
hibernate.cache.lettuce.use_resp3=true
```

## 의존성

```kotlin
dependencies {
    implementation(Libs.hibernate_h6_core)
    implementation(project(":hibernate-cache-lettuce-h6"))
}
```

## H6 vs H7 차이

| 항목 | H6 (`:hibernate-cache-lettuce-h6`) | H7 (`:hibernate-cache-lettuce`) |
|------|------------------------------------|---------------------------------|
| Hibernate 버전 | 6.6.15.Final | 7.2.6.Final |
| 패키지 | `io.bluetape4k.hibernate.cache.lettuce.h6` | `io.bluetape4k.hibernate.cache.lettuce` |
| API 호환성 | `org.hibernate.cache.spi.support.*` (동일) | `org.hibernate.cache.spi.support.*` |
