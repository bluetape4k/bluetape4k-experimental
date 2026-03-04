package io.bluetape4k.cache.nearcache.lettuce

import java.time.Duration

/**
 * Lettuce Near Cache (2-tier cache) 설정.
 *
 * @param K 키 타입
 * @param V 값 타입
 */
data class NearCacheConfig<K : Any, V : Any>(
    val cacheName: String = "lettuce-near-cache",
    val maxLocalSize: Long = 10_000,
    val localExpireAfterWrite: Duration = Duration.ofMinutes(30),
    val localExpireAfterAccess: Duration? = null,
    val redisTtl: Duration? = null,
    val useRespProtocol3: Boolean = true,
    val recordStats: Boolean = false,
)

/**
 * [NearCacheConfig] DSL 빌더.
 */
fun <K : Any, V : Any> nearCacheConfig(
    block: NearCacheConfigBuilder<K, V>.() -> Unit,
): NearCacheConfig<K, V> =
    NearCacheConfigBuilder<K, V>().apply(block).build()

class NearCacheConfigBuilder<K : Any, V : Any> {
    var cacheName: String = "lettuce-near-cache"
    var maxLocalSize: Long = 10_000
    var localExpireAfterWrite: Duration = Duration.ofMinutes(30)
    var localExpireAfterAccess: Duration? = null
    var redisTtl: Duration? = null
    var useRespProtocol3: Boolean = true
    var recordStats: Boolean = false

    fun build(): NearCacheConfig<K, V> = NearCacheConfig(
        cacheName = cacheName,
        maxLocalSize = maxLocalSize,
        localExpireAfterWrite = localExpireAfterWrite,
        localExpireAfterAccess = localExpireAfterAccess,
        redisTtl = redisTtl,
        useRespProtocol3 = useRespProtocol3,
        recordStats = recordStats,
    )
}
