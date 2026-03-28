package io.bluetape4k.benchmark.cache

import io.bluetape4k.cache.nearcache.LettuceNearCache
import io.bluetape4k.cache.nearcache.LettuceNearCacheConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.StringCodec
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lettuce near cache의 핵심 read/write 경로를 비교하기 위한 JMH benchmark 입니다.
 *
 * 최근 변경된 atomic `replace(oldValue, newValue)` 와
 * 단일 명령 기반 `putIfAbsent` 경로를 반복 측정할 수 있도록 구성합니다.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class LettuceNearCacheBenchmark {

    @Param("false", "true")
    var ttlEnabled: String = "false"

    private val sequence = AtomicInteger()

    private lateinit var redisClient: RedisClient
    private lateinit var directConnection: StatefulRedisConnection<String, String>
    private lateinit var directCommands: RedisCommands<String, String>
    private lateinit var cache: LettuceNearCache<String>
    private lateinit var hotKey: String

    @Setup
    fun setUp() {
        val cacheName = "bench-near-cache-${UUID.randomUUID()}"
        redisClient = BenchmarkRedisSupport.newResp3Client()
        directConnection = BenchmarkRedisSupport.newDirectConnection()
        directCommands = directConnection.sync()
        BenchmarkRedisSupport.flushDb(directCommands)

        cache = LettuceNearCache(
            redisClient = redisClient,
            codec = StringCodec.UTF8,
            config = LettuceNearCacheConfig(
                cacheName = cacheName,
                redisTtl = ttlDuration(),
            ),
        )

        hotKey = "hot-key"
        cache.put(hotKey, "value-0")
        cache.get(hotKey)
    }

    @TearDown
    fun tearDown() {
        runCatching { cache.close() }
        runCatching { BenchmarkRedisSupport.flushDb(directCommands) }
        runCatching { directConnection.close() }
        runCatching { redisClient.shutdown() }
    }

    /**
     * 로컬 캐시 hit 비용을 측정합니다.
     */
    @Benchmark
    fun getLocalHit(): String? = cache.get(hotKey)

    /**
     * 신규 key 삽입 경로의 비용을 측정합니다.
     * TTL 사용 여부는 [ttlEnabled] 파라미터로 바꿉니다.
     */
    @Benchmark
    fun putIfAbsentNewKey(): String? =
        cache.putIfAbsent("put-if-absent-${sequence.incrementAndGet()}", "value")

    /**
     * compare-and-set 기반 교체 경로의 비용을 측정합니다.
     */
    @Benchmark
    fun replaceWithExpectedValue(): Boolean {
        val id = sequence.incrementAndGet()
        val key = "replace-$id"
        val oldValue = "old-$id"
        val newValue = "new-$id"
        directCommands.set(redisKey(key), oldValue)
        return cache.replace(key, oldValue, newValue)
    }

    private fun redisKey(key: String): String = "${cache.cacheName}:$key"

    private fun ttlDuration(): Duration? =
        if (ttlEnabled.toBoolean()) Duration.ofSeconds(30) else null
}
