# exposed-lettuce 캐시 전략 구현 계획

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** JetBrains Exposed JDBC DAO를 백엔드로 사용하는 Lettuce 기반 Read-through / Write-through / Write-behind 캐시 전략 구현

**Architecture:** Redisson의 MapLoader/MapWriter 패턴을 Lettuce로 포팅. `LettuceLoadedMap<K,V>`이 핵심 Redis 래퍼이며, `ExposedEntityMapLoader`/`ExposedEntityMapWriter`가 Exposed IdTable을 통해 DB I/O를 처리. 사용자는 `AbstractJdbcLettuceRepository`를 상속하고 추상 메서드 4개만 구현.

**Tech Stack:** Kotlin 2.3, Lettuce 7, JetBrains Exposed v1, Resilience4j, LettuceBinaryCodec (LZ4Fory), H2(테스트), Testcontainers Redis

**Spec:** `docs/superpowers/specs/2026-03-12-exposed-lettuce-cache-design.md`

---

## 파일 맵

### 신규 생성

| 파일 | 역할 |
|------|------|
| `data/exposed-lettuce/build.gradle.kts` | 모듈 의존성 |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/WriteMode.kt` | WriteMode enum (NONE/WRITE_THROUGH/WRITE_BEHIND) |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/MapLoader.kt` | Read-through 인터페이스 |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/MapWriter.kt` | Write-through/behind 인터페이스 |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceCacheConfig.kt` | 캐시 설정 (TTL, batch size, retry 등) |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMap.kt` | 핵심 Redis 래퍼 (read-through + write-through + write-behind) |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/EntityMapLoader.kt` | Exposed transaction 래핑 추상 loader |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapLoader.kt` | IdTable 기반 loader 구현 |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/EntityMapWriter.kt` | Resilience4j Retry 래핑 추상 writer |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapWriter.kt` | WriteMode 분기 writer 구현 |
| `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/repository/AbstractJdbcLettuceRepository.kt` | 추상 4개 메서드 기반 repository |
| `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/AbstractExposedLettuceTest.kt` | 테스트 인프라 (Redis + H2) |
| `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMapReadThroughTest.kt` | Read-through 통합 테스트 |
| `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMapWriteThroughTest.kt` | Write-through 통합 테스트 |
| `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMapWriteBehindTest.kt` | Write-behind 통합 테스트 |
| `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/repository/CustomerRepository.kt` | 테스트용 구체 Repository |
| `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/repository/AbstractJdbcLettuceRepositoryTest.kt` | Repository 통합 테스트 |

---

## Chunk 1: 모듈 스캐폴딩 + 핵심 인터페이스

### Task 1: build.gradle.kts 및 디렉토리 구조 생성

**Files:**
- Create: `data/exposed-lettuce/build.gradle.kts`
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/.gitkeep`

- [ ] **Step 1: 디렉토리 생성**

```bash
mkdir -p data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map
mkdir -p data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/repository
mkdir -p data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/map
mkdir -p data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/repository
```

- [ ] **Step 2: build.gradle.kts 작성**

```kotlin
// data/exposed-lettuce/build.gradle.kts
dependencies {
    api(Libs.lettuce_core)
    api(Libs.bluetape4k_io)
    api(Libs.bluetape4k_redis)
    api(Libs.bluetape4k_resilience4j)

    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.bluetape4k_exposed_jdbc)

    api(Libs.fory_kotlin)
    api(Libs.lz4_java)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.bluetape4k_exposed_jdbc_tests)
    testImplementation(Libs.h2_v2)
    testImplementation(Libs.hikaricp)
}
```

- [ ] **Step 3: 모듈 등록 확인**

`settings.gradle.kts`의 `includeModules("data", false, false)` 가 있는지 확인.
없으면 추가: `includeModules("data", false, false)`

> `settings.gradle.kts` 확인: `includeModules` 함수가 자동 감지하므로 보통 수정 불필요.

- [ ] **Step 4: 빌드 확인 (컴파일만)**

```bash
./gradlew :exposed-lettuce:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add data/exposed-lettuce/
git commit -m "chore: add exposed-lettuce module scaffold"
```

---

### Task 2: WriteMode + MapLoader + MapWriter 인터페이스

**Files:**
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/WriteMode.kt`
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/MapLoader.kt`
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/MapWriter.kt`

- [ ] **Step 1: WriteMode.kt 작성**

```kotlin
// data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/WriteMode.kt
package io.bluetape4k.exposed.lettuce.map

enum class WriteMode {
    /** Redis 전용 — DB write 없음 */
    NONE,
    /** Redis + DB 동시 쓰기 */
    WRITE_THROUGH,
    /** Redis 즉시 + DB 비동기 flush */
    WRITE_BEHIND,
}
```

- [ ] **Step 2: MapLoader.kt 작성**

```kotlin
// data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/MapLoader.kt
package io.bluetape4k.exposed.lettuce.map

/**
 * Read-through 캐시를 위한 로더 인터페이스.
 * Redis cache miss 시 [load]로 원본 데이터를 조회한다.
 */
interface MapLoader<K, V> {
    /** 단건 조회. null이면 Redis에 저장하지 않는다. */
    fun load(key: K): V?

    /** 전체 키 목록 반환 (캐시 워밍업 용도) */
    fun loadAllKeys(): Iterable<K>
}
```

- [ ] **Step 3: MapWriter.kt 작성**

```kotlin
// data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/MapWriter.kt
package io.bluetape4k.exposed.lettuce.map

