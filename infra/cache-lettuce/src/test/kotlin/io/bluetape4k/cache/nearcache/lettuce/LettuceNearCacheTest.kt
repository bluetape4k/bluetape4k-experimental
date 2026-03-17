package io.bluetape4k.cache.nearcache.lettuce

import io.bluetape4k.junit5.concurrency.MultithreadingTester
import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
import io.bluetape4k.logging.KLogging
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.testcontainers.utility.Base58
import java.nio.ByteBuffer
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith

class LettuceNearCacheTest: AbstractLettuceNearCacheTest() {

    companion object: KLogging()

    private lateinit var cache: LettuceNearCache<String>

    @BeforeAll
    fun createCache() {
        if (::cache.isInitialized) {
            cache.close()
        }
        cache = LettuceNearCache(
            redisClient = resp3Client,
            codec = StringCodec.UTF8,
            config = NearCacheConfig(cacheName = "test-near-cache-" + Base58.randomString(6)),
        )
        // BeforeEach의 flushdb 이후 생성
    }

    @AfterAll
    fun tearDown() {
        if (::cache.isInitialized) cache.close()
    }

    // ---- 기본 CRUD ----

    @Test
    fun `get - 존재하지 않는 키는 null 반환`() {
        verifyGetMiss { cache.get(it) }
    }

    @RepeatedTest(REPEAT_SIZE)
    fun `put and get - read-through`() {
        verifyPutAndGet(
            put = { k, v -> cache.put(k, v) },
            get = { cache.get(it) },
        )
    }

    @Test
    fun `put - Redis에도 반영됨`() {
        cache.put("k", "v")
        // prefix key로 확인
        directCommands.get("${cache.cacheName}:k") shouldBeEqualTo "v"
    }

    @Test
    fun `put - Redis 쓰기 실패 시 local cache를 오염시키지 않는다`() {
        val failingCache = LettuceNearCache(
            redisClient = resp3Client,
            codec = failingValueCodec(),
            config = NearCacheConfig(cacheName = "failing-put-cache-" + Base58.randomString(6)),
        )

        failingCache.use { broken ->
            assertWriteFailure {
                broken.put("k", "boom")
            }

            broken.localCacheSize() shouldBeEqualTo 0L
            broken.containsKey("k").shouldBeFalse()
        }
    }

    @Test
    fun `get - front miss 시 Redis에서 읽어 front populate`() {
        // prefix key로 직접 설정해야 cache.get()이 찾을 수 있음
        directCommands.set("${cache.cacheName}:remote-key", "remote-val")
        cache.localCacheSize() shouldBeEqualTo 0L

        cache.get("remote-key") shouldBeEqualTo "remote-val"
        cache.localCacheSize() shouldBeEqualTo 1L  // front populated
    }

    @Test
    fun `get - MultithreadingTester 동일 key 첫 조회 경쟁에서도 read-through 결과가 유지된다`() {
        val key = "contended-read-through"
        directCommands.set("${cache.cacheName}:$key", "remote-val")
        cache.clearLocal()

        val observed = Collections.synchronizedList(mutableListOf<String>())
        MultithreadingTester()
            .workers(8)
            .rounds(1)
            .addAll(
                List(8) {
                    {
                        observed += cache.get(key).shouldNotBeNull()
                    }
                }
            )
            .run()

        observed.size shouldBeEqualTo 8
        observed.forEach { it shouldBeEqualTo "remote-val" }
        cache.localCacheSize() shouldBeEqualTo 1L
    }

    @Test
    fun `putAll and getAll`() {
        verifyGetAll(
            putAll = { cache.putAll(it) },
            getAll = { cache.getAll(it) },
        )
    }

    @Test
    fun `putAll - Redis 쓰기 실패 시 local cache를 오염시키지 않는다`() {
        val failingCache = LettuceNearCache(
            redisClient = resp3Client,
            codec = failingValueCodec(),
            config = NearCacheConfig(cacheName = "failing-put-all-cache-" + Base58.randomString(6)),
        )

        failingCache.use { broken ->
            assertWriteFailure {
                broken.putAll(mapOf("k1" to "boom"))
            }

            broken.localCacheSize() shouldBeEqualTo 0L
            broken.get("k1").shouldBeNull()
        }
    }

    @Test
    fun `remove - front + Redis 삭제`() {
        verifyRemove(
            put = { k, v -> cache.put(k, v) },
            get = { cache.get(it) },
            remove = { cache.remove(it) },
        )
    }

    @Test
    fun `removeAll - 여러 키 삭제`() {
        verifyRemoveAll(
            putAll = { cache.putAll(it) },
            removeAll = { cache.removeAll(it) },
            get = { cache.get(it) },
        )
    }

