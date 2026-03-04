package io.bluetape4k.cache.nearcache.lettuce

import io.lettuce.core.TrackingArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.push.PushListener
import io.lettuce.core.codec.RedisCodec
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Redis RESP3 CLIENT TRACKING 기반 local cache invalidation 리스너.
 *
 * Redis 서버에서 키가 변경될 때 CLIENT TRACKING이 invalidation push 메시지를 보내고,
 * 수신 즉시 [LocalCache.invalidate]를 호출해 로컬 캐시 항목을 무효화한다.
 *
 * - `CLIENT TRACKING ON NOLOOP`: 자신이 쓴 키는 invalidation을 받지 않는다.
 * - `PushMessage.getContent()`: content[0] = type ByteBuffer, content[1] = keys List<ByteBuffer> or null
 *
 * @param K 키 타입
 * @param V 값 타입
 */
class TrackingInvalidationListener<K : Any, V : Any>(
    private val localCache: LocalCache<K, V>,
    private val connection: StatefulRedisConnection<K, V>,
    private val codec: RedisCodec<K, V>,
) : AutoCloseable {

    private val log = LoggerFactory.getLogger(javaClass)
    private val started = AtomicBoolean(false)

    /**
     * invalidate push 메시지를 처리하는 PushListener.
     * content[0] = type (ByteBuffer), content[1] = key list (List<ByteBuffer>) or null (= full flush)
     */
    private val pushListener = PushListener { message ->
        if (message.type == "invalidate") {
            handleInvalidation(message.content)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleInvalidation(content: List<Any?>) {
        // content[0] = type string as ByteBuffer (already matched "invalidate")
        // content[1] = null (flush all) or List<ByteBuffer> (key bytes)
        val keysRaw = if (content.size >= 2) content[1] else null

        if (keysRaw == null) {
            log.debug("Received full invalidation flush")
            localCache.clear()
            return
        }

        val keys = when (keysRaw) {
            is List<*> -> (keysRaw as List<ByteBuffer?>)
                .filterNotNull()
                .map { codec.decodeKey(it.duplicate()) }
            is ByteBuffer -> listOf(codec.decodeKey(keysRaw.duplicate()))
            else -> emptyList()
        }

        if (keys.isNotEmpty()) {
            log.debug("Invalidating {} keys from local cache", keys.size)
            localCache.invalidateAll(keys)
        }
    }

    /**
     * CLIENT TRACKING을 활성화하고 push 리스너를 등록한다.
     */
    fun start() {
        if (started.compareAndSet(false, true)) {
            try {
                connection.addListener(pushListener)
                connection.sync().clientTracking(
                    TrackingArgs.Builder.enabled().noloop()
                )
                log.debug("CLIENT TRACKING (RESP3) enabled")
            } catch (e: Exception) {
                started.set(false)
                connection.removeListener(pushListener)
                log.warn("Failed to enable CLIENT TRACKING: {}", e.message, e)
                throw e
            }
        }
    }

    /**
     * CLIENT TRACKING을 비활성화하고 push 리스너를 제거한다.
     */
    override fun close() {
        if (started.compareAndSet(true, false)) {
            runCatching {
                connection.sync().clientTracking(TrackingArgs.Builder.enabled(false))
            }.onFailure { e ->
                log.warn("Failed to disable CLIENT TRACKING: {}", e.message)
            }
            connection.removeListener(pushListener)
            log.debug("CLIENT TRACKING disabled and listener removed")
        }
    }
}
