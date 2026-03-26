package io.bluetape4k.exposed.cockroachdb.domain

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable

object UserUUIDs : UUIDTable("user_uuids") {
    val username = varchar("username", 100).uniqueIndex()
}