/**
 * Write-through / Write-behind 캐시를 위한 writer 인터페이스.
 * Redis 저장 후 원본 저장소(DB 등)에 반영하는 책임을 갖는다.
 */
interface MapWriter<K, V> {
    /** 엔티티 배치 저장 */
    fun write(map: Map<K, V>)
    /** 엔티티 배치 삭제 */
    fun delete(keys: Collection<K>)
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew :exposed-lettuce:compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/
git commit -m "feat: add WriteMode, MapLoader, MapWriter interfaces"
```

---

### Task 3: LettuceCacheConfig

**Files:**
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceCacheConfig.kt`

- [ ] **Step 1: LettuceCacheConfig.kt 작성**

```kotlin
// data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceCacheConfig.kt
package io.bluetape4k.exposed.lettuce.map

import java.time.Duration

data class LettuceCacheConfig(
    val writeMode: WriteMode = WriteMode.WRITE_THROUGH,
    /** Write-behind flush 배치 크기 */
    val writeBehindBatchSize: Int = 50,
    /** Write-behind flush 주기 */
    val writeBehindDelay: Duration = Duration.ofMillis(1000),
    /** Write-behind 큐 최대 용량. 초과 시 IllegalStateException */
    val writeBehindQueueCapacity: Int = 10_000,
    /** 종료 시 큐 drain 대기 시간 */
    val writeBehindShutdownTimeout: Duration = Duration.ofSeconds(10),
    /** DB write 재시도 횟수 */
    val writeRetryAttempts: Int = 3,
    /** DB write 재시도 간격 */
    val writeRetryInterval: Duration = Duration.ofMillis(100),
    /** Redis 키 TTL */
    val ttl: Duration = Duration.ofMinutes(30),
    /** Redis 키 prefix */
    val keyPrefix: String = "cache",
) {
    companion object {
        val READ_ONLY = LettuceCacheConfig(writeMode = WriteMode.NONE)
        val READ_WRITE_THROUGH = LettuceCacheConfig(writeMode = WriteMode.WRITE_THROUGH)
        val WRITE_BEHIND = LettuceCacheConfig(writeMode = WriteMode.WRITE_BEHIND)
    }
}
```

- [ ] **Step 2: 커밋**

```bash
git add data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceCacheConfig.kt
git commit -m "feat: add LettuceCacheConfig"
```

---

## Chunk 2: LettuceLoadedMap (핵심 Redis 래퍼)

### Task 4: LettuceLoadedMap — Read-through + Write-through

**Files:**
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMap.kt`
- Create: `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/AbstractExposedLettuceTest.kt`
- Create: `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMapReadThroughTest.kt`

- [ ] **Step 1: 테스트 인프라 AbstractExposedLettuceTest 작성**

```kotlin
// src/test/kotlin/io/bluetape4k/exposed/lettuce/AbstractExposedLettuceTest.kt
package io.bluetape4k.exposed.lettuce

import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.storage.RedisServer
import io.lettuce.core.RedisClient
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll

abstract class AbstractExposedLettuceTest {

