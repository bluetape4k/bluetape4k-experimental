package io.bluetape4k.redis.lettuce.filter

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands

/**
 * Lettuce 기반 분산 Cuckoo Filter (Sync).
 *
 * BloomFilter와 달리 원소 **삭제**를 지원합니다.
 * kick-out 재배치 실패 시 undo-log로 원래 상태를 복구합니다.
 */
class LettuceCuckooFilter(
    private val connection: StatefulRedisConnection<String, String>,
    val filterName: String,
    val options: CuckooFilterOptions = CuckooFilterOptions.Default,
) : AutoCloseable {

    companion object : KLogging()

    private val bucketsKey = "$filterName:buckets"
    private val configKey = "$filterName:config"
    val numBuckets: Long = (options.capacity + options.bucketSize - 1) / options.bucketSize

    private val commands: RedisCommands<String, String> = connection.sync()

    /**
     * 필터 메타데이터를 초기화합니다.
     *
     * 동일한 이름의 필터가 이미 존재하면 저장된 구성과 현재 옵션의 호환성을 검증합니다.
     * `capacity`, `bucketSize`, `numBuckets` 중 하나라도 다르면 [IllegalStateException]을 던집니다.
     */
    fun tryInit(): Boolean {
        val set = commands.hsetnx(configKey, "capacity", options.capacity.toString())
        if (set) {
            commands.hset(
                configKey, mapOf(
                    "bucketSize" to options.bucketSize.toString(),
                    "numBuckets" to numBuckets.toString(),
                    "count" to "0",
                )
            )
            log.debug { "CuckooFilter 초기화: name=$filterName, numBuckets=$numBuckets" }
            return true
        }

        val storedCapacity = commands.hget(configKey, "capacity")?.toLongOrNull()
        val storedBucketSize = commands.hget(configKey, "bucketSize")?.toIntOrNull()
        val storedNumBuckets = commands.hget(configKey, "numBuckets")?.toLongOrNull()

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

    fun insert(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return commands.eval<Long>(
            CuckooFilterScripts.INSERT, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString(),
            options.bucketSize.toString(), options.maxIterations.toString(), numBuckets.toString()
        ) == 1L
    }

    fun contains(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return commands.eval<Long>(
            CuckooFilterScripts.CONTAINS, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString()
        ) == 1L
    }

    fun delete(element: String): Boolean {
        val (fp, i1, i2) = fingerprint(element)
        return commands.eval<Long>(
            CuckooFilterScripts.DELETE, ScriptOutputType.INTEGER,
            arrayOf(bucketsKey, configKey),
            fp.toString(), i1.toString(), i2.toString()
        ) == 1L
    }

    fun count(): Long = commands.hget(configKey, "count")?.toLongOrNull() ?: 0L

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
