package io.bluetape4k.exposed.cockroachdb.domain

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

object Orders : LongIdTable("orders") {
    val userId = long("user_id")
    val amount = decimal("amount", 10, 2)
    val status = varchar("status", 20)
    val orderedAt = timestamp("ordered_at").defaultExpression(CurrentTimestamp)
}
