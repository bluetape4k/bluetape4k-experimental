package io.bluetape4k.redis.lettuce

import io.bluetape4k.codec.Base58
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.storage.RedisServer
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI

abstract class AbstractRedisLettuceTest {

    companion object : KLogging() {
        val redis: RedisServer by lazy { RedisServer.Launcher.redis }

        val client: RedisClient by lazy {
            RedisClient.create(
                RedisURI.builder()
                    .withHost(redis.host)
                    .withPort(redis.port)
                    .build()
            )
        }

        fun randomName(): String = "test:${Base58.randomString(8)}"
    }
}
