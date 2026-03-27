# NearCacheMap (Pub/Sub 기반 2-tier 캐시) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pub/Sub 기반 분산 무효화를 지원하는 2-tier 캐시 (Caffeine L1 + Redis L2) 구현

**Architecture:** SyncStrategy(NONE/INVALIDATE/UPDATE)로 다중 인스턴스 간 Pub/Sub 동기화. ReconnectionStrategy(NONE/CLEAR/LOAD)로 재연결 시 로컬 캐시 처리. storeCacheMiss로 Redis miss 결과 로컬 캐싱.

**Tech Stack:** Lettuce 6.x, Caffeine 3.x, Kotlin Coroutines (suspend), Testcontainers (Redis)

---

## 파일 구조

```text
infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/cache/
  NearCacheMapOptions.kt           (enum + data class)
  LettuceNearCacheMap.kt           (Sync)
  LettuceSuspendNearCacheMap.kt    (Coroutine)

infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/cache/
  AbstractNearCacheMapTest.kt
  LettuceNearCacheMapTest.kt
  LettuceSuspendNearCacheMapTest.kt
```

**연결 구조 (두 연결 분리):**
- `dataConnection: StatefulRedisConnection<String, V>` — 데이터 읽기/쓰기 (Codec 기반)
- `pubSubConnection: StatefulRedisPubSubConnection<String, String>` — 무효화 Pub/Sub (SyncStrategy != NONE 시에만)

**키 스키마:**
- 데이터: `{cacheName}:{key}`
- 토픽: `{cacheName}:topic`
- 무효화 로그: `{cacheName}:invalidation-log` (ReconnectionStrategy.LOAD 전용)

**UPDATE 전략 구현 방식:** publish `"UPDATE:{key}"` → 수신 인스턴스가 Redis GET 후 로컬 갱신 (codec을 Pub/Sub 메시지에 포함하는 복잡도 회피)

---

## Task 1: NearCacheMapOptions

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/cache/NearCacheMapOptions.kt`

- [ ] **Step 1: NearCacheMapOptions.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.cache

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class SyncStrategy { NONE, INVALIDATE, UPDATE }
enum class ReconnectionStrategy { NONE, CLEAR, LOAD }
enum class StoreMode { LOCALCACHE_ONLY, LOCALCACHE_REDIS }
enum class EvictionPolicy { NONE, LRU, LFU, SOFT, WEAK }
enum class ExpirationEventPolicy {
    DONT_SUBSCRIBE,
    SUBSCRIBE_WITH_KEYEVENT_PATTERN,
    SUBSCRIBE_WITH_KEYSPACE_CHANNEL,
}

/**
 * NearCacheMap 설정 옵션.
 *
 * @param cacheName 캐시 이름 (Redis 키 prefix로 사용, ':' 포함 불가)
 * @param maxLocalSize 로컬 Caffeine 캐시 최대 항목 수
 * @param localTtl 로컬 캐시 항목 TTL (expireAfterWrite)
 * @param localExpireAfterAccess 로컬 캐시 접근 후 TTL (null이면 비활성)
 * @param redisTtl Redis 항목 TTL (null이면 무제한)
 * @param recordStats Caffeine 통계 기록 여부
 * @param syncStrategy 다중 인스턴스 간 동기화 전략
 * @param reconnectionStrategy Redis 재연결 시 로컬 캐시 처리 방식
 * @param storeMode 로컬 전용 vs 로컬+Redis
 * @param evictionPolicy 로컬 캐시 제거 정책 (SOFT/WEAK는 Caffeine 참조 방식, 나머지는 TinyLFU)
 * @param storeCacheMiss Redis miss 결과를 로컬에 캐싱할지 여부
 * @param expirationEventPolicy Redis keyspace 만료 이벤트 구독 방식
 */
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

- [ ] **Step 2: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/cache/NearCacheMapOptions.kt
git commit -m "feat: NearCacheMapOptions (enum + data class) 추가"
```

---

