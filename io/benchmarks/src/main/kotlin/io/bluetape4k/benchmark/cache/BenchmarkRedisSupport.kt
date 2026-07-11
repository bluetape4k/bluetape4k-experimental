package io.bluetape4k.benchmark.cache

import io.bluetape4k.testcontainers.storage.RedisServer
import io.bluetape4k.utils.ShutdownQueue
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.protocol.ProtocolVersion

/**
 * benchmark 용 Redis testcontainer 지원 객체입니다.
 *
 * RESP3 + CLIENT TRACKING 기반 near cache benchmark 가
 * 테스트 코드와 동일한 실행 환경을 재사용하도록 돕습니다.
 */
object BenchmarkRedisSupport {

    val redis: RedisServer by lazy {
        val reuse = BenchmarkContainerReuse.isEnabled()
        RedisServer(reuse = reuse).apply {
            start()
            if (!reuse) {
                ShutdownQueue.register(this)
            }
        }
    }

    private val clientRESP3Protocol: ClientOptions = ClientOptions.builder()
        .protocolVersion(ProtocolVersion.RESP3)
        .build()

    fun newResp3Client(): RedisClient =
        RedisClient.create(
            RedisServer.Launcher.LettuceLib.getRedisURI(redis.host, redis.port)
        ).also { client ->
            client.options = clientRESP3Protocol
        }.apply {
            ShutdownQueue.register { this.shutdown() }
        }

    fun newDirectConnection(): StatefulRedisConnection<String, String> =
        newResp3Client().connect(StringCodec.UTF8)
            .apply {
                ShutdownQueue.register(this)
            }

    fun flushDb(commands: RedisCommands<String, String>) {
        commands.flushdb()
    }
}

internal object BenchmarkContainerReuse {
    const val PROPERTY_NAME: String = "bluetape4k.testcontainers.reuse"

    fun isEnabled(
        propertyValue: String? = System.getProperty(PROPERTY_NAME),
        environment: Map<String, String> = System.getenv(),
    ): Boolean {
        val isCi = environment.containsKey("CI") || environment.containsKey("GITHUB_ACTIONS")
        return !isCi && propertyValue?.toBooleanStrictOrNull() == true
    }
}
