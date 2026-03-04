package io.bluetape4k.cache.nearcache.lettuce

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

/**
 * Caffeine 기반 [LocalCache] 구현.
 */
class CaffeineLocalCache<K : Any, V : Any>(config: NearCacheConfig<K, V>) : LocalCache<K, V> {

    private val cache: Cache<K, V> = Caffeine.newBuilder()
        .maximumSize(config.maxLocalSize)
        .expireAfterWrite(config.localExpireAfterWrite)
        .also { builder ->
            config.localExpireAfterAccess?.let { builder.expireAfterAccess(it) }
        }
        .build()

    override fun get(key: K): V? = cache.getIfPresent(key)

    override fun getAll(keys: Set<K>): Map<K, V> = cache.getAllPresent(keys)

    override fun put(key: K, value: V) = cache.put(key, value)

    override fun putAll(map: Map<out K, V>) = cache.putAll(map)

    override fun remove(key: K) = cache.invalidate(key)

    override fun removeAll(keys: Set<K>) = cache.invalidateAll(keys)

    override fun invalidate(key: K) = cache.invalidate(key)

    override fun invalidateAll(keys: Collection<K>) = cache.invalidateAll(keys)

    override fun containsKey(key: K): Boolean = cache.getIfPresent(key) != null

    override fun clear() = cache.invalidateAll()

    override fun estimatedSize(): Long = cache.estimatedSize()

    override fun close() {
        cache.invalidateAll()
        cache.cleanUp()
    }
}
