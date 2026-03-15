package io.bluetape4k.ulid

import io.bluetape4k.ulid.internal.ULIDFactory
import io.bluetape4k.ulid.internal.ULIDMonotonic
import io.bluetape4k.ulid.internal.ULIDMonotonic.Companion.DefaultMonotonic
import io.bluetape4k.ulid.internal.ULIDStatefulMonotonic
import io.bluetape4k.ulid.internal.currentTimeMillis
import kotlin.random.Random

interface ULID: Comparable<ULID> {

    val mostSignificantBits: Long
    val leastSignificantBits: Long

    val timestamp: Long

    fun toByteArray(): ByteArray

    fun increment(): ULID


    interface Factory {

        fun randomULID(timestamp: Long = currentTimeMillis()): String

        fun nextULID(timestamp: Long = currentTimeMillis()): ULID

        fun fromByteArray(data: ByteArray): ULID

        fun parseULID(ulidString: String): ULID

    }

    interface Monotonic {

        fun nextULID(previous: ULID, timestamp: Long = currentTimeMillis()): ULID

        fun nextULIDStrict(previous: ULID, timestamp: Long = currentTimeMillis()): ULID?

        companion object: Monotonic by DefaultMonotonic

    }

    interface StatefulMonotonic: Factory {

        fun nextULIDStrict(timestamp: Long = currentTimeMillis()): ULID?

    }

    companion object: Factory by ULIDFactory.Default {

        fun factory(random: Random = Random): Factory = ULIDFactory(random)

        fun monotonic(factory: Factory = ULID): Monotonic = ULIDMonotonic(factory)

        fun statefulMonotonic(factory: Factory = ULID): StatefulMonotonic =
            ULIDStatefulMonotonic(factory = factory, monotonic = monotonic(factory))
    }
}
