package io.bluetape4k.exposed.lettuce.map

import java.time.Duration

/**
 * [LettuceLoadedMap] 동작을 제어하는 설정.
 *
 * @param cacheName Redis 키 prefix (콜론 포함 불가)
 * @param ttl Redis 항목 TTL (null이면 만료 없음)
 * @param writeMode 쓰기 전략 ([WriteMode])
 * @param writeBehindDelay Write-Behind 모드의 비동기 지연 (기본 0)
 */
data class LettuceCacheConfig(
    val cacheName: String = "exposed-lettuce",
    val ttl: Duration? = null,
    val writeMode: WriteMode = WriteMode.WRITE_THROUGH,
    val writeBehindDelay: Duration = Duration.ZERO,
) {
    init {
        require(cacheName.isNotBlank()) { "cacheName must not be blank" }
        require(':' !in cacheName) {
            "cacheName must not contain ':'. Use '-' or '_' instead (e.g. 'my-cache')."
        }
    }

    /** Redis 저장 키 생성: "{cacheName}:{key}" */
    fun redisKey(key: String): String = "$cacheName:$key"
}

/** DSL 빌더 진입점 */
inline fun lettuceCacheConfig(block: LettuceCacheConfigBuilder.() -> Unit): LettuceCacheConfig =
    LettuceCacheConfigBuilder().apply(block).build()

class LettuceCacheConfigBuilder {
    var cacheName: String = "exposed-lettuce"
    var ttl: Duration? = null
    var writeMode: WriteMode = WriteMode.WRITE_THROUGH
    var writeBehindDelay: Duration = Duration.ZERO

    fun build(): LettuceCacheConfig = LettuceCacheConfig(
        cacheName = cacheName,
        ttl = ttl,
        writeMode = writeMode,
        writeBehindDelay = writeBehindDelay,
    )
}
