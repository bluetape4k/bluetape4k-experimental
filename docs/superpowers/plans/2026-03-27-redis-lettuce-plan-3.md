# HyperLogLog + BloomFilter + CuckooFilter 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** LettuceHyperLogLog / LettuceBloomFilter / LettuceCuckooFilter (Sync + Suspend) 구현

**Architecture:**
- HyperLogLog: native Redis PFADD/PFCOUNT/PFMERGE 래퍼, generic `V` codec 지원
- BloomFilter: MurmurHash3 128bit 클라이언트 해시 → SETBIT/GETBIT Lua, `String` 원소
- CuckooFilter: Lua 기반 fingerprint 이중 버킷 삽입/삭제, `String` 원소

**Tech Stack:** Lettuce 6.x, Guava Hashing (MurmurHash3), Kotlin Coroutines, Testcontainers (Redis)

---

## 파일 구조

```text
infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/
  hll/
    LettuceHyperLogLog.kt           (Sync)
    LettuceSuspendHyperLogLog.kt    (Coroutine)
  filter/
    BloomFilterOptions.kt
    LettuceBloomFilter.kt           (Sync)
    LettuceSuspendBloomFilter.kt    (Coroutine)
    CuckooFilterOptions.kt
    LettuceCuckooFilter.kt          (Sync)
    LettuceSuspendCuckooFilter.kt   (Coroutine)

infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/
  hll/
    LettuceHyperLogLogTest.kt
    LettuceSuspendHyperLogLogTest.kt
  filter/
    LettuceBloomFilterTest.kt
    LettuceSuspendBloomFilterTest.kt
    LettuceCuckooFilterTest.kt
    LettuceSuspendCuckooFilterTest.kt
```

**공통 주의사항:**
- BloomFilter/CuckooFilter는 `String` 원소를 클라이언트에서 `UTF-8 ByteArray`로 변환 후 MurmurHash3 적용
- `guava:com.google.guava:guava` 의존성 사용 (`Hashing.murmur3_128()`)
- CuckooFilter Lua에서 비트 XOR: Redis 6.x(Lua 5.1)는 `bit.bxor`, Redis 7.0+(Lua 5.4)는 `~` — 계획에서는 `bit.bxor` 사용
- 모든 클래스는 `AutoCloseable` 구현

---

## Task 1: LettuceHyperLogLog (Sync + Suspend)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/hll/LettuceHyperLogLog.kt`
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/hll/LettuceSuspendHyperLogLog.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/hll/LettuceHyperLogLogTest.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/hll/LettuceSuspendHyperLogLogTest.kt`

> **Redis 자료구조:** `{name}` → HyperLogLog (Redis 내부 관리)
> **클러스터 주의:** `countWith`/`mergeWith`에서 여러 키 전달 시 모두 같은 슬롯에 있어야 함 (hash tag 필요)

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package io.bluetape4k.redis.lettuce.hll

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodecs
import io.lettuce.core.codec.StringCodec
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LettuceHyperLogLogTest: AbstractRedisLettuceTest() {

    private lateinit var hll: LettuceHyperLogLog<String>

    @BeforeEach
    fun setup() {
        hll = LettuceHyperLogLog(client.connect(StringCodec.UTF8), "hll-${randomName()}")
    }

    @AfterEach
    fun teardown() = hll.close()

    @Test
    fun `add - 새 원소 추가 시 true`() {
        hll.add("a", "b", "c").shouldBeTrue()
    }

    @Test
    fun `count - 추가한 원소 수 근사값 반환`() {
        hll.add("a", "b", "c", "a")  // 중복 포함
        hll.count() shouldBeEqualTo 3L  // HLL 근사: 중복 제거
    }

    @Test
    fun `countWith - 두 HLL 합산 카운트`() {
        val hll2 = LettuceHyperLogLog(client.connect(StringCodec.UTF8), "hll2-${randomName()}")
        hll2.use {
            hll.add("a", "b")
            it.add("c", "d")
            hll.countWith(it) shouldBeEqualTo 4L
        }
    }

    @Test
    fun `mergeWith - 여러 HLL를 dest로 병합`() {
        val hll2 = LettuceHyperLogLog(client.connect(StringCodec.UTF8), "hll2-${randomName()}")
        val dest = "merged-${randomName()}"
        hll2.use {
            hll.add("a", "b")
            it.add("c", "d")
            hll.mergeWith(dest, it)
            // dest HLL의 count 확인은 별도 명령 필요 (이 테스트에서는 예외 없음만 검증)
        }
    }
}
```

- [ ] **Step 2: LettuceHyperLogLog.kt 구현**

