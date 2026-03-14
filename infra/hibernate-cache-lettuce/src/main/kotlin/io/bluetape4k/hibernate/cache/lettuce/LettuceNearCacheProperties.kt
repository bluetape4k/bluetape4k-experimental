package io.bluetape4k.hibernate.cache.lettuce

import io.bluetape4k.cache.nearcache.lettuce.NearCacheConfig
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodec
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodecs
import java.time.Duration

/**
 * Hibernate properties에서 Near Cache 설정을 파싱하는 data class.
 *
 * ```
 * # Hibernate properties 예시
 * hibernate.cache.lettuce.redis_uri=redis://localhost:6379
 * hibernate.cache.lettuce.codec=lz4fory               # lz4fory(기본), fory, kryo, lz4kryo 등
 * hibernate.cache.lettuce.local.max_size=10000
 * hibernate.cache.lettuce.local.expire_after_write=30m
 * hibernate.cache.lettuce.redis_ttl.default=120s
 * hibernate.cache.lettuce.redis_ttl.{regionName}=300s  # region별 TTL 오버라이드
 * hibernate.cache.lettuce.use_resp3=true
 * ```
 */
data class LettuceNearCacheProperties(
    val redisUri: String = "redis://localhost:6379",
    val codec: String = "lz4fory",
    val localMaxSize: Long = 10_000L,
    val localExpireAfterWrite: Duration = Duration.ofMinutes(30),
    val redisTtlDefault: Duration? = Duration.ofSeconds(120),
    val regionTtls: Map<String, Duration> = emptyMap(),
    val useResp3: Boolean = true,
    val recordLocalStats: Boolean = false,
) {
    companion object {
        private const val PREFIX = "hibernate.cache.lettuce."

        fun from(configValues: Map<String, Any>): LettuceNearCacheProperties {
            fun str(key: String, default: String): String =
                configValues["$PREFIX$key"]?.toString() ?: default

            fun long(key: String, default: Long): Long =
                configValues["$PREFIX$key"]?.toString()?.toLongOrNull() ?: default

            fun bool(key: String, default: Boolean): Boolean =
                configValues["$PREFIX$key"]?.toString()?.toBooleanStrictOrNull() ?: default

            fun duration(key: String, default: Duration?): Duration? {
                val raw = configValues["$PREFIX$key"]?.toString() ?: return default
                return parseDuration(raw) ?: default
            }

            val regionTtls = configValues.entries
                .filter { it.key.startsWith("${PREFIX}redis_ttl.") && !it.key.endsWith(".default") }
                .associate { (k, v) ->
                    k.removePrefix("${PREFIX}redis_ttl.") to
                            (parseDuration(v.toString()) ?: Duration.ofSeconds(120))
                }

            return LettuceNearCacheProperties(
                redisUri = str("redis_uri", "redis://localhost:6379"),
                codec = str("codec", "lz4fory"),
                localMaxSize = long("local.max_size", 10_000L),
                localExpireAfterWrite = duration("local.expire_after_write", Duration.ofMinutes(30))
                    ?: Duration.ofMinutes(30),
                redisTtlDefault = duration("redis_ttl.default", Duration.ofSeconds(120)),
                regionTtls = regionTtls,
                useResp3 = bool("use_resp3", true),
                recordLocalStats = bool("local.record_stats", false),
            )
        }

        private fun parseDuration(raw: String): Duration? = when {
            raw.endsWith("ms") -> Duration.ofMillis(raw.dropLast(2).toLongOrNull() ?: return null)
            raw.endsWith("s")  -> Duration.ofSeconds(raw.dropLast(1).toLongOrNull() ?: return null)
            raw.endsWith("m")  -> Duration.ofMinutes(raw.dropLast(1).toLongOrNull() ?: return null)
            raw.endsWith("h")  -> Duration.ofHours(raw.dropLast(1).toLongOrNull() ?: return null)
            else               -> Duration.ofSeconds(raw.toLongOrNull() ?: return null)
        }
    }

    /**
     * [BinarySerializers]를 사용해 [LettuceBinaryCodec]을 직접 생성.
     * [io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodecs]는 protobuf 의존성 문제로 사용하지 않음.
     */
    fun createCodec(): LettuceBinaryCodec<Any> = when (codec.lowercase()) {
        "jdk"        -> LettuceBinaryCodecs.jdk()
        "kryo"       -> LettuceBinaryCodecs.kryo()
        "fory"       -> LettuceBinaryCodecs.fory()
        "gzipjdk"    -> LettuceBinaryCodecs.gzipJdk()
        "gzipkryo"   -> LettuceBinaryCodecs.gzipKryo()
        "gzipfory"   -> LettuceBinaryCodecs.gzipFory()
        "lz4jdk"     -> LettuceBinaryCodecs.lz4Jdk()
        "lz4kryo"    -> LettuceBinaryCodecs.lz4Kryo()
        "lz4fory"    -> LettuceBinaryCodecs.lz4Fory()
        "snappyjdk"  -> LettuceBinaryCodecs.snappyJdk()
        "snappykryo" -> LettuceBinaryCodecs.snappyKryo()
        "snappyfory" -> LettuceBinaryCodecs.snappyFory()
        "zstdjdk"    -> LettuceBinaryCodecs.zstdJdk()
        "zstdkryo"   -> LettuceBinaryCodecs.zstdKryo()
        "zstdfory"   -> LettuceBinaryCodecs.zstdFory()
        else         -> LettuceBinaryCodecs.Default
    }

    fun buildNearCacheConfig(regionName: String): NearCacheConfig<String, Any> {
        val ttl = regionTtls[regionName] ?: redisTtlDefault
        return NearCacheConfig(
            cacheName = regionName,
            maxLocalSize = localMaxSize,
            frontExpireAfterWrite = localExpireAfterWrite,
            redisTtl = ttl,
            useRespProtocol3 = useResp3,
            recordStats = recordLocalStats,
        )
    }
}
