package io.bluetape4k.exposed.bigquery.domain

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp

object Events : Table("events") {
    val eventId = long("event_id")
    val userId = long("user_id")
    val eventType = varchar("event_type", 50)
    val region = varchar("region", 20)
    val amount = decimal("amount", 15, 2).nullable()
    val occurredAt = timestamp("occurred_at")

    override val primaryKey = PrimaryKey(eventId)
}
