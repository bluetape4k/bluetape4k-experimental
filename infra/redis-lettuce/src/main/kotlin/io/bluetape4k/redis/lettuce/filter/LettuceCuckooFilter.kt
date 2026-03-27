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
        }
        return set
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
