package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object Doctors : LongIdTable("scheduling_doctors") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val specialty = varchar("specialty", 255).nullable()
    val maxConcurrentPatients = integer("max_concurrent_patients").nullable()
}
