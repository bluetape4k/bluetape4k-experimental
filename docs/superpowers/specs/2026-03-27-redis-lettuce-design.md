# redis-lettuce 모듈 설계 스펙

**날짜:** 2026-03-27 (검토 반영: 2026-03-27)
**위치:** `bluetape4k-experimental/infra/redis-lettuce`
**목표:** Redisson 고유 기능 및 Pro 기능을 Lettuce 기반 Kotlin 관용 API로 구현

---

## 1. 모듈 위치 및 구조

```
bluetape4k-experimental/infra/redis-lettuce/
  build.gradle.kts
  src/main/kotlin/io/bluetape4k/redis/lettuce/
    lock/
      FairLockOptions.kt
      LettuceFairLock.kt              ← Sync (Virtual Thread), Reentrant
      LettuceSuspendFairLock.kt       ← Coroutine, Reentrant
    rate/
      RateLimiterConfig.kt
      RateType.kt
      LettuceRateLimiter.kt
      LettuceSuspendRateLimiter.kt
    cache/
      NearCacheMapOptions.kt          ← enum 포함 (SyncStrategy 등)
      LettuceNearCacheMap.kt
      LettuceSuspendNearCacheMap.kt
    hll/
      LettuceHyperLogLog.kt
      LettuceSuspendHyperLogLog.kt
    filter/
      BloomFilterOptions.kt
      LettuceBloomFilter.kt
      LettuceSuspendBloomFilter.kt
      CuckooFilterOptions.kt
      LettuceCuckooFilter.kt
      LettuceSuspendCuckooFilter.kt
    LettuceFeatures.kt                ← RedisClient 확장함수 팩토리
  src/test/kotlin/io/bluetape4k/redis/lettuce/
    lock/
    rate/
    cache/
    hll/
    filter/
```

---

## 2. 코딩 원칙

- `bluetape4k-projects/infra/lettuce` 패턴 100% 준수
- **Sync first** (Virtual Thread 사용자 대상), Suspend 버전은 `LettuceSuspend` prefix
- **연결 타입:** 기능별로 다름 (아래 참조)
    - FairLock/RateLimiter: `StatefulRedisConnection<String, String>`
    - NearCacheMap/HyperLogLog: `StatefulRedisConnection<String, V>` (제네릭 Codec)
    - BloomFilter/CuckooFilter: `StatefulRedisConnection<String, ByteArray>`
- **직렬화:** 팩토리에 `RedisCodec<String, V>` 파라미터 제공, 기본값은 `LettuceBinaryCodecs.lz4Fory()`
- Lua 스크립트는 companion object 상수 (인라인 문자열)
- KDoc 한국어, `KLogging()` / `KLoggingChannel()` 사용
- `data class` + `companion object { val Default }` Options 패턴
- `copy()`로 기본값에서 일부만 변경 가능
- 모든 기능 클래스는 `AutoCloseable` 구현, `close()` 시 connection/listener 정리

---

## 3. Options/Config 클래스

### 3-1. FairLockOptions

```kotlin
data class FairLockOptions(
    val leaseTime: Duration = 30.seconds,
    val threadWaitTimeout: Duration = 5.seconds,
    val retryDelay: Duration = 50.milliseconds,
    val backOffPolicy: BackOffPolicy = BackOffPolicy.Exponential(),
) {
    companion object {
        val Default = FairLockOptions()
    }
}

sealed class BackOffPolicy {
    data class Fixed(val delay: Duration = 50.milliseconds): BackOffPolicy()
    data class Exponential(
        val initialDelay: Duration = 50.milliseconds,  // retryDelay와 동일하게 맞춤
        val maxDelay: Duration = 128.milliseconds,
        val multiplier: Int = 2,
    ): BackOffPolicy()
}
```