## Task 2: LettuceNearCacheMap Core

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/cache/LettuceNearCacheMap.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/cache/AbstractNearCacheMapTest.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/cache/LettuceNearCacheMapTest.kt`

- [ ] **Step 1: 실패 테스트 — AbstractNearCacheMapTest.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.cache

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

abstract class AbstractNearCacheMapTest: AbstractRedisLettuceTest() {

    protected abstract fun createCache(options: NearCacheMapOptions): LettuceNearCacheMap<String>
    protected lateinit var cache: LettuceNearCacheMap<String>

    @BeforeEach
    fun setup() {
        cache = createCache(
            NearCacheMapOptions(cacheName = "test-${randomName()}", syncStrategy = SyncStrategy.NONE)
        )
    }

    @AfterEach
    fun teardown() = cache.close()

    @Test
    fun `NearCacheMapOptions - cacheName에 콜론 포함 시 예외`() {
        assertThrows<IllegalArgumentException> { NearCacheMapOptions(cacheName = "bad:name") }
    }

    @Test
    fun `get - 없는 키는 null`() {
        cache.get("missing").shouldBeNull()
    }

    @Test
    fun `put - get 로컬 히트`() {
        cache.put("k1", "v1")
        cache.get("k1") shouldBeEqualTo "v1"
    }

    @Test
    fun `put - Redis에 저장됨 (clearLocal 후 재조회)`() {
        cache.put("k1", "v1")
        cache.clearLocal()
        cache.get("k1") shouldBeEqualTo "v1"
    }

    @Test
    fun `remove - 로컬 + Redis에서 삭제`() {
        cache.put("k1", "v1")
        cache.remove("k1")
        cache.get("k1").shouldBeNull()
        cache.clearLocal()
        cache.get("k1").shouldBeNull()
    }

    @Test
    fun `containsKey - 존재하면 true`() {
        cache.put("k1", "v1")
        cache.containsKey("k1").shouldBeTrue()
        cache.containsKey("missing").shouldBeFalse()
    }

    @Test
    fun `putIfAbsent - 없을 때만 저장, 있으면 기존 값 반환`() {
        cache.putIfAbsent("k1", "v1").shouldBeNull()
        cache.putIfAbsent("k1", "v2") shouldBeEqualTo "v1"
        cache.get("k1") shouldBeEqualTo "v1"
    }

    @Test
    fun `replace - 있을 때만 교체`() {
        cache.replace("missing", "v1").shouldBeFalse()
        cache.put("k1", "v1")
        cache.replace("k1", "v2").shouldBeTrue()
        cache.get("k1") shouldBeEqualTo "v2"
    }

    @Test
    fun `replace CAS - oldValue 일치 시만 교체`() {
        cache.put("k1", "v1")
        cache.replace("k1", "wrong", "v2").shouldBeFalse()
        cache.replace("k1", "v1", "v2").shouldBeTrue()
        cache.get("k1") shouldBeEqualTo "v2"
    }

    @Test
    fun `getAll - 여러 키 일괄 조회`() {
        cache.put("k1", "v1")
        cache.put("k2", "v2")
        val result = cache.getAll(setOf("k1", "k2", "k3"))
        result["k1"] shouldBeEqualTo "v1"
        result["k2"] shouldBeEqualTo "v2"
        result.containsKey("k3").shouldBeFalse()
    }

    @Test
    fun `putAll - 여러 키 일괄 저장`() {
        cache.putAll(mapOf("k1" to "v1", "k2" to "v2"))
        cache.get("k1") shouldBeEqualTo "v1"
        cache.get("k2") shouldBeEqualTo "v2"
    }

    @Test
    fun `getAndRemove - 조회 후 삭제`() {
        cache.put("k1", "v1")
        cache.getAndRemove("k1") shouldBeEqualTo "v1"
        cache.get("k1").shouldBeNull()
    }

    @Test
    fun `getAndReplace - 조회 후 교체`() {
        cache.put("k1", "v1")
        cache.getAndReplace("k1", "v2") shouldBeEqualTo "v1"
        cache.get("k1") shouldBeEqualTo "v2"
    }

    @Test
    fun `storeCacheMiss - Redis miss를 로컬 센티넬로 캐싱`() {
        val missCache = createCache(
            NearCacheMapOptions(
                cacheName = "miss-${randomName()}",
                syncStrategy = SyncStrategy.NONE,
                storeCacheMiss = true,
            )
        )
        missCache.use {
            it.get("missing").shouldBeNull()
            it.get("missing").shouldBeNull()  // 두 번째: 센티넬 로컬 히트
        }
    }

    @Test
    fun `clearAll - 로컬 + Redis 모두 삭제`() {
        cache.put("k1", "v1")
        cache.put("k2", "v2")
        cache.clearAll()
        cache.get("k1").shouldBeNull()
        cache.get("k2").shouldBeNull()
    }
}
```

- [ ] **Step 2: LettuceNearCacheMap.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pub/Sub 기반 NearCacheMap — Caffeine(L1) + Redis(L2) 2-tier 캐시 (Sync).
 *
 * SyncStrategy에 따라 다중 인스턴스 간 로컬 캐시 동기화:
 * - NONE: Pub/Sub 없음 (단일 인스턴스 전용)
 * - INVALIDATE: write 시 key publish → 수신 인스턴스 로컬 무효화
 * - UPDATE: write 시 "UPDATE:key" publish → 수신 인스턴스 Redis 재조회 후 로컬 갱신
 */
