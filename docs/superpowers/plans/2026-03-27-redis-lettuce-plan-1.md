# redis-lettuce Plan 1: 모듈 셋업 + FairLock + RateLimiter

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `bluetape4k-experimental/infra/redis-lettuce` 모듈을 새로 만들고, Reentrant FairLock(Sync/Suspend)과 Token Bucket RateLimiter(Sync/Suspend)를 Lettuce 기반 Kotlin API로 구현한다.

**Architecture:** Lua 스크립트로 Redis 원자성 보장, Sync 버전(Virtual Thread)과 Suspend 버전(Coroutine) 쌍으로 제공. FairLock은 Hash(재진입 카운터)+List(FIFO 큐)+ZSet(타임아웃)+Pub/Sub(알림) 구조. RateLimiter는 Token Bucket + ZSet(permit 만료 추적) 구조.

**Tech Stack:** Kotlin 2.x, Lettuce 7.x, kotlinx-coroutines, atomicfu, JUnit 5 + Kotest + Kluent, Testcontainers Redis

**스펙 참조:** `docs/superpowers/specs/2026-03-27-redis-lettuce-design.md`

---

## 파일 구조

```
infra/redis-lettuce/
  build.gradle.kts
  src/main/kotlin/io/bluetape4k/redis/lettuce/
    lock/
      BackOffPolicy.kt
      FairLockOptions.kt
      LettuceFairLock.kt              ← Sync, Reentrant (Thread 기반 소유자)
      LettuceSuspendFairLock.kt       ← Coroutine, Reentrant (AtomicLong 기반 소유자)
    rate/
      RateType.kt
      RateLimiterConfig.kt
      LettuceRateLimiter.kt           ← Sync, Token Bucket
      LettuceSuspendRateLimiter.kt    ← Coroutine, Token Bucket
    LettuceFeatures.kt                ← RedisClient 확장함수 팩토리
  src/test/kotlin/io/bluetape4k/redis/lettuce/
    LettuceTestSupport.kt
    LettuceFeaturesTest.kt
    lock/
      AbstractFairLockTest.kt
      LettuceFairLockTest.kt
      LettuceSuspendFairLockTest.kt
    rate/
      AbstractRateLimiterTest.kt
      LettuceRateLimiterTest.kt
      LettuceSuspendRateLimiterTest.kt
```

---

## Task 1: 모듈 생성 및 build.gradle.kts

**Files:**
- Create: `infra/redis-lettuce/build.gradle.kts`

- [ ] **Step 1: 디렉토리 생성**

```bash
cd ~/work/bluetape4k/bluetape4k-experimental
mkdir -p infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/{lock,rate}
mkdir -p infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/{lock,rate}
```

- [ ] **Step 2: build.gradle.kts 작성**

`infra/redis-lettuce/build.gradle.kts`:
```kotlin
dependencies {
    api(Libs.lettuce_core)
    api(Libs.kotlinx_atomicfu)

    api(Libs.bluetape4k_io)
    api(Libs.bluetape4k_redis)
    testImplementation(Libs.bluetape4k_junit5)

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    implementation(Libs.kotlinx_coroutines_reactive)
    testImplementation(Libs.kotlinx_coroutines_test)

    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
}
```

> 참조: `infra/cache-lettuce/build.gradle.kts`와 동일 패턴. kryo/fory/lz4는 Plan 2(NearCacheMap)에서 추가.

- [ ] **Step 3: settings.gradle.kts 확인 (자동 포함 여부)**

```bash
grep "includeModules" ~/work/bluetape4k/bluetape4k-experimental/settings.gradle.kts
```

기대 출력: `includeModules("infra", false, false)` — infra 하위 모듈 자동 포함, 추가 설정 불필요.

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew :infra:redis-lettuce:dependencies --configuration compileClasspath 2>&1 | tail -5
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add infra/redis-lettuce/build.gradle.kts
git commit -m "chore: redis-lettuce 모듈 초기 설정"
```

---

## Task 2: 공통 테스트 지원

**Files:**
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/LettuceTestSupport.kt`

- [ ] **Step 1: LettuceTestSupport.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce

import io.bluetape4k.LibraryName
import io.bluetape4k.codec.Base58
import io.bluetape4k.junit5.faker.Fakers
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.storage.RedisServer
import io.bluetape4k.utils.ShutdownQueue
import io.lettuce.core.RedisClient

object LettuceTestSupport: KLogging() {
    val redis: RedisServer by lazy { RedisServer.Launcher.redis }
    val client: RedisClient by lazy {
        LettuceClients.clientOf(redis.host, redis.port)
            .apply { ShutdownQueue.register { this.shutdown() } }
    }
    fun randomName(): String = "$LibraryName:${Base58.randomString(8)}"
    fun randomString(size: Int = 256): String = Fakers.fixedString(size)
}

abstract class AbstractRedisLettuceTest {
    companion object: KLogging()
    protected val client: RedisClient get() = LettuceTestSupport.client
    protected fun randomName(): String = LettuceTestSupport.randomName()
}
```

> 참조: `bluetape4k-projects/infra/lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/LettuceTestUtils.kt` 패턴

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew :infra:redis-lettuce:compileTestKotlin 2>&1 | tail -5
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/LettuceTestSupport.kt
git commit -m "test: 테스트 공통 지원 클래스 추가"
```

---

## Task 3: BackOffPolicy + FairLockOptions

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/lock/BackOffPolicy.kt`
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/lock/FairLockOptions.kt`

- [ ] **Step 1: BackOffPolicy.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.lock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 락 재시도 백오프 정책 */
sealed class BackOffPolicy {
    data class Fixed(val delay: Duration = 50.milliseconds): BackOffPolicy()
    data class Exponential(
        val initialDelay: Duration = 50.milliseconds,
        val maxDelay: Duration = 128.milliseconds,
        val multiplier: Int = 2,
    ): BackOffPolicy()
}
```

- [ ] **Step 2: FairLockOptions.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.lock

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [LettuceFairLock] / [LettuceSuspendFairLock] 설정
 */
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
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew :infra:redis-lettuce:compileKotlin 2>&1 | tail -5
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/lock/
git commit -m "feat: FairLockOptions, BackOffPolicy 추가"
```

