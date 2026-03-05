package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.cache.nearcache.lettuce.LettuceNearCache
import org.hibernate.cache.spi.support.DomainDataStorageAccess
import org.hibernate.engine.spi.SharedSessionContractImplementor

/**
 * [DomainDataStorageAccess] 구현체.
 *
 * [LettuceNearCache]를 래핑하여 Hibernate 2nd level cache 브릿지 역할을 한다.
 * Region 격리는 nearCache의 cacheName(=regionName) prefix가 담당한다.
 * Redis 실제 key: `{regionName}:{entityKey}`
 *
 * - [getFromCache]: Caffeine(L1) → Redis(L2) 순서로 조회
 * - [putIntoCache]: write-through (L1 + L2 동시 저장)
 * - [evictData]: region 전체 evict 시 local + Redis 모두 제거
 * - [evictData] with key: 특정 key만 L1+L2 제거
 */
class LettuceNearCacheStorageAccess(
    private val regionName: String,
    private val nearCache: LettuceNearCache<Any>,
) : DomainDataStorageAccess {

    // nearCache가 cacheName(=regionName) prefix를 Redis key에 자동으로 추가하므로
    // 여기서는 key를 문자열로 변환만 한다. (이중 prefix 방지)
    private fun cacheKey(key: Any): String = key.toString()

    override fun getFromCache(key: Any, session: SharedSessionContractImplementor): Any? =
        nearCache.get(cacheKey(key))

    override fun putIntoCache(key: Any, value: Any, session: SharedSessionContractImplementor) {
        nearCache.put(cacheKey(key), value)
    }

    override fun contains(key: Any): Boolean =
        nearCache.containsKey(cacheKey(key))

    override fun evictData(key: Any) {
        nearCache.remove(cacheKey(key))
    }

    /**
     * region 전체 evict: local + Redis 모두 제거한다.
     */
    override fun evictData() {
        nearCache.clearAll()
    }

    override fun release() {
        nearCache.close()
    }
}
