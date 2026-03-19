package io.bluetape4k.scheduling.appointment.event

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

object AppointmentEventLogs : LongIdTable("scheduling_appointment_event_logs") {
    val eventType = varchar("event_type", 50)
    val entityType = varchar("entity_type", 100)
    val entityId = long("entity_id")
    val clinicId = long("clinic_id")
    val payloadJson = text("payload_json")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