---

## Task 4: LettuceFairLock (Sync, Reentrant)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/lock/LettuceFairLock.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/lock/AbstractFairLockTest.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/lock/LettuceFairLockTest.kt`

> **연결:** 데이터용 `StatefulRedisConnection<String, String>` + Pub/Sub 전용 `StatefulRedisPubSubConnection<String, String>` 분리.
> Redis는 SUBSCRIBE 상태에서 일반 명령 실행 제한 → 두 연결 필수.

> **소유자 식별:** `"${Thread.currentThread().threadId()}:$INSTANCE_UUID"` (Virtual Thread 포함)
> **재진입:** Hash holdCount 증가/감소. unlock 횟수 = lock 횟수여야 완전 해제.

> **Lua 스크립트 (상세 의사코드는 스펙 4-1절 참조):**
>
> `TRY_LOCK_SCRIPT` — KEYS: lockName, queueKey, timeoutKey, channelKey / ARGV: ownerKey, leaseMs, nowMs, waitMs
> - 만료된 대기 스레드 정리 (ZSet score < now → List, ZSet 에서 제거)
> - 락 비어있거나 현재 소유자면 → hincrby holdCount + 1, pexpire, 큐에서 제거 → nil 반환(성공)
> - 아니면 → 큐 미등록 시 rpush + zadd 후 queueHead 남은 대기 시간 반환
> - queueHead nil 가드: `if queueHead == false then return 0 end`
>
> `UNLOCK_SCRIPT` — holdCount 감소 → 0이면 del + 큐 제거 + publish 'unlock'
>
> `ACQUIRE_FAILED_SCRIPT` — 타임아웃/취소 시 lrem + zrem 으로 큐 정리

- [ ] **Step 1: 실패 테스트 — AbstractFairLockTest.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.lock

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

abstract class AbstractFairLockTest: AbstractRedisLettuceTest() {

    protected abstract fun createLock(name: String): LettuceFairLock
    private lateinit var lock: LettuceFairLock

    @BeforeEach
    fun setup() { lock = createLock(randomName()) }

    @Test
    fun `tryLock - 락 획득 성공`() {
        lock.tryLock().shouldBeTrue()
        lock.isLocked().shouldBeTrue()
        lock.isHeldByCurrentThread().shouldBeTrue()
        lock.unlock()
        lock.isLocked().shouldBeFalse()
    }

    @Test
    fun `tryLock - 이미 잠긴 경우 false`() {
        lock.tryLock().shouldBeTrue()
        try {
            createLock(lock.lockName).tryLock().shouldBeFalse()
        } finally {
            lock.unlock()
        }
    }

    @Test
    fun `재진입 - 동일 스레드 중첩 lock`() {
        lock.tryLock().shouldBeTrue()
        lock.tryLock().shouldBeTrue()   // reentrant: holdCount=2
        lock.unlock()
        lock.isLocked().shouldBeTrue()  // holdCount=1 남음
        lock.unlock()
        lock.isLocked().shouldBeFalse()
    }

    @Test
    fun `unlock - 미보유 시 예외`() {
        assertThrows<IllegalStateException> { lock.unlock() }
    }

