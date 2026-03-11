package io.bluetape4k.exposed.lettuce.map

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.RedisCodec
import kotlinx.atomicfu.atomic
import java.util.concurrent.Executors

/**
 * Lettuce(Redis) 기반 Read-through / Write-through / Write-behind Map.
 *
 * - **Read-through**: 캐시 미스 시 [MapLoader]를 통해 DB에서 값을 로드하고 Redis에 캐싱한다.
 * - **Write-through**: [MapWriter]를 통해 DB에 즉시 쓰고 Redis도 갱신한다.
 * - **Write-behind**: [MapWriter]를 통해 DB에 비동기로 쓰고 Redis는 즉시 갱신한다.
 * - **READ_ONLY**: Redis만 사용하고 DB 쓰기를 하지 않는다.
 *
 * @param K 키 타입 (String으로 직렬화 가능해야 함)
 * @param V 값 타입
 * @param connection Lettuce StatefulRedisConnection
 * @param codec RedisCodec<String, V>
 * @param config [LettuceCacheConfig] 설정
 * @param loader DB 로드 함수 (Read-through, null이면 캐시 전용)
 * @param writer DB 쓰기 함수 (Write-through / Write-behind, null이면 쓰기 없음)
 * @param keySerializer K → String 변환 함수 (기본: toString())
 */
