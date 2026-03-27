package io.bluetape4k.redis.lettuce.hll

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands

/**
 * Lettuce 기반 HyperLogLog (Sync).
 *
 * Redis의 PFADD/PFCOUNT/PFMERGE 명령을 래핑합니다.
 * 카디널리티 추정 오차율 ≈ 0.81%.
 */
class LettuceHyperLogLog<V : Any>(
    private val connection: StatefulRedisConnection<String, V>,
    val name: String,
) : AutoCloseable {

    companion object : KLogging()

    private val commands: RedisCommands<String, V> = connection.sync()

    fun add(vararg elements: V): Boolean {
        val changed = commands.pfadd(name, *elements) == 1L
        log.debug { "HyperLogLog add: name=$name, changed=$changed" }
        return changed
    }

    fun count(): Long = commands.pfcount(name)

    fun countWith(vararg others: LettuceHyperLogLog<V>): Long {
        val keys = arrayOf(name) + others.map { it.name }.toTypedArray()
        return commands.pfcount(*keys)
    }

    fun mergeWith(destName: String, vararg others: LettuceHyperLogLog<V>) {
        val sourceKeys = arrayOf(name) + others.map { it.name }.toTypedArray()
        commands.pfmerge(destName, *sourceKeys)
        log.debug { "HyperLogLog merge: sources=${sourceKeys.toList()} → dest=$destName" }
    }

    override fun close() = connection.close()
}