    @Test
    fun `FIFO - 5 Virtual Thread 경쟁`() {
        val order = CopyOnWriteArrayList<Int>()
        val start = CountDownLatch(1)
        val done = CountDownLatch(5)
        val errorCount = AtomicInteger(0)
        val executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()
        repeat(5) { i ->
            executor.submit {
                try {
                    start.await()
                    createLock(lock.lockName).use { myLock ->
                        myLock.lock(java.time.Duration.ofSeconds(15))
                        try { order.add(i) } finally { myLock.unlock() }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally { done.countDown() }
            }
        }
        start.countDown()
        done.await(30, TimeUnit.SECONDS)
        executor.shutdown()
        errorCount.get() shouldBeEqualTo 0
        order.size shouldBeEqualTo 5
    }
}
```

- [ ] **Step 2: 컴파일 오류 확인 (LettuceFairLock 미존재)**

```bash
./gradlew :infra:redis-lettuce:compileTestKotlin 2>&1 | grep "error:" | head -3
```

기대: `error: unresolved reference: LettuceFairLock`

- [ ] **Step 3: LettuceFairLock.kt 구현**

전체 코드는 스펙 4-1절 Lua 의사코드 + 아래 구조를 따른다:

```kotlin
package io.bluetape4k.redis.lettuce.lock

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LettuceFairLock(
    private val connection: StatefulRedisConnection<String, String>,
    private val pubSubConnection: StatefulRedisPubSubConnection<String, String>,
    val lockName: String,
    val options: FairLockOptions = FairLockOptions.Default,
): AutoCloseable {

    companion object: KLogging() {
        private val INSTANCE_UUID: String = UUID.randomUUID().toString()

        // 스펙 4-1절 Lua 의사코드 참조 (queueHead nil 가드 포함)
        private val TRY_LOCK_SCRIPT = """
local now = tonumber(ARGV[3])
local expired = redis.call('zrangebyscore', KEYS[3], '-inf', now)
for _, v in ipairs(expired) do
    redis.call('lrem', KEYS[2], 0, v)
    redis.call('zrem', KEYS[3], v)
end
local lockExists = redis.call('exists', KEYS[1])
local queueHead = redis.call('lindex', KEYS[2], 0)
if lockExists == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
    if queueHead == ARGV[1] or queueHead == false then
        redis.call('hincrby', KEYS[1], ARGV[1], 1)
        redis.call('pexpire', KEYS[1], ARGV[2])
        redis.call('lrem', KEYS[2], 0, ARGV[1])
        return nil
    end
end
if redis.call('zscore', KEYS[3], ARGV[1]) == false then
    redis.call('rpush', KEYS[2], ARGV[1])
    redis.call('zadd', KEYS[3], now + tonumber(ARGV[4]), ARGV[1])
end
if queueHead == false then return 0 end
local headTimeout = redis.call('zscore', KEYS[3], queueHead)
if headTimeout == false then return 0 end
return math.max(tonumber(headTimeout) - now, 0)
""".trimIndent()

        // 반환값: 0=미보유(오류), 1=완전해제(del+publish), 2=부분해제(holdCount 감소, 아직 보유)
        private val UNLOCK_SCRIPT = """
if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then return 0 end
local remaining = redis.call('hincrby', KEYS[1], ARGV[1], -1)
if remaining > 0 then
    redis.call('pexpire', KEYS[1], ARGV[2])
    return 2
end
redis.call('del', KEYS[1])
redis.call('lrem', KEYS[2], 0, ARGV[1])
redis.call('zrem', KEYS[3], ARGV[1])
redis.call('publish', KEYS[4], 'unlock')
return 1
""".trimIndent()

        private val ACQUIRE_FAILED_SCRIPT = """
redis.call('lrem', KEYS[2], 0, ARGV[1])
redis.call('zrem', KEYS[3], ARGV[1])
""".trimIndent()
    }

    private val commands: RedisCommands<String, String> get() = connection.sync()
    private val queueKey = "$lockName:queue"
    private val timeoutKey = "$lockName:timeout"
    private val channelKey = "$lockName:channel"
    private fun ownerKey(): String = "${Thread.currentThread().threadId()}:$INSTANCE_UUID"

    fun isLocked(): Boolean = commands.exists(lockName) > 0
    fun isHeldByCurrentThread(): Boolean = commands.hexists(lockName, ownerKey())

    fun tryLock(leaseTime: Duration = options.leaseTime.toJavaDuration()): Boolean {
        val key = ownerKey()
        val now = System.currentTimeMillis()
        val result = commands.eval<Long>(
            TRY_LOCK_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(lockName, queueKey, timeoutKey, channelKey),
            key, leaseTime.toMillis().toString(), now.toString(),
            options.threadWaitTimeout.inWholeMilliseconds.toString()
        )
        return result == null
    }

    fun lock(waitTime: Duration = Duration.ofMinutes(5), leaseTime: Duration = options.leaseTime.toJavaDuration()) {
        val key = ownerKey()
        val deadline = System.currentTimeMillis() + waitTime.toMillis()
        var delay = options.retryDelay.inWholeMilliseconds
        val latch = CountDownLatch(1)
        val listener = object: RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                if (channel == channelKey) latch.countDown()
            }
        }
        pubSubConnection.addListener(listener)
        pubSubConnection.sync().subscribe(channelKey)
        try {
            while (true) {
                if (tryLock(leaseTime)) { log.debug { "FairLock 획득: lockName=$lockName" }; return }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) break
                latch.await(minOf(delay, remaining), TimeUnit.MILLISECONDS)
                delay = when (val p = options.backOffPolicy) {
                    is BackOffPolicy.Fixed -> p.delay.inWholeMilliseconds
                    is BackOffPolicy.Exponential -> minOf(delay * p.multiplier, p.maxDelay.inWholeMilliseconds)
                }
            }
        } finally {
            pubSubConnection.sync().unsubscribe(channelKey)
            pubSubConnection.removeListener(listener)
        }
        commands.eval<Long>(ACQUIRE_FAILED_SCRIPT, ScriptOutputType.INTEGER, arrayOf(lockName, queueKey, timeoutKey), key)
        throw IllegalStateException("FairLock 획득 시간 초과: lockName=$lockName")
    }

    fun unlock() {
        val result = commands.eval<Long>(
            UNLOCK_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(lockName, queueKey, timeoutKey, channelKey),
            ownerKey(), options.leaseTime.inWholeMilliseconds.toString()
        )
        when (result) {
            0L -> throw IllegalStateException("락 해제 실패 (토큰 불일치): lockName=$lockName")
            1L -> log.debug { "FairLock 완전 해제: lockName=$lockName" }
            2L -> log.debug { "FairLock 부분 해제 (재진입 중): lockName=$lockName" }
        }
    }

    override fun close() {
        runCatching { pubSubConnection.sync().unsubscribe(channelKey) }
        runCatching { pubSubConnection.close() }
        runCatching { connection.close() }
    }
}

private fun kotlin.time.Duration.toJavaDuration(): java.time.Duration =
    java.time.Duration.ofMillis(inWholeMilliseconds)
```

- [ ] **Step 4: LettuceFairLockTest.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.lock

import io.lettuce.core.codec.StringCodec

class LettuceFairLockTest: AbstractFairLockTest() {
    override fun createLock(name: String): LettuceFairLock =
        LettuceFairLock(
            connection = client.connect(StringCodec.UTF8),
            pubSubConnection = client.connectPubSub(StringCodec.UTF8),
            lockName = name,
        )
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.lock.LettuceFairLockTest" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`, 전체 PASS

- [ ] **Step 6: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/lock/LettuceFairLock.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/lock/
git commit -m "feat: LettuceFairLock (Sync, Reentrant) 구현"
```

---

## Task 5: LettuceSuspendFairLock (Coroutine, AtomicLong 소유자)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/lock/LettuceSuspendFairLock.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/lock/LettuceSuspendFairLockTest.kt`

> **핵심 차이점 (LettuceFairLock 대비):**
> - 소유자: `"${ownerCounter.incrementAndGet()}:$INSTANCE_UUID"` (AtomicLong, 스레드 무관)
> - ownerRef: `atomic<String?>(null)` (atomicfu) — 소유 여부 판단, 인스턴스 레벨 재진입 허용
> - 모든 Redis 호출: `asyncCommands.xxxxx().await()` (suspend)
> - Pub/Sub 알림: `Channel<Unit>(CONFLATED)` + RedisPubSubAdapter
> - 취소/타임아웃 시 finally 블록에서 ACQUIRE_FAILED_SCRIPT 실행 (큐 정리)