> **재진입(Reentrant) 설계:** FairLock은 Hash 기반 재진입 카운터를 사용합니다.
> 동일 소유자가 lock을 재획득하면 hold count가 증가하고, unlock 횟수가 hold count와 같아야 해제됩니다.
>
> **소유자 식별자 전략:**
> - `LettuceFairLock` (Sync): `"${Thread.currentThread().threadId()}:${instanceUUID}"` — Virtual Thread 포함 스레드 단위 식별
> - `LettuceSuspendFairLock` (Coroutine): `"${ownerCounter.incrementAndGet()}:${instanceUUID}"` — `ownerCounter`는 인스턴스당 `AtomicLong`, 생성된 ID를 `atomic<String?>` 필드에 저장.
>   코루틴은 재개 시 스레드가 바뀔 수 있으므로 스레드 ID 사용 불가. 대신 인스턴스 레벨 `ownerRef`(atomicfu) 필드로 소유 여부 판단 → 동일 인스턴스를 통한 재진입 허용.

### 3-2. RateLimiterConfig

```kotlin
enum class RateType {
    OVERALL,
    PER_CLIENT
}

data class RateLimiterConfig(
    val rate: Long,
    val rateInterval: Duration,
    val rateType: RateType = RateType.OVERALL,
) {
    companion object {
        val Default = RateLimiterConfig(rate = 10, rateInterval = 1.seconds)
    }
}
```

> **PER_CLIENT clientId:** `RedisClient` 인스턴스당 고정 UUID를 생성하여 사용.
> 재시작 시 새 UUID가 생성되므로 이전 클라이언트의 permit은 TTL 만료 후 회수됨.

### 3-3. NearCacheMapOptions

> Redisson 오픈소스 분석 결과: SyncStrategy.UPDATE, ExpirationEventPolicy, storeCacheMiss 모두
> 일반(오픈소스) 버전에 이미 존재. Pro 차이는 클러스터 환경 Pub/Sub 안정성과 성능 최적화 위주.
>
> **무효화 방식 결정:** Pub/Sub 채널 사용 (기존 `LettuceNearCache`의 CLIENT TRACKING과 다름).
> 이유: UPDATE 전략 지원, 다중 인스턴스 환경에서 명시적 제어 가능, RESP3 의존성 없음.

```kotlin
enum class SyncStrategy {
    NONE,
    INVALIDATE,
    UPDATE
}
enum class ReconnectionStrategy {
    NONE,
    CLEAR,
    LOAD
}
enum class StoreMode {
    LOCALCACHE_ONLY,
    LOCALCACHE_REDIS
}

/** 로컬 캐시 항목 제거 정책 (Caffeine 기반) */
enum class EvictionPolicy {
    NONE,
    LRU,
    LFU,
    SOFT,
    WEAK
}

/**
 * Redis keyspace 만료 이벤트 구독 방식.
 * DONT_SUBSCRIBE 외에는 Redis 서버 설정 필요: notify-keyspace-events Ex (keyevent) 또는 Kx (keyspace).
 */
enum class ExpirationEventPolicy {
    DONT_SUBSCRIBE,
    SUBSCRIBE_WITH_KEYEVENT_PATTERN,
    SUBSCRIBE_WITH_KEYSPACE_CHANNEL,
}

data class NearCacheMapOptions(
    val cacheName: String = "near-cache-map",
    val maxLocalSize: Long = 10_000,
    val localTtl: Duration = 30.minutes,
    val localExpireAfterAccess: Duration? = null,
    val redisTtl: Duration? = null,
    val recordStats: Boolean = false,
    val syncStrategy: SyncStrategy = SyncStrategy.INVALIDATE,
    val reconnectionStrategy: ReconnectionStrategy = ReconnectionStrategy.CLEAR,
    val storeMode: StoreMode = StoreMode.LOCALCACHE_REDIS,
    val evictionPolicy: EvictionPolicy = EvictionPolicy.LRU,
    val storeCacheMiss: Boolean = false,
    val expirationEventPolicy: ExpirationEventPolicy = ExpirationEventPolicy.DONT_SUBSCRIBE,
) {
    companion object {
        val Default = NearCacheMapOptions()
    }

    init {
        require(cacheName.isNotBlank()) { "cacheName must not be blank" }
        require(':' !in cacheName) { "cacheName must not contain ':'" }
        require(maxLocalSize > 0) { "maxLocalSize must be positive" }
        require(localTtl > Duration.ZERO) { "localTtl must be positive" }
        localExpireAfterAccess?.let { require(it > Duration.ZERO) { "localExpireAfterAccess must be positive" } }
        redisTtl?.let { require(it > Duration.ZERO) { "redisTtl must be positive" } }
    }
}
```