class LettuceNearCacheMap<V: Any>(
    private val redisClient: RedisClient,
    private val codec: RedisCodec<String, V>,
    val options: NearCacheMapOptions = NearCacheMapOptions.Default,
): AutoCloseable {

    companion object: KLogging() {
        private object NullSentinel  // storeCacheMiss 전용 센티넬 — null과 cache miss를 구분

        private const val LOG_TTL_MS = 660_000L  // invalidation-log ZSet 유지 시간 (11분)

        // 원자적 CAS: oldValue 일치 시에만 newValue로 교체, KEEPTTL 유지
        private const val COMPARE_AND_SET_SCRIPT = """
local current = redis.call('GET', KEYS[1])
if current == false or current ~= ARGV[1] then return 0 end
redis.call('SET', KEYS[1], ARGV[2], 'XX', 'KEEPTTL')
return 1"""
    }

    val cacheName: String get() = options.cacheName
    private val closed = atomic(false)

    private val topicKey = "${options.cacheName}:topic"
    private val invalidationLogKey = "${options.cacheName}:invalidation-log"

    private val setArgsPx: SetArgs? = options.redisTtl?.let { SetArgs.Builder.px(it.inWholeMilliseconds) }
    private val setArgsNxPx: SetArgs? = options.redisTtl?.let { SetArgs.Builder.nx().px(it.inWholeMilliseconds) }

    // Caffeine 로컬 캐시 (Any = V | NullSentinel)
    private val localCache: Cache<String, Any> = buildLocalCache()

    // 데이터 연결 — Codec 기반 직렬화
    private val dataConnection: StatefulRedisConnection<String, V> = redisClient.connect(codec)
    private val commands: RedisCommands<String, V> = dataConnection.sync()

    // Pub/Sub 연결 — String codec (무효화 메시지는 키 이름)
    private val pubSubConnection: StatefulRedisPubSubConnection<String, String>? =
        if (options.syncStrategy != SyncStrategy.NONE) redisClient.connectPubSub(StringCodec.UTF8) else null

    private val firstSubscribe = AtomicBoolean(true)

    private val invalidationListener = object: RedisPubSubAdapter<String, String>() {
        override fun message(channel: String, message: String) {
            if (channel != topicKey) return
            when {
                message.startsWith("UPDATE:") -> {
                    val key = message.removePrefix("UPDATE:")
                    if (options.storeMode == StoreMode.LOCALCACHE_REDIS) {
                        // 리스너는 Lettuce Netty I/O 스레드에서 실행 — blocking sync() 금지
                        // async GET + whenComplete 로 로컬 캐시 갱신 (데드락 방지)
                        dataConnection.async().get(redisKey(key)).whenComplete { value, _ ->
                            if (value != null) localCache.put(key, value as Any)
                            else localCache.invalidate(key)
                        }
                    }
                }
                else -> localCache.invalidate(message)
            }
            log.debug { "NearCacheMap 무효화 수신: cacheName=${options.cacheName}, key=$message" }
        }

        override fun subscribed(channel: String, count: Long) {
            // 최초 구독 이후 subscribed() 호출 = 재연결
            if (!firstSubscribe.compareAndSet(true, false)) {
                handleReconnect()
            }
        }
    }

    init {
        if (pubSubConnection != null) {
            pubSubConnection.addListener(invalidationListener)
            pubSubConnection.sync().subscribe(topicKey)
        }
    }

    private fun buildLocalCache(): Cache<String, Any> {
        val builder = Caffeine.newBuilder()
            .maximumSize(options.maxLocalSize)
            .expireAfterWrite(options.localTtl.toJavaDuration())
        options.localExpireAfterAccess?.let { builder.expireAfterAccess(it.toJavaDuration()) }
        if (options.recordStats) builder.recordStats()
        return when (options.evictionPolicy) {
            EvictionPolicy.SOFT -> builder.softValues().build()
            EvictionPolicy.WEAK -> builder.weakValues().build()
            else -> builder.build()  // NONE/LRU/LFU → Caffeine Window TinyLFU (default)
        }
    }

    private fun redisKey(key: String) = "${options.cacheName}:$key"

    private fun handleReconnect() {
        when (options.reconnectionStrategy) {
            ReconnectionStrategy.NONE -> Unit
            ReconnectionStrategy.CLEAR, ReconnectionStrategy.LOAD -> {
                // ⚠️ LOAD 전략 미지원: 현재 구현에서는 CLEAR와 동일하게 전체 삭제
                // invalidation-log ZSet 기반 선택적 키 복구는 향후 구현 예정 (TODO)
                log.debug { "NearCacheMap 재연결: 로컬 캐시 전체 삭제 (cacheName=${options.cacheName})" }
                localCache.invalidateAll()
            }
        }
    }

    fun get(key: String): V? {
        val local = localCache.getIfPresent(key)
        if (local === NullSentinel) return null         // storeCacheMiss 센티넬 히트
        @Suppress("UNCHECKED_CAST")
        if (local != null) return local as V            // 로컬 캐시 히트

        if (options.storeMode == StoreMode.LOCALCACHE_ONLY) return null

        val value = commands.get(redisKey(key))
        if (value != null) {
            localCache.put(key, value as Any)
        } else if (options.storeCacheMiss) {
            localCache.put(key, NullSentinel)
        }
        return value
    }

    fun getAll(keys: Set<String>): Map<String, V> {
        val result = mutableMapOf<String, V>()
        val missed = mutableListOf<String>()
        for (key in keys) {
            val local = localCache.getIfPresent(key)
            if (local === NullSentinel) continue
            @Suppress("UNCHECKED_CAST")
            if (local != null) { result[key] = local as V; continue }
            missed += key
        }
        if (missed.isNotEmpty() && options.storeMode == StoreMode.LOCALCACHE_REDIS) {
            val async: RedisAsyncCommands<String, V> = dataConnection.async()
            val futures = missed.associateWith { async.get(redisKey(it)) }
            dataConnection.flushCommands()
            futures.forEach { (key, future) ->
                val value = future.get()
                if (value != null) { result[key] = value; localCache.put(key, value as Any) }
                else if (options.storeCacheMiss) localCache.put(key, NullSentinel)
            }
        }
        return result
    }

    fun put(key: String, value: V) {
        localCache.put(key, value as Any)
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) setRedis(key, value)
        publishChange(key, isUpdate = true)
        recordInvalidation(key)
    }

    fun putAll(entries: Map<String, V>) {
        if (entries.isEmpty()) return
        entries.forEach { (k, v) -> localCache.put(k, v as Any) }
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) setRedisBulk(entries)
        entries.keys.forEach { publishChange(it, isUpdate = true) }
        if (options.reconnectionStrategy == ReconnectionStrategy.LOAD) entries.keys.forEach { recordInvalidation(it) }
    }

    fun putIfAbsent(key: String, value: V): V? {
        val existing = get(key)
        if (existing != null) return existing
        if (options.storeMode == StoreMode.LOCALCACHE_ONLY) {
            localCache.put(key, value as Any)
            publishChange(key, isUpdate = false)
            return null
        }
        val rKey = redisKey(key)
        val status = if (setArgsNxPx != null) commands.set(rKey, value, setArgsNxPx)
                     else commands.set(rKey, value, SetArgs.Builder.nx())
        return if (status == "OK") {
            localCache.put(key, value as Any)
            publishChange(key, isUpdate = false)
            recordInvalidation(key)
            null
        } else commands.get(rKey)
    }

    fun remove(key: String) {
        localCache.invalidate(key)
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) commands.del(redisKey(key))
        publishInvalidate(key)
        recordInvalidation(key)
    }

    fun removeAll(keys: Set<String>) {
        keys.forEach { localCache.invalidate(it) }
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) {
            commands.del(*keys.map { redisKey(it) }.toTypedArray())
        }
        keys.forEach { publishInvalidate(it) }
        if (options.reconnectionStrategy == ReconnectionStrategy.LOAD) keys.forEach { recordInvalidation(it) }
    }

    fun replace(key: String, value: V): Boolean {
        if (options.storeMode == StoreMode.LOCALCACHE_ONLY) {
            val local = localCache.getIfPresent(key)
            if (local == null || local === NullSentinel) return false
            localCache.put(key, value as Any)
            publishChange(key, isUpdate = true)
            return true
        }
        commands.get(redisKey(key)) ?: return false
        val ok = commands.set(redisKey(key), value, SetArgs.Builder.xx()) != null
        if (ok) { localCache.put(key, value as Any); publishChange(key, isUpdate = true); recordInvalidation(key) }
        return ok
    }

    fun replace(key: String, oldValue: V, newValue: V): Boolean {
        val replaced = commands.eval<Long>(
            COMPARE_AND_SET_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(redisKey(key)), oldValue, newValue
        ) == 1L
        if (replaced) { localCache.put(key, newValue as Any); publishChange(key, isUpdate = true); recordInvalidation(key) }
        return replaced
    }

    fun getAndRemove(key: String): V? {
        val value = get(key) ?: return null
        remove(key)
        return value
    }

    fun getAndReplace(key: String, value: V): V? {
        val existing = get(key) ?: return null
        put(key, value)
        return existing
    }

    fun containsKey(key: String): Boolean {
        val local = localCache.getIfPresent(key)
        if (local === NullSentinel) return false  // storeCacheMiss 센티넬 = 존재하지 않음
        if (local != null) return true
        if (options.storeMode == StoreMode.LOCALCACHE_ONLY) return false
        return commands.exists(redisKey(key)) > 0
    }

    fun clearLocal() = localCache.invalidateAll()

    fun clearAll() {
        clearLocal()
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) runCatching { clearRedis() }
    }

    fun localCacheSize(): Long = localCache.estimatedSize()

    override fun close() {
        if (closed.compareAndSet(expect = false, update = true)) {
            runCatching { pubSubConnection?.sync()?.unsubscribe(topicKey) }
            runCatching { pubSubConnection?.removeListener(invalidationListener) }
            runCatching { pubSubConnection?.close() }
            runCatching { dataConnection.close() }
            runCatching { localCache.cleanUp() }
            log.debug { "LettuceNearCacheMap [${options.cacheName}] closed" }
        }
    }

    private fun setRedis(key: String, value: V) {
        val rKey = redisKey(key)
        if (setArgsPx != null) commands.set(rKey, value, setArgsPx) else commands.set(rKey, value)
    }

    private fun setRedisBulk(entries: Map<String, V>) {
        val async: RedisAsyncCommands<String, V> = dataConnection.async()
        entries.forEach { (key, value) ->
            if (setArgsPx != null) async.set(redisKey(key), value, setArgsPx)
            else async.set(redisKey(key), value)
        }
        dataConnection.flushCommands()
    }

    private fun publishChange(key: String, isUpdate: Boolean) {
        when (options.syncStrategy) {
            SyncStrategy.NONE -> Unit
            SyncStrategy.INVALIDATE -> publishInvalidate(key)
            SyncStrategy.UPDATE -> {
                if (isUpdate) pubSubConnection?.async()?.publish(topicKey, "UPDATE:$key")
                else publishInvalidate(key)
            }
        }
    }

    private fun publishInvalidate(key: String) {
        pubSubConnection?.async()?.publish(topicKey, key)
    }

    private fun recordInvalidation(key: String) {
        if (options.reconnectionStrategy == ReconnectionStrategy.LOAD) {
            val now = System.currentTimeMillis().toDouble()
            dataConnection.async().zadd(invalidationLogKey, now, key)
            dataConnection.async().pexpire(invalidationLogKey, LOG_TTL_MS)
        }
    }

    private fun clearRedis() {
        val pattern = "${options.cacheName}:*"
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val result: KeyScanCursor<String> =
                commands.scan(cursor, ScanArgs.Builder.matches(pattern).limit(100L))
            // invalidation-log는 ReconnectionStrategy.LOAD 복구에 필요 — 삭제 제외
            val toDelete = result.keys.filter { it != invalidationLogKey }
            if (toDelete.isNotEmpty()) commands.del(*toDelete.toTypedArray())
            cursor = result
        } while (!result.isFinished)
    }
}
```

- [ ] **Step 3: LettuceNearCacheMapTest.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.cache

import io.lettuce.core.codec.StringCodec
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class LettuceNearCacheMapTest: AbstractNearCacheMapTest() {
    override fun createCache(options: NearCacheMapOptions): LettuceNearCacheMap<String> =
        LettuceNearCacheMap(client, StringCodec.UTF8, options)

    @Test
    fun `INVALIDATE - 인스턴스 A의 put이 인스턴스 B의 로컬 캐시를 무효화`() {
        val name = "sync-${randomName()}"
        val opts = NearCacheMapOptions(cacheName = name, syncStrategy = SyncStrategy.INVALIDATE)
        val cacheA = LettuceNearCacheMap(client, StringCodec.UTF8, opts)
        val cacheB = LettuceNearCacheMap(client, StringCodec.UTF8, opts)

        cacheA.use { a ->
            cacheB.use { b ->
                a.put("k1", "v1")
                b.get("k1")  // B가 Redis에서 로드 → 로컬 캐싱
                b.localCacheSize() shouldBeEqualTo 1L

                Thread.sleep(50)
                a.put("k1", "v2")   // publish "k1" → B 무효화
                Thread.sleep(150)   // Pub/Sub 전파 대기

                b.localCacheSize() shouldBeEqualTo 0L  // B 로컬에서 제거됨
                b.get("k1") shouldBeEqualTo "v2"       // Redis에서 최신 값 로드
            }
        }
    }

    @Test
    fun `UPDATE - 인스턴스 A의 put이 인스턴스 B의 로컬 캐시를 갱신`() {
        val name = "update-${randomName()}"
        val opts = NearCacheMapOptions(cacheName = name, syncStrategy = SyncStrategy.UPDATE)
        val cacheA = LettuceNearCacheMap(client, StringCodec.UTF8, opts)
        val cacheB = LettuceNearCacheMap(client, StringCodec.UTF8, opts)

        cacheA.use { a ->
            cacheB.use { b ->
                a.put("k1", "v1")
                b.get("k1")  // B 로컬 캐싱

                Thread.sleep(50)
                a.put("k1", "v2")  // publish "UPDATE:k1" → B가 Redis GET
                Thread.sleep(200)  // B 갱신 완료 대기

                b.localCacheSize() shouldBeEqualTo 1L  // B 로컬에 v2 갱신됨
                b.get("k1") shouldBeEqualTo "v2"
            }
        }
    }
}
```