```kotlin
package io.bluetape4k.redis.lettuce.hll

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands

/**
 * Lettuce 기반 HyperLogLog (Sync).
 *
 * Redis의 PFADD/PFCOUNT/PFMERGE 명령을 래핑합니다.
 * 카디널리티 추정 오차율 ≈ 0.81%.
 *
 * **클러스터 주의:** [countWith]/[mergeWith]에서 여러 키를 전달할 경우,
 * 모두 동일한 Redis 슬롯에 있어야 합니다 (hash tag 사용 권장).
 *
 * @param V 원소 타입 (connection의 Codec으로 직렬화)
 */
class LettuceHyperLogLog<V: Any>(
    private val connection: StatefulRedisConnection<String, V>,
    val name: String,
): AutoCloseable {

    companion object: KLogging()

    private val commands: RedisCommands<String, V> = connection.sync()

    /**
     * 원소를 추가합니다. HLL 내부 표현이 변경되면 true를 반환합니다.
     */
    fun add(vararg elements: V): Boolean {
        val changed = commands.pfadd(name, *elements) == 1L
        log.debug { "HyperLogLog add: name=$name, elements=${elements.size}, changed=$changed" }
        return changed
    }

    /**
     * 이 HLL의 고유 원소 수 추정값을 반환합니다.
     */
    fun count(): Long = commands.pfcount(name)

    /**
     * 이 HLL과 [others]를 합산한 고유 원소 수 추정값을 반환합니다.
     * HLL 자체는 변경되지 않습니다.
     */
    fun countWith(vararg others: LettuceHyperLogLog<V>): Long {
        val keys = arrayOf(name) + others.map { it.name }.toTypedArray()
        return commands.pfcount(*keys)
    }

    /**
     * 이 HLL과 [others]를 [destName]으로 병합합니다 (PFMERGE).
     */
    fun mergeWith(destName: String, vararg others: LettuceHyperLogLog<V>) {
        val sourceKeys = arrayOf(name) + others.map { it.name }.toTypedArray()
        commands.pfmerge(destName, *sourceKeys)
        log.debug { "HyperLogLog merge: sources=${sourceKeys.toList()} → dest=$destName" }
    }

    override fun close() = connection.close()
}
```

- [ ] **Step 3: LettuceSuspendHyperLogLog.kt 구현**

```kotlin
package io.bluetape4k.redis.lettuce.hll

import io.bluetape4k.logging.KLoggingChannel
import io.bluetape4k.logging.debug
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await

/**
 * Lettuce 기반 HyperLogLog (Coroutine/Suspend).
 */
class LettuceSuspendHyperLogLog<V: Any>(
    private val connection: StatefulRedisConnection<String, V>,
    val name: String,
): AutoCloseable {

    companion object: KLoggingChannel()

    private val asyncCommands: RedisAsyncCommands<String, V> get() = connection.async()

    suspend fun add(vararg elements: V): Boolean {
        val changed = asyncCommands.pfadd(name, *elements).await() == 1L
        log.debug { "SuspendHyperLogLog add: name=$name, changed=$changed" }
        return changed
    }

    suspend fun count(): Long = asyncCommands.pfcount(name).await()

    suspend fun countWith(vararg others: LettuceSuspendHyperLogLog<V>): Long {
        val keys = arrayOf(name) + others.map { it.name }.toTypedArray()
        return asyncCommands.pfcount(*keys).await()
    }

    suspend fun mergeWith(destName: String, vararg others: LettuceSuspendHyperLogLog<V>) {
        val sourceKeys = arrayOf(name) + others.map { it.name }.toTypedArray()
        asyncCommands.pfmerge(destName, *sourceKeys).await()
        log.debug { "SuspendHyperLogLog merge: sources=${sourceKeys.toList()} → dest=$destName" }
    }

    override fun close() = connection.close()
}
```