**`storeCacheMiss` 동작:**

- `get(key)` 결과가 null → sentinel 값(`NULL_SENTINEL`)을 로컬에 저장 (TTL: `localTtl`)
- 이후 동일 key → Redis 조회 없이 즉시 null 반환
- `put(key, value)` / `remove(key)` 호출 시 sentinel 제거

### 3-4. BloomFilterOptions

```kotlin
data class BloomFilterOptions(
    val expectedInsertions: Long = 1_000_000L,
    val falseProbability: Double = 0.03,
) {
    companion object {
        val Default = BloomFilterOptions()
    }

    init {
        require(expectedInsertions > 0) { "expectedInsertions must be positive" }
        require(falseProbability > 0.0 && falseProbability < 1.0) { "falseProbability must be in (0, 1) exclusive — p=0 and p=1 are mathematically invalid for ln(p)" }
    }
}
```

> **`tryInit()` 멱등성:** 이미 초기화된 경우 NX 플래그로 무시. 다른 파라미터로 재초기화 시 예외.

### 3-5. CuckooFilterOptions

```kotlin
data class CuckooFilterOptions(
    val capacity: Long = 1_000_000L,
    val bucketSize: Int = 4,
    val maxIterations: Int = 500,
) {
    companion object {
        val Default = CuckooFilterOptions()
    }

    init {
        require(capacity > 0) { "capacity must be positive" }
        require(bucketSize in 1..8) { "bucketSize must be in [1, 8]" }
        require(maxIterations > 0) { "maxIterations must be positive" }
    }
}
```

---

## 4. 기능별 구현 전략

### 4-1. FairLock (우선순위 1)

**설계 결정:** Reentrant (Hash 기반, Redisson 동일)

**Redis 자료구조:**

```
{name}              → Hash { "threadId:uuid" → holdCount }  (재진입 카운터)
{name}:queue        → 대기 스레드 FIFO 리스트 (RPUSH/LRANGE/LREM)
{name}:timeout      → 스레드별 타임아웃 ZSet (score=expire timestamp ms)
{name}:channel      → Pub/Sub 알림 채널 (unlock 시 다음 스레드 깨움)
```

**Lua 의사코드 — `tryLock`:**

```lua
-- KEYS[1]={name}, KEYS[2]={name}:queue, KEYS[3]={name}:timeout, KEYS[4]={name}:channel
-- ARGV[1]=threadKey("threadId:uuid"), ARGV[2]=leaseTimeMs, ARGV[3]=currentTimeMs, ARGV[4]=threadWaitTimeMs

-- 1. 만료된 대기 스레드 정리 (score < now)
local expired = redis.call('zrangebyscore', KEYS[3], '-inf', ARGV[3])
for _, v in ipairs(expired) do
    redis.call('lrem', KEYS[2], 0, v)
    redis.call('zrem', KEYS[3], v)
end

-- 2. 락이 비어있고 OR 현재 스레드가 이미 보유 중
local lockExists = redis.call('exists', KEYS[1])
local queueHead = redis.call('lindex', KEYS[2], 0)
if lockExists == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
    if queueHead == ARGV[1] or queueHead == false then
        -- 재진입: hold count 증가
        redis.call('hincrby', KEYS[1], ARGV[1], 1)
        redis.call('pexpire', KEYS[1], ARGV[2])
        redis.call('lrem', KEYS[2], 0, ARGV[1])
        return nil  -- 성공
    end
end

-- 3. 큐에 등록 (없으면 RPUSH)
if redis.call('zscore', KEYS[3], ARGV[1]) == false then
    redis.call('rpush', KEYS[2], ARGV[1])
    redis.call('zadd', KEYS[3], tonumber(ARGV[3]) + tonumber(ARGV[4]), ARGV[1])
end

-- 4. 큐 앞 스레드의 남은 대기 시간 반환 (queueHead nil 가드)
if queueHead == false then return 0 end
local headTimeout = redis.call('zscore', KEYS[3], queueHead)
if headTimeout == false then return 0 end
return math.max(tonumber(headTimeout) - tonumber(ARGV[3]), 0)
```