- [ ] **Step 1: 실패 테스트 — LettuceSuspendFairLockTest.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.lock

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class LettuceSuspendFairLockTest: AbstractRedisLettuceTest() {

    private lateinit var lock: LettuceSuspendFairLock

    @BeforeEach
    fun setup() {
        lock = LettuceSuspendFairLock(
            connection = client.connect(StringCodec.UTF8),
            pubSubConnection = client.connectPubSub(StringCodec.UTF8),
            lockName = randomName(),
        )
    }

    @Test
    fun `tryLock - 획득 성공`() = runTest {
        lock.tryLock().shouldBeTrue()
        lock.isLocked().shouldBeTrue()
        lock.isHeldByCurrentInstance().shouldBeTrue()
        lock.unlock()
        lock.isLocked().shouldBeFalse()
    }

    @Test
    fun `재진입 - 동일 인스턴스 중첩 lock`() = runTest {
        lock.tryLock().shouldBeTrue()
        lock.tryLock().shouldBeTrue()   // instance-level reentrant
        lock.unlock()
        lock.isLocked().shouldBeTrue()  // holdCount=1 남음
        lock.unlock()
        lock.isLocked().shouldBeFalse()
    }

    @Test
    fun `unlock - 미보유 시 예외`() = runTest {
        assertFailsWith<IllegalStateException> { lock.unlock() }
    }

    @Test
    fun `코루틴 취소 시 큐 정리`() = runTest {
        lock.tryLock().shouldBeTrue()
        val lock2 = LettuceSuspendFairLock(
            client.connect(StringCodec.UTF8),
            client.connectPubSub(StringCodec.UTF8),
            lock.lockName,
        )
        val job = launch(Dispatchers.IO) { runCatching { lock2.lock() } }
        kotlinx.coroutines.delay(100)
        job.cancel()
        job.join()
        lock.unlock()
        // 취소된 lock2 큐 항목 정리 → lock 재획득 가능
        lock.tryLock().shouldBeTrue()
        lock.unlock()
    }

    @Test
    fun `10 코루틴 동시 경쟁`() = runTest {
        val results = CopyOnWriteArrayList<Int>()
        val errorCount = AtomicInteger(0)
        val jobs = (0 until 10).map { i ->
            launch(Dispatchers.IO) {
                val myLock = LettuceSuspendFairLock(
                    client.connect(StringCodec.UTF8),
                    client.connectPubSub(StringCodec.UTF8),
                    lock.lockName,
                )
                runCatching {
                    myLock.lock()
                    try { results.add(i) } finally { myLock.unlock() }
                }.onFailure { errorCount.incrementAndGet() }
            }
        }
        jobs.forEach { it.join() }
        errorCount.get() shouldBeEqualTo 0
        results.size shouldBeEqualTo 10
    }
}
```

- [ ] **Step 2: LettuceSuspendFairLock.kt 구현**

```kotlin
package io.bluetape4k.redis.lettuce.lock

