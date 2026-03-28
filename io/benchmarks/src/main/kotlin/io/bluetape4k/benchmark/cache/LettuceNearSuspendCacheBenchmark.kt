package io.bluetape4k.benchmark.cache

import io.bluetape4k.cache.nearcache.LettuceNearCacheConfig
import io.bluetape4k.cache.nearcache.LettuceSuspendNearCache
import io.bluetape4k.junit5.coroutines.runSuspendIO
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
 * suspend near cache 핵심 경로를 비교하기 위한 JMH benchmark 입니다.
 *
 * 코루틴 API를 `runSuspendIO` 로 감싸 측정하며,
 * 향후 dispatcher/atomic 연산 변경 전후의 회귀 비교에 사용합니다.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class LettuceNearSuspendCacheBenchmark {

    @Param("false", "true")
    var ttlEnabled: String = "false"

    private val sequence = AtomicInteger()

    private lateinit var redisClient: RedisClient
    private lateinit var directConnection: StatefulRedisConnection<String, String>
    private lateinit var directCommands: RedisCommands<String, String>
    private lateinit var cache: LettuceSuspendNearCache<String>
    private lateinit var hotKey: String

    @Setup
    fun setUp() {
        runSuspendIO {
            val cacheName = "bench-near-suspend-cache-${UUID.randomUUID()}"

            redisClient = BenchmarkRedisSupport.newResp3Client()
            directConnection = BenchmarkRedisSupport.newDirectConnection()
            directCommands = directConnection.sync()
            BenchmarkRedisSupport.flushDb(directCommands)

            cache = LettuceSuspendNearCache(
                redisClient = redisClient,
                codec = StringCodec.UTF8,
                config = LettuceNearCacheConfig(
                    cacheName = cacheName,
                    redisTtl = ttlDuration(),
                    useRespProtocol3 = false,
                ),
            )

            hotKey = "hot-key"
            cache.put(hotKey, "value-0")
            cache.get(hotKey)
        }
    }

    @TearDown
    fun tearDown() {
        runSuspendIO {
            runCatching { cache.close() }
            runCatching { BenchmarkRedisSupport.flushDb(directCommands) }
            runCatching { directConnection.close() }
            runCatching { redisClient.shutdown() }
        }
    }

    /**
     * suspend local cache hit 경로를 측정합니다.
     */
    @Benchmark
    fun getLocalHit(): String? {
        var result: String? = null
        runSuspendIO {
            result = cache.get(hotKey)
        }
        return result
    }

    /**
     * suspend put-if-absent 신규 key 삽입 비용을 측정합니다.
     */
    @Benchmark
    fun putIfAbsentNewKey(): String? {
        var result: String? = null
        runSuspendIO {
            result = cache.putIfAbsent("put-if-absent-${sequence.incrementAndGet()}", "value")
        }
        return result
    }

    /**
     * suspend compare-and-set 교체 경로의 비용을 측정합니다.
     */
    @Benchmark
    fun replaceWithExpectedValue(): Boolean {
        var replaced = false
        runSuspendIO {
            val id = sequence.incrementAndGet()
            val key = "replace-$id"
            val oldValue = "old-$id"
            val newValue = "new-$id"
            directCommands.set(redisKey(key), oldValue)
            replaced = cache.replace(key, oldValue, newValue)
        }
        return replaced
    }

    private fun redisKey(key: String): String = "${cache.cacheName}:$key"

    private fun ttlDuration(): Duration? =
        if (ttlEnabled.toBoolean()) Duration.ofSeconds(30) else null
}