- [ ] **Step 4: LettuceSuspendHyperLogLogTest.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.hll

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LettuceSuspendHyperLogLogTest: AbstractRedisLettuceTest() {

    private lateinit var hll: LettuceSuspendHyperLogLog<String>

    @BeforeEach
    fun setup() {
        hll = LettuceSuspendHyperLogLog(client.connect(StringCodec.UTF8), "shll-${randomName()}")
    }

    @AfterEach
    fun teardown() = hll.close()

    @Test
    fun `add - count`() = runTest {
        hll.add("x", "y", "z").shouldBeTrue()
        hll.count() shouldBeEqualTo 3L
    }

    @Test
    fun `countWith 두 HLL 합산`() = runTest {
        val hll2 = LettuceSuspendHyperLogLog(client.connect(StringCodec.UTF8), "shll2-${randomName()}")
        hll2.use {
            hll.add("a", "b")
            it.add("c", "d")
            hll.countWith(it) shouldBeEqualTo 4L
        }
    }
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.hll.*" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/hll/ \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/hll/
git commit -m "feat: LettuceHyperLogLog/LettuceSuspendHyperLogLog 구현"
```

---

## Task 2: BloomFilterOptions + LettuceBloomFilter (Sync)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/BloomFilterOptions.kt`
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceBloomFilter.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceBloomFilterTest.kt`

> **Redis 자료구조:**
> - `{name}:config` → Hash { k, m, n, p }
> - `{name}` → BitSet (SETBIT/GETBIT)
>
> **해시 공식:** m = ⌈-n·ln(p) / (ln2)²⌉, k = round(m/n · ln2)
>
> **hash positions:** MurmurHash3-128 → (h1, h2) → positions[i] = (h1 + i·h2) mod m

- [ ] **Step 1: BloomFilterOptions.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.filter

/**
 * BloomFilter 설정 옵션.
 *
 * @param expectedInsertions 예상 삽입 원소 수
 * @param falseProbability 허용 오탐률 (0 < p < 1, 양 끝 제외)
 */
data class BloomFilterOptions(
    val expectedInsertions: Long = 1_000_000L,
    val falseProbability: Double = 0.03,
) {
    companion object {
        val Default = BloomFilterOptions()
    }

    init {
        require(expectedInsertions > 0) { "expectedInsertions must be positive" }
        require(falseProbability > 0.0 && falseProbability < 1.0) {
            "falseProbability must be in (0, 1) exclusive — p=0 and p=1 are mathematically invalid for ln(p)"
        }
    }
}
```

- [ ] **Step 2: 실패 테스트 작성**

```kotlin
package io.bluetape4k.redis.lettuce.filter

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LettuceBloomFilterTest: AbstractRedisLettuceTest() {

    private lateinit var bf: LettuceBloomFilter

    @BeforeEach
    fun setup() {
        bf = LettuceBloomFilter(
            client.connect(StringCodec.UTF8),
            "bf-${randomName()}",
            BloomFilterOptions(expectedInsertions = 1000L, falseProbability = 0.01),
        )
        bf.tryInit()
    }

    @AfterEach
    fun teardown() = bf.close()

    @Test
    fun `BloomFilterOptions - 잘못된 falseProbability 예외`() {
        assertThrows<IllegalArgumentException> { BloomFilterOptions(falseProbability = 0.0) }
        assertThrows<IllegalArgumentException> { BloomFilterOptions(falseProbability = 1.0) }
    }

    @Test
    fun `add - contains true`() {
        bf.add("hello")
        bf.contains("hello").shouldBeTrue()
    }

    @Test
    fun `contains - 없는 원소는 false (오탐 없이)`() {
        bf.add("a")
        bf.contains("definitely-not-added-xyz").shouldBeFalse()
    }

    @Test
    fun `tryInit - 이미 초기화된 경우 false`() {
        bf.tryInit().shouldBeFalse()  // 두 번째 호출: NX로 무시
    }

    @Test
    fun `다량 원소 추가 후 false positive rate 검증`() {
        val n = 500
        (1..n).forEach { bf.add("element-$it") }

        // 추가한 원소는 반드시 true
        (1..n).forEach { bf.contains("element-$it").shouldBeTrue() }

        // 오탐 검사: 1000개 미추가 원소 중 오탐 비율 < 5%
        val falsePositives = (n + 1..n + 1000).count { bf.contains("element-$it") }
        assert(falsePositives < 50) { "false positive rate too high: $falsePositives/1000" }
    }
}
```

- [ ] **Step 3: LettuceBloomFilter.kt 구현**

```kotlin
package io.bluetape4k.redis.lettuce.filter

import com.google.common.hash.Hashing
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Lettuce 기반 분산 Bloom Filter (Sync).
 *
 * MurmurHash3 128bit로 k개 비트 위치를 계산하여 Redis BitSet에 저장합니다.
 * [tryInit] 호출로 초기화 후 [add]/[contains]를 사용합니다.
 *
 * **오탐(false positive):** 가능. 미탐(false negative): 불가능.
 * **원소 삭제:** 지원하지 않음 (삭제가 필요하면 [LettuceCuckooFilter] 사용).
 *
 * @param connection StringCodec 기반 연결
 * @param filterName 필터 이름 (Redis 키 prefix)
 * @param options BloomFilterOptions
 */
class LettuceBloomFilter(
    private val connection: StatefulRedisConnection<String, String>,
    val filterName: String,
    val options: BloomFilterOptions = BloomFilterOptions.Default,
): AutoCloseable {

    companion object: KLogging() {
        // KEYS[1]=bitsetKey / ARGV[1..k]=bit positions (문자열)
        private const val ADD_SCRIPT = """
for i = 1, #ARGV do
    redis.call('setbit', KEYS[1], ARGV[i], 1)
end
return 1"""

        // KEYS[1]=bitsetKey / ARGV[1..k]=bit positions
        private const val CONTAINS_SCRIPT = """
for i = 1, #ARGV do
    if redis.call('getbit', KEYS[1], ARGV[i]) == 0 then return 0 end
end
return 1"""
    }

    private val configKey = "$filterName:config"
    private val commands: RedisCommands<String, String> = connection.sync()

    // 비트 배열 크기 m = ceil(-n * ln(p) / (ln(2))^2)
    val m: Long = ceil(-options.expectedInsertions * ln(options.falseProbability) / ln(2.0).pow(2)).toLong()

    // 해시 함수 수 k = round(m/n * ln(2))
    val k: Int = (m.toDouble() / options.expectedInsertions * ln(2.0)).roundToInt().coerceAtLeast(1)

    /**
     * 필터를 초기화합니다. 이미 초기화된 경우 false를 반환합니다 (NX 멱등성).
     */
    fun tryInit(): Boolean {
        val set = commands.hsetnx(configKey, "k", k.toString())
        if (set) {
            commands.hset(configKey, mapOf(
                "m" to m.toString(),
                "n" to options.expectedInsertions.toString(),
                "p" to options.falseProbability.toString(),
            ))
            log.debug { "BloomFilter 초기화: name=$filterName, m=$m, k=$k" }
        }
        return set
    }

    /**
     * 원소를 추가합니다. String 원소를 UTF-8로 인코딩하여 해시 위치를 계산합니다.
     */
    fun add(element: String) {
        val positions = hashPositions(element)
        commands.eval<Long>(ADD_SCRIPT, ScriptOutputType.INTEGER, arrayOf(filterName), *positions)
        log.debug { "BloomFilter add: name=$filterName, element=$element" }
    }

    /**
     * 원소가 필터에 포함되어 있는지 확인합니다.
     * false이면 확실히 없음. true이면 있거나 오탐.
     */
    fun contains(element: String): Boolean {
        val positions = hashPositions(element)
        return commands.eval<Long>(CONTAINS_SCRIPT, ScriptOutputType.INTEGER, arrayOf(filterName), *positions) == 1L
    }

    override fun close() = connection.close()

    /**
     * MurmurHash3 128bit를 이용해 k개 비트 위치(문자열)를 반환합니다.
     * 공식: positions[i] = |h1 + i * h2| mod m
     */
    private fun hashPositions(element: String): Array<String> {
        val bytes = element.toByteArray(Charsets.UTF_8)
        val hashCode = Hashing.murmur3_128().hashBytes(bytes)
        val hashBytes = hashCode.asBytes()
        val buf = ByteBuffer.wrap(hashBytes).order(ByteOrder.LITTLE_ENDIAN)
        val h1 = buf.getLong()
        val h2 = buf.getLong()
        return Array(k) { i ->
            val pos = Math.floorMod(h1 + i.toLong() * h2, m)
            pos.toString()
        }
    }
}
```

- [ ] **Step 4: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.filter.LettuceBloomFilterTest" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/BloomFilterOptions.kt \
        infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceBloomFilter.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceBloomFilterTest.kt
git commit -m "feat: BloomFilterOptions + LettuceBloomFilter (MurmurHash3, Lua SETBIT) 구현"
```

---

## Task 3: LettuceSuspendBloomFilter (Coroutine)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceSuspendBloomFilter.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceSuspendBloomFilterTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package io.bluetape4k.redis.lettuce.filter

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LettuceSuspendBloomFilterTest: AbstractRedisLettuceTest() {

    private lateinit var bf: LettuceSuspendBloomFilter

    @BeforeEach
    fun setup() = runTest {
        bf = LettuceSuspendBloomFilter(
            client.connect(StringCodec.UTF8),
            "sbf-${randomName()}",
            BloomFilterOptions(expectedInsertions = 1000L, falseProbability = 0.01),
        )
        bf.tryInit()
    }

    @AfterEach
    fun teardown() = bf.close()

    @Test
    fun `add - contains true`() = runTest {
        bf.add("hello")
        bf.contains("hello").shouldBeTrue()
    }

    @Test
    fun `contains - 없는 원소 false`() = runTest {
        bf.contains("not-added-xyz").shouldBeFalse()
    }
}
```

- [ ] **Step 2: LettuceSuspendBloomFilter.kt 구현**

```kotlin
package io.bluetape4k.redis.lettuce.filter

import com.google.common.hash.Hashing
import io.bluetape4k.logging.KLoggingChannel
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Lettuce 기반 분산 Bloom Filter (Coroutine/Suspend).
 */
class LettuceSuspendBloomFilter(
    private val connection: StatefulRedisConnection<String, String>,
    val filterName: String,
    val options: BloomFilterOptions = BloomFilterOptions.Default,
): AutoCloseable {

    companion object: KLoggingChannel() {
        private const val ADD_SCRIPT = """
for i = 1, #ARGV do
    redis.call('setbit', KEYS[1], ARGV[i], 1)
end
return 1"""

        private const val CONTAINS_SCRIPT = """
for i = 1, #ARGV do
    if redis.call('getbit', KEYS[1], ARGV[i]) == 0 then return 0 end
end
return 1"""
    }

    private val configKey = "$filterName:config"
    private val asyncCommands: RedisAsyncCommands<String, String> get() = connection.async()

    val m: Long = ceil(-options.expectedInsertions * ln(options.falseProbability) / ln(2.0).pow(2)).toLong()
    val k: Int = (m.toDouble() / options.expectedInsertions * ln(2.0)).roundToInt().coerceAtLeast(1)

    suspend fun tryInit(): Boolean {
        val set = asyncCommands.hsetnx(configKey, "k", k.toString()).await()
        if (set) {
            asyncCommands.hset(configKey, mapOf(
                "m" to m.toString(),
                "n" to options.expectedInsertions.toString(),
                "p" to options.falseProbability.toString(),
            )).await()
            log.debug { "SuspendBloomFilter 초기화: name=$filterName, m=$m, k=$k" }
        }
        return set
    }

    suspend fun add(element: String) {
        val positions = hashPositions(element)
        asyncCommands.eval<Long>(ADD_SCRIPT, ScriptOutputType.INTEGER, arrayOf(filterName), *positions).await()
    }

    suspend fun contains(element: String): Boolean {
        val positions = hashPositions(element)
        return asyncCommands.eval<Long>(CONTAINS_SCRIPT, ScriptOutputType.INTEGER, arrayOf(filterName), *positions).await() == 1L
    }

    override fun close() = connection.close()

    private fun hashPositions(element: String): Array<String> {
        val bytes = element.toByteArray(Charsets.UTF_8)
        val hashCode = Hashing.murmur3_128().hashBytes(bytes)
        val hashBytes = hashCode.asBytes()
        val buf = ByteBuffer.wrap(hashBytes).order(ByteOrder.LITTLE_ENDIAN)
        val h1 = buf.getLong()
        val h2 = buf.getLong()
        return Array(k) { i -> Math.floorMod(h1 + i.toLong() * h2, m).toString() }
    }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.filter.LettuceSuspendBloomFilterTest" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceSuspendBloomFilter.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceSuspendBloomFilterTest.kt
git commit -m "feat: LettuceSuspendBloomFilter (Coroutine) 구현"
```

---

## Task 4: CuckooFilterOptions + LettuceCuckooFilter (Sync)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/CuckooFilterOptions.kt`
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceCuckooFilter.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceCuckooFilterTest.kt`

> **Redis 자료구조:**
> - `{name}:config` → Hash { capacity, bucketSize, numBuckets, count }
> - `{name}:buckets` → Hash { bucketIdx(1-based 문자열) → "fp1,fp2,fp3,fp4" (CSV) }
>
> **fingerprint 계산:**
> - `fp = |MurmurHash3(element).h1| % 255 + 1`  (1~255, 0 제외)
> - `i1 = |h1| % numBuckets` (0-based)
> - `i2 = i1 XOR (fp_hash % numBuckets)` (fp_hash: fp를 정수로 취급한 단순 해시)
> - Redis Hash key: i1+1, i2+1 (1-based 문자열)
>
> **삽입 실패:** maxIterations 초과 시 false 반환 (필터 포화)
>
> **Lua XOR:** `bit.bxor(a, b)` 사용 (Redis 6.x Lua 5.1 호환)

- [ ] **Step 1: CuckooFilterOptions.kt 작성**

```kotlin
package io.bluetape4k.redis.lettuce.filter

/**
 * CuckooFilter 설정 옵션.
 *
 * @param capacity 최대 원소 수 (버킷 수 = capacity / bucketSize)
 * @param bucketSize 버킷당 슬롯 수 (2^n 권장)
 * @param maxIterations kick-out 재배치 최대 반복 횟수
 */
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

- [ ] **Step 2: 실패 테스트 작성**

```kotlin
package io.bluetape4k.redis.lettuce.filter

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LettuceCuckooFilterTest: AbstractRedisLettuceTest() {

    private lateinit var cf: LettuceCuckooFilter

    @BeforeEach
    fun setup() {
        cf = LettuceCuckooFilter(
            client.connect(StringCodec.UTF8),
            "cf-${randomName()}",
            CuckooFilterOptions(capacity = 1000L, bucketSize = 4),
        )
        cf.tryInit()
    }

    @AfterEach
    fun teardown() = cf.close()

    @Test
    fun `CuckooFilterOptions - 잘못된 bucketSize 예외`() {
        assertThrows<IllegalArgumentException> { CuckooFilterOptions(bucketSize = 0) }
        assertThrows<IllegalArgumentException> { CuckooFilterOptions(bucketSize = 9) }
    }

    @Test
    fun `insert - contains true`() {
        cf.insert("hello").shouldBeTrue()
        cf.contains("hello").shouldBeTrue()
    }

    @Test
    fun `contains - 없는 원소 false`() {
        cf.contains("not-inserted").shouldBeFalse()
    }

    @Test
    fun `delete - 삽입 후 삭제, 이후 contains false`() {
        cf.insert("world")
        cf.delete("world").shouldBeTrue()
        cf.contains("world").shouldBeFalse()
    }

    @Test
    fun `delete - 없는 원소 삭제 시 false`() {
        cf.delete("ghost").shouldBeFalse()
    }

    @Test
    fun `count - 삽입/삭제에 따른 카운트`() {
        cf.insert("a")
        cf.insert("b")
        cf.insert("a")  // 중복 삽입 (CuckooFilter는 중복 허용)
        assert(cf.count() >= 2L) { "count should be at least 2" }
        cf.delete("a")
        assert(cf.count() >= 1L) { "count should be at least 1 after delete" }
    }
}
```

- [ ] **Step 3: LettuceCuckooFilter.kt 구현**

```kotlin
package io.bluetape4k.redis.lettuce.filter

import com.google.common.hash.Hashing
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lettuce 기반 분산 Cuckoo Filter (Sync).
 *
 * BloomFilter와 달리 원소 **삭제**를 지원합니다. 오탐 가능, 미탐 없음.
 * fingerprint 이중 버킷 방식으로 원소를 Redis Hash에 저장합니다.
 *
 * **주의:** 동일 원소의 중복 삽입이 가능합니다. 삭제는 하나의 fingerprint만 제거합니다.
 *
 * @param connection StringCodec 기반 연결
 * @param filterName 필터 이름 (Redis 키 prefix)
 * @param options CuckooFilterOptions
 */
class LettuceCuckooFilter(
    private val connection: StatefulRedisConnection<String, String>,
    val filterName: String,
    val options: CuckooFilterOptions = CuckooFilterOptions.Default,
): AutoCloseable {

    companion object: KLogging() {
        // KEYS[1]=bucketsKey, KEYS[2]=configKey
        // ARGV[1]=fp, ARGV[2]=i1(1-based), ARGV[3]=i2(1-based),
        // ARGV[4]=bucketSize, ARGV[5]=maxIterations, ARGV[6]=numBuckets
        private const val INSERT_SCRIPT = """
local fp = ARGV[1]
local i1 = tonumber(ARGV[2])
local i2 = tonumber(ARGV[3])
local bucketSize = tonumber(ARGV[4])
local maxIter = tonumber(ARGV[5])
local numBuckets = tonumber(ARGV[6])

local function getSlots(idx)
    local val = redis.call('hget', KEYS[1], tostring(idx))
    if not val or val == '' then return {} end
    local t = {}
    for s in string.gmatch(val, '[^,]+') do t[#t+1] = s end
    return t
end

local function setSlots(idx, t)
    if #t == 0 then redis.call('hdel', KEYS[1], tostring(idx))
    else redis.call('hset', KEYS[1], tostring(idx), table.concat(t, ',')) end
end

local function tryAdd(idx, fp)
    local t = getSlots(idx)
    if #t < bucketSize then t[#t+1] = fp; setSlots(idx, t); return true end
    return false
end

if tryAdd(i1, fp) then redis.call('hincrby', KEYS[2], 'count', 1); return 1 end
if tryAdd(i2, fp) then redis.call('hincrby', KEYS[2], 'count', 1); return 1 end

-- kick-out 재배치
local cur = i1
local cur_fp = fp
for iter = 1, maxIter do
    local t = getSlots(cur)
    local kick_pos = (iter % #t) + 1
    local kicked = t[kick_pos]
    t[kick_pos] = cur_fp
    setSlots(cur, t)

    -- alt index: bit.bxor(cur-1, fp_hash % numBuckets) + 1
    local fp_num = tonumber(kicked) or 0
    local fp_hash = math.abs(fp_num * 2654435761) % numBuckets
    local alt = bit.bxor(cur - 1, fp_hash) % numBuckets + 1

    if tryAdd(alt, kicked) then redis.call('hincrby', KEYS[2], 'count', 1); return 1 end
    cur = alt
    cur_fp = kicked
end
return 0"""

        // KEYS[1]=bucketsKey, KEYS[2]=configKey
        // ARGV[1]=fp, ARGV[2]=i1(1-based), ARGV[3]=i2(1-based)
        private const val CONTAINS_SCRIPT = """
local fp = ARGV[1]
local i1 = tostring(ARGV[2])
local i2 = tostring(ARGV[3])

local function bucketContains(idx, fp)
    local val = redis.call('hget', KEYS[1], idx)
    if not val then return false end
    for s in string.gmatch(val, '[^,]+') do
        if s == fp then return true end
    end
    return false
end

if bucketContains(i1, fp) or bucketContains(i2, fp) then return 1 end
return 0"""

        // KEYS[1]=bucketsKey, KEYS[2]=configKey
        // ARGV[1]=fp, ARGV[2]=i1(1-based), ARGV[3]=i2(1-based)
        private const val DELETE_SCRIPT = """
local fp = ARGV[1]
local i1 = tostring(ARGV[2])
local i2 = tostring(ARGV[3])

local function bucketRemove(idx, fp)
    local val = redis.call('hget', KEYS[1], idx)
    if not val then return false end
    local t = {}
    local found = false
    for s in string.gmatch(val, '[^,]+') do
        if s == fp and not found then found = true
        else t[#t+1] = s end
    end
    if found then
        if #t == 0 then redis.call('hdel', KEYS[1], idx)
        else redis.call('hset', KEYS[1], idx, table.concat(t, ',')) end
    end
    return found
end

if bucketRemove(i1, fp) then redis.call('hincrby', KEYS[2], 'count', -1); return 1 end
if bucketRemove(i2, fp) then redis.call('hincrby', KEYS[2], 'count', -1); return 1 end
return 0"""
    }

    private val bucketsKey = "$filterName:buckets"
    private val configKey = "$filterName:config"

    // 버킷 수 = capacity / bucketSize (올림)
    val numBuckets: Long = (options.capacity + options.bucketSize - 1) / options.bucketSize

    private val commands: RedisCommands<String, String> = connection.sync()

    /**
     * 필터를 초기화합니다. 이미 초기화된 경우 false를 반환합니다 (NX 멱등성).
     */
    fun tryInit(): Boolean {
        val set = commands.hsetnx(configKey, "capacity", options.capacity.toString())
        if (set) {
            commands.hset(configKey, mapOf(
                "bucketSize" to options.bucketSize.toString(),
                "numBuckets" to numBuckets.toString(),
                "count" to "0",
            ))
            log.debug { "CuckooFilter 초기화: name=$filterName, numBuckets=$numBuckets" }
        }
        return set
    }

    /**
     * 원소를 삽입합니다. 성공 시 true, 필터 포화 시 false.
     */
    fun insert(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return commands.eval<Long>(
            INSERT_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString(),
            options.bucketSize.toString(), options.maxIterations.toString(), numBuckets.toString()
        ) == 1L
    }

    /**
     * 원소가 필터에 포함되어 있는지 확인합니다.
     */
    fun contains(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return commands.eval<Long>(
            CONTAINS_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString()
        ) == 1L
    }

    /**
     * 원소를 삭제합니다. 존재하지 않으면 false.
     */
    fun delete(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return commands.eval<Long>(
            DELETE_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString()
        ) == 1L
    }

    /**
     * 필터에 삽입된 원소 수 (근사).
     */
    fun count(): Long = commands.hget(configKey, "count")?.toLongOrNull() ?: 0L

    override fun close() = connection.close()

    private data class FingerprintData(val fp: Int, val i1: Long, val i2: Long)

    private fun fingerprint(element: String): FingerprintData {
        val bytes = element.toByteArray(Charsets.UTF_8)
        val hashCode = Hashing.murmur3_128().hashBytes(bytes)
        val hashBytes = hashCode.asBytes()
        val buf = ByteBuffer.wrap(hashBytes).order(ByteOrder.LITTLE_ENDIAN)
        val h1 = buf.getLong()
        val h2 = buf.getLong()

        val fp = (Math.abs(h1.toInt()) % 255) + 1  // 1..255
        val i1 = Math.floorMod(h1, numBuckets) + 1   // 1-based
        // i2 = 1 + (i1-1 XOR fp_hash) mod numBuckets
        val fpHash = Math.abs(fp.toLong() * 2654435761L) % numBuckets
        val i2 = Math.floorMod((i1 - 1) xor fpHash, numBuckets) + 1  // 1-based

        return FingerprintData(fp, i1, i2)
    }
}
```

- [ ] **Step 4: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.filter.LettuceCuckooFilterTest" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/CuckooFilterOptions.kt \
        infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceCuckooFilter.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceCuckooFilterTest.kt
git commit -m "feat: CuckooFilterOptions + LettuceCuckooFilter (Lua 이중 버킷) 구현"
```

---

## Task 5: LettuceSuspendCuckooFilter (Coroutine)

**Files:**
- Create: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceSuspendCuckooFilter.kt`
- Create: `infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceSuspendCuckooFilterTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package io.bluetape4k.redis.lettuce.filter

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LettuceSuspendCuckooFilterTest: AbstractRedisLettuceTest() {

    private lateinit var cf: LettuceSuspendCuckooFilter

    @BeforeEach
    fun setup() = runTest {
        cf = LettuceSuspendCuckooFilter(
            client.connect(StringCodec.UTF8),
            "scf-${randomName()}",
            CuckooFilterOptions(capacity = 1000L, bucketSize = 4),
        )
        cf.tryInit()
    }

    @AfterEach
    fun teardown() = cf.close()

    @Test
    fun `insert - contains true`() = runTest {
        cf.insert("hello").shouldBeTrue()
        cf.contains("hello").shouldBeTrue()
    }

    @Test
    fun `contains - 없는 원소 false`() = runTest {
        cf.contains("ghost").shouldBeFalse()
    }

    @Test
    fun `delete - 삭제 후 false`() = runTest {
        cf.insert("world")
        cf.delete("world").shouldBeTrue()
        cf.contains("world").shouldBeFalse()
    }
}
```

- [ ] **Step 2: LettuceSuspendCuckooFilter.kt 구현**

```kotlin
package io.bluetape4k.redis.lettuce.filter

import com.google.common.hash.Hashing
import io.bluetape4k.logging.KLoggingChannel
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lettuce 기반 분산 Cuckoo Filter (Coroutine/Suspend).
 * [LettuceCuckooFilter]와 동일한 Lua 스크립트 사용, suspend 방식으로 호출.
 */
class LettuceSuspendCuckooFilter(
    private val connection: StatefulRedisConnection<String, String>,
    val filterName: String,
    val options: CuckooFilterOptions = CuckooFilterOptions.Default,
): AutoCloseable {

    companion object: KLoggingChannel() {
        // LettuceCuckooFilter와 동일한 스크립트 (중복 제거를 위해 companion에서 재사용)
        private const val INSERT_SCRIPT = LettuceCuckooFilter.INSERT_SCRIPT_CONST
        private const val CONTAINS_SCRIPT = LettuceCuckooFilter.CONTAINS_SCRIPT_CONST
        private const val DELETE_SCRIPT = LettuceCuckooFilter.DELETE_SCRIPT_CONST
    }

    private val bucketsKey = "$filterName:buckets"
    private val configKey = "$filterName:config"
    val numBuckets: Long = (options.capacity + options.bucketSize - 1) / options.bucketSize

    private val asyncCommands: RedisAsyncCommands<String, String> get() = connection.async()

    suspend fun tryInit(): Boolean {
        val set = asyncCommands.hsetnx(configKey, "capacity", options.capacity.toString()).await()
        if (set) {
            asyncCommands.hset(configKey, mapOf(
                "bucketSize" to options.bucketSize.toString(),
                "numBuckets" to numBuckets.toString(),
                "count" to "0",
            )).await()
            log.debug { "SuspendCuckooFilter 초기화: name=$filterName" }
        }
        return set
    }

    suspend fun insert(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return asyncCommands.eval<Long>(
            INSERT_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString(),
            options.bucketSize.toString(), options.maxIterations.toString(), numBuckets.toString()
        ).await() == 1L
    }

    suspend fun contains(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return asyncCommands.eval<Long>(
            CONTAINS_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString()
        ).await() == 1L
    }

    suspend fun delete(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return asyncCommands.eval<Long>(
            DELETE_SCRIPT, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString()
        ).await() == 1L
    }

    suspend fun count(): Long = asyncCommands.hget(configKey, "count").await()?.toLongOrNull() ?: 0L

    override fun close() = connection.close()

    private data class FingerprintData(val fp: Int, val i1: Long, val i2: Long)

    private fun fingerprint(element: String): FingerprintData {
        val bytes = element.toByteArray(Charsets.UTF_8)
        val hashCode = Hashing.murmur3_128().hashBytes(bytes)
        val hashBytes = hashCode.asBytes()
        val buf = ByteBuffer.wrap(hashBytes).order(ByteOrder.LITTLE_ENDIAN)
        val h1 = buf.getLong()
        val h2 = buf.getLong()
        val fp = (Math.abs(h1.toInt()) % 255) + 1
        val i1 = Math.floorMod(h1, numBuckets) + 1
        val fpHash = Math.abs(fp.toLong() * 2654435761L) % numBuckets
        val i2 = Math.floorMod((i1 - 1) xor fpHash, numBuckets) + 1
        return FingerprintData(fp, i1, i2)
    }
}
```

> **구현 노트:** `LettuceSuspendCuckooFilter`에서 `INSERT_SCRIPT_CONST` 등을 참조하기 위해
> `LettuceCuckooFilter.companion`에 `const val INSERT_SCRIPT_CONST = "..."` 형태로
> Lua 스크립트를 `internal const val`로 노출해야 합니다. 또는 스크립트를 별도 object로 추출합니다:
>
> ```kotlin
> // CuckooFilterScripts.kt
> internal object CuckooFilterScripts {
>     const val INSERT = "..."
>     const val CONTAINS = "..."
>     const val DELETE = "..."
> }
> ```
>
> 두 클래스 모두 `CuckooFilterScripts.INSERT` 등을 참조하도록 수정합니다.

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.filter.LettuceSuspendCuckooFilterTest" 2>&1 | tail -15
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 4: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceSuspendCuckooFilter.kt \
        infra/redis-lettuce/src/test/kotlin/io/bluetape4k/redis/lettuce/filter/LettuceSuspendCuckooFilterTest.kt
git commit -m "feat: LettuceSuspendCuckooFilter (Coroutine) 구현"
```

---

## Task 6: LettuceFeatures factory 확장함수 추가

**Files:**
- Modify: `infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/LettuceFeatures.kt`

- [ ] **Step 1: HyperLogLog / BloomFilter / CuckooFilter factory 추가**

```kotlin
// LettuceFeatures.kt에 추가

import io.bluetape4k.redis.lettuce.filter.BloomFilterOptions
import io.bluetape4k.redis.lettuce.filter.CuckooFilterOptions
import io.bluetape4k.redis.lettuce.filter.LettuceCuckooFilter
import io.bluetape4k.redis.lettuce.filter.LettuceSuspendBloomFilter
import io.bluetape4k.redis.lettuce.filter.LettuceSuspendCuckooFilter
import io.bluetape4k.redis.lettuce.filter.LettuceBloomFilter
import io.bluetape4k.redis.lettuce.hll.LettuceHyperLogLog
import io.bluetape4k.redis.lettuce.hll.LettuceSuspendHyperLogLog
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec

// ========== HyperLogLog ==========

/**
 * HyperLogLog (Sync) 생성.
 */
fun <V: Any> RedisClient.hyperLogLog(
    name: String,
    codec: RedisCodec<String, V> = LettuceBinaryCodecs.lz4Fory(),
): LettuceHyperLogLog<V> = LettuceHyperLogLog(connect(codec), name)

/**
 * HyperLogLog (Coroutine/Suspend) 생성.
 */
fun <V: Any> RedisClient.suspendHyperLogLog(
    name: String,
    codec: RedisCodec<String, V> = LettuceBinaryCodecs.lz4Fory(),
): LettuceSuspendHyperLogLog<V> = LettuceSuspendHyperLogLog(connect(codec), name)

// ========== BloomFilter ==========

/**
 * BloomFilter (Sync) 생성. [LettuceBloomFilter.tryInit] 호출 필요.
 */
fun RedisClient.bloomFilter(
    name: String,
    options: BloomFilterOptions = BloomFilterOptions.Default,
): LettuceBloomFilter = LettuceBloomFilter(connect(StringCodec.UTF8), name, options)

/**
 * BloomFilter (Coroutine/Suspend) 생성.
 */
fun RedisClient.suspendBloomFilter(
    name: String,
    options: BloomFilterOptions = BloomFilterOptions.Default,
): LettuceSuspendBloomFilter = LettuceSuspendBloomFilter(connect(StringCodec.UTF8), name, options)

// ========== CuckooFilter ==========

/**
 * CuckooFilter (Sync) 생성. [LettuceCuckooFilter.tryInit] 호출 필요.
 */
fun RedisClient.cuckooFilter(
    name: String,
    options: CuckooFilterOptions = CuckooFilterOptions.Default,
): LettuceCuckooFilter = LettuceCuckooFilter(connect(StringCodec.UTF8), name, options)

/**
 * CuckooFilter (Coroutine/Suspend) 생성.
 */
fun RedisClient.suspendCuckooFilter(
    name: String,
    options: CuckooFilterOptions = CuckooFilterOptions.Default,
): LettuceSuspendCuckooFilter = LettuceSuspendCuckooFilter(connect(StringCodec.UTF8), name, options)
```

- [ ] **Step 2: 전체 filter + hll 테스트 실행**

```bash
./gradlew :infra:redis-lettuce:test --tests "io.bluetape4k.redis.lettuce.hll.*" \
    --tests "io.bluetape4k.redis.lettuce.filter.*" 2>&1 | tail -20
```

기대: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋**

```bash
git add infra/redis-lettuce/src/main/kotlin/io/bluetape4k/redis/lettuce/LettuceFeatures.kt
git commit -m "feat: LettuceFeatures HyperLogLog/BloomFilter/CuckooFilter factory 확장함수 추가"
```
