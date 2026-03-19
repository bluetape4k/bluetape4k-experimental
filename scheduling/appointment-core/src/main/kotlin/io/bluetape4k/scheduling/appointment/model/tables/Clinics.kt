package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object Clinics : LongIdTable("scheduling_clinics") {
    val name = varchar("name", 255)
    val slotDurationMinutes = integer("slot_duration_minutes").default(30)
    val timezone = varchar("timezone", 50).default("Asia/Seoul")
    val maxConcurrentPatients = integer("max_concurrent_patients").default(1)
}
