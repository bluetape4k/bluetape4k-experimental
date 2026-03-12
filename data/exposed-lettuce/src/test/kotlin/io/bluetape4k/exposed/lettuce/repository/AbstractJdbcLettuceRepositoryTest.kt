package io.bluetape4k.exposed.lettuce.repository

import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.storage.RedisServer
import io.lettuce.core.RedisClient

/**
 * [AbstractJdbcLettuceRepository] 통합 테스트.
 *
 * - H2 in-memory DB (Exposed DSL)
 * - Redis Testcontainers (Lettuce)
 */
abstract class AbstractJdbcLettuceRepositoryTest {

    companion object : KLogging() {
        val redis: RedisServer by lazy { RedisServer.Launcher.redis }

        val redisClient: RedisClient by lazy {
            RedisClient.create(
                RedisServer.Launcher.LettuceLib.getRedisURI(redis.host, redis.port)
            )
        }
    }


}