import io.bluetape4k.logging.KLoggingChannel
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class LettuceSuspendFairLock(
    private val connection: StatefulRedisConnection<String, String>,
    private val pubSubConnection: StatefulRedisPubSubConnection<String, String>,
    val lockName: String,
    val options: FairLockOptions = FairLockOptions.Default,
): AutoCloseable {

    companion object: KLoggingChannel() {
        // Lua 스크립트만 companion에 선언 (JVM static 공유 안전)
        private val TRY_LOCK_SCRIPT = """
local now = tonumber(ARGV[3])
local expired = redis.call('zrangebyscore', KEYS[3], '-inf', now)
for _, v in ipairs(expired) do
    redis.call('lrem', KEYS[2], 0, v)
    redis.call('zrem', KEYS[3], v)
end
local lockExists = redis.call('exists', KEYS[1])
local queueHead = redis.call('lindex', KEYS[2], 0)
if lockExists == 0 or redis.call('hexists', KEYS[1], ARGV[1]) == 1 then
    if queueHead == ARGV[1] or queueHead == false then
        redis.call('hincrby', KEYS[1], ARGV[1], 1)
        redis.call('pexpire', KEYS[1], ARGV[2])
        redis.call('lrem', KEYS[2], 0, ARGV[1])
        return nil
    end
end
if redis.call('zscore', KEYS[3], ARGV[1]) == false then
    redis.call('rpush', KEYS[2], ARGV[1])
    redis.call('zadd', KEYS[3], now + tonumber(ARGV[4]), ARGV[1])
end
if queueHead == false then return 0 end
local headTimeout = redis.call('zscore', KEYS[3], queueHead)
if headTimeout == false then return 0 end
return math.max(tonumber(headTimeout) - now, 0)
""".trimIndent()

        // 반환값: 0=미보유(오류), 1=완전해제(del+publish), 2=부분해제(holdCount 감소, 아직 보유)
        private val UNLOCK_SCRIPT = """
if redis.call('hexists', KEYS[1], ARGV[1]) == 0 then return 0 end
local remaining = redis.call('hincrby', KEYS[1], ARGV[1], -1)
if remaining > 0 then
    redis.call('pexpire', KEYS[1], ARGV[2])
    return 2
end
redis.call('del', KEYS[1])
redis.call('lrem', KEYS[2], 0, ARGV[1])
redis.call('zrem', KEYS[3], ARGV[1])
redis.call('publish', KEYS[4], 'unlock')
return 1
""".trimIndent()

        private val ACQUIRE_FAILED_SCRIPT = """
redis.call('lrem', KEYS[2], 0, ARGV[1])
redis.call('zrem', KEYS[3], ARGV[1])
""".trimIndent()
    }

    // 인스턴스 고유 식별자 + 소유자 ID 생성기 (인스턴스 레벨 — companion에 두면 모든 인스턴스가 공유되어 충돌)
    private val instanceUUID: String = UUID.randomUUID().toString()
    private val ownerCounter = AtomicLong(0)
    private val ownerRef = atomic<String?>(null)
    private val asyncCommands: RedisAsyncCommands<String, String> get() = connection.async()
    private val queueKey = "$lockName:queue"
    private val timeoutKey = "$lockName:timeout"
    private val channelKey = "$lockName:channel"

    // ownerRef가 있으면 재진입 경로, 없으면 새 ID 생성
    private fun currentOwnerKey(): String =
        ownerRef.value ?: "${ownerCounter.incrementAndGet()}:$INSTANCE_UUID"

    suspend fun isLocked(): Boolean = asyncCommands.exists(lockName).await() > 0

    suspend fun isHeldByCurrentInstance(): Boolean {
        val key = ownerRef.value ?: return false
        return asyncCommands.hexists(lockName, key).await()
    }

    suspend fun tryLock(leaseTime: Duration = options.leaseTime): Boolean {
        val key = currentOwnerKey()
        val now = System.currentTimeMillis()
        val result = asyncCommands.eval<Long>(
            TRY_LOCK_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(lockName, queueKey, timeoutKey, channelKey),
            key, leaseTime.inWholeMilliseconds.toString(), now.toString(),
            options.threadWaitTimeout.inWholeMilliseconds.toString()
        ).await()
        return if (result == null) { ownerRef.value = key; true } else false
    }

    suspend fun lock(waitTime: Duration = 5.minutes, leaseTime: Duration = options.leaseTime) {
        val key = currentOwnerKey()
        val deadline = System.currentTimeMillis() + waitTime.inWholeMilliseconds
        var delay = options.retryDelay.inWholeMilliseconds
        val notifyChannel = Channel<Unit>(Channel.CONFLATED)
        val listener = object: RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                if (channel == channelKey) notifyChannel.trySend(Unit)
            }
        }
        pubSubConnection.addListener(listener)
        pubSubConnection.async().subscribe(channelKey).await()
        var acquired = false
        try {
            while (coroutineContext.isActive) {
                val now = System.currentTimeMillis()
                if (now >= deadline) break
                val result = asyncCommands.eval<Long>(
                    TRY_LOCK_SCRIPT, ScriptOutputType.INTEGER,
                    arrayOf(lockName, queueKey, timeoutKey, channelKey),
                    key, leaseTime.inWholeMilliseconds.toString(), now.toString(),
                    options.threadWaitTimeout.inWholeMilliseconds.toString()
                ).await()
                if (result == null) {
                    ownerRef.value = key
                    acquired = true
                    log.debug { "SuspendFairLock 획득: lockName=$lockName" }
                    return
                }
                val waitMs = minOf(delay, deadline - System.currentTimeMillis())
                if (waitMs <= 0) break
                withTimeoutOrNull(waitMs.milliseconds) { notifyChannel.receive() }
                delay = when (val p = options.backOffPolicy) {
                    is BackOffPolicy.Fixed -> p.delay.inWholeMilliseconds
                    is BackOffPolicy.Exponential -> minOf(delay * p.multiplier, p.maxDelay.inWholeMilliseconds)
                }
            }
        } finally {
            runCatching { pubSubConnection.async().unsubscribe(channelKey).await() }
            pubSubConnection.removeListener(listener)
            if (!acquired) {
                runCatching {
                    asyncCommands.eval<Long>(
                        ACQUIRE_FAILED_SCRIPT, ScriptOutputType.INTEGER,
                        arrayOf(lockName, queueKey, timeoutKey), key
                    ).await()
                }
            }
        }
        throw IllegalStateException("SuspendFairLock 시간 초과: lockName=$lockName")
    }

    suspend fun unlock() {
        val key = ownerRef.value
            ?: throw IllegalStateException("락 미보유: lockName=$lockName")
        val result = asyncCommands.eval<Long>(
            UNLOCK_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(lockName, queueKey, timeoutKey, channelKey),
            key, options.leaseTime.inWholeMilliseconds.toString()
        ).await()
        when (result) {
            0L -> throw IllegalStateException("락 해제 실패 (토큰 불일치): lockName=$lockName")
            1L -> { ownerRef.value = null; log.debug { "SuspendFairLock 완전 해제: lockName=$lockName" } }
            2L -> log.debug { "SuspendFairLock 부분 해제 (재진입 중): lockName=$lockName" }
        }
    }

    override fun close() {
        runCatching { pubSubConnection.sync().unsubscribe(channelKey) }
        runCatching { pubSubConnection.close() }
        runCatching { connection.close() }
    }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.lock.LettuceSuspendFairLockTest" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`, 전체 PASS

- [ ] **Step 4: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/lock/LettuceSuspendFairLock.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/lock/LettuceSuspendFairLockTest.kt
git commit -m "feat: LettuceSuspendFairLock (Coroutine, AtomicLong 소유자) 구현"
```

---

## Task 6: RateType + RateLimiterConfig

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/rate/RateType.kt`
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/rate/RateLimiterConfig.kt`

- [ ] **Step 1: RateType.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.rate

enum class RateType { OVERALL, PER_CLIENT }
```