- [ ] **Step 4: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.cache.LettuceNearCacheMapTest" 2>&1 | tail -20
```

기대: `BUILD SUCCESSFUL`, 전체 PASS

- [ ] **Step 5: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/cache/LettuceNearCacheMap.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/cache/AbstractNearCacheMapTest.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/cache/LettuceNearCacheMapTest.kt
git commit -m "feat: LettuceNearCacheMap (Caffeine+Redis+Pub/Sub, Sync) 구현"
```

---

## Task 3: LettuceSuspendNearCacheMap (Coroutine)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/cache/LettuceSuspendNearCacheMap.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/cache/LettuceSuspendNearCacheMapTest.kt`

> LettuceNearCacheMap과 동일한 로직. 차이:
> - 모든 Redis 접근은 `asyncCommands.xxx().await()`
> - Pub/Sub 리스너의 UPDATE 처리: Netty I/O 스레드이므로 `sync()` 금지 → `async().get().whenComplete { }` 사용
> - `publishChange`/`publishInvalidate`는 fire-and-forget (await 없음)

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package io.bluetape4k.redis.lettuce.cache

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LettuceSuspendNearCacheMapTest: AbstractRedisLettuceTest() {

    private lateinit var cache: LettuceSuspendNearCacheMap<String>

    @BeforeEach
    fun setup() {
        cache = LettuceSuspendNearCacheMap(
            client, StringCodec.UTF8,
            NearCacheMapOptions(cacheName = "suspend-${randomName()}", syncStrategy = SyncStrategy.NONE)
        )
    }

    @AfterEach
    fun teardown() = cache.close()

    @Test
    fun `get - 없는 키는 null`() = runTest { cache.get("missing").shouldBeNull() }

    @Test
    fun `put - get 로컬 히트`() = runTest {
        cache.put("k1", "v1")
        cache.get("k1") shouldBeEqualTo "v1"
    }

    @Test
    fun `put - Redis에 저장됨 (clearLocal 후 재조회)`() = runTest {
        cache.put("k1", "v1")
        cache.clearLocal()
        cache.get("k1") shouldBeEqualTo "v1"
    }

    @Test
    fun `remove - 삭제`() = runTest {
        cache.put("k1", "v1")
        cache.remove("k1")
        cache.get("k1").shouldBeNull()
    }

    @Test
    fun `containsKey`() = runTest {
        cache.put("k1", "v1")
        cache.containsKey("k1").shouldBeTrue()
        cache.containsKey("missing").shouldBeFalse()
    }

    @Test
    fun `replace CAS`() = runTest {
        cache.put("k1", "v1")
        cache.replace("k1", "wrong", "v2").shouldBeFalse()
        cache.replace("k1", "v1", "v2").shouldBeTrue()
        cache.get("k1") shouldBeEqualTo "v2"
    }

    @Test
    fun `putAll - getAll`() = runTest {
        cache.putAll(mapOf("k1" to "v1", "k2" to "v2"))
        val result = cache.getAll(setOf("k1", "k2", "k3"))
        result["k1"] shouldBeEqualTo "v1"
        result["k2"] shouldBeEqualTo "v2"
        result.containsKey("k3").shouldBeFalse()
    }
}
```

- [ ] **Step 2: LettuceSuspendNearCacheMap.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.bluetape4k.logging.KLoggingChannel
import io.bluetape4k.logging.debug
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.future.await
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pub/Sub 기반 NearCacheMap — Caffeine(L1) + Redis(L2) 2-tier 캐시 (Coroutine/Suspend).
 */
class LettuceSuspendNearCacheMap<V: Any>(
    private val redisClient: RedisClient,
    private val codec: RedisCodec<String, V>,
    val options: NearCacheMapOptions = NearCacheMapOptions.Default,
): AutoCloseable {

    companion object: KLoggingChannel() {
        private object NullSentinel
        private const val LOG_TTL_MS = 660_000L
        private const val COMPARE_AND_SET_SCRIPT = """
local current = redis.call('GET', KEYS[1])
if current == false or current ~= ARGV[1] then return 0 end
redis.call('SET', KEYS[1], ARGV[2], 'XX', 'KEEPTTL')
return 1"""
    }

    val cacheName: String get() = options.cacheName
    private val closed = atomic(false)

    private val topicKey = "${options.cacheName}:topic"
    private val invalidationLogKey = "${options.cacheName}:invalidation-log"

    private val setArgsPx: SetArgs? = options.redisTtl?.let { SetArgs.Builder.px(it.inWholeMilliseconds) }
    private val setArgsNxPx: SetArgs? = options.redisTtl?.let { SetArgs.Builder.nx().px(it.inWholeMilliseconds) }

    private val localCache: Cache<String, Any> = buildLocalCache()
    private val dataConnection: StatefulRedisConnection<String, V> = redisClient.connect(codec)
    private val asyncCommands: RedisAsyncCommands<String, V> get() = dataConnection.async()

    private val pubSubConnection: StatefulRedisPubSubConnection<String, String>? =
        if (options.syncStrategy != SyncStrategy.NONE) redisClient.connectPubSub(StringCodec.UTF8) else null

    private val firstSubscribe = AtomicBoolean(true)

    private val invalidationListener = object: RedisPubSubAdapter<String, String>() {
        override fun message(channel: String, message: String) {
            if (channel != topicKey) return
            when {
                message.startsWith("UPDATE:") -> {
                    val key = message.removePrefix("UPDATE:")
                    if (options.storeMode == StoreMode.LOCALCACHE_REDIS) {
                        // 리스너는 Lettuce Netty I/O 스레드 — blocking sync() 금지
                        dataConnection.async().get(redisKey(key)).whenComplete { value, _ ->
                            if (value != null) localCache.put(key, value as Any)
                            else localCache.invalidate(key)
                        }
                    }
                }
                else -> localCache.invalidate(message)
            }
        }

        override fun subscribed(channel: String, count: Long) {
            if (!firstSubscribe.compareAndSet(true, false)) handleReconnect()
        }
    }

    init {
        if (pubSubConnection != null) {
            pubSubConnection.addListener(invalidationListener)
            pubSubConnection.sync().subscribe(topicKey)
        }
    }

    private fun buildLocalCache(): Cache<String, Any> {
        val builder = Caffeine.newBuilder()
            .maximumSize(options.maxLocalSize)
            .expireAfterWrite(options.localTtl.toJavaDuration())
        options.localExpireAfterAccess?.let { builder.expireAfterAccess(it.toJavaDuration()) }
        if (options.recordStats) builder.recordStats()
        return when (options.evictionPolicy) {
            EvictionPolicy.SOFT -> builder.softValues().build()
            EvictionPolicy.WEAK -> builder.weakValues().build()
            else -> builder.build()
        }
    }

    private fun redisKey(key: String) = "${options.cacheName}:$key"

    private fun handleReconnect() {
        when (options.reconnectionStrategy) {
            ReconnectionStrategy.NONE -> Unit
            else -> { log.debug { "재연결: 로컬 캐시 전체 삭제" }; localCache.invalidateAll() }
        }
    }

    suspend fun get(key: String): V? {
        val local = localCache.getIfPresent(key)
        if (local === NullSentinel) return null
        @Suppress("UNCHECKED_CAST")
        if (local != null) return local as V
        if (options.storeMode == StoreMode.LOCALCACHE_ONLY) return null
        val value = asyncCommands.get(redisKey(key)).await()
        if (value != null) localCache.put(key, value as Any)
        else if (options.storeCacheMiss) localCache.put(key, NullSentinel)
        return value
    }

    suspend fun getAll(keys: Set<String>): Map<String, V> {
        val result = mutableMapOf<String, V>()
        val missed = mutableListOf<String>()
        for (key in keys) {
            val local = localCache.getIfPresent(key)
            if (local === NullSentinel) continue
            @Suppress("UNCHECKED_CAST")
            if (local != null) { result[key] = local as V; continue }
            missed += key
        }
        if (missed.isNotEmpty() && options.storeMode == StoreMode.LOCALCACHE_REDIS) {
            val futures = missed.associateWith { asyncCommands.get(redisKey(it)) }
            futures.forEach { (key, f) ->
                val value = f.await()
                if (value != null) { result[key] = value; localCache.put(key, value as Any) }
                else if (options.storeCacheMiss) localCache.put(key, NullSentinel)
            }
        }
        return result
    }

    suspend fun put(key: String, value: V) {
        localCache.put(key, value as Any)
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) setRedis(key, value)
        publishChange(key, isUpdate = true)
        recordInvalidation(key)
    }

    suspend fun putAll(entries: Map<String, V>) {
        if (entries.isEmpty()) return
        entries.forEach { (k, v) -> localCache.put(k, v as Any) }
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) {
            val futures = entries.map { (k, v) ->
                if (setArgsPx != null) asyncCommands.set(redisKey(k), v, setArgsPx)
                else asyncCommands.set(redisKey(k), v)
            }
            futures.forEach { it.await() }
        }
        entries.keys.forEach { publishChange(it, isUpdate = true) }
        if (options.reconnectionStrategy == ReconnectionStrategy.LOAD) entries.keys.forEach { recordInvalidation(it) }
    }

    suspend fun putIfAbsent(key: String, value: V): V? {
        val existing = get(key)
        if (existing != null) return existing
        if (options.storeMode == StoreMode.LOCALCACHE_ONLY) {
            localCache.put(key, value as Any)
            publishChange(key, isUpdate = false)
            return null
        }
        val rKey = redisKey(key)
        val status = if (setArgsNxPx != null) asyncCommands.set(rKey, value, setArgsNxPx).await()
                     else asyncCommands.set(rKey, value, SetArgs.Builder.nx()).await()
        return if (status == "OK") {
            localCache.put(key, value as Any)
            publishChange(key, isUpdate = false)
            recordInvalidation(key)
            null
        } else asyncCommands.get(rKey).await()
    }

    suspend fun remove(key: String) {
        localCache.invalidate(key)
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) asyncCommands.del(redisKey(key)).await()
        publishInvalidate(key)
        recordInvalidation(key)
    }

    suspend fun removeAll(keys: Set<String>) {
        keys.forEach { localCache.invalidate(it) }
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) {
            asyncCommands.del(*keys.map { redisKey(it) }.toTypedArray()).await()
        }
        keys.forEach { publishInvalidate(it) }
        if (options.reconnectionStrategy == ReconnectionStrategy.LOAD) keys.forEach { recordInvalidation(it) }
    }

    suspend fun replace(key: String, value: V): Boolean {
        if (options.storeMode == StoreMode.LOCALCACHE_ONLY) {
            val local = localCache.getIfPresent(key)
            if (local == null || local === NullSentinel) return false
            localCache.put(key, value as Any)
            publishChange(key, isUpdate = true)
            return true
        }
        asyncCommands.get(redisKey(key)).await() ?: return false
        val ok = asyncCommands.set(redisKey(key), value, SetArgs.Builder.xx()).await() != null
        if (ok) { localCache.put(key, value as Any); publishChange(key, isUpdate = true); recordInvalidation(key) }
        return ok
    }

    suspend fun replace(key: String, oldValue: V, newValue: V): Boolean {
        val replaced = asyncCommands.eval<Long>(
            COMPARE_AND_SET_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(redisKey(key)), oldValue, newValue
        ).await() == 1L
        if (replaced) { localCache.put(key, newValue as Any); publishChange(key, isUpdate = true); recordInvalidation(key) }
        return replaced
    }

    suspend fun getAndRemove(key: String): V? {
        val value = get(key) ?: return null
        remove(key)
        return value
    }

    suspend fun getAndReplace(key: String, value: V): V? {
        val existing = get(key) ?: return null
        put(key, value)
        return existing
    }

    suspend fun containsKey(key: String): Boolean {
        val local = localCache.getIfPresent(key)
        if (local === NullSentinel) return false  // storeCacheMiss 센티넬 = 존재하지 않음
        if (local != null) return true
        if (options.storeMode == StoreMode.LOCALCACHE_ONLY) return false
        return asyncCommands.exists(redisKey(key)).await() > 0
    }

    fun clearLocal() = localCache.invalidateAll()

    suspend fun clearAll() {
        clearLocal()
        if (options.storeMode == StoreMode.LOCALCACHE_REDIS) runCatching { clearRedis() }
    }

    fun localCacheSize(): Long = localCache.estimatedSize()

    override fun close() {
        if (closed.compareAndSet(expect = false, update = true)) {
            runCatching { pubSubConnection?.sync()?.unsubscribe(topicKey) }
            runCatching { pubSubConnection?.removeListener(invalidationListener) }
            runCatching { pubSubConnection?.close() }
            runCatching { dataConnection.close() }
            runCatching { localCache.cleanUp() }
            log.debug { "LettuceSuspendNearCacheMap [${options.cacheName}] closed" }
        }
    }

    private suspend fun setRedis(key: String, value: V) {
        val rKey = redisKey(key)
        if (setArgsPx != null) asyncCommands.set(rKey, value, setArgsPx).await()
        else asyncCommands.set(rKey, value).await()
    }

    private fun publishChange(key: String, isUpdate: Boolean) {
        when (options.syncStrategy) {
            SyncStrategy.NONE -> Unit
            SyncStrategy.INVALIDATE -> publishInvalidate(key)
            SyncStrategy.UPDATE -> {
                if (isUpdate) pubSubConnection?.async()?.publish(topicKey, "UPDATE:$key")
                else publishInvalidate(key)
            }
        }
    }

    private fun publishInvalidate(key: String) {
        pubSubConnection?.async()?.publish(topicKey, key)
    }

    private fun recordInvalidation(key: String) {
        if (options.reconnectionStrategy == ReconnectionStrategy.LOAD) {
            val now = System.currentTimeMillis().toDouble()
            dataConnection.async().zadd(invalidationLogKey, now, key)
            dataConnection.async().pexpire(invalidationLogKey, LOG_TTL_MS)
        }
    }

    private fun clearRedis() {
        val syncCommands = dataConnection.sync()
        val pattern = "${options.cacheName}:*"
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val result: KeyScanCursor<String> =
                syncCommands.scan(cursor, ScanArgs.Builder.matches(pattern).limit(100L))
            val toDelete = result.keys.filter { it != invalidationLogKey }
            if (toDelete.isNotEmpty()) syncCommands.del(*toDelete.toTypedArray())
            cursor = result
        } while (!result.isFinished)
    }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.cache.LettuceSuspendNearCacheMapTest" 2>&1 | tail -20
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/cache/LettuceSuspendNearCacheMap.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/cache/LettuceSuspendNearCacheMapTest.kt
git commit -m "feat: LettuceSuspendNearCacheMap (Coroutine) 구현"
```

