package io.bluetape4k.redis.lettuce.filter

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Lettuce 기반 분산 Bloom Filter (Coroutine/Suspend).
 *
 * [LettuceBloomFilter]의 코루틴 버전입니다.
 */
class LettuceSuspendBloomFilter(
    private val connection: StatefulRedisConnection<String, String>,
    val filterName: String,
    val options: BloomFilterOptions = BloomFilterOptions.Default,
) : AutoCloseable {

    companion object : KLogging() {
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
            asyncCommands.hset(
                configKey, mapOf(
                    "m" to m.toString(),
                    "n" to options.expectedInsertions.toString(),
                    "p" to options.falseProbability.toString(),
                )
            ).await()
            log.debug { "SuspendBloomFilter 초기화: name=$filterName, m=$m, k=$k" }
            return true
        }
        val storedM = asyncCommands.hget(configKey, "m").await()?.toLongOrNull()
        val storedK = asyncCommands.hget(configKey, "k").await()?.toIntOrNull()
        if (storedM != null && storedK != null && (storedM != m || storedK != k)) {
            throw IllegalStateException(
                "BloomFilter '$filterName' 이미 다른 파라미터로 초기화됨: " +
                    "저장된 m=$storedM/k=$storedK, 현재 m=$m/k=$k"
            )
        }
        return false
    }

    suspend fun add(element: String) {
        val positions = hashPositions(element)
        asyncCommands.eval<Long>(ADD_SCRIPT, ScriptOutputType.INTEGER, arrayOf(filterName), *positions).await()
    }

    suspend fun contains(element: String): Boolean {
        val positions = hashPositions(element)
        return asyncCommands.eval<Long>(
            CONTAINS_SCRIPT, ScriptOutputType.INTEGER, arrayOf(filterName), *positions
        ).await() == 1L
    }

    override fun close() = connection.close()

    private fun hashPositions(element: String): Array<String> {
        val bytes = element.toByteArray(Charsets.UTF_8)
        val (h1, h2) = Murmur3.hash128x64(bytes)
        return Array(k) { i -> Math.floorMod(h1 + i.toLong() * h2, m).toString() }
    }
}
