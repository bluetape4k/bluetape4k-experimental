package io.bluetape4k.redis.lettuce.hll

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await

/**
 * Lettuce 기반 HyperLogLog (Coroutine/Suspend).
 */
class LettuceSuspendHyperLogLog<V : Any>(
    private val connection: StatefulRedisConnection<String, V>,
    val name: String,
) : AutoCloseable {

    companion object : KLogging()

    private val asyncCommands: RedisAsyncCommands<String, V> get() = connection.async()

    suspend fun add(vararg elements: V): Boolean {
        val changed = asyncCommands.pfadd(name, *elements).await() == 1L
        log.debug { "SuspendHyperLogLog add: name=$name, changed=$changed" }
        return changed
    }

    suspend fun count(): Long = asyncCommands.pfcount(name).await()

    suspend fun countWith(vararg others: LettuceSuspendHyperLogLog<V>): Long {
        val keys = arrayOf(name) + others.map { it.name }.toTypedArray()
        return asyncCommands.pfcount(*keys).await()
    }

    suspend fun mergeWith(destName: String, vararg others: LettuceSuspendHyperLogLog<V>) {
        val sourceKeys = arrayOf(name) + others.map { it.name }.toTypedArray()
        asyncCommands.pfmerge(destName, *sourceKeys).await()
        log.debug { "SuspendHyperLogLog merge: sources=${sourceKeys.toList()} → dest=$destName" }
    }

    override fun close() = connection.close()
}