**Lua 의사코드 — `unlock`:**

```lua
-- 1. 보유자 검증
if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then return 0 end
-- 2. hold count 감소
local remaining = redis.call('hincrby', KEYS[1], ARGV[1], -1)
if remaining > 0 then
    redis.call('pexpire', KEYS[1], ARGV[2])
    return 1
end
-- 3. 완전 해제
redis.call('del', KEYS[1])
-- 4. 큐에서 제거 후 다음 스레드 pub/sub 알림
redis.call('lrem', KEYS[2], 0, ARGV[1])
redis.call('zrem', KEYS[3], ARGV[1])
redis.call('publish', KEYS[4], 'unlock')
return 1
```

**Lua 의사코드 — `acquireFailed` (타임아웃 시 큐 제거):**

```lua
redis.call('lrem', KEYS[2], 0, ARGV[1])
redis.call('zrem', KEYS[3], ARGV[1])
```

**`close()` 정리:** Pub/Sub 구독 해제 + connection 닫기.

### 4-2. RateLimiter (우선순위 2)

**설계 결정:** Token Bucket + ZSet permit expiry 추적 (Redisson 동일)

**Redis 자료구조:**

```
{name}:config   → Hash { rate, interval, type }
{name}:value    → String (잔여 토큰 수, OVERALL)
{name}:permits  → ZSet (score=expireTime ms, member=randomBytes+permitCount, OVERALL)
{name}:{clientId}:value   → String (PER_CLIENT 잔여)
{name}:{clientId}:permits → ZSet (PER_CLIENT)
```

**Lua 의사코드 — `tryAcquire(permits)`:**

```lua
-- KEYS[1]={name}:value, KEYS[2]={name}:permits (또는 PER_CLIENT 변형)
-- ARGV[1]=permits, ARGV[2]=currentTimeMs, ARGV[3]=rate, ARGV[4]=intervalMs, ARGV[5]=randomHex(permit member prefix)

-- 1. 만료된 permit 회수 (ZSet에서 expireTime < now 제거 → 토큰 복원)
local expired = redis.call('zrangebyscore', KEYS[2], '-inf', ARGV[2])
if #expired > 0 then
    redis.call('zremrangebyscore', KEYS[2], '-inf', ARGV[2])
    -- expired permit에서 count 합산 후 value에 반영 (struct 파싱)
end

-- 2. 잔여 토큰 확인
local current = tonumber(redis.call('get', KEYS[1])) or tonumber(ARGV[3])
if current < tonumber(ARGV[1]) then return -1 end  -- 거부

-- 3. 토큰 차감 + permit ZSet에 만료시간 기록
redis.call('decrby', KEYS[1], ARGV[1])
local expireAt = tonumber(ARGV[2]) + tonumber(ARGV[4])
-- permit member: "randomHex:permitCount" 단순 문자열 (struct.pack 환경 의존성 회피)
local member = ARGV[5] .. ':' .. ARGV[1]  -- ARGV[5]=randomHex(8bytes hex), ARGV[1]=permits
redis.call('zadd', KEYS[2], expireAt, member)
return 0  -- 허용
```

**`trySetRate` (초기화, NX):**

