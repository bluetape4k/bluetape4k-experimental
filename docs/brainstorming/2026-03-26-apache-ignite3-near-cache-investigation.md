# Apache Ignite 3 Near Cache 도입 가능성 조사

**날짜**: 2026-03-26
**결론**: Ignite 3는 Near Cache 용도로 부적합 — 도입 포기

---

## 배경

bluetape4k-experimental의 `infra/cache-lettuce-near` 모듈(Caffeine L1 + Redis L2 + CLIENT TRACKING)과 유사한 Near Cache를 Apache Ignite 3 백엔드로 구현하려는 시도. 이전에도 시도했다가 실패한 이력 있음.

---

## 조사 결과

### 1. Near Cache — 미지원

- Ignite 3는 아키텍처 전면 재설계 시 **모든 클라이언트를 Thin Client로 통일**
- Near Cache는 원래 Thick Client + Server Node 구조에서만 동작
- `NearCacheConfiguration` 클래스 자체가 Ignite 3 API에 존재하지 않음
- Ignite 3.0 Wishlist(2020)에서 Near Cache를 "문제 많고 개발 진행을 느리게 함"으로 지목, 제거 결정

### 2. 캐시 무효화 메커니즘 — 미흡

- Redis `CLIENT TRACKING`(서버 push 방식)과 달리, Ignite 3는 Continuous Query를 클라이언트가 직접 구독·관리
- 파티션 할당 기반 내부 캐시 무효화는 존재하나, 애플리케이션 레벨 Near Cache 무효화와는 다른 개념

### 3. 캐시 = 테이블 구조 — 운영 복잡도 문제

Ignite 3에서 캐시를 추가하려면 테이블 DDL이 필요:

```sql
-- 캐시마다 테이블 DDL 작성 필요
CREATE TABLE user_cache (
    key VARCHAR PRIMARY KEY,
    value VARBINARY
) WITH "CACHE_NAME=user-cache, ...";
```

Redis(현재 방식)와 비교:
```kotlin
// 설정 한 줄로 끝
nearCacheConfig {
    cacheName = "user-cache"
    maxLocalSize = 10_000
}
```

Near Cache의 핵심 가치("투명하고 가볍게 붙이는 것")와 정반대 방향.

### 4. Read-Through / Write-Through / Write-Behind — 전부 미지원

Ignite 2에서 지원하던 `CacheStore` / `CacheStoreAdapter` 인터페이스가 **완전히 제거**됨.

| 기능 | Ignite 2 | Ignite 3 (3.1.0) |
|------|----------|-----------------|
| Read-Through | ✅ 지원 | ❌ 미지원 |
| Write-Through | ✅ 지원 | ❌ 미지원 |
| Write-Behind | ✅ 지원 | ❌ 미지원 |
| `CacheStore` / `CacheStoreAdapter` | ✅ 지원 | ❌ 제거됨 |
| Spring `@Cacheable` / `@CachePut` | ✅ 지원 | ❌ 미지원 |
| Spring Boot 자동 구성 (`IgniteClient`) | ✅ 지원 | ✅ 지원 |
| Spring Data JDBC | ✅ 지원 | ✅ 지원 |

#### 왜 제거됐나

Ignite 3는 패러다임 자체가 바뀜:
- **Ignite 2**: "캐시처럼 동작하는 분산 DB" — 외부 DB 앞단에 붙는 캐시 계층
- **Ignite 3**: "DB처럼 동작하는 분산 DB" — Ignite 자체가 Primary Store

`CacheStore`(외부 DB 연동 인터페이스) 개념 자체를 버림.

---

## 이전 실패 원인 정리

1. **Near Cache API 자체 없음** — 직접 구현 필요
2. **캐시 = 테이블** — 캐시 추가마다 DDL 작성, 스키마 버전 관리, 마이그레이션 필요
3. **무효화 메커니즘 미흡** — Continuous Query 직접 관리, Redis CLIENT TRACKING 수준의 push 무효화 없음
4. **Value 타입 사전 정의 필요** — 제네릭 캐시 구현 어려움
5. **Read/Write-Through 미지원** — 캐시-DB 연동 패턴 전면 재구현 필요
6. **Spring Cache 추상화 미지원** — `@Cacheable` 등 사용 불가

---

## 결론

**Apache Ignite 3는 캐시 솔루션이 아닌 분산 SQL DB로 봐야 한다.**

bluetape4k Near Cache 목적으로는 도입 가치 없음. 기존 Lettuce Near Cache(`infra/cache-lettuce-near`) 유지가 현실적.

### 향후 탐색 가능한 대안

- **Hazelcast 5.x** — Near Cache 공식 지원, Spring Cache 통합
- **Redis + Caffeine 현행 유지** — 이미 검증된 구현체
- **Apache Geode / VMware GemFire** — Near Cache 지원하나 상용 라이선스 이슈

---

## 참고 자료

- [Apache Ignite 3 공식 문서](https://ignite.apache.org/docs/ignite3/latest/)
- [Ignite 2 → 3 마이그레이션 가이드](https://ignite.apache.org/docs/ignite3/latest/installation/migration-from-ai2/overview)
- [Near Caches — Ignite 2 문서](https://ignite.apache.org/docs/ignite2/latest/configuring-caches/near-cache)
- [Apache Ignite 3.0 Wishlist (Confluence)](https://cwiki.apache.org/confluence/display/IGNITE/Apache+Ignite+3.0+Wishlist)
- [Spring Boot Integration — Ignite 3.1.0](https://ignite.apache.org/docs/ignite3/3.1.0/develop/integrate/spring-boot)
