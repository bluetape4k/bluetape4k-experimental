package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

object AppointmentNotes : LongIdTable("scheduling_appointment_notes") {
    val appointmentId = reference("appointment_id", Appointments, onDelete = ReferenceOption.CASCADE)
    val noteType = varchar("note_type", 50)
    val content = text("content")
    val createdBy = varchar("created_by", 255).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
