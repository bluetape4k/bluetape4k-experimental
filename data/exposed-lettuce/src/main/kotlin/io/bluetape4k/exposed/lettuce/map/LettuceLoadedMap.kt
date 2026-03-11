package io.bluetape4k.exposed.lettuce.map

import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.logging.error
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodec
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.StringCodec
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Lettuce(Redis) 기반 Read-through / Write-through / Write-behind Map.
 *
 * - **Read-through**: 캐시 미스 시 [MapLoader]를 통해 DB에서 값을 로드하고 Redis에 캐싱한다.
 * - **Write-through**: [MapWriter]를 통해 DB에 즉시 쓰고 Redis도 갱신한다.
 * - **Write-behind**: [MapWriter]를 통해 DB에 비동기로 쓰고 Redis는 즉시 갱신한다.
 * - **NONE**: Redis만 사용하고 DB 쓰기를 하지 않는다.
 *
 * @param K 키 타입
 * @param V 값 타입
 * @param client Lettuce [RedisClient]
 * @param loader DB 로드 함수 (Read-through, null이면 캐시 전용)
 * @param writer DB 쓰기 함수 (Write-through / Write-behind, null이면 쓰기 없음)
 * @param config [LettuceCacheConfig] 설정
 * @param keySerializer K → String 변환 함수 (기본: toString())
 */
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

    private val strConnection: StatefulRedisConnection<String, String> = client.connect(StringCodec.UTF8)
    private val strCommands: RedisCommands<String, String> = strConnection.sync()

    private val ttlSeconds = config.ttl.seconds

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
        val cached = runCatching { commands.get(redisKey) }.getOrNull()
        if (cached != null) return cached
        val loader = loader ?: return null
        val value = loader.load(key) ?: return null
        runCatching { commands.set(redisKey, value, SetArgs().ex(ttlSeconds)) }
            .onFailure { log.warn(it) { "Redis SETEX 실패: $redisKey" } }
        return value
    }

    operator fun set(key: K, value: V) {
        commands.set(redisKey(key), value, SetArgs().ex(ttlSeconds))
        when (config.writeMode) {
            WriteMode.WRITE_THROUGH -> writer?.write(mapOf(key to value))
            WriteMode.WRITE_BEHIND -> {
                val queue = writeBehindQueue ?: return
                if (!queue.offer(key to value)) {
                    throw IllegalStateException(
                        "Write-behind 큐 포화 (capacity=${config.writeBehindQueueCapacity})"
                    )
                }
            }
            WriteMode.NONE -> {}
        }
    }

    fun getAll(keys: Set<K>): Map<K, V> {
        if (keys.isEmpty()) return emptyMap()
        val result = mutableMapOf<K, V>()
        val missedKeys = mutableSetOf<K>()
        for (key in keys) {
            val value = runCatching { commands.get(redisKey(key)) }.getOrNull()
            if (value != null) result[key] = value else missedKeys.add(key)
        }
        if (missedKeys.isNotEmpty() && loader != null) {
            for (key in missedKeys) {
                val value = loader.load(key) ?: continue
                result[key] = value
                runCatching { commands.set(redisKey(key), value, SetArgs().ex(ttlSeconds)) }
                    .onFailure { log.warn(it) { "Redis SETEX 실패: ${redisKey(key)}" } }
            }
        }
        return result
    }

    fun delete(key: K) {
        commands.del(redisKey(key))
        if (config.writeMode != WriteMode.NONE) writer?.delete(listOf(key))
    }

    fun deleteAll(keys: Collection<K>) {
        if (keys.isEmpty()) return
        commands.del(*keys.map { redisKey(it) }.toTypedArray())
        if (config.writeMode != WriteMode.NONE) writer?.delete(keys)
    }

    fun clear() {
        val keys = commands.keys("${config.keyPrefix}:*")
        if (keys.isNotEmpty()) commands.del(*keys.toTypedArray())
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
                    runCatching {
                        val deadLetterKey = "${config.keyPrefix}:dead-letter"
                        strCommands.lpush(deadLetterKey, *batch.keys.map { keySerializer(it) }.toTypedArray())
                    }.onFailure { ex -> log.error(ex) { "Dead letter 기록 실패" } }
                }
        }
    }

    override fun close() {
        scheduler?.let { sched ->
            sched.shutdown()
            val deadline = System.currentTimeMillis() + config.writeBehindShutdownTimeout.toMillis()
            while (writeBehindQueue?.isNotEmpty() == true && System.currentTimeMillis() < deadline) {
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