class LettuceLoadedMap<K: Any, V: Any>(
    private val connection: StatefulRedisConnection<String, V>,
    private val codec: RedisCodec<String, V>,
    private val config: LettuceCacheConfig = LettuceCacheConfig(),
    private val loader: MapLoader<K, V>? = null,
    private val writer: MapWriter<K, V>? = null,
    private val keySerializer: (K) -> String = { it.toString() },
): AutoCloseable {

    companion object: KLogging()

    private val closed = atomic(false)
    val isClosed: Boolean by closed

    private val commands: RedisCommands<String, V> = connection.sync()

    // Write-behind 전용 단일 스레드 executor
    private val writeBehindExecutor by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "lettuce-write-behind-${config.cacheName}").also { it.isDaemon = true }
        }
    }

    private fun redisKey(key: K): String = config.redisKey(keySerializer(key))

    /**
     * 키에 해당하는 값을 반환한다.
     * - Redis hit → return
     * - Redis miss + loader 있음 → DB 로드 후 Redis에 캐싱 → return
     * - Redis miss + loader 없음 → null
     */
    fun get(key: K): V? {
        val rKey = redisKey(key)
        val cached = commands.get(rKey)
        if (cached != null) {
            log.debug { "Cache hit: $rKey" }
            return cached
        }

        val loaded = loader?.load(key) ?: return null
        setRedis(rKey, loaded)
        log.debug { "Cache miss -> loaded from DB: $rKey" }
        return loaded
    }

    /**
     * 여러 키를 한 번에 조회한다.
     * 캐시 미스된 키는 [MapLoader]로 일괄 로드한다.
     */
    fun getAll(keys: Set<K>): Map<K, V> {
        if (keys.isEmpty()) return emptyMap()

        val result = mutableMapOf<K, V>()
        val missedKeys = mutableListOf<K>()

        keys.forEach { key ->
            val cached = commands.get(redisKey(key))
            if (cached != null) result[key] = cached
            else missedKeys.add(key)
        }

        if (missedKeys.isNotEmpty() && loader != null) {
            missedKeys.forEach { key ->
                loader.load(key)?.let { value ->
                    result[key] = value
                    setRedis(redisKey(key), value)
                }
            }
        }

        return result
    }

    /**
     * key-value를 저장한다.
     * - WriteMode에 따라 DB 쓰기 전략이 달라진다.
     */
    fun put(key: K, value: V) {
        setRedis(redisKey(key), value)
        when (config.writeMode) {
            WriteMode.READ_ONLY -> { /* no DB write */ }
            WriteMode.WRITE_THROUGH -> writer?.write(key, value)
            WriteMode.WRITE_BEHIND -> scheduleWriteBehind(key, value)
        }
    }

    /**
     * 여러 항목을 일괄 저장한다.
     */
    fun putAll(entries: Map<K, V>) {
        if (entries.isEmpty()) return
        entries.forEach { (k, v) -> setRedis(redisKey(k), v) }
        when (config.writeMode) {
            WriteMode.READ_ONLY -> { /* no DB write */ }
            WriteMode.WRITE_THROUGH -> writer?.writeAll(entries)
            WriteMode.WRITE_BEHIND -> entries.forEach { (k, v) -> scheduleWriteBehind(k, v) }
        }
    }

    /**
     * 키에 해당하는 항목을 제거한다.
     */
    fun remove(key: K) {
        commands.del(redisKey(key))
        when (config.writeMode) {
            WriteMode.READ_ONLY -> { /* no DB write */ }
            WriteMode.WRITE_THROUGH -> writer?.delete(key)
            WriteMode.WRITE_BEHIND -> scheduleDeleteBehind(key)
        }
    }

    /**
     * 여러 항목을 일괄 제거한다.
     */
    fun removeAll(keys: Set<K>) {
        if (keys.isEmpty()) return
        commands.del(*keys.map { redisKey(it) }.toTypedArray())
        when (config.writeMode) {
            WriteMode.READ_ONLY -> { /* no DB write */ }
            WriteMode.WRITE_THROUGH -> writer?.deleteAll(keys)
            WriteMode.WRITE_BEHIND -> keys.forEach { scheduleDeleteBehind(it) }
        }
    }

    /**
     * 키가 Redis에 존재하는지 확인한다.
     */
    fun containsKey(key: K): Boolean =
        commands.exists(redisKey(key)) > 0

    /**
     * 이 cacheName에 속한 Redis 항목 수를 반환한다 (SCAN).
     */
    fun size(): Long {
        val pattern = "${config.cacheName}:*"
        var count = 0L
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val result: KeyScanCursor<String> =
                commands.scan(cursor, ScanArgs.Builder.matches(pattern).limit(100))
            count += result.keys.size
            cursor = result
        } while (!result.isFinished)
        return count
    }

    /**
     * 이 cacheName에 속한 모든 Redis 항목을 삭제한다 (SCAN 방식, FLUSHDB 금지).
     */
    fun clearAll() {
        val pattern = "${config.cacheName}:*"
        var cursor: ScanCursor = ScanCursor.INITIAL
        do {
            val result: KeyScanCursor<String> =
                commands.scan(cursor, ScanArgs.Builder.matches(pattern).limit(100))
            if (result.keys.isNotEmpty()) {
                commands.del(*result.keys.toTypedArray())
            }
            cursor = result
        } while (!result.isFinished)
    }

    override fun close() {
        if (closed.compareAndSet(expect = false, update = true)) {
            if (config.writeMode == WriteMode.WRITE_BEHIND) {
                runCatching { writeBehindExecutor.shutdown() }
            }
            log.debug { "LettuceLoadedMap [${config.cacheName}] closed" }
        }
    }

    // ---- private helpers ----

    private fun setRedis(rKey: String, value: V) {
        val ttl = config.ttl
        if (ttl != null) {
            commands.set(rKey, value, SetArgs.Builder.ex(ttl.seconds))
        } else {
            commands.set(rKey, value)
        }
    }

    private fun scheduleWriteBehind(key: K, value: V) {
        writeBehindExecutor.submit {
            runCatching { writer?.write(key, value) }
                .onFailure { e -> log.warn(e) { "Write-behind failed for key=$key" } }
        }
    }

    private fun scheduleDeleteBehind(key: K) {
        writeBehindExecutor.submit {
            runCatching { writer?.delete(key) }
                .onFailure { e -> log.warn(e) { "Write-behind delete failed for key=$key" } }
        }
    }
}
