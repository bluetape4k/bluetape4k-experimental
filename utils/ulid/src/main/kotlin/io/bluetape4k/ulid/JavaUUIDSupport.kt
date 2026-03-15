package io.bluetape4k.ulid

import java.util.*

fun ULID.toUUID(): UUID = UUID(this.mostSignificantBits, this.leastSignificantBits)

fun ULID.Companion.fromUUID(uuid: UUID): ULID =
    ULID.fromByteArray(
        ByteArray(16).also { bytes ->
            val msb = uuid.mostSignificantBits
            val lsb = uuid.leastSignificantBits

            (0..7).forEach { bytes[it] = (msb shr ((7 - it) * 8) and 0xFF).toByte() }
            (8..15).forEach { bytes[it] = (lsb shr ((15 - it) * 8) and 0xFF).toByte() }
        }
    )
