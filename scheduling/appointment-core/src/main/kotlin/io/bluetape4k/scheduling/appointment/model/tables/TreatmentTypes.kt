package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object TreatmentTypes : LongIdTable("scheduling_treatment_types") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val defaultDurationMinutes = integer("default_duration_minutes")
    val requiresEquipment = bool("requires_equipment").default(false)
    val maxConcurrentPatients = integer("max_concurrent_patients").nullable()
}
