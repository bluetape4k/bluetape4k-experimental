# Hibernate Lettuce Feature Review Plan

## Requirements Summary

- 목표는 옛 `~/work/debop/hibernate-redis` 구현에서 지금도 가치 있는 기능과 테스트 자산을 추려서, `bluetape4k-experimental`의 `spring-boot/hibernate-lettuce` 모듈에 무엇을 추가할지 결정하는 것이다.
- 검토 범위는 우선 `spring-boot/hibernate-lettuce`와 그 하위 예제/테스트로 제한한다. 코어 구현체 `infra/hibernate-cache-lettuce`는 이미 갖춘 기능을 기준선으로 사용하고, 필요 시 최소 diff로 연계 변경만 허용한다.
- 옛 구현에서 참고할 핵심 축은 다음 세 가지다.
  - Spring/JPA 환경에서 query cache를 함께 활성화하는 부트스트랩 패턴 (`hibernate5/src/test/java/org/hibernate/cache/redis/jpa/JpaCacheConfiguration.java:77-85`)
  - RegionFactory 기반 query cache / timestamps / natural-id 검증 시나리오 (`hibernate5/src/test/java/org/hibernate/cache/redis/hibernate5/AbstractHibernateCacheTest.java:64-77`, `84-219`)
  - 외부 설정 파일과 TTL override 계약 (`hibernate5/src/test/resources/conf/hibernate-redis.properties:16-26`)

## Current State Review

### 이미 있는 기능

- 코어 `infra/hibernate-cache-lettuce`는 Hibernate 7 `RegionFactoryTemplate` 기반으로 domain data, query results, timestamps storage access를 모두 생성한다 (`infra/hibernate-cache-lettuce/src/main/kotlin/io/bluetape4k/hibernate/cache/lettuce/LettuceNearCacheRegionFactory.kt:81-102`).
- 코어 테스트는 query cache hit/miss 및 stale invalidation을 이미 검증한다 (`infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateQueryCacheTest.kt:22-124`).
- natural-id, composite-id, update timestamps key 표현도 이미 검증한다 (`infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateAdvancedKeyCacheTest.kt:29-136`).
- Spring Boot auto-configuration은 region factory, TTL, codec, metrics 설정을 Hibernate properties로 전달한다 (`spring-boot/hibernate-lettuce/src/main/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheHibernateAutoConfiguration.kt:33-66`).
- Spring Boot 테스트는 기본 property binding, metrics/actuator bean 등록, 단순 entity 저장/조회 및 병렬 조회 안정성을 검증한다 (`spring-boot/hibernate-lettuce/src/test/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheAutoConfigurationTest.kt:36-157`, `spring-boot/hibernate-lettuce/src/test/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheIntegrationTest.kt:60-108`).

### 아직 부족한 부분

- Boot auto-configuration은 `hibernate.cache.use_second_level_cache=true`만 강제하고, query cache 활성화는 노출하지 않는다 (`spring-boot/hibernate-lettuce/src/main/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheHibernateAutoConfiguration.kt:37-41`).
- Boot 레벨 테스트에는 cacheable query, natural-id cache, update timestamps invalidation 같은 “사용자 관점 시나리오”가 없다. 현재 통합 테스트는 단순 `JpaRepository.findById` 경로에 머문다 (`spring-boot/hibernate-lettuce/src/test/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheIntegrationTest.kt:60-108`).
- Boot properties에는 query cache on/off, statistics on/off를 분리해 제어하는 의도가 드러나지 않는다. 현재는 metrics가 켜지면 `hibernate.generate_statistics=true`를 설정하는 수준이다 (`spring-boot/hibernate-lettuce/src/main/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheHibernateAutoConfiguration.kt:59-65`).
- README는 query cache와 natural-id cache를 “지원 가능한 기능”으로 설명하지 않는다. 하지만 코어 README는 query results/timestamps region과 TTL 예외를 이미 문서화하고 있다 (`infra/hibernate-cache-lettuce/README.md:15-18`, `80`, `168-170`).

## Decision

- `spring-boot/hibernate-lettuce`에 추가할 우선 기능은 “옛 Redis 구현의 query-cache 중심 사용성”을 Boot 레벨에서 노출하고 검증하는 것이다.
- 옛 구현의 Redisson 전용 외부 설정 파일 모델(`redisson-config`, YAML 경로)은 Lettuce 기반 구조와 맞지 않으므로 이관 대상에서 제외한다 (`hibernate5/src/test/resources/conf/hibernate-redis.properties:16-26`).
- soft-lock 기반 `READ_WRITE` 전략 계열 구현은 현재 코어가 `NONSTRICT_READ_WRITE`를 기본으로 선택하고 있고, README도 이를 권장하므로 이관 우선순위에서 제외한다 (`infra/hibernate-cache-lettuce/src/main/kotlin/io/bluetape4k/hibernate/cache/lettuce/LettuceNearCacheRegionFactory.kt:73`, `infra/hibernate-cache-lettuce/README.md:28-31`, `129`).

## Acceptance Criteria

