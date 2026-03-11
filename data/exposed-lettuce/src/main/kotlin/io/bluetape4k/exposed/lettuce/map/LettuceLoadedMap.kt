package io.bluetape4k.exposed.lettuce.map

import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.logging.error
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodec
import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
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

    companion object : KLogging() {
        private const val MAX_DEAD_LETTER_RETRY = 3
    }

    @Suppress("UNCHECKED_CAST")
    private val codec = LettuceBinaryCodec<V>(BinarySerializers.LZ4Fory)

    private val connection: StatefulRedisConnection<String, V> = client.connect(codec)
    private val commands: RedisCommands<String, V> = connection.sync()

    // Fix 5: lazy 초기화 - strConnection은 dead letter 기록 시에만 사용
    private val strConnection: StatefulRedisConnection<String, String> by lazy { client.connect(StringCodec.UTF8) }
    private val strCommands: RedisCommands<String, String> by lazy { strConnection.sync() }

    private val ttlSeconds = config.ttl.seconds

    // Fix 1: Triple<K, V, Int> - retryCount 포함
    private val writeBehindQueue: LinkedBlockingDeque<Triple<K, V, Int>>? =
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
        // Fix 2: WRITE_BEHIND는 큐 offer 먼저, NONE/WRITE_THROUGH는 Redis 먼저
        when (config.writeMode) {
            WriteMode.NONE -> {
                commands.set(redisKey(key), value, SetArgs().ex(ttlSeconds))
            }
            WriteMode.WRITE_THROUGH -> {
                commands.set(redisKey(key), value, SetArgs().ex(ttlSeconds))
                writer?.write(mapOf(key to value))
            }
            WriteMode.WRITE_BEHIND -> {
                val queue = writeBehindQueue ?: return
                // 먼저 큐에 offer 시도 (Triple with retryCount=0)
                if (!queue.offer(Triple(key, value, 0))) {
                    throw IllegalStateException(
                        "Write-behind 큐 포화 (capacity=${config.writeBehindQueueCapacity})"
                    )
                }
                // 큐 삽입 성공 후 Redis 쓰기
                commands.set(redisKey(key), value, SetArgs().ex(ttlSeconds))
            }
        }
    }

    fun getAll(keys: Set<K>): Map<K, V> {
        if (keys.isEmpty()) return emptyMap()
        val keyList = keys.toList()
        val redisKeys = keyList.map { redisKey(it) }.toTypedArray()

        // Fix 4: MGET으로 한번에 조회
        val values = runCatching { commands.mget(*redisKeys) }.getOrNull() ?: emptyList()

        val result = mutableMapOf<K, V>()
        val missedKeys = mutableListOf<K>()

        values.forEachIndexed { i, kv ->
            if (kv != null && kv.hasValue()) result[keyList[i]] = kv.value
            else missedKeys.add(keyList[i])
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
        // Fix 3: KEYS 대신 SCAN 사용 (프로덕션 안전)
        val pattern = "${config.keyPrefix}:*"
        val scanArgs = ScanArgs.Builder.matches(pattern).limit(100)
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val scanResult = commands.scan(cursor, scanArgs)
            if (scanResult.keys.isNotEmpty()) {
                commands.del(*scanResult.keys.toTypedArray())
            }
            cursor = scanResult
        } while (!cursor.isFinished)
    }

    private fun flushWriteBehindQueue() {
        val queue = writeBehindQueue ?: return
        // Fix 1: Triple<K, V, Int> - retryCount 추적
        val entries = mutableListOf<Triple<K, V, Int>>()
        var count = 0
        while (count < config.writeBehindBatchSize) {
            val entry = queue.poll() ?: break
            entries.add(entry)
            count++
        }
        if (entries.isEmpty()) return

        val batch = entries.associate { it.first to it.second }
        runCatching { writer?.write(batch) }
            .onFailure { e ->
                val retryCount = entries.first().third + 1
                log.error(e) { "Write-behind flush 실패 (attempt $retryCount): ${batch.keys}" }
                if (retryCount < MAX_DEAD_LETTER_RETRY) {
                    // 재시도: 큐 앞에 재삽입
                    entries.forEach { (k, v, _) ->
                        queue.offerFirst(Triple(k, v, retryCount))
                    }
                } else {
                    // Dead letter 기록 후 폐기
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
