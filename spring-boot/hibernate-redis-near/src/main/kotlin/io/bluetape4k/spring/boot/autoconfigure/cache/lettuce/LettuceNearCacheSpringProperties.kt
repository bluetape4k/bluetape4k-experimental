package io.bluetape4k.spring.boot.autoconfigure.cache.lettuce

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * `application.yml` / `application.properties` 기반 Spring Boot 설정.
 *
 * ```yaml
 * bluetape4k:
 *   cache:
 *     lettuce-near:
 *       redis-uri: redis://localhost:6379
 *       codec: zstdfory
 *       use-resp3: true
 *       local:
 *         max-size: 10000
 *         expire-after-write: 30m
 *       redis-ttl:
 *         default: 120s
 *         regions:
 *           myEntity: 300s
 *       metrics:
 *         enabled: true
 *         enable-caffeine-stats: true
 * ```
 */
@ConfigurationProperties(prefix = "bluetape4k.cache.lettuce-near")
data class LettuceNearCacheSpringProperties(
    val enabled: Boolean = true,
    val redisUri: String = "redis://localhost:6379",
    val codec: String = "zstdfory",
    val useResp3: Boolean = true,
    val local: LocalProperties = LocalProperties(),
    val redisTtl: RedisTtlProperties = RedisTtlProperties(),
    val metrics: MetricsProperties = MetricsProperties(),
) {
    data class LocalProperties(
        val maxSize: Long = 10_000,
        val expireAfterWrite: Duration = Duration.ofMinutes(30),
    )

    data class RedisTtlProperties(
        val default: Duration = Duration.ofSeconds(120),
        val regions: Map<String, Duration> = emptyMap(),
    )

    data class MetricsProperties(
        val enabled: Boolean = true,
        val enableCaffeineStats: Boolean = true,
    )
}
