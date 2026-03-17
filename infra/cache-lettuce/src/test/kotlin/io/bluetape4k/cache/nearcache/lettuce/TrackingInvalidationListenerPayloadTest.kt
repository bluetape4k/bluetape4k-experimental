package io.bluetape4k.cache.nearcache.lettuce

import io.lettuce.core.codec.StringCodec
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContainSame
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

class TrackingInvalidationListenerPayloadTest : AbstractLettuceNearCacheTest() {

    @Test
    fun `invalidation payload가 mixed type 이어도 cacheName prefix 키만 무효화한다`() {
        val front = RecordingLocalCache()
        val connection = resp3Client.connect(StringCodec.UTF8)
        TrackingInvalidationListener(front, connection, "test-cache").use { listener ->
            invokeHandleInvalidation(
                listener = listener,
                content = listOf(
                    "invalidate",
                    listOf(
                        ByteBuffer.wrap("test-cache:k1".toByteArray()),
                        "test-cache:k2",
                        ByteBuffer.wrap("other-cache:k3".toByteArray()),
                        1234,
                    )
                )
            )
        }
        connection.close()

        front.invalidatedKeys.shouldContainSame(listOf("k1", "k2"))
    }

    @Test
    fun `invalidation payload 가 null 이면 local cache 전체 clear 한다`() {
        val front = RecordingLocalCache()
        val connection = resp3Client.connect(StringCodec.UTF8)
        TrackingInvalidationListener(front, connection, "test-cache").use { listener ->
            invokeHandleInvalidation(listener = listener, content = listOf("invalidate", null))
        }
        connection.close()

        front.clearCount shouldBeEqualTo 1
    }

    @Test
    fun `다른 cacheName 키만 전달되면 invalidate 하지 않는다`() {
        val front = RecordingLocalCache()
        val connection = resp3Client.connect(StringCodec.UTF8)
        TrackingInvalidationListener(front, connection, "test-cache").use { listener ->
            invokeHandleInvalidation(
                listener = listener,
                content = listOf(
                    "invalidate",
                    listOf(
                        ByteBuffer.wrap("other-cache:a".toByteArray()),
                        "other-cache:b"
                    )
                )
            )
        }
        connection.close()

        front.invalidatedKeys.size shouldBeEqualTo 0
    }

    @Test
    fun `invalidation payload가 ByteArray면 정상 디코딩 후 무효화한다`() {
        val front = RecordingLocalCache()
        val connection = resp3Client.connect(StringCodec.UTF8)
        TrackingInvalidationListener(front, connection, "test-cache").use { listener ->
            invokeHandleInvalidation(
                listener = listener,
                content = listOf(
                    "invalidate",
                    listOf("test-cache:byte-key".toByteArray())
                )
            )
        }
        connection.close()

        front.invalidatedKeys.shouldContainSame(listOf("byte-key"))
    }

    @Test
    fun `invalidation payload가 단일 ByteBuffer면 해당 key만 무효화한다`() {
        val front = RecordingLocalCache()
        val connection = resp3Client.connect(StringCodec.UTF8)
        TrackingInvalidationListener(front, connection, "test-cache").use { listener ->
            invokeHandleInvalidation(
                listener = listener,
                content = listOf(
                    "invalidate",
                    ByteBuffer.wrap("test-cache:single-key".toByteArray())
                )
            )
        }
        connection.close()

        front.invalidatedKeys.shouldContainSame(listOf("single-key"))
    }

    private fun invokeHandleInvalidation(listener: TrackingInvalidationListener<String>, content: List<Any?>) {
        val method = TrackingInvalidationListener::class.java.getDeclaredMethod("handleInvalidation", List::class.java)
        method.isAccessible = true
        method.invoke(listener, content)
    }

    private class RecordingLocalCache : LocalCache<String, String> {
        val invalidatedKeys = mutableListOf<String>()
        var clearCount: Int = 0

        override fun get(key: String): String? = null

        override fun getAll(keys: Set<String>): Map<String, String> = emptyMap()

        override fun put(key: String, value: String) {}

        override fun putAll(map: Map<out String, String>) {}

        override fun remove(key: String) {}

        override fun removeAll(keys: Set<String>) {}

        override fun invalidate(key: String) {
            invalidatedKeys.add(key)
        }

        override fun invalidateAll(keys: Collection<String>) {
            invalidatedKeys.addAll(keys)
        }

        override fun containsKey(key: String): Boolean = false

        override fun clear() {
            clearCount += 1
        }

        override fun estimatedSize(): Long = 0L
    }
}