```lua
if redis.call('hsetnx', KEYS[1], 'rate', ARGV[1]) == 1 then
    redis.call('hset', KEYS[1], 'interval', ARGV[2], 'type', ARGV[3])
    redis.call('set', KEYS[2], ARGV[1])  -- 초기 토큰 = rate
end
```

**`close()`:** connection 닫기 (Pub/Sub 없음).

### 4-3. NearCacheMap (우선순위 3)

**설계 결정:** 무효화 방식 = **Pub/Sub** (기존 `LettuceNearCache`의 CLIENT TRACKING과 다름)

**Redis 자료구조 (키 스키마 확정):**

```
{cacheName}:{key}             → 실제 값 (codec 직렬화)
{cacheName}:topic             → Pub/Sub 무효화 채널 (PUBLISH/SUBSCRIBE)
{cacheName}:invalidation-log  → LOAD 전략용 ZSet (score=timestamp ms, value=keyHash 16bytes)
```

**SyncStrategy 동작:**

- `NONE`: Pub/Sub 없음 (단일 인스턴스 전용)
- `INVALIDATE`: write 시 `PUBLISH {cacheName}:topic {keyHash(16bytes)}` → 수신 인스턴스 로컬 무효화
- `UPDATE`: write 시 `PUBLISH {cacheName}:topic {keyHash + serialized(value)}` → 수신 인스턴스 로컬 직접 갱신

**ReconnectionStrategy:**

- `NONE`: 재연결 후 아무것도 하지 않음 (stale 위험)
- `CLEAR`: 재연결 시 로컬 캐시 전체 삭제 (기본값, 안전)
- `LOAD`: `{cacheName}:invalidation-log` ZSet 기반 선택적 복구
    - write 연산마다 `ZADD {cacheName}:invalidation-log score=now value=keyHash(16bytes)` 기록
    - log 자체 TTL: 11분 (eviction scheduler 자동 정리)
    - 재연결 시 `ZRANGEBYSCORE(lastDisconnectTime, +∞)` → 변경된 키만 선택 무효화
    - 단절 > 10분 OR Redis key 소멸: CLEAR 폴백

**직렬화:** `RedisCodec<String, V>` 파라미터, 기본값 `LettuceBinaryCodecs.lz4Fory()`

**`storeCacheMiss` 동작:**

- `get(key)` 결과가 null → sentinel `NearCacheMissMarker` 객체를 로컬에 저장 (TTL: `localTtl`)
- 이후 동일 key → Redis 조회 없이 즉시 null 반환
- `put(key, value)` / `remove(key)` 호출 시 sentinel 제거

**ExpirationEventPolicy 동작:**

- `DONT_SUBSCRIBE`: Redis TTL 만료 감지 안 함 (로컬 TTL로만 관리)
- `SUBSCRIBE_WITH_KEYEVENT_PATTERN`: `__keyevent@*:expired` 구독 → 만료 시 로컬 자동 무효화
- `SUBSCRIBE_WITH_KEYSPACE_CHANNEL`: `__keyspace@N__:{cacheName}:*` 구독

**`close()` 정리:** Pub/Sub 구독 해제 + invalidation-log listener 해제 + connection 닫기 + Caffeine 캐시 닫기

### 4-4. HyperLogLog (우선순위 4)

네이티브 Redis 명령 래퍼 (Lua 불필요). `RedisCodec<String, V>` 사용.

```
{name}  → HyperLogLog 자료구조 (Redis 내부 관리)
```

- `add(vararg elements: V): Boolean` → `PFADD`
- `count(): Long` → `PFCOUNT {name}`
- `countWith(vararg others: LettuceHyperLogLog<V>): Long` → `PFCOUNT {name} {other1} ...`
- `mergeWith(dest: String, vararg others: LettuceHyperLogLog<V>)` → `PFMERGE`

> **클러스터 주의:** `PFCOUNT`에 여러 키 전달 시 모두 같은 슬롯에 있어야 함 (hash tag 필요).

**`close()`:** connection 닫기.

### 4-5. BloomFilter (우선순위 5)

