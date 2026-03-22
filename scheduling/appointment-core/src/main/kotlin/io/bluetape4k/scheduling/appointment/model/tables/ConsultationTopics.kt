package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * 상담 주제/종류. CONSULTATION 카테고리의 TreatmentType에 연결.
 * 예: 시술안내, 진료비 안내, 치료 계획 상담 등
 */
object ConsultationTopics : LongIdTable("scheduling_consultation_topics") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val description = varchar("description", 500).nullable()
    val defaultDurationMinutes = integer("default_duration_minutes").default(30)
}
