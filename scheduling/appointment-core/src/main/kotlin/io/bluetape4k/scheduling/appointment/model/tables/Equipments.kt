package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object Equipments : LongIdTable("scheduling_equipments") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val usageDurationMinutes = integer("usage_duration_minutes")
    val quantity = integer("quantity").default(1)

    init {
        // 병원별 장비 목록 조회
        index("idx_equipments_clinic_id", false, clinicId)
    }
}
