package io.bluetape4k.exposed.cockroachdb.domain

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

object Users : LongIdTable("users") {
    val username = varchar("username", 100).uniqueIndex()
    val email = varchar("email", 255)
    val age = integer("age").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