    @Test
    fun `containsKey - 키 존재 여부 확인`() {
        verifyContainsKey(
            put = { k, v -> cache.put(k, v) },
            containsKey = { cache.containsKey(it) },
            remove = { cache.remove(it) },
        )
    }

    @Test
    fun `putIfAbsent - 캐시 값 없으면 추가, 있으면 기존 값 반환`() {
        verifyPutIfAbsent(
            putIfAbsent = { k, v -> cache.putIfAbsent(k, v) },
            get = { cache.get(it) },
        )
    }

    @Test
    fun `putIfAbsent - TTL이 있는 경우 단일 명령으로 TTL까지 함께 저장한다`() {
        val ttlCacheName = "put-if-absent-ttl-" + Base58.randomString(6)
        val ttlCache = LettuceNearCache(
            redisClient = resp3Client,
            codec = StringCodec.UTF8,
            config = NearCacheConfig(
                cacheName = ttlCacheName,
                redisTtl = Duration.ofSeconds(30),
            ),
        )

        ttlCache.use { c ->
            c.putIfAbsent("ttl-key", "ttl-val").shouldBeNull()
            val ttl = directCommands.ttl("${ttlCacheName}:ttl-key")
            (ttl > 0L).shouldBeTrue()
        }
    }

    @Test
    fun `putIfAbsent - MultithreadingTester 경쟁 상황에서도 단 한 번만 저장된다`() {
        val key = "contended-put-if-absent"
        val winnerCount = AtomicInteger(0)
        val observedValues = Collections.synchronizedList(mutableListOf<String>())
        val candidates = (1..8).map { "value-$it" }

        MultithreadingTester()
            .workers(candidates.size)
            .rounds(1)
            .addAll(
                candidates.map { candidate ->
                    {
                        val existing = cache.putIfAbsent(key, candidate)
                        if (existing == null) {
                            winnerCount.incrementAndGet()
                        } else {
                            observedValues += existing
                        }
                    }
                }
            )
            .run()

        winnerCount.get() shouldBeEqualTo 1
        val stored = cache.get(key).shouldNotBeNull()
        observedValues.forEach { it shouldBeEqualTo stored }
        directCommands.get("${cache.cacheName}:$key") shouldBeEqualTo stored
    }

    @Test
    fun `replace - 캐시 값 교체`() {
        verifyReplace(
            put = { k, v -> cache.put(k, v) },
            replace = { k, v -> cache.replace(k, v) },
            get = { cache.get(it) },
        )
    }

    @Test
    fun `replace(key, oldValue, newValue) - 값이 일치할 때만 교체`() {
        cache.put("k", "old")
        cache.replace("k", "wrong", "new") shouldBeEqualTo false
        cache.replace("k", "old", "new") shouldBeEqualTo true
        cache.get("k") shouldBeEqualTo "new"
    }

    @Test
    fun `get - StructuredTaskScopeTester 병렬 조회에서도 값 일관성을 유지한다`() {
        assumeTrue(structuredTaskScopeAvailable(), "StructuredTaskScope runtime is not available")

        val key = "structured-read-through"
        directCommands.set("${cache.cacheName}:$key", "structured-value")

        StructuredTaskScopeTester()
            .rounds(32)
            .add {
                cache.get(key) shouldBeEqualTo "structured-value"
            }
            .run()

        cache.localCacheSize() shouldBeEqualTo 1L
        cache.get(key) shouldBeEqualTo "structured-value"
    }

    @Test
    fun `getAndRemove - 캐시 값 조회 및 삭제`() {
        verifyGetAndRemove(
            put = { k, v -> cache.put(k, v) },
            getAndRemove = { cache.getAndRemove(it) },
            get = { cache.get(it) },
        )
    }

    @Test
    fun `getAndReplace - 캐시 값 조회 및 교체`() {
        verifyGetAndReplace(
            put = { k, v -> cache.put(k, v) },
            getAndReplace = { k, v -> cache.getAndReplace(k, v) },
            get = { cache.get(it) },
        )
    }

    // ---- clearLocal / clearAll ----

    @Test
    fun `clearLocal - 로컬만 초기화, Redis 유지`() {
        verifyClearLocal(
            put = { k, v -> cache.put(k, v) },
            clearLocal = { cache.clearLocal() },
            localSize = { cache.localCacheSize() },
            // prefix key로 Redis 직접 확인
            getFromRedis = { directCommands.get("${cache.cacheName}:$it") },
        )
    }