- `spring-boot/hibernate-lettuce`에서 Spring property만으로 Hibernate query cache를 활성화하거나 비활성화할 수 있다.
- query cache 활성화 시 Boot integration test에서 두 번째 cacheable query 호출 후 Hibernate statistics의 query cache hit가 증가한다.
- query cache 활성화 시 entity 변경 이후 같은 cacheable query가 stale 결과를 반환하지 않고 miss/invalidation 경로가 검증된다.
- 필요하다면 natural-id cache 사용 예제가 추가되고, 적어도 Boot integration test 또는 example/demo 수준에서 `@NaturalIdCache` 시나리오가 한번은 검증된다.
- README와 example 설정이 새 property 계약을 설명하고, query cache/timestamps 관련 주의사항을 포함한다.
- 검증은 전체 빌드가 아니라 `./gradlew :spring-boot-hibernate-lettuce:test` 또는 해당 예제 모듈 단위 테스트로 끝난다.

## Implementation Steps

1. Boot property surface 설계
   - `LettuceNearCacheSpringProperties`에 query cache 토글용 중첩 설정 또는 명시 필드를 추가한다.
   - 1차 후보는 `queryCache.enabled` 한 개만 추가해 `hibernate.cache.use_query_cache`를 매핑하는 것이다.
   - 자연어 설명이 아닌 명시 설정으로 두는 이유는 현재 auto-configuration이 2nd-level cache만 암묵 활성화하고 있어, query cache까지 자동으로 켜 버리면 사용자가 의도하지 않은 query result 캐싱이 생길 수 있기 때문이다.
   - 수정 대상:
     - `spring-boot/hibernate-lettuce/src/main/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheSpringProperties.kt`
     - `spring-boot/hibernate-lettuce/src/main/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheHibernateAutoConfiguration.kt`

2. Hibernate property 매핑 강화
   - query cache enabled 시 `hibernate.cache.use_query_cache=true`를 주입한다.
   - statistics는 query cache 또는 metrics가 켜졌을 때 필요한지 결정하고, 중복 의도를 README에 명확히 적는다.
   - Hibernate 공식 문서상 query result cache는 `USE_QUERY_CACHE`와 timestamps region 계약을 전제로 하므로, 현재 코어가 timestamps region storage access를 이미 제공한다는 점을 전제로 Boot 레벨만 보강한다.

3. Auto-configuration 단위 테스트 보강
   - `ApplicationContextRunner` 테스트에 query cache property 바인딩과 Hibernate property 반영 검증을 추가한다.
   - `enabled=false` 또는 `query-cache.enabled=false`일 때 `hibernate.cache.use_query_cache`가 설정되지 않음을 검증한다.
   - 필요 시 statistics 제어 규칙도 함께 테스트한다.
   - 수정 대상:
     - `spring-boot/hibernate-lettuce/src/test/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheAutoConfigurationTest.kt`

4. Boot integration test를 query-cache 시나리오로 확장
   - 현재 단순 entity repository 테스트에 더해, cacheable JPQL 또는 Spring Data query hint 기반 query cache 시나리오를 추가한다.
   - 우선순위 높은 시나리오:
     - 같은 cacheable query 재실행 시 `queryCachePutCount > 0`, `queryCacheHitCount > 0`
     - 엔티티 수정 후 같은 query를 실행하면 stale 결과가 아닌 최신 결과 반환, `queryCacheMissCount > 0`
   - 가능하면 `EntityManager`를 직접 주입해 query hint 기반 테스트를 추가하고, Boot에서 statistics 접근을 위해 `EntityManagerFactory.unwrap(SessionFactory::class.java)`를 사용한다.
   - 수정 대상:
     - `spring-boot/hibernate-lettuce/src/test/kotlin/io/bluetape4k/spring/boot/autoconfigure/cache/lettuce/LettuceNearCacheIntegrationTest.kt`

5. natural-id 또는 advanced entity 시나리오 노출 여부 결정
   - 코어가 이미 강하게 검증하고 있으므로, Boot 모듈에서는 full parity가 아니라 “대표 시나리오 한 개”만 두는 것이 적절하다.
   - 추천안:
     - `@NaturalIdCache` 엔티티를 테스트 내부에 추가하고 `session.byNaturalId(...)`를 통해 Boot wiring 하에서도 natural-id cache hit가 발생함을 검증한다.
   - 이 단계는 query cache 추가 이후 2순위로 둔다. 이유는 old repo에서 가장 사용자 체감이 컸던 시나리오가 JPA query cache와 Spring wiring 검증이기 때문이다 (`hibernate5/src/test/java/org/hibernate/cache/redis/jpa/JpaCacheTest.java:74-195`).

6. 문서 및 예제 업데이트
   - README에 `query-cache.enabled`와 query cache 사용 예제를 추가한다.
   - example app에 cacheable query 또는 관련 설정을 반영해 “실사용 경로”를 하나 만든다.
   - timestamps region TTL 주의사항은 core README 내용을 요약해 Boot README에도 연결한다.
   - 수정 대상:
     - `spring-boot/hibernate-lettuce/README.md`
     - `examples/spring-boot-hibernate-lettuce-demo/src/main/resources/application.yml`
     - 필요 시 `examples/spring-boot-hibernate-lettuce-demo/src/main/kotlin/.../repository` 또는 controller