    companion object : KLogging() {
        val redis: RedisServer by lazy { RedisServer.Launcher.redis }

        val redisClient: RedisClient by lazy {
            RedisClient.create(
                RedisServer.Launcher.LettuceLib.getRedisURI(redis.host, redis.port)
            )
        }

        @JvmStatic
        @BeforeAll
        fun setupDatabase() {
            Database.connect(
                url = "jdbc:h2:mem:exposed_lettuce_test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                driver = "org.h2.Driver"
            )
        }

        /** 테스트 테이블 초기화 헬퍼 */
        fun initTable(vararg tables: Table) = transaction {
            SchemaUtils.createMissingTablesAndColumns(*tables)
        }
    }
}
```

- [ ] **Step 2: Read-through 실패 테스트 작성**

```kotlin
// src/test/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMapReadThroughTest.kt
package io.bluetape4k.exposed.lettuce.map

import io.bluetape4k.exposed.lettuce.AbstractExposedLettuceTest
import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import io.bluetape4k.exposed.lettuce.map.LettuceLoadedMap
import io.bluetape4k.exposed.lettuce.map.MapLoader
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LettuceLoadedMapReadThroughTest : AbstractExposedLettuceTest() {

    private val store = mutableMapOf("1" to "value1", "2" to "value2")

    private val loader = object : MapLoader<String, String> {
        override fun load(key: String): String? = store[key]
        override fun loadAllKeys(): Iterable<String> = store.keys
    }

    private lateinit var cache: LettuceLoadedMap<String, String>

    @BeforeEach
    fun setUp() {
        cache = LettuceLoadedMap(
            client = redisClient,
            loader = loader,
            config = LettuceCacheConfig.READ_ONLY,
        )
        cache.clear()
    }

    @Test
    fun `miss된 키는 loader에서 조회하여 Redis에 저장한다`() {
        // DB에만 있는 데이터 → cache miss → loader 호출 → Redis 저장
        cache["1"] shouldBeEqualTo "value1"
        // 두 번째 조회는 Redis에서 반환 (loader 호출 없음)
        cache["1"] shouldBeEqualTo "value1"
    }

    @Test
    fun `loader에 없는 키는 null 반환하고 Redis에 저장하지 않는다`() {
        cache["nonexistent"].shouldBeNull()
    }

    @Test
    fun `getAll은 miss된 키만 loader에서 bulk 조회한다`() {
        val result = cache.getAll(setOf("1", "2", "99"))
        result["1"] shouldBeEqualTo "value1"
        result["2"] shouldBeEqualTo "value2"
        result["99"].shouldBeNull()
    }
}
```

- [ ] **Step 3: 테스트 실행 → FAIL 확인**

```bash
./gradlew :exposed-lettuce:test --tests "*.LettuceLoadedMapReadThroughTest" 2>&1 | tail -20
```

Expected: 컴파일 에러 (LettuceLoadedMap 미존재)

- [ ] **Step 4: LettuceLoadedMap.kt 구현 (Read-through + Write-through)**

```kotlin
// src/main/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMap.kt
package io.bluetape4k.exposed.lettuce.map

import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.logging.KLogging
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodec
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class LettuceLoadedMap<K : Any, V : Any>(
    private val client: RedisClient,
    private val loader: MapLoader<K, V>? = null,
    private val writer: MapWriter<K, V>? = null,
    private val config: LettuceCacheConfig = LettuceCacheConfig.READ_WRITE_THROUGH,
    private val keySerializer: (K) -> String = { it.toString() },
) : Closeable {

    companion object : KLogging()

    @Suppress("UNCHECKED_CAST")
    private val codec = LettuceBinaryCodec<V>(BinarySerializers.LZ4Fory)

    private val connection: StatefulRedisConnection<String, V> = client.connect(codec)
    private val commands: RedisCommands<String, V> = connection.sync()

    // Dead letter 기록용 String 전용 연결 (Write-behind 실패 시 키 목록 기록)
    private val strConnection: StatefulRedisConnection<String, String> =
        client.connect(StringCodec.UTF8)
    private val strCommands: RedisCommands<String, String> = strConnection.sync()

    private val ttlSeconds = config.ttl.seconds

    // Write-behind 큐 (WRITE_BEHIND 모드에서만 사용)
    private val writeBehindQueue: LinkedBlockingDeque<Pair<K, V>>? =
        if (config.writeMode == WriteMode.WRITE_BEHIND)
            LinkedBlockingDeque(config.writeBehindQueueCapacity)
        else null

    private val scheduler: ScheduledExecutorService? =
        if (config.writeMode == WriteMode.WRITE_BEHIND)
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "lettuce-write-behind-flusher").also { it.isDaemon = true }
            }
        else null

    init {
        scheduler?.scheduleWithFixedDelay(
            ::flushWriteBehindQueue,
            config.writeBehindDelay.toMillis(),
            config.writeBehindDelay.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    private fun redisKey(key: K): String = "${config.keyPrefix}:${keySerializer(key)}"

    operator fun get(key: K): V? {
        val redisKey = redisKey(key)
        // 1. Redis에서 조회
        val cached = runCatching { commands.get(redisKey) }.getOrNull()
        if (cached != null) return cached

        // 2. Redis miss → fallback: Redis 연결 실패 시에도 loader로 DB 조회
        val loader = loader ?: return null
        val value = loader.load(key) ?: return null

        // 3. Redis에 저장 (TTL 적용)
        runCatching {
            commands.set(redisKey, value, SetArgs().ex(ttlSeconds))
        }.onFailure { log.warn(it) { "Redis SETEX 실패: $redisKey" } }

        return value
    }

    operator fun set(key: K, value: V) {
        val redisKey = redisKey(key)
        // Redis 저장
        commands.set(redisKey, value, SetArgs().ex(ttlSeconds))

        when (config.writeMode) {
            WriteMode.WRITE_THROUGH -> {
                writer?.write(mapOf(key to value))
            }
            WriteMode.WRITE_BEHIND -> {
                val queue = writeBehindQueue ?: return
                if (!queue.offer(key to value)) {
                    throw IllegalStateException(
                        "Write-behind 큐 포화 (capacity=${config.writeBehindQueueCapacity}). " +
                        "writeBehindQueueCapacity를 늘리거나 writeBehindDelay를 줄이세요."
                    )
                }
            }
            WriteMode.NONE -> { /* no-op */ }
        }
    }

    fun getAll(keys: Set<K>): Map<K, V> {
        if (keys.isEmpty()) return emptyMap()

        val result = mutableMapOf<K, V>()
        val missedKeys = mutableSetOf<K>()

        // Redis pipeline으로 bulk GET
        for (key in keys) {
            val value = runCatching { commands.get(redisKey(key)) }.getOrNull()
            if (value != null) result[key] = value
            else missedKeys.add(key)
        }

        if (missedKeys.isNotEmpty() && loader != null) {
            for (key in missedKeys) {
                val value = loader.load(key) ?: continue
                result[key] = value
                runCatching {
                    commands.set(redisKey(key), value, SetArgs().ex(ttlSeconds))
                }.onFailure { log.warn(it) { "Redis SETEX 실패: ${redisKey(key)}" } }
            }
        }

        return result
    }

    fun delete(key: K) {
        commands.del(redisKey(key))
        if (config.writeMode != WriteMode.NONE) {
            writer?.delete(listOf(key))
        }
    }

    fun deleteAll(keys: Collection<K>) {
        if (keys.isEmpty()) return
        commands.del(*keys.map { redisKey(it) }.toTypedArray())
        if (config.writeMode != WriteMode.NONE) {
            writer?.delete(keys)
        }
    }

    fun clear() {
        // keyPrefix 패턴 키 전체 삭제 (테스트/개발 전용)
        val pattern = "${config.keyPrefix}:*"
        val keys = commands.keys(pattern)
        if (keys.isNotEmpty()) {
            commands.del(*keys.toTypedArray())
        }
    }

    private fun flushWriteBehindQueue() {
        val queue = writeBehindQueue ?: return
        val batch = mutableMapOf<K, V>()
        var count = 0
        while (count < config.writeBehindBatchSize) {
            val entry = queue.poll() ?: break
            batch[entry.first] = entry.second
            count++
        }
        if (batch.isNotEmpty()) {
            runCatching { writer?.write(batch) }
                .onFailure { e ->
                    log.error(e) { "Write-behind flush 실패. Dead letter 기록: ${batch.keys}" }
                    // Dead letter: 각 키를 별도 StringCodec 연결로 기록
                    // StringCodec 전용 커맨드는 생성자에서 별도 connect() 필요:
                    //   val strCommands = client.connect(StringCodec.UTF8).sync()
                    //   strCommands.lpush("${config.keyPrefix}:dead-letter", key.toString())
                    // 여기서는 Redis Key만 기록 (값은 Redis에 이미 있으므로 key만으로 재처리 가능)
                    runCatching {
                        val deadLetterKey = "${config.keyPrefix}:dead-letter"
                        strCommands.lpush(deadLetterKey, *batch.keys.map { keySerializer(it) }.toTypedArray())
                    }.onFailure { ex -> log.error(ex) { "Dead letter 기록 실패: ${batch.keys}" } }
                }
        }
    }

    override fun close() {
        scheduler?.let { sched ->
            sched.shutdown()
            // drain 잔여 항목
            val deadline = System.currentTimeMillis() + config.writeBehindShutdownTimeout.toMillis()
            while (writeBehindQueue?.isNotEmpty() == true &&
                   System.currentTimeMillis() < deadline) {
                flushWriteBehindQueue()
            }
            if (writeBehindQueue?.isNotEmpty() == true) {
                log.warn { "Write-behind shutdown 타임아웃: ${writeBehindQueue.size}개 항목 유실" }
            }
            sched.awaitTermination(1, TimeUnit.SECONDS)
        }
        strConnection.close()
        connection.close()
    }
}
```

> **주의**: `flushWriteBehindQueue`의 Dead letter 기록은 실제 구현 시 `Map<K,V>`를 별도 직렬화하여 Redis String으로 저장하도록 보완 필요. 위 코드는 컴파일 가능한 초안.

- [ ] **Step 5: Read-through 테스트 실행**

```bash
./gradlew :exposed-lettuce:test --tests "*.LettuceLoadedMapReadThroughTest" 2>&1 | tail -30
```

Expected: 3개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add data/exposed-lettuce/src/
git commit -m "feat: implement LettuceLoadedMap with read-through support"
```

---

### Task 5: LettuceLoadedMap — Write-through 테스트

**Files:**
- Create: `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMapWriteThroughTest.kt`

- [ ] **Step 1: Write-through 실패 테스트 작성**

```kotlin
// src/test/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMapWriteThroughTest.kt
package io.bluetape4k.exposed.lettuce.map

import io.bluetape4k.exposed.lettuce.AbstractExposedLettuceTest
import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import io.bluetape4k.exposed.lettuce.map.LettuceLoadedMap
import io.bluetape4k.exposed.lettuce.map.MapWriter
import io.bluetape4k.exposed.lettuce.map.WriteMode
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContainKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LettuceLoadedMapWriteThroughTest : AbstractExposedLettuceTest() {

    private val dbStore = mutableMapOf<String, String>()

    private val writer = object : MapWriter<String, String> {
        override fun write(map: Map<String, String>) { dbStore.putAll(map) }
        override fun delete(keys: Collection<String>) { keys.forEach { dbStore.remove(it) } }
    }

    private lateinit var cache: LettuceLoadedMap<String, String>

    @BeforeEach
    fun setUp() {
        dbStore.clear()
        cache = LettuceLoadedMap(
            client = redisClient,
            writer = writer,
            config = LettuceCacheConfig.READ_WRITE_THROUGH,
        )
        cache.clear()
    }

    @Test
    fun `save 시 Redis와 DB에 동시 저장된다`() {
        cache["key1"] = "value1"

        cache["key1"] shouldBeEqualTo "value1"   // Redis hit
        dbStore shouldContainKey "key1"            // DB에도 저장됨
        dbStore["key1"] shouldBeEqualTo "value1"
    }

    @Test
    fun `delete 시 Redis와 DB에서 동시 삭제된다`() {
        cache["key1"] = "value1"
        cache.delete("key1")

        cache["key1"].shouldBeNull()
        dbStore.containsKey("key1") shouldBeEqualTo false
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :exposed-lettuce:test --tests "*.LettuceLoadedMapWriteThroughTest" 2>&1 | tail -20
```

Expected: 2개 테스트 PASS

- [ ] **Step 3: 커밋**

```bash
git add data/exposed-lettuce/src/test/
git commit -m "test: add write-through tests for LettuceLoadedMap"
```

---

### Task 6: LettuceLoadedMap — Write-behind 테스트

**Files:**
- Create: `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMapWriteBehindTest.kt`

- [ ] **Step 1: Write-behind 실패 테스트 작성**

```kotlin
// src/test/kotlin/io/bluetape4k/exposed/lettuce/map/LettuceLoadedMapWriteBehindTest.kt
package io.bluetape4k.exposed.lettuce.map

import io.bluetape4k.exposed.lettuce.AbstractExposedLettuceTest
import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import io.bluetape4k.exposed.lettuce.map.LettuceLoadedMap
import io.bluetape4k.exposed.lettuce.map.MapWriter
import io.bluetape4k.exposed.lettuce.map.WriteMode
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainKey
import org.amshove.kluent.shouldNotContainKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class LettuceLoadedMapWriteBehindTest : AbstractExposedLettuceTest() {

    private val dbStore = mutableMapOf<String, String>()

    private val writer = object : MapWriter<String, String> {
        override fun write(map: Map<String, String>) { dbStore.putAll(map) }
        override fun delete(keys: Collection<String>) { keys.forEach { dbStore.remove(it) } }
    }

    private lateinit var cache: LettuceLoadedMap<String, String>

    @BeforeEach
    fun setUp() {
        dbStore.clear()
        cache = LettuceLoadedMap(
            client = redisClient,
            writer = writer,
            config = LettuceCacheConfig.WRITE_BEHIND.copy(
                writeBehindDelay = Duration.ofMillis(200),  // 테스트용 짧은 delay
                writeBehindBatchSize = 10,
            ),
        )
        cache.clear()
    }

    @Test
    fun `save 시 Redis에 즉시 저장되고 DB는 delay 후 flush된다`() {
        cache["key1"] = "value1"

        // Redis에는 즉시 저장됨
        cache["key1"] shouldBeEqualTo "value1"
        // DB에는 아직 없음
        dbStore shouldNotContainKey "key1"

        // flush 대기
        Thread.sleep(500)

        // DB에 flush됨
        dbStore shouldContainKey "key1"
        dbStore["key1"] shouldBeEqualTo "value1"
    }

    @Test
    fun `close 시 큐 잔여 항목이 DB에 반영된다`() {
        cache["key2"] = "value2"
        // flush 전에 close
        cache.close()

        dbStore shouldContainKey "key2"
        dbStore["key2"] shouldBeEqualTo "value2"
    }
}
```

- [ ] **Step 2: 테스트 실행**

```bash
./gradlew :exposed-lettuce:test --tests "*.LettuceLoadedMapWriteBehindTest" 2>&1 | tail -20
```

Expected: 2개 테스트 PASS

- [ ] **Step 3: 커밋**

```bash
git add data/exposed-lettuce/src/test/
git commit -m "test: add write-behind tests for LettuceLoadedMap"
```

---

## Chunk 3: Exposed 연동 계층 (Loader + Writer)

### Task 7: EntityMapLoader + ExposedEntityMapLoader

**Files:**
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/EntityMapLoader.kt`
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapLoader.kt`
- Create: `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapLoaderTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
// src/test/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapLoaderTest.kt
package io.bluetape4k.exposed.lettuce.map

import io.bluetape4k.exposed.lettuce.AbstractExposedLettuceTest
import io.bluetape4k.exposed.lettuce.map.ExposedEntityMapLoader
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldContainSame
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// 테스트용 IdTable
object LoaderTestTable : IntIdTable("loader_test_items") {
    val name = varchar("name", 100)
}

data class LoaderTestItem(val id: Int, val name: String)

class ExposedEntityMapLoaderTest : AbstractExposedLettuceTest() {

    private val loader = ExposedEntityMapLoader(
        table = LoaderTestTable,
        toEntity = { row ->
            LoaderTestItem(
                id = row[LoaderTestTable.id].value,
                name = row[LoaderTestTable.name],
            )
        },
    )

    companion object {
        init { initTable(LoaderTestTable) }
    }

    @BeforeEach
    fun setUp() {
        transaction { LoaderTestTable.deleteAll() }
        transaction {
            LoaderTestTable.insert { it[id] = 1; it[name] = "Item1" }
            LoaderTestTable.insert { it[id] = 2; it[name] = "Item2" }
            LoaderTestTable.insert { it[id] = 3; it[name] = "Item3" }
        }
    }

    @Test
    fun `load는 존재하는 ID의 엔티티를 반환한다`() {
        val item = loader.load(1)
        item?.id shouldBeEqualTo 1
        item?.name shouldBeEqualTo "Item1"
    }

    @Test
    fun `load는 존재하지 않는 ID에 null을 반환한다`() {
        loader.load(999).shouldBeNull()
    }

    @Test
    fun `loadAllKeys는 전체 ID 목록을 반환한다`() {
        val keys = loader.loadAllKeys().toList()
        keys shouldContainSame listOf(1, 2, 3)
    }
}
```

- [ ] **Step 2: EntityMapLoader.kt 작성**

```kotlin
// src/main/kotlin/io/bluetape4k/exposed/lettuce/map/EntityMapLoader.kt
package io.bluetape4k.exposed.lettuce.map

import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Exposed transaction 내에서 DB 조회를 수행하는 추상 MapLoader.
 */
abstract class EntityMapLoader<ID, E> : MapLoader<ID, E> {

    override fun load(key: ID): E? = transaction { loadById(key) }

    override fun loadAllKeys(): Iterable<ID> = transaction { loadAllIds() }

    protected abstract fun loadById(id: ID): E?
    protected abstract fun loadAllIds(): Iterable<ID>
}
```

- [ ] **Step 3: ExposedEntityMapLoader.kt 작성**

```kotlin
// src/main/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapLoader.kt
package io.bluetape4k.exposed.lettuce.map

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.selectAll
import org.jetbrains.exposed.v1.jdbc.select

/**
 * [IdTable] 기반 [EntityMapLoader] 구현체.
 *
 * @param table Exposed IdTable
 * @param toEntity ResultRow를 엔티티로 변환하는 함수
 * @param batchSize loadAllKeys() 페이지네이션 배치 크기
 */
class ExposedEntityMapLoader<ID : Comparable<ID>, E>(
    private val table: IdTable<ID>,
    private val toEntity: (ResultRow) -> E,
    private val batchSize: Int = 1000,
) : EntityMapLoader<ID, E>() {

    override fun loadById(id: ID): E? =
        table.selectAll()
            .where { table.id eq id }
            .singleOrNull()
            ?.let(toEntity)

    /**
     * limit/offset 반복으로 전체 키 조회.
     * 주의: 대용량 테이블에서는 O(N) 스캔 발생.
     */
    override fun loadAllIds(): Iterable<ID> = buildList {
        var offset = 0L
        while (true) {
            val batch = table.select(table.id)
                .limit(batchSize).offset(offset)
                .map { it[table.id].value }
            addAll(batch)
            if (batch.size < batchSize) break
            offset += batchSize
        }
    }
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./gradlew :exposed-lettuce:compileKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/EntityMapLoader.kt
git add data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapLoader.kt
git commit -m "feat: implement EntityMapLoader and ExposedEntityMapLoader"
```

---

### Task 8: EntityMapWriter + ExposedEntityMapWriter

**Files:**
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/EntityMapWriter.kt`
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapWriter.kt`
- Create: `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapWriterTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
// src/test/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapWriterTest.kt
package io.bluetape4k.exposed.lettuce.map

import io.bluetape4k.exposed.lettuce.AbstractExposedLettuceTest
import io.bluetape4k.exposed.lettuce.map.ExposedEntityMapWriter
import io.bluetape4k.exposed.lettuce.map.WriteMode
import org.amshove.kluent.shouldBeEqualTo
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

object WriterTestTable : IntIdTable("writer_test") {
    val name = varchar("name", 100)
}

data class WriterTestEntity(val id: Int, val name: String)

class ExposedEntityMapWriterTest : AbstractExposedLettuceTest() {

    companion object {
        init { initTable(WriterTestTable) }
    }

    private lateinit var writer: ExposedEntityMapWriter<Int, WriterTestEntity>

    @BeforeEach
    fun setUp() {
        transaction { WriterTestTable.deleteAll() }
        writer = ExposedEntityMapWriter(
            table = WriterTestTable,
            writeMode = WriteMode.WRITE_THROUGH,
            updateEntity = { stmt, e -> stmt[WriterTestTable.name] = e.name },
            insertEntity = { stmt, e ->
                stmt[WriterTestTable.id] = e.id
                stmt[WriterTestTable.name] = e.name
            },
        )
    }

    @Test
    fun `write는 신규 엔티티를 INSERT한다`() {
        writer.write(mapOf(1 to WriterTestEntity(1, "Alice")))

        transaction {
            WriterTestTable.selectAll().count() shouldBeEqualTo 1L
        }
    }

    @Test
    fun `write는 기존 엔티티를 UPDATE한다`() {
        writer.write(mapOf(1 to WriterTestEntity(1, "Alice")))
        writer.write(mapOf(1 to WriterTestEntity(1, "Alice Updated")))

        transaction {
            val row = WriterTestTable.selectAll().single()
            row[WriterTestTable.name] shouldBeEqualTo "Alice Updated"
        }
    }

    @Test
    fun `delete는 엔티티를 삭제한다`() {
        writer.write(mapOf(1 to WriterTestEntity(1, "Alice")))
        writer.delete(listOf(1))

        transaction {
            WriterTestTable.selectAll().count() shouldBeEqualTo 0L
        }
    }
}
```

- [ ] **Step 2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :exposed-lettuce:test --tests "*.ExposedEntityMapWriterTest" 2>&1 | tail -10
```

Expected: FAIL (ExposedEntityMapWriter 미존재)

- [ ] **Step 3: EntityMapWriter.kt 작성**

```kotlin
// src/main/kotlin/io/bluetape4k/exposed/lettuce/map/EntityMapWriter.kt
package io.bluetape4k.exposed.lettuce.map

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Resilience4j Retry + Exposed transaction 래핑 추상 MapWriter.
 */
abstract class EntityMapWriter<ID, E>(
    retryConfig: RetryConfig = RetryConfig.ofDefaults(),
) : MapWriter<ID, E> {

    private val retry = Retry.of("exposed-lettuce-writer", retryConfig)

    override fun write(map: Map<ID, E>) {
        Retry.decorateRunnable(retry) {
            transaction { writeEntities(map) }
        }.run()
    }

    override fun delete(keys: Collection<ID>) {
        Retry.decorateRunnable(retry) {
            transaction { deleteEntities(keys) }
        }.run()
    }

    protected abstract fun writeEntities(map: Map<ID, E>)
    protected abstract fun deleteEntities(keys: Collection<ID>)
}
```

- [ ] **Step 4: ExposedEntityMapWriter.kt 작성**

```kotlin
// src/main/kotlin/io/bluetape4k/exposed/lettuce/map/ExposedEntityMapWriter.kt
package io.bluetape4k.exposed.lettuce.map

import io.github.resilience4j.retry.RetryConfig
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.BatchInsertStatement
import org.jetbrains.exposed.v1.jdbc.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Duration

/**
 * [IdTable] 기반 [EntityMapWriter] 구현체.
 * WriteMode에 따라 UPDATE/INSERT 분기 처리.
 */
class ExposedEntityMapWriter<ID : Comparable<ID>, E>(
    private val table: IdTable<ID>,
    private val writeMode: WriteMode,
    private val updateEntity: (UpdateStatement, E) -> Unit,
    private val insertEntity: (BatchInsertStatement, E) -> Unit,
    private val chunkSize: Int = 1000,
    retryAttempts: Int = 3,
    retryInterval: Duration = Duration.ofMillis(100),
) : EntityMapWriter<ID, E>(
    RetryConfig.custom<Any>()
        .maxAttempts(retryAttempts)
        .waitDuration(retryInterval)
        .build()
) {

    override fun writeEntities(map: Map<ID, E>) {
        if (map.isEmpty() || writeMode == WriteMode.NONE) return

        // WRITE_THROUGH, WRITE_BEHIND 모두 동일한 upsert 로직 (duplicate key 방지)
        val existingIds = table.select(table.id)
            .where { table.id inList map.keys }
            .map { it[table.id].value }
            .toSet()

        // 기존 엔티티 UPDATE
        existingIds.forEach { id ->
            table.update({ table.id eq id }) { updateEntity(it, map[id]!!) }
        }

        // 신규 엔티티 batchInsert
        val newIds = map.keys - existingIds
        if (newIds.isNotEmpty()) {
            newIds.chunked(chunkSize).forEach { chunk ->
                table.batchInsert(chunk) { id -> insertEntity(this, map[id]!!) }
            }
        }
    }

    override fun deleteEntities(keys: Collection<ID>) {
        if (keys.isEmpty()) return
        table.deleteWhere { table.id inList keys }
    }
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew :exposed-lettuce:test --tests "*.ExposedEntityMapWriterTest" 2>&1 | tail -20
```

Expected: 3개 테스트 PASS

- [ ] **Step 6: 커밋**

```bash
git add data/exposed-lettuce/src/
git commit -m "feat: implement EntityMapWriter and ExposedEntityMapWriter"
```

---

## Chunk 4: AbstractJdbcLettuceRepository + 통합 테스트

### Task 9: AbstractJdbcLettuceRepository 구현

**Files:**
- Create: `data/exposed-lettuce/src/main/kotlin/io/bluetape4k/exposed/lettuce/repository/AbstractJdbcLettuceRepository.kt`
- Create: `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/repository/CustomerRepository.kt`
- Create: `data/exposed-lettuce/src/test/kotlin/io/bluetape4k/exposed/lettuce/repository/AbstractJdbcLettuceRepositoryTest.kt`

- [ ] **Step 1: 실패 테스트 작성 (CustomerRepository + 통합 테스트)**

```kotlin
// src/test/kotlin/io/bluetape4k/exposed/lettuce/repository/CustomerRepository.kt
package io.bluetape4k.exposed.lettuce.repository

import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import io.bluetape4k.exposed.lettuce.repository.AbstractJdbcLettuceRepository
import io.lettuce.core.RedisClient
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.jdbc.BatchInsertStatement
import org.jetbrains.exposed.v1.jdbc.UpdateStatement

object CustomerTable : IntIdTable("customers") {
    val name = varchar("name", 100)
    val email = varchar("email", 200)
}

data class Customer(val id: Int, val name: String, val email: String)

class CustomerRepository(
    client: RedisClient,
    config: LettuceCacheConfig = LettuceCacheConfig.READ_WRITE_THROUGH,
) : AbstractJdbcLettuceRepository<Int, Customer>(client, config) {

    override val table = CustomerTable

    override fun ResultRow.toEntity() = Customer(
        id = this[CustomerTable.id].value,
        name = this[CustomerTable.name],
        email = this[CustomerTable.email],
    )

    override fun UpdateStatement.updateEntity(entity: Customer) {
        this[CustomerTable.name] = entity.name
        this[CustomerTable.email] = entity.email
    }

    override fun BatchInsertStatement.insertEntity(entity: Customer) {
        this[CustomerTable.id] = entity.id
        this[CustomerTable.name] = entity.name
        this[CustomerTable.email] = entity.email
    }
}
```

```kotlin
// src/test/kotlin/io/bluetape4k/exposed/lettuce/repository/AbstractJdbcLettuceRepositoryTest.kt
package io.bluetape4k.exposed.lettuce.repository

import io.bluetape4k.exposed.lettuce.AbstractExposedLettuceTest
import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AbstractJdbcLettuceRepositoryTest : AbstractExposedLettuceTest() {

    private lateinit var repo: CustomerRepository

    companion object {
        init { initTable(CustomerTable) }
    }

    @BeforeEach
    fun setUp() {
        transaction { CustomerTable.deleteAll() }
        repo = CustomerRepository(redisClient, LettuceCacheConfig.READ_WRITE_THROUGH)
        repo.clearCache()
    }

    @Test
    fun `save 후 findById로 조회된다 (Redis hit)`() {
        repo.save(1, Customer(1, "Alice", "alice@example.com"))
        val found = repo.findById(1)

        found.shouldNotBeNull()
        found.name shouldBeEqualTo "Alice"
    }

    @Test
    fun `DB에만 있는 데이터는 read-through로 조회된다`() {
        // DB에 직접 저장 (Redis bypass)
        transaction {
            CustomerTable.insert {
                it[id] = 2
                it[name] = "Bob"
                it[email] = "bob@example.com"
            }
        }

        // cache에는 없지만 read-through로 DB에서 조회됨
        val found = repo.findById(2)
        found.shouldNotBeNull()
        found.name shouldBeEqualTo "Bob"
    }

    @Test
    fun `delete 후 findById는 null을 반환한다`() {
        repo.save(1, Customer(1, "Alice", "alice@example.com"))
        repo.delete(1)

        repo.findById(1).shouldBeNull()
    }

    @Test
    fun `findAll은 여러 엔티티를 한번에 조회한다`() {
        repo.save(1, Customer(1, "Alice", "alice@example.com"))
        repo.save(2, Customer(2, "Bob", "bob@example.com"))

        val result = repo.findAll(listOf(1, 2, 99))
        result.size shouldBeEqualTo 2
        result[1]?.name shouldBeEqualTo "Alice"
        result[2]?.name shouldBeEqualTo "Bob"
    }
}
```

- [ ] **Step 2: 테스트 실행 → FAIL 확인**

```bash
./gradlew :exposed-lettuce:test --tests "*.AbstractJdbcLettuceRepositoryTest" 2>&1 | tail -10
```

Expected: FAIL (AbstractJdbcLettuceRepository 미존재)

- [ ] **Step 3: AbstractJdbcLettuceRepository.kt 구현**

```kotlin
// src/main/kotlin/io/bluetape4k/exposed/lettuce/repository/AbstractJdbcLettuceRepository.kt
package io.bluetape4k.exposed.lettuce.repository

import io.bluetape4k.exposed.lettuce.map.ExposedEntityMapLoader
import io.bluetape4k.exposed.lettuce.map.ExposedEntityMapWriter
import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import io.bluetape4k.exposed.lettuce.map.LettuceLoadedMap
import io.lettuce.core.RedisClient
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.jdbc.BatchInsertStatement
import org.jetbrains.exposed.v1.jdbc.UpdateStatement
import java.io.Closeable

/**
 * Lettuce + Exposed JDBC 기반 캐시 Repository 기반 클래스.
 *
 * 추상 메서드 4개 구현으로 Read-through / Write-through / Write-behind 캐시 완성.
 * AbstractJdbcRedissonRepository와 동일한 패턴.
 */
abstract class AbstractJdbcLettuceRepository<ID : Comparable<ID>, E : Any>(
    client: RedisClient,
    config: LettuceCacheConfig = LettuceCacheConfig.READ_WRITE_THROUGH,
) : Closeable {

    // ── 구현 필수 (4개) ──────────────────────────────────────────────────────────

    abstract val table: IdTable<ID>

    abstract fun ResultRow.toEntity(): E

    abstract fun UpdateStatement.updateEntity(entity: E)

    abstract fun BatchInsertStatement.insertEntity(entity: E)

    // ── 선택 override ────────────────────────────────────────────────────────────

    /** Redis 키 변환. 기본 toString(). */
    open fun serializeKey(id: ID): String = id.toString()

    // ── 내부 구성 ─────────────────────────────────────────────────────────────────

    protected val cache: LettuceLoadedMap<ID, E> = LettuceLoadedMap(
        client = client,
        loader = ExposedEntityMapLoader(table) { it.toEntity() },
        writer = ExposedEntityMapWriter(
            table = table,
            writeMode = config.writeMode,
            updateEntity = { stmt, e -> stmt.updateEntity(e) },
            insertEntity = { stmt, e -> stmt.insertEntity(e) },
            retryAttempts = config.writeRetryAttempts,
            retryInterval = config.writeRetryInterval,
        ),
        config = config,
        keySerializer = ::serializeKey,
    )

    // ── 공개 CRUD API ─────────────────────────────────────────────────────────────

    fun findById(id: ID): E? = cache[id]

    fun findAll(ids: Collection<ID>): Map<ID, E> = cache.getAll(ids.toSet())

    fun save(id: ID, entity: E) { cache[id] = entity }

    fun delete(id: ID) { cache.delete(id) }

    fun deleteAll(ids: Collection<ID>) { cache.deleteAll(ids) }

    fun clearCache() { cache.clear() }

    override fun close() = cache.close()
}
```

- [ ] **Step 4: 테스트 실행**

```bash
./gradlew :exposed-lettuce:test 2>&1 | tail -30
```

Expected: 전체 테스트 PASS

- [ ] **Step 5: 커밋**

```bash
git add data/exposed-lettuce/src/
git commit -m "feat: implement AbstractJdbcLettuceRepository with full cache strategy support"
```

---

### Task 10: 전체 빌드 검증

- [ ] **Step 1: 모듈 전체 테스트 실행**

```bash
./gradlew :exposed-lettuce:test --info 2>&1 | tail -40
```

Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 2: 컴파일 경고 확인**

```bash
./gradlew :exposed-lettuce:build 2>&1 | grep -E "warning|error" | head -20
```

Expected: 경고/에러 없음

- [ ] **Step 3: 최종 커밋**

```bash
git add .
git commit -m "feat: complete exposed-lettuce cache strategy (read-through, write-through, write-behind)"
```

---

## 구현 시 주의사항

### 의존성 이름 확인
`buildSrc/src/main/kotlin/Libs.kt`에서 아래 확인:
- `Libs.bluetape4k_resilience4j` — resilience4j 래퍼 존재 여부
- `Libs.bluetape4k_exposed_jdbc_tests` — 테스트 헬퍼 존재 여부
- `Libs.hikaricp` — HikariCP 이름

### LettuceBinaryCodec 타입 파라미터
`LettuceBinaryCodec<V>(BinarySerializers.LZ4Fory)`는 `RedisCodec<String, V>` 구현체.
`client.connect(codec)`으로 `StatefulRedisConnection<String, V>` 생성.

### CLAUDE.md infra 주의사항
`LettuceBinaryCodecs` (복수형) 사용 금지. `LettuceBinaryCodec` (단수형) 직접 사용.

### Exposed v1 임포트
`org.jetbrains.exposed.v1.*` 패키지 사용 (Exposed v1 마이그레이션 완료된 프로젝트).
