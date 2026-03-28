package io.bluetape4k.redis.lettuce.filter

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await

/**
 * Lettuce 기반 분산 Cuckoo Filter (Coroutine/Suspend).
 *
 * [LettuceCuckooFilter]의 코루틴 버전입니다.
 */
class LettuceSuspendCuckooFilter(
    private val connection: StatefulRedisConnection<String, String>,
    val filterName: String,
    val options: CuckooFilterOptions = CuckooFilterOptions.Default,
) : AutoCloseable {

    companion object : KLogging()

    private val bucketsKey = "$filterName:buckets"
    private val configKey = "$filterName:config"
    val numBuckets: Long = (options.capacity + options.bucketSize - 1) / options.bucketSize

    private val asyncCommands: RedisAsyncCommands<String, String> get() = connection.async()

    /**
     * 필터 메타데이터를 초기화합니다.
     *
     * 동일한 이름의 필터가 이미 존재하면 저장된 구성과 현재 옵션의 호환성을 검증합니다.
     * `capacity`, `bucketSize`, `numBuckets` 중 하나라도 다르면 [IllegalStateException]을 던집니다.
     */
    suspend fun tryInit(): Boolean {
        val set = asyncCommands.hsetnx(configKey, "capacity", options.capacity.toString()).await()
        if (set) {
            asyncCommands.hset(
                configKey, mapOf(
                    "bucketSize" to options.bucketSize.toString(),
                    "numBuckets" to numBuckets.toString(),
                    "count" to "0",
                )
            ).await()
            log.debug { "SuspendCuckooFilter 초기화: name=$filterName" }
            return true
        }

        val storedCapacity = asyncCommands.hget(configKey, "capacity").await()?.toLongOrNull()
        val storedBucketSize = asyncCommands.hget(configKey, "bucketSize").await()?.toIntOrNull()
        val storedNumBuckets = asyncCommands.hget(configKey, "numBuckets").await()?.toLongOrNull()

        if (
            storedCapacity != null &&
            storedBucketSize != null &&
            storedNumBuckets != null &&
            (storedCapacity != options.capacity ||
                storedBucketSize != options.bucketSize ||
                storedNumBuckets != numBuckets)
        ) {
            throw IllegalStateException(
                "CuckooFilter '$filterName' 이미 다른 파라미터로 초기화됨: " +
                    "저장된 capacity=$storedCapacity/bucketSize=$storedBucketSize/numBuckets=$storedNumBuckets, " +
                    "현재 capacity=${options.capacity}/bucketSize=${options.bucketSize}/numBuckets=$numBuckets"
            )
        }
        return false
    }

    suspend fun insert(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return asyncCommands.eval<Long>(
            CuckooFilterScripts.INSERT, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString(),
            options.bucketSize.toString(), options.maxIterations.toString(), numBuckets.toString()
        ).await() == 1L
    }

    suspend fun contains(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return asyncCommands.eval<Long>(
            CuckooFilterScripts.CONTAINS, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString()
        ).await() == 1L
    }

    suspend fun delete(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return asyncCommands.eval<Long>(
            CuckooFilterScripts.DELETE, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString()
        ).await() == 1L
    }

    suspend fun count(): Long = asyncCommands.hget(configKey, "count").await()?.toLongOrNull() ?: 0L

    override fun close() = connection.close()

    private data class FingerprintData(val fp: Int, val i1: Long, val i2: Long)

    private fun fingerprint(element: String): FingerprintData {
        val bytes = element.toByteArray(Charsets.UTF_8)
        val (h1, _) = Murmur3.hash128x64(bytes)
        val fp = (Math.abs(h1.toInt()) % 255) + 1
        val i1 = Math.floorMod(h1, numBuckets) + 1
        val fpHash = Math.abs(fp.toLong() * 2654435761L) % numBuckets
        val i2 = Math.floorMod((i1 - 1) xor fpHash, numBuckets) + 1
        return FingerprintData(fp, i1, i2)
    }
}