**해시 함수:** MurmurHash3 128bit (Redisson 방식, Highway Hash 대신 JVM 표준 구현 사용)

```
{name}:config  → Hash { k=해시함수수, m=비트배열크기, n=expectedInsertions, p=falseProbability }
{name}         → BitSet (SETBIT/GETBIT)
```

**초기화 공식:**

- `m = ceil(-n * ln(p) / (ln(2))^2)`
- `k = round(m / n * ln(2))`

**핵심 Lua — `add(element)`:**

```lua
-- KEYS[1]={name}, ARGV[1..k]=hash positions
for i=1,#ARGV do
    redis.call('setbit', KEYS[1], ARGV[i], 1)
end
return 1
```

**핵심 Lua — `contains(element)`:**

```lua
for i=1,#ARGV do
    if redis.call('getbit', KEYS[1], ARGV[i]) == 0 then return 0 end
end
return 1
```

**`close()`:** connection 닫기.

### 4-6. CuckooFilter (우선순위 6)

**알고리즘:** Cuckoo Hashing with fingerprint relocation

```
{name}:config   → Hash { capacity, bucketSize, count }
{name}:buckets  → Hash { bucketIdx → fingerprint1,fingerprint2,... }  (CSV)
```

**핵심 Lua — `insert(element)`:**

```lua
-- 1. fingerprint = hash(element) mod 255 + 1  (0 제외)
-- 2. i1 = hash1(element) mod numBuckets
-- 3. i2 = i1 XOR hash(fingerprint) mod numBuckets
-- 4. 빈 슬롯 있으면 삽입, 없으면 kick-out relocation (최대 maxIterations 반복)
-- 5. maxIterations 초과: 실패 반환
```

**핵심 Lua — `contains(element)`:**

```lua
-- fingerprint, i1, i2 계산 후 두 버킷 중 하나에 fingerprint 존재 여부 확인
```

**핵심 Lua — `delete(element)`:**

```lua
-- fingerprint, i1, i2 계산 후 두 버킷 중 하나에서 fingerprint 제거
```

**`close()`:** connection 닫기.

---

## 5. 팩토리 (RedisClient 확장함수)

```kotlin
// LettuceFeatures.kt
fun RedisClient.fairLock(
    name: String,
    options: FairLockOptions = FairLockOptions.Default,
): LettuceFairLock  // AutoCloseable

fun RedisClient.suspendFairLock(
    name: String,
    options: FairLockOptions = FairLockOptions.Default,
): LettuceSuspendFairLock  // AutoCloseable

fun RedisClient.rateLimiter(
    name: String,
    config: RateLimiterConfig,
): LettuceRateLimiter  // AutoCloseable

fun RedisClient.suspendRateLimiter(
    name: String,
    config: RateLimiterConfig,
): LettuceSuspendRateLimiter  // AutoCloseable

fun <V: Any> RedisClient.nearCacheMap(
    name: String,
    codec: RedisCodec<String, V> = LettuceBinaryCodecs.lz4Fory(),
    options: NearCacheMapOptions = NearCacheMapOptions.Default,
): LettuceNearCacheMap<V>  // AutoCloseable

fun <V: Any> RedisClient.hyperLogLog(
    name: String,
    codec: RedisCodec<String, V> = LettuceBinaryCodecs.lz4Fory(),
): LettuceHyperLogLog<V>  // AutoCloseable

fun RedisClient.bloomFilter(
    name: String,
    options: BloomFilterOptions = BloomFilterOptions.Default,
): LettuceBloomFilter  // AutoCloseable

fun RedisClient.cuckooFilter(
    name: String,
    options: CuckooFilterOptions = CuckooFilterOptions.Default,
): LettuceCuckooFilter  // AutoCloseable
```