    @Test
    fun `clearAll - 로컬 + Redis 초기화`() {
        cache.put("k1", "v1")
        cache.put("k2", "v2")
        cache.clearAll()
        cache.localCacheSize() shouldBeEqualTo 0L
        // prefix key로 삭제 확인
        directCommands.get("${cache.cacheName}:k1").shouldBeNull()
        directCommands.get("${cache.cacheName}:k2").shouldBeNull()
    }

    @Test
    fun `clearAll - 다른 cacheName의 데이터는 유지됨`() {
        val otherCache = LettuceNearCache(
            redisClient = resp3Client,
            codec = StringCodec.UTF8,
            config = NearCacheConfig(cacheName = "other-cache-" + Base58.randomString(6)),
        )
        otherCache.use { other ->
            cache.put("shared-key", "from-main-cache")
            other.put("shared-key", "from-other-cache")

            cache.clearAll()

            // main cache 데이터 삭제됨
            cache.get("shared-key").shouldBeNull()
            directCommands.get("${cache.cacheName}:shared-key").shouldBeNull()

            // other cache 데이터 유지됨
            other.get("shared-key") shouldBeEqualTo "from-other-cache"
            directCommands.get("${other.cacheName}:shared-key") shouldBeEqualTo "from-other-cache"
        }
    }

    // ---- TTL ----

    @Test
    fun `Redis TTL - TTL이 있는 캐시 설정`() {
        val ttlCacheName = "ttl-test-" + Base58.randomString(6)
        val ttlCache = LettuceNearCache(
            redisClient = resp3Client,
            codec = StringCodec.UTF8,
            config = NearCacheConfig(
                cacheName = ttlCacheName,
                redisTtl = Duration.ofSeconds(2),
            ),
        )
        ttlCache.use { c ->
            c.put("ttl-key", "ttl-val")
            c.get("ttl-key") shouldBeEqualTo "ttl-val"
            // prefix key로 TTL 확인
            val ttl = directCommands.ttl("${ttlCacheName}:ttl-key")
            (ttl > 0L).shouldBeTrue()
        }
    }

    @Test
    fun `Redis TTL - millisecond 단위도 보존된다`() {
        val ttlCacheName = "ttl-ms-test-" + Base58.randomString(6)
        val ttlCache = LettuceNearCache(
            redisClient = resp3Client,
            codec = StringCodec.UTF8,
            config = NearCacheConfig(
                cacheName = ttlCacheName,
                redisTtl = Duration.ofMillis(500),
            ),
        )
        ttlCache.use { c ->
            c.put("ttl-key", "ttl-val")
            val ttl = directCommands.pttl("${ttlCacheName}:ttl-key")
            (ttl in 1..500).shouldBeTrue()
        }
    }

    @Test
    fun `NearCacheConfig는 잘못된 duration을 즉시 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            NearCacheConfig<String, String>(
                cacheName = "invalid-config",
                frontExpireAfterWrite = Duration.ZERO,
            )
        }
    }

    // ---- redisSize ----

    @Test
    fun `redisSize - cacheName에 속한 Redis key 개수`() {
        cache.put("s1", "v1")
        cache.put("s2", "v2")
        cache.put("s3", "v3")
        cache.backCacheSize() shouldBeEqualTo 3L
        cache.remove("s2")
        cache.backCacheSize() shouldBeEqualTo 2L
    }

    // ---- lifecycle ----

    @Test
    fun `close - 중복 close 시 예외 없음`() {
        val c = LettuceNearCache(resp3Client)
        c.close()
        c.close() // 두 번 호출해도 예외 없어야 함
    }

    private fun failingValueCodec(): RedisCodec<String, String> = object : RedisCodec<String, String> {
        override fun decodeKey(bytes: ByteBuffer): String = StringCodec.UTF8.decodeKey(bytes)

        override fun decodeValue(bytes: ByteBuffer): String = StringCodec.UTF8.decodeValue(bytes)

        override fun encodeKey(key: String): ByteBuffer = StringCodec.UTF8.encodeKey(key)

        override fun encodeValue(value: String): ByteBuffer {
            throw IllegalStateException("encode failure for test")
        }
    }

    private fun assertWriteFailure(block: () -> Unit) {
        val thrown = runCatching(block).exceptionOrNull()
        thrown.shouldNotBeNull()
        generateSequence(thrown) { it.cause }
            .any { it is IllegalStateException && it.message == "encode failure for test" }
            .shouldBeTrue()
    }

    private fun structuredTaskScopeAvailable(): Boolean =
        runCatching {
            Class.forName("java.util.concurrent.StructuredTaskScope\$ShutdownOnFailure")
        }.isSuccess
}