---

## Task 4: LettuceFeatures factory nearCacheMap 확장함수 추가

**Files:**
- Modify: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/LettuceFeatures.kt`

- [ ] **Step 1: nearCacheMap/suspendNearCacheMap 확장함수 추가**

```kotlin
// LettuceFeatures.kt 기존 파일에 아래 import + 함수 추가

import io.bluetape4k.redis.lettuce.cache.LettuceSuspendNearCacheMap
import io.bluetape4k.redis.lettuce.cache.LettuceNearCacheMap
import io.bluetape4k.redis.lettuce.cache.NearCacheMapOptions
import io.lettuce.core.codec.RedisCodec

/**
 * Pub/Sub 기반 NearCacheMap (Sync) 생성.
 * `name`이 cacheName으로 사용되며, `options`의 다른 설정은 유지됨.
 */
fun <V: Any> RedisClient.nearCacheMap(
    name: String,
    codec: RedisCodec<String, V> = LettuceBinaryCodecs.lz4Fory(),
    options: NearCacheMapOptions = NearCacheMapOptions.Default,
): LettuceNearCacheMap<V> = LettuceNearCacheMap(this, codec, options.copy(cacheName = name))

/**
 * Pub/Sub 기반 NearCacheMap (Coroutine/Suspend) 생성.
 */
fun <V: Any> RedisClient.suspendNearCacheMap(
    name: String,
    codec: RedisCodec<String, V> = LettuceBinaryCodecs.lz4Fory(),
    options: NearCacheMapOptions = NearCacheMapOptions.Default,
): LettuceSuspendNearCacheMap<V> = LettuceSuspendNearCacheMap(this, codec, options.copy(cacheName = name))
```

- [ ] **Step 2: 전체 캐시 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.cache.*" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/LettuceFeatures.kt
git commit -m "feat: LettuceFeatures nearCacheMap/suspendNearCacheMap factory 추가"
```