## Risks And Mitigations

- 위험: query cache를 기본 활성화하면 예기치 않은 stale query 결과나 메모리 사용 증가를 유발할 수 있다.
  - 대응: 기본값은 `false`로 두고 opt-in property로 노출한다.
- 위험: Boot integration test가 내부 Hibernate statistics 구현 세부사항에 과하게 결합될 수 있다.
  - 대응: exact 숫자보다 `> 0` 및 stale/non-stale 결과 검증처럼 의미 기반 assertion을 사용한다. 이는 코어 테스트 패턴과도 일치한다 (`infra/hibernate-cache-lettuce/src/test/kotlin/io/bluetape4k/hibernate/cache/lettuce/HibernateQueryCacheTest.kt:46-62`, `108-123`).
- 위험: natural-id 시나리오까지 한 번에 넣으면 테스트 fixture가 비대해질 수 있다.
  - 대응: 1차 구현은 query cache만 필수, natural-id는 후속 단계 또는 별도 commit으로 분리한다.
- 위험: README가 코어 모듈과 다른 계약을 설명하면 사용자 혼란이 생긴다.
  - 대응: property 이름은 Boot prefix만 다르게 두고, 실제 Hibernate property 매핑표를 함께 문서화한다.

## Out Of Scope

- Redisson YAML 외부 설정 파일 호환 레이어
- RedisRegionFactory / SingletonRedisRegionFactory 같은 다중 팩토리 모델 복제
- Hibernate 5 시절 soft-lock 기반 `READ_WRITE` / `TRANSACTIONAL` access strategy parity
- 작업 대상 모듈 밖 대규모 리팩터링

## Verification Steps

1. `./gradlew :spring-boot-hibernate-lettuce:test`
2. query cache 시나리오를 example까지 확장했다면 `./gradlew :spring-boot-hibernate-lettuce-demo:test`
3. 필요 시 README/property 계약 검토:
   - Boot README의 property 예제가 실제 테스트 property 이름과 일치하는지 확인
   - query cache 기본값과 opt-in 동작이 테스트와 문서에서 동일한지 확인

## Required Test Scenarios

### Auto-configuration tests

- `query-cache.enabled=true`일 때 `hibernate.cache.use_query_cache=true`가 주입된다.
- `query-cache.enabled=false`일 때 query cache 관련 Hibernate property가 주입되지 않는다.
- `metrics.enabled=false`이면서 `query-cache.enabled=true`인 경우에도 query cache 동작에 필요한 최소 property만 반영되는지 확인한다.
- region TTL / local cache duration / codec 기존 회귀 테스트는 그대로 유지한다.

### Integration tests

- 같은 cacheable query를 두 번 실행하면 첫 번째 실행 후 `queryCachePutCount > 0`, 두 번째 실행 후 `queryCacheHitCount > 0`.
- cacheable query 결과를 만든 뒤 엔티티를 수정하면, 같은 query 재실행 시 stale 결과가 아니라 최신 결과를 반환하고 `queryCacheMissCount > 0`.
- query cache를 끈 상태에서는 동일 query 재실행에도 query cache hit가 증가하지 않는다.
- 기존 단순 entity 저장/조회 시나리오는 유지해서 “2nd-level cache 기본 wiring” 회귀를 막는다.

### Optional advanced tests

- `@NaturalIdCache` 엔티티를 추가해 Boot wiring 하에서도 natural-id cache put/hit가 증가하는지 검증한다.
- 가능하다면 update timestamps region key가 생성되는지 또는 invalidation이 실제로 동작하는지를 간접 검증한다.

### Test design rules

- 통계값은 exact count보다 `> 0` 또는 “증가했다” 중심으로 검증한다.
- 병렬성 테스트는 현재 모듈의 안정성 회귀 방지용으로 유지하되, query cache 시나리오와 섞지 않는다.
- 새 구현 코드를 넣는 경우 테스트를 먼저 추가하거나, 최소한 같은 커밋 안에서 테스트와 구현을 함께 넣는다.

## Recommended Execution Order

1. query cache property 추가
2. auto-configuration unit test 추가
3. Boot integration query cache test 추가
4. README/example 반영
5. 여유가 있으면 natural-id Boot 시나리오 추가

## Summary Recommendation

- 옛 `hibernate-redis`에서 지금 옮길 가치가 가장 높은 것은 Redisson 구성 파일이 아니라, Spring/JPA 사용자가 바로 체감하는 query cache wiring과 그 회귀 테스트다.
- `infra/hibernate-cache-lettuce`는 이미 대부분의 저수준 기능을 갖췄으므로, `spring-boot/hibernate-lettuce`에서는 “기능 추가”보다 “기능 노출과 검증”에 집중하는 계획이 가장 안전하고 최소 diff 원칙에도 맞다.
