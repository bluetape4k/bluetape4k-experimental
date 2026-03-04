package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.cache.nearcache.lettuce.LettuceNearCache
import org.hibernate.cache.spi.support.DomainDataStorageAccess
import org.hibernate.engine.spi.SharedSessionContractImplementor

/**
 * [DomainDataStorageAccess] 구현체.
 *
 * [LettuceNearCache]를 래핑하여 Hibernate 2nd level cache 브릿지 역할을 한다.
 * Region 격리를 위해 모든 key에 `regionName:` prefix를 추가한다.
 *
 * - [getFromCache]: Caffeine(L1) → Redis(L2) 순서로 조회
 * - [putIntoCache]: write-through (L1 + L2 동시 저장)
 * - [evictData]: region 전체 evict 시 local cache만 clear (Redis는 TTL 만료)
 * - [evictData] with key: 특정 key만 L1+L2 제거
 */
class LettuceNearCacheStorageAccess(
    private val regionName: String,
    private val nearCache: LettuceNearCache<String, Any>,
) : DomainDataStorageAccess {

    private fun regionKey(key: Any): String = "$regionName:$key"

    override fun getFromCache(key: Any, session: SharedSessionContractImplementor): Any? =
        nearCache.get(regionKey(key))

    override fun putIntoCache(key: Any, value: Any, session: SharedSessionContractImplementor) {
        nearCache.put(regionKey(key), value)
    }

    override fun contains(key: Any): Boolean =
        nearCache.containsKey(regionKey(key))

    override fun evictData(key: Any) {
        nearCache.remove(regionKey(key))
    }

    /**
     * region 전체 evict: local cache만 clear한다.
     * Redis 데이터는 TTL로 자연 만료되도록 유지.
     * (flushdb()는 다른 region 데이터까지 삭제하므로 사용하지 않음)
     */
    override fun evictData() {
        nearCache.clearLocal()
    }

    override fun release() {
        nearCache.close()
    }
}
