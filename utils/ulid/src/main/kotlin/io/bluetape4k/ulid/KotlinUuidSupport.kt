@file:OptIn(ExperimentalUuidApi::class)

package io.bluetape4k.ulid

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun ULID.toUuid(): Uuid = Uuid.fromLongs(this.mostSignificantBits, this.leastSignificantBits)

fun ULID.Companion.fromUuid(uuid: Uuid): ULID =
    ULID.fromByteArray(uuid.toByteArray())
