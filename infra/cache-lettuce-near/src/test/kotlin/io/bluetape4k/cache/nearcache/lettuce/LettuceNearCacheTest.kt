package io.bluetape4k.cache.nearcache.lettuce

import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.time.Duration

class LettuceNearCacheTest : AbstractLettuceNearCacheTest() {

    private lateinit var cache: LettuceNearCache<String, String>

    @BeforeEach
    fun createCache() {
        if (::cache.isInitialized) cache.close()
        cache = LettuceNearCache(
            redisClient = resp3Client,
            codec = StringCodec.UTF8,
            config = NearCacheConfig(cacheName = "test-near-cache"),
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
        directCommands.get("k") shouldBeEqualTo "v"
    }

    @Test
    fun `get - front miss 시 Redis에서 읽어 front populate`() {
        directCommands.set("remote-key", "remote-val")
        cache.localSize() shouldBeEqualTo 0L

        cache.get("remote-key") shouldBeEqualTo "remote-val"
        cache.localSize() shouldBeEqualTo 1L  // front populated
    }

    @Test
    fun `putAll and getAll`() {
        verifyGetAll(
            putAll = { cache.putAll(it) },
            getAll = { cache.getAll(it) },
        )
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
    fun `containsKey`() {
        verifyContainsKey(
            put = { k, v -> cache.put(k, v) },
            containsKey = { cache.containsKey(it) },
            remove = { cache.remove(it) },
        )
    }

    @Test
    fun `putIfAbsent`() {
        verifyPutIfAbsent(
            putIfAbsent = { k, v -> cache.putIfAbsent(k, v) },
            get = { cache.get(it) },
        )
    }

    @Test
    fun `replace`() {
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
    fun `getAndRemove`() {
        verifyGetAndRemove(
            put = { k, v -> cache.put(k, v) },
            getAndRemove = { cache.getAndRemove(it) },
            get = { cache.get(it) },
        )
    }

    @Test
    fun `getAndReplace`() {
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
            localSize = { cache.localSize() },
        )
    }

    @Test
    fun `clearAll - 로컬 + Redis 초기화`() {
        cache.put("k1", "v1")
        cache.put("k2", "v2")
        cache.clearAll()
        cache.localSize() shouldBeEqualTo 0L
        directCommands.get("k1").shouldBeNull()
        directCommands.get("k2").shouldBeNull()
    }

    // ---- TTL ----

    @Test
    fun `Redis TTL - TTL이 있는 캐시 설정`() {
        val ttlCache = LettuceNearCache(
            redisClient = resp3Client,
            codec = StringCodec.UTF8,
            config = NearCacheConfig(
                cacheName = "ttl-test",
                redisTtl = Duration.ofSeconds(2),
            ),
        )
        ttlCache.use { c ->
            c.put("ttl-key", "ttl-val")
            c.get("ttl-key") shouldBeEqualTo "ttl-val"
            val ttl = directCommands.ttl("ttl-key")
            (ttl > 0L).shouldBeTrue()
        }
    }

    // ---- lifecycle ----

    @Test
    fun `close - 중복 close 시 예외 없음`() {
        val c = LettuceNearCache.create(resp3Client)
        c.close()
        c.close() // 두 번 호출해도 예외 없어야 함
    }
}
