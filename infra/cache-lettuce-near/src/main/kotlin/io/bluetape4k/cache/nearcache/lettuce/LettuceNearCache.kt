package io.bluetape4k.cache.nearcache.lettuce

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lettuce 기반 Near Cache (2-tier cache) - 동기(Blocking) 구현.
 *
 * ## 아키텍처
 * ```
 * Application
 *     |
 * [LettuceNearCache]
 *     |
 * +---+---+
 * |       |
 * Front   Back
 * Caffeine  Redis (via Lettuce)
 *
 * Invalidation: Redis CLIENT TRACKING (RESP3) -> server push -> local invalidate
 * ```
 *
 * - Read: front hit → return / front miss → Redis GET → front populate → return
 * - Write: front put + Redis SET (write-through)
 * - Invalidation: RESP3 CLIENT TRACKING push → [CaffeineLocalCache.invalidate]
 *
 * @param K 키 타입
 * @param V 값 타입
 */
class LettuceNearCache<K : Any, V : Any>(
    private val redisClient: RedisClient,
    private val codec: RedisCodec<K, V>,
    private val config: NearCacheConfig<K, V> = NearCacheConfig(),
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(javaClass)
    private val closed = AtomicBoolean(false)

    private val localCache: LocalCache<K, V> = CaffeineLocalCache(config)
    private val connection: StatefulRedisConnection<K, V> = redisClient.connect(codec)
    private val commands: RedisCommands<K, V> = connection.sync()
    private val trackingListener: TrackingInvalidationListener<K, V>

    init {
        trackingListener = TrackingInvalidationListener(localCache, connection, codec)
        if (config.useRespProtocol3) {
            runCatching { trackingListener.start() }
                .onFailure { e -> log.warn("CLIENT TRACKING start failed, cache will work without invalidation: {}", e.message) }
        }
    }

    /**
     * 키에 대한 값을 조회한다.
     * - front hit → return
     * - front miss → Redis GET → front populate → return
     */
    fun get(key: K): V? {
        localCache.get(key)?.let { return it }

        return commands.get(key)?.also { value ->
            localCache.put(key, value)
        }
    }

    /**
     * 여러 키에 대한 값을 한 번에 조회한다 (multi-get).
     */
    fun getAll(keys: Set<K>): Map<K, V> {
        val result = mutableMapOf<K, V>()
        val missedKeys = mutableListOf<K>()

        for (key in keys) {
            val local = localCache.get(key)
            if (local != null) {
                result[key] = local
            } else {
                missedKeys.add(key)
            }
        }

        if (missedKeys.isNotEmpty()) {
            val pipeline = connection.async()
            val futures = missedKeys.associateWith { pipeline.get(it) }
            connection.flushCommands()
            futures.forEach { (key, future) ->
                val value = future.get()
                if (value != null) {
                    result[key] = value
                    localCache.put(key, value)
                }
            }
        }

        return result
    }

    /**
     * key-value를 저장한다 (write-through).
     * front cache + Redis SET (TTL 있으면 SETEX).
     *
     * write-through 후 async Redis GET을 fire-and-forget으로 실행해 CLIENT TRACKING을 활성화한다.
     * 이로써 local cache가 front hit 상태라도 다른 인스턴스가 같은 키를 수정하면
     * invalidation push를 수신할 수 있다.
     */
    fun put(key: K, value: V) {
        localCache.put(key, value)
        setRedis(key, value)
        // CLIENT TRACKING 활성화: 다른 인스턴스가 이 키를 수정할 때 invalidation을 받을 수 있도록
        connection.async().get(key)
    }

    /**
     * 여러 key-value를 한 번에 저장한다.
     */
    fun putAll(map: Map<out K, V>) {
        localCache.putAll(map)
        val async = connection.async()
        map.forEach { (key, value) ->
            setRedis(key, value)
            async.get(key)  // CLIENT TRACKING 활성화
        }
    }

    /**
     * 해당 키가 없을 때만 저장한다 (put-if-absent).
     * @return 기존 값(있었으면) 또는 null(새로 저장됨)
     */
    fun putIfAbsent(key: K, value: V): V? {
        val existing = get(key)
        if (existing != null) return existing

        val setted = commands.setnx(key, value)
        return if (setted) {
            config.redisTtl?.let { ttl ->
                commands.expire(key, ttl.seconds)
            }
            localCache.put(key, value)
            null
        } else {
            commands.get(key)
        }
    }

    /**
     * 키를 제거한다 (front + Redis).
     */
    fun remove(key: K) {
        localCache.remove(key)
        commands.del(key)
    }

    /**
     * 여러 키를 한 번에 제거한다.
     */
    fun removeAll(keys: Set<K>) {
        localCache.removeAll(keys)
        keys.forEach { commands.del(it) }
    }

    /**
     * 기존 값을 새 값으로 교체한다.
     * @return 교체 성공 여부
     */
    fun replace(key: K, value: V): Boolean {
        commands.get(key) ?: return false
        val ok = commands.set(key, value, SetArgs.Builder.xx()) != null
        if (ok) {
            localCache.put(key, value)
        }
        return ok
    }

    /**
     * 기존 값이 oldValue와 같을 때만 newValue로 교체한다.
     */
    fun replace(key: K, oldValue: V, newValue: V): Boolean {
        val current = get(key) ?: return false
        if (current != oldValue) return false
        return replace(key, newValue)
    }

    /**
     * 조회 후 제거한다.
     */
    fun getAndRemove(key: K): V? {
        val value = get(key)
        if (value != null) {
            remove(key)
        }
        return value
    }

    /**
     * 조회 후 교체한다.
     */
    fun getAndReplace(key: K, value: V): V? {
        val existing = get(key) ?: return null
        put(key, value)
        return existing
    }

    /**
     * 해당 키가 캐시에 존재하는지 확인한다 (front or Redis).
     */
    fun containsKey(key: K): Boolean {
        if (localCache.containsKey(key)) return true
        return commands.exists(key) > 0
    }

    /**
     * 로컬 캐시만 비운다 (Redis 유지).
     */
    fun clearLocal() {
        localCache.clear()
    }

    /**
     * 로컬 캐시 + Redis를 모두 비운다.
     */
    fun clearAll() {
        localCache.clear()
        commands.flushdb()
    }

    /**
     * 로컬 캐시의 추정 크기.
     */
    fun localSize(): Long = localCache.estimatedSize()

    /**
     * 로컬 캐시(Caffeine) 통계. [NearCacheConfig.recordStats]가 true일 때만 유효한 값을 반환한다.
     */
    fun localStats(): com.github.benmanes.caffeine.cache.stats.CacheStats? = localCache.stats()

    /**
     * 모든 리소스를 정리하고 연결을 닫는다.
     */
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { trackingListener.close() }
            runCatching { connection.close() }
            runCatching { localCache.close() }
            log.debug("LettuceNearCache [{}] closed", config.cacheName)
        }
    }

    private fun setRedis(key: K, value: V) {
        val ttl = config.redisTtl
        if (ttl != null) {
            commands.set(key, value, SetArgs.Builder.ex(ttl.seconds))
        } else {
            commands.set(key, value)
        }
    }

    companion object {
        /**
         * String 키/값 타입의 Near Cache를 생성한다.
         */
        fun create(
            redisClient: RedisClient,
            config: NearCacheConfig<String, String> = NearCacheConfig(),
        ): LettuceNearCache<String, String> =
            LettuceNearCache(redisClient, StringCodec.UTF8, config)
    }
}