- [ ] **Step 2: RateLimiterConfig.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.rate

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class RateLimiterConfig(
    val rate: Long,
    val rateInterval: Duration,
    val rateType: RateType = RateType.OVERALL,
) {
    companion object {
        val Default = RateLimiterConfig(rate = 10, rateInterval = 1.seconds)
    }
    init {
        require(rate > 0) { "rate must be positive" }
        require(rateInterval.isPositive()) { "rateInterval must be positive" }
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew :infra:redis-lettuce:compileKotlin 2>&1 | tail -5
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/rate/
git commit -m "feat: RateType, RateLimiterConfig 추가"
```

---

## Task 7: LettuceRateLimiter (Sync, Token Bucket)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/rate/LettuceRateLimiter.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/rate/AbstractRateLimiterTest.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/rate/LettuceRateLimiterTest.kt`

> **Redis 자료구조:**
> - `{name}:config`   → Hash { rate, interval, type }
> - `{name}:value`    → String (잔여 토큰)
> - `{name}:permits`  → ZSet (score=expireTimeMs, member="${randomHex}:${permits}")
> - PER_CLIENT: `:value` / `:permits` 앞에 `:${CLIENT_ID}` 삽입
>
> **permit member 형식:** `"${randomHex}:${permits}"` 단순 문자열 (struct.pack 미사용)
> **Lua TRY_ACQUIRE_SCRIPT 구조:**
> 1. `zrangebyscore` 만료 permit → zremrangebyscore + incrby 복원
> 2. 잔여 < 요청 → -1 반환 (거부)
> 3. decrby 차감 + zadd 기록 → 남은 토큰 반환

- [ ] **Step 1: 실패 테스트 — AbstractRateLimiterTest.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.rate

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

abstract class AbstractRateLimiterTest: AbstractRedisLettuceTest() {

    protected abstract fun createRateLimiter(name: String, config: RateLimiterConfig): LettuceRateLimiter
    private lateinit var limiter: LettuceRateLimiter

    @BeforeEach
    fun setup() {
        limiter = createRateLimiter(randomName(), RateLimiterConfig(rate = 5, rateInterval = 1.seconds))
        limiter.trySetRate()
    }

    @Test
    fun `rate 이내 요청 허용`() {
        repeat(5) { limiter.tryAcquire().shouldBeTrue() }
    }

    @Test
    fun `rate 초과 시 거부`() {
        repeat(5) { limiter.tryAcquire() }
        limiter.tryAcquire().shouldBeFalse()
    }

    @Test
    fun `인터벌 후 permit 복구`() {
        repeat(5) { limiter.tryAcquire() }
        limiter.tryAcquire().shouldBeFalse()
        Thread.sleep(1100)
        limiter.tryAcquire().shouldBeTrue()
    }

    @Test
    fun `availablePermits 감소 확인`() {
        val before = limiter.availablePermits()
        limiter.tryAcquire()
        val after = limiter.availablePermits()
        (before - after) shouldBeEqualTo 1L
    }
}
```

- [ ] **Step 2: LettuceRateLimiter.kt 구현**

```kotlin
package io.bluetape4k.redis.lettuce.rate

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import java.util.UUID
import kotlin.time.Duration

class LettuceRateLimiter(
    private val connection: StatefulRedisConnection<String, String>,
    val rateName: String,
    val config: RateLimiterConfig,
): AutoCloseable {

    companion object: KLogging() {
        // KEYS[1]=configKey, KEYS[2]=valueKey / ARGV[1]=rate, ARGV[2]=intervalMs, ARGV[3]=type
        private val TRY_SET_RATE_SCRIPT = """
if redis.call('hsetnx', KEYS[1], 'rate', ARGV[1]) == 1 then
    redis.call('hset', KEYS[1], 'interval', ARGV[2], 'type', ARGV[3])
    redis.call('set', KEYS[2], ARGV[1])
    return 1
end
return 0
""".trimIndent()

        // KEYS[1]=valueKey, KEYS[2]=permitsKey
        // ARGV[1]=permits, ARGV[2]=nowMs, ARGV[3]=rate, ARGV[4]=intervalMs, ARGV[5]=randomHex
        private val TRY_ACQUIRE_SCRIPT = """
local now = tonumber(ARGV[2])
local rate = tonumber(ARGV[3])
local intervalMs = tonumber(ARGV[4])
local permits = tonumber(ARGV[1])
local expired = redis.call('zrangebyscore', KEYS[2], '-inf', now)
if #expired > 0 then
    redis.call('zremrangebyscore', KEYS[2], '-inf', now)
    local restored = 0
    for _, m in ipairs(expired) do
        local sep = string.find(m, ':')
        if sep then restored = restored + tonumber(string.sub(m, sep + 1)) end
    end
    redis.call('incrby', KEYS[1], restored)
end
local current = tonumber(redis.call('get', KEYS[1])) or rate
if current > rate then redis.call('set', KEYS[1], rate); current = rate end
if current < permits then return -1 end
redis.call('decrby', KEYS[1], permits)
redis.call('zadd', KEYS[2], now + intervalMs, ARGV[5] .. ':' .. ARGV[1])
return current - permits
""".trimIndent()
    }

    // PER_CLIENT 격리를 위한 인스턴스별 고유 ID (companion이면 모든 인스턴스가 동일한 키 사용)
    private val clientId: String = UUID.randomUUID().toString().replace("-", "").take(16)

    private val commands: RedisCommands<String, String> get() = connection.sync()
    private val configKey = "$rateName:config"
    private fun valueKey() = if (config.rateType == RateType.PER_CLIENT) "$rateName:$clientId:value" else "$rateName:value"
    private fun permitsKey() = if (config.rateType == RateType.PER_CLIENT) "$rateName:$clientId:permits" else "$rateName:permits"

    fun trySetRate(rate: Long = config.rate, interval: Duration = config.rateInterval) {
        commands.eval<Long>(
            TRY_SET_RATE_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(configKey, valueKey()),
            rate.toString(), interval.inWholeMilliseconds.toString(), config.rateType.name
        )
        log.debug { "RateLimiter 초기화: rateName=$rateName, rate=$rate" }
    }

    fun availablePermits(): Long = commands.get(valueKey())?.toLongOrNull() ?: config.rate

    fun tryAcquire(permits: Long = 1L): Boolean {
        require(permits > 0) { "permits must be positive" }
        val randomHex = UUID.randomUUID().toString().replace("-", "").take(8)
        val result = commands.eval<Long>(
            TRY_ACQUIRE_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(valueKey(), permitsKey()),
            permits.toString(), System.currentTimeMillis().toString(),
            config.rate.toString(), config.rateInterval.inWholeMilliseconds.toString(), randomHex
        )
        val acquired = result != null && result >= 0
        log.debug { "RateLimiter tryAcquire: rateName=$rateName, permits=$permits, acquired=$acquired" }
        return acquired
    }

    fun acquire(permits: Long = 1L, waitTime: Duration = config.rateInterval) {
        val deadline = System.currentTimeMillis() + waitTime.inWholeMilliseconds
        while (System.currentTimeMillis() < deadline) {
            if (tryAcquire(permits)) return
            Thread.sleep(50)
        }
        throw IllegalStateException("RateLimiter 획득 시간 초과: rateName=$rateName")
    }

    override fun close() = runCatching { connection.close() }.let { }
}
```

- [ ] **Step 3: LettuceRateLimiterTest.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.rate

import io.lettuce.core.codec.StringCodec

class LettuceRateLimiterTest: AbstractRateLimiterTest() {
    override fun createRateLimiter(name: String, config: RateLimiterConfig): LettuceRateLimiter =
        LettuceRateLimiter(client.connect(StringCodec.UTF8), name, config)
}
```

- [ ] **Step 4: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.rate.LettuceRateLimiterTest" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`, 전체 PASS

- [ ] **Step 5: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/rate/LettuceRateLimiter.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/rate/
git commit -m "feat: LettuceRateLimiter (Sync, Token Bucket) 구현"
```

---

## Task 8: LettuceSuspendRateLimiter (Coroutine)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/rate/LettuceSuspendRateLimiter.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/rate/LettuceSuspendRateLimiterTest.kt`

> LettuceRateLimiter와 동일한 Lua 스크립트 사용, `asyncCommands.xxxxx().await()` 방식으로 변환.

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package io.bluetape4k.redis.lettuce.rate

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class LettuceSuspendRateLimiterTest: AbstractRedisLettuceTest() {

    private lateinit var limiter: LettuceSuspendRateLimiter

    @BeforeEach
    fun setup() = kotlinx.coroutines.runBlocking {
        limiter = LettuceSuspendRateLimiter(
            client.connect(StringCodec.UTF8),
            randomName(),
            RateLimiterConfig(rate = 5, rateInterval = 1.seconds),
        )
        limiter.trySetRate()
    }

    @Test
    fun `rate 이내 요청 허용`() = runTest {
        repeat(5) { limiter.tryAcquire().shouldBeTrue() }
    }

    @Test
    fun `rate 초과 시 거부`() = runTest {
        repeat(5) { limiter.tryAcquire() }
        limiter.tryAcquire().shouldBeFalse()
    }

    @Test
    fun `PER_CLIENT 클라이언트별 독립`() = runTest {
        val limiter2 = LettuceSuspendRateLimiter(
            client.connect(StringCodec.UTF8),
            limiter.rateName,
            RateLimiterConfig(rate = 5, rateInterval = 1.seconds, rateType = RateType.PER_CLIENT),
        )
        repeat(5) { limiter2.tryAcquire().shouldBeTrue() }
        limiter2.tryAcquire().shouldBeFalse()
    }

    @Test
    fun `10 코루틴 동시 시도 - 5개만 허용`() = runTest {
        val count = AtomicInteger(0)
        val jobs = (0 until 10).map {
            launch(Dispatchers.IO) { if (limiter.tryAcquire()) count.incrementAndGet() }
        }
        jobs.forEach { it.join() }
        count.get() shouldBeEqualTo 5
    }
}
```

- [ ] **Step 2: LettuceSuspendRateLimiter.kt 구현**

```kotlin
package io.bluetape4k.redis.lettuce.rate

import io.bluetape4k.logging.KLoggingChannel
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import java.util.UUID
import kotlin.time.Duration

class LettuceSuspendRateLimiter(
    private val connection: StatefulRedisConnection<String, String>,
    val rateName: String,
    val config: RateLimiterConfig,
): AutoCloseable {

    companion object: KLoggingChannel() {
        private val TRY_SET_RATE_SCRIPT = """
if redis.call('hsetnx', KEYS[1], 'rate', ARGV[1]) == 1 then
    redis.call('hset', KEYS[1], 'interval', ARGV[2], 'type', ARGV[3])
    redis.call('set', KEYS[2], ARGV[1])
    return 1
end
return 0
""".trimIndent()

        private val TRY_ACQUIRE_SCRIPT = """
local now = tonumber(ARGV[2])
local rate = tonumber(ARGV[3])
local intervalMs = tonumber(ARGV[4])
local permits = tonumber(ARGV[1])
local expired = redis.call('zrangebyscore', KEYS[2], '-inf', now)
if #expired > 0 then
    redis.call('zremrangebyscore', KEYS[2], '-inf', now)
    local restored = 0
    for _, m in ipairs(expired) do
        local sep = string.find(m, ':')
        if sep then restored = restored + tonumber(string.sub(m, sep + 1)) end
    end
    redis.call('incrby', KEYS[1], restored)
end
local current = tonumber(redis.call('get', KEYS[1])) or rate
if current > rate then redis.call('set', KEYS[1], rate); current = rate end
if current < permits then return -1 end
redis.call('decrby', KEYS[1], permits)
redis.call('zadd', KEYS[2], now + intervalMs, ARGV[5] .. ':' .. ARGV[1])
return current - permits
""".trimIndent()
    }

    // PER_CLIENT 격리를 위한 인스턴스별 고유 ID (companion이면 모든 인스턴스가 동일한 키 사용)
    private val clientId: String = UUID.randomUUID().toString().replace("-", "").take(16)

    private val asyncCommands: RedisAsyncCommands<String, String> get() = connection.async()
    private val configKey = "$rateName:config"
    private fun valueKey() = if (config.rateType == RateType.PER_CLIENT) "$rateName:$clientId:value" else "$rateName:value"
    private fun permitsKey() = if (config.rateType == RateType.PER_CLIENT) "$rateName:$clientId:permits" else "$rateName:permits"

    suspend fun trySetRate(rate: Long = config.rate, interval: Duration = config.rateInterval) {
        asyncCommands.eval<Long>(
            TRY_SET_RATE_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(configKey, valueKey()),
            rate.toString(), interval.inWholeMilliseconds.toString(), config.rateType.name
        ).await()
        log.debug { "SuspendRateLimiter 초기화: rateName=$rateName, rate=$rate" }
    }

    suspend fun availablePermits(): Long =
        asyncCommands.get(valueKey()).await()?.toLongOrNull() ?: config.rate

    suspend fun tryAcquire(permits: Long = 1L): Boolean {
        require(permits > 0) { "permits must be positive" }
        val randomHex = UUID.randomUUID().toString().replace("-", "").take(8)
        val result = asyncCommands.eval<Long>(
            TRY_ACQUIRE_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(valueKey(), permitsKey()),
            permits.toString(), System.currentTimeMillis().toString(),
            config.rate.toString(), config.rateInterval.inWholeMilliseconds.toString(), randomHex
        ).await()
        val acquired = result != null && result >= 0
        log.debug { "SuspendRateLimiter tryAcquire: rateName=$rateName, permits=$permits, acquired=$acquired" }
        return acquired
    }

    suspend fun acquire(permits: Long = 1L, waitTime: Duration = config.rateInterval) {
        val deadline = System.currentTimeMillis() + waitTime.inWholeMilliseconds
        while (System.currentTimeMillis() < deadline) {
            if (tryAcquire(permits)) return
            delay(50)
        }
        throw IllegalStateException("SuspendRateLimiter 획득 시간 초과: rateName=$rateName")
    }

    override fun close() = runCatching { connection.close() }.let { }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.rate.LettuceSuspendRateLimiterTest" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`, 전체 PASS

- [ ] **Step 4: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/rate/LettuceSuspendRateLimiter.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/rate/LettuceSuspendRateLimiterTest.kt
git commit -m "feat: LettuceSuspendRateLimiter (Coroutine, Token Bucket) 구현"
```

---

## Task 9: LettuceFeatures 팩토리

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/LettuceFeatures.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/LettuceFeaturesTest.kt`

- [ ] **Step 1: LettuceFeatures.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce

import io.bluetape4k.redis.lettuce.lock.FairLockOptions
import io.bluetape4k.redis.lettuce.lock.LettuceFairLock
import io.bluetape4k.redis.lettuce.lock.LettuceSuspendFairLock
import io.bluetape4k.redis.lettuce.rate.LettuceRateLimiter
import io.bluetape4k.redis.lettuce.rate.LettuceSuspendRateLimiter
import io.bluetape4k.redis.lettuce.rate.RateLimiterConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.codec.StringCodec

/** [LettuceFairLock] 생성. 데이터 connection + Pub/Sub connection 분리 */
fun RedisClient.fairLock(name: String, options: FairLockOptions = FairLockOptions.Default): LettuceFairLock =
    LettuceFairLock(connect(StringCodec.UTF8), connectPubSub(StringCodec.UTF8), name, options)

/** [LettuceSuspendFairLock] 생성. 데이터 connection + Pub/Sub connection 분리 */
fun RedisClient.suspendFairLock(name: String, options: FairLockOptions = FairLockOptions.Default): LettuceSuspendFairLock =
    LettuceSuspendFairLock(connect(StringCodec.UTF8), connectPubSub(StringCodec.UTF8), name, options)

/** [LettuceRateLimiter] 생성 */
fun RedisClient.rateLimiter(name: String, config: RateLimiterConfig): LettuceRateLimiter =
    LettuceRateLimiter(connect(StringCodec.UTF8), name, config)

/** [LettuceSuspendRateLimiter] 생성 */
fun RedisClient.suspendRateLimiter(name: String, config: RateLimiterConfig): LettuceSuspendRateLimiter =
    LettuceSuspendRateLimiter(connect(StringCodec.UTF8), name, config)
```

- [ ] **Step 2: LettuceFeaturesTest.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce

import io.bluetape4k.redis.lettuce.rate.RateLimiterConfig
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class LettuceFeaturesTest: AbstractRedisLettuceTest() {

    @Test
    fun `fairLock 팩토리 동작 확인`() {
        client.fairLock(randomName()).use { lock ->
            lock.tryLock().shouldBeTrue()
            lock.unlock()
        }
    }

    @Test
    fun `suspendFairLock 팩토리 동작 확인`() = runTest {
        client.suspendFairLock(randomName()).use { lock ->
            lock.tryLock().shouldBeTrue()
            lock.unlock()
        }
    }

    @Test
    fun `rateLimiter 팩토리 동작 확인`() {
        client.rateLimiter(randomName(), RateLimiterConfig(5, 1.seconds)).use { limiter ->
            limiter.trySetRate()
            limiter.tryAcquire().shouldBeTrue()
        }
    }

    @Test
    fun `suspendRateLimiter 팩토리 동작 확인`() = runTest {
        client.suspendRateLimiter(randomName(), RateLimiterConfig(5, 1.seconds)).use { limiter ->
            limiter.trySetRate()
            limiter.tryAcquire().shouldBeTrue()
        }
    }
}
```

- [ ] **Step 3: 전체 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test 2>&1 | tail -20
```

기대: `BUILD SUCCESSFUL`, 전체 PASS

- [ ] **Step 4: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/LettuceFeatures.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/LettuceFeaturesTest.kt
git commit -m "feat: LettuceFeatures 팩토리 (fairLock/rateLimiter) 추가"
```

---

## 자체 검토

### Spec 커버리지

| 요구사항 | Task |
|---|---|
| 모듈 `infra/redis-lettuce` 생성 | Task 1 |
| BackOffPolicy + FairLockOptions | Task 3 |
| LettuceFairLock (Sync, Reentrant, Pub/Sub 분리) | Task 4 |
| LettuceSuspendFairLock (Coroutine, AtomicLong) | Task 5 |
| RateType + RateLimiterConfig | Task 6 |
| LettuceRateLimiter (Sync, Token Bucket, struct.pack 미사용) | Task 7 |
| LettuceSuspendRateLimiter (Coroutine) | Task 8 |
| LettuceFeatures 팩토리 | Task 9 |
| FIFO + Reentrant 테스트 | Task 4, 5 |
| PER_CLIENT 독립 테스트 | Task 8 |
| 코루틴 취소 큐 정리 테스트 | Task 5 |

### Codex 피드백 반영

| 항목 | 처리 |
|---|---|
| [높음] SuspendFairLock AtomicLong 소유자 | Task 5 ownerCounter + ownerRef |
| [높음] NearCacheMap 연결 분리 | 스펙 명시 (Plan 2에서 구현) |
| [중간] queueHead nil 가드 | Task 4, 5 Lua |
| [중간] struct.pack 제거 | Task 7, 8 Lua (단순 문자열) |
| [낮음] falseProbability 오픈 구간 | 스펙 반영 (Plan 3에서 구현) |

### 주의 사항

- `tryLock(leaseTime)` 파라미터: LettuceFairLock(Sync)는 `java.time.Duration`, LettuceSuspendFairLock(Coroutine)은 `kotlin.time.Duration` — **Plan 2 이전에 통일 여부 결정 필요**
