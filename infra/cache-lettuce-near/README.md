# infra-cache-lettuce-near

Lettuce 기반 **Near Cache (2-tier cache)** 구현체.
Caffeine(L1 로컬) + Redis(L2 원격)의 2계층 캐시를 제공하며, **RESP3 CLIENT TRACKING**으로 분산 환경에서 로컬 캐시 일관성을 자동으로 유지한다.

## 아키텍처

```
Application
    │
[LettuceNearCache / LettuceNearSuspendCache]
    │
┌───┴───────────────────┐
│                       │
▼                       ▼
Caffeine (L1)       Redis (L2, via Lettuce)
로컬 인메모리 캐시    분산 캐시

Invalidation:
Redis → CLIENT TRACKING (RESP3 push) → CaffeineLocalCache.invalidate()
```

### 읽기 전략 (Read-Through)
1. L1(Caffeine) 히트 → 즉시 반환
2. L1 미스 → Redis GET → L1 populate → 반환

### 쓰기 전략 (Write-Through)
1. L1(Caffeine) put
2. Redis SET (TTL 설정 시 SETEX)
3. async Redis GET → CLIENT TRACKING 활성화

### 무효화 전략 (Invalidation)
- RESP3 `CLIENT TRACKING ON NOLOOP` 활성화
- 다른 인스턴스가 키를 수정하면 Redis 서버가 invalidation push를 전송
- `PushListener`가 수신 즉시 Caffeine에서 해당 키 제거

## 제공 클래스

| 클래스 | 설명 |
|--------|------|
| `LettuceNearCache<K, V>` | 동기(Blocking) Near Cache |
| `LettuceNearSuspendCache<K, V>` | 코루틴(Suspend) Near Cache |
| `NearCacheConfig<K, V>` | 설정 data class + DSL 빌더 |
| `CaffeineLocalCache<K, V>` | Caffeine 기반 L1 캐시 |
| `TrackingInvalidationListener<K, V>` | RESP3 CLIENT TRACKING 리스너 |

## 의존성

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.bluetape4k:bluetape4k-experimental-infra-cache-lettuce-near")

    // 직렬화 런타임 (선택)
    implementation("org.apache.fory:fory-kotlin:0.15.0")
    implementation("org.lz4:lz4-java:1.8.0")
}
```

## 빠른 시작

### String 키/값 (동기)

```kotlin
val redisClient = RedisClient.create("redis://localhost:6379")

val cache = LettuceNearCache.create(
    redisClient = redisClient,
    config = NearCacheConfig(
        cacheName = "my-cache",
        maxLocalSize = 10_000,
        localExpireAfterWrite = Duration.ofMinutes(30),
        redisTtl = Duration.ofSeconds(120),
    )
)

cache.put("key", "value")
val value = cache.get("key")   // L1 히트 시 Redis 미조회

cache.close()
redisClient.shutdown()
```

### 커스텀 타입 + DSL 빌더 (동기)

```kotlin
val codec = LettuceBinaryCodec<MyValue>(BinarySerializers.LZ4Fory)

val config = nearCacheConfig<String, MyValue> {
    cacheName = "product-cache"
    maxLocalSize = 50_000
    localExpireAfterWrite = Duration.ofMinutes(10)
    redisTtl = Duration.ofMinutes(5)
    useRespProtocol3 = true
    recordStats = true
}

val cache = LettuceNearCache(redisClient, codec, config)
```

### 코루틴 (Suspend)

```kotlin
val cache = LettuceNearSuspendCache.create(
    redisClient = redisClient,
    config = NearCacheConfig(
        cacheName = "async-cache",
        redisTtl = Duration.ofMinutes(5),
    )
)

// suspend 함수 내에서 사용
coroutineScope {
    cache.put("key", "value")
    val value = cache.get("key")
    cache.remove("key")
}

cache.close()
```

### 멀티-get / 멀티-put

```kotlin
// 일괄 조회 (L1 히트는 Redis를 조회하지 않음)
val results: Map<String, String> = cache.getAll(setOf("k1", "k2", "k3"))

// 일괄 저장
cache.putAll(mapOf("k1" to "v1", "k2" to "v2"))
```

## NearCacheConfig 설정 옵션

| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `cacheName` | `"lettuce-near-cache"` | 캐시 이름 |
| `maxLocalSize` | `10_000` | Caffeine 최대 항목 수 |
| `localExpireAfterWrite` | `30분` | Caffeine write 후 만료 시간 |
| `localExpireAfterAccess` | `null` | Caffeine access 후 만료 시간 (null = 비활성) |
| `redisTtl` | `null` | Redis TTL (null = 만료 없음) |
| `useRespProtocol3` | `true` | RESP3 + CLIENT TRACKING 활성화 |
| `recordStats` | `false` | Caffeine 통계 수집 활성화 |

## 통계 수집

```kotlin
val config = NearCacheConfig(recordStats = true)
val cache = LettuceNearCache(redisClient, codec, config)

// ... 사용 후
val stats = cache.localStats()   // CacheStats? (null if recordStats = false)
println("Hit rate: ${stats?.hitRate()}")
println("Miss count: ${stats?.missCount()}")
println("Eviction count: ${stats?.evictionCount()}")
```

## CLIENT TRACKING 동작 원리

```
인스턴스 A            Redis 서버         인스턴스 B
    │                    │                   │
    │── CLIENT TRACKING ON ──>               │
    │── GET "key" ──────>│                   │
    │<── value ──────────│                   │
    │                    │<── SET "key" ─────│
    │<── invalidate push ─                   │
    │── local.remove("key")                  │
```

- `NOLOOP` 옵션: 자신이 변경한 키의 invalidation은 수신하지 않음
- Redis 6+ 및 RESP3 프로토콜 필요

## API 참조

### LettuceNearCache / LettuceNearSuspendCache

| 메서드 | 설명 |
|--------|------|
| `get(key)` | 키 조회 (L1 미스 시 Redis 조회) |
| `getAll(keys)` | 멀티-get |
| `put(key, value)` | write-through 저장 |
| `putAll(map)` | 일괄 저장 |
| `putIfAbsent(key, value)` | 없을 때만 저장, 기존 값 반환 |
| `remove(key)` | L1 + Redis 삭제 |
| `removeAll(keys)` | 일괄 삭제 |
| `replace(key, value)` | 기존 값 교체 |
| `replace(key, oldValue, newValue)` | CAS 교체 |
| `getAndRemove(key)` | 조회 후 삭제 |
| `getAndReplace(key, value)` | 조회 후 교체 |
| `containsKey(key)` | L1 또는 Redis 존재 확인 |
| `clearLocal()` | L1만 비움 (Redis 유지) |
| `clearAll()` | L1 + Redis 모두 비움 |
| `localSize()` | L1 추정 항목 수 |
| `localStats()` | Caffeine 통계 (recordStats = true 시) |
| `close()` | 리소스 해제 |

## 제약 사항

- Redis 6 이상 + RESP3 지원 필요 (CLIENT TRACKING 사용 시)
- RESP3를 지원하지 않는 Redis 환경에서는 `useRespProtocol3 = false`로 설정 (invalidation 비활성화)
- `clearAll()`은 `FLUSHDB`를 실행하므로 프로덕션 환경에서 주의