> **연결 소유권:** 팩토리가 내부적으로 connection을 생성하며 호출자는 `close()` 책임을 진다. `use { }` 블록 권장.
>
> **NearCacheMap 연결 분리 주의:**
> Redis는 SUBSCRIBE 상태에서 일반 명령 실행이 제한되므로, `LettuceNearCacheMap`은 반드시 두 연결을 분리한다:
> - **데이터 연결:** `StatefulRedisConnection<String, V>` — GET/SET/DEL 등 일반 명령용
> - **Pub/Sub 연결:** `StatefulRedisPubSubConnection<String, String>` — SUBSCRIBE/PUBLISH 전용
>
> FairLock/RateLimiter는 Pub/Sub가 없거나(RateLimiter) 별도 pub 연결을 추가(FairLock unlock 시 PUBLISH)하므로 단일 연결로 충분.

---

## 6. 테스트 전략

- Testcontainers Redis (`RedisServer.Launcher.redis`) 기반 통합 테스트
- 각 기능별 `Abstract*Test` → 동기/비동기 공통 테스트 케이스
- **FairLock:**
    - FIFO 순서 검증 (Virtual Thread 10개 동시 경쟁)
    - 재진입(reentrant) lock/unlock 검증
    - 타임아웃 시 큐 정리 검증
    - suspend 버전: 코루틴 취소 시 큐 제거 검증
- **RateLimiter:**
    - rate 초과 시 거부 검증
    - 윈도우 슬라이딩 후 permit 복구 검증
    - PER_CLIENT: 클라이언트별 독립 카운트 검증
    - suspend 버전: 취소 전파 검증
- **NearCacheMap:**
    - SyncStrategy별 invalidation 전파 검증 (2-인스턴스)
    - LOAD ReconnectionStrategy: 재연결 후 변경된 키만 무효화 검증
    - storeCacheMiss: null 결과 캐싱 후 Redis 조회 미발생 검증
    - suspend 버전: 재연결 경쟁조건(LOAD) 검증
- **BloomFilter:** false positive rate 검증 (expectedInsertions, falseProbability 조합)
- **CuckooFilter:** insert/contains/delete 기본 동작 + full bucket 시 relocation 검증
- **공통:** suspend API의 `Dispatchers.IO` 경계 격리, `kotlinx.coroutines.test` 사용

---

## 7. Redisson 일반 vs Pro 분석 (NearCacheMap 관련)

| 기능                         | 일반(오픈소스)   | Pro       | 우리 구현      |
|----------------------------|------------|-----------|------------|
| SyncStrategy: INVALIDATE   | ✅          | ✅         | ✅ Pub/Sub  |
| SyncStrategy: UPDATE       | ✅          | ✅         | ✅ Pub/Sub  |
| ReconnectionStrategy: LOAD | ✅          | ✅ (더 안정적) | ✅ ZSet 기반  |
| EvictionPolicy (LRU/LFU 등) | ✅          | ✅         | ✅ Caffeine |
| CacheProvider: CAFFEINE    | ✅          | ✅         | ✅ 기본값      |
| storeCacheMiss             | ✅          | ✅         | ✅ sentinel |
| ExpirationEventPolicy      | ✅          | ✅         | ✅          |
| 클러스터 전체 Pub/Sub            | ❌ 단일 슬롯 한계 | ✅         | 추후 검토      |
| Write-behind 영속성           | ❌          | ✅         | 범위 외       |

**결론:** 단일 노드/Sentinel 환경에서 일반 버전 기능으로 Pro 수준 커버 가능. 클러스터 환경의 Pub/Sub 제약은 추후 `RedisClusterClient` 기반 확장으로 대응.

---

## 8. 기존 모듈 호환성

`bluetape4k-projects/infra/cache-lettuce`의 `LettuceNearCache`와의 관계:

- **공존:** `redis-lettuce`의 `LettuceNearCacheMap`은 새 모듈. 기존 `cache-lettuce`는 유지.
- **마이그레이션:** 추후 `NearCacheMapOptions`로 전환 시 필드 매핑으로 `copy()` 수준 변환 가능.
- **차이점:** 기존은 CLIENT TRACKING(RESP3), 신규는 Pub/Sub 기반 — 의도적 설계 변경.
