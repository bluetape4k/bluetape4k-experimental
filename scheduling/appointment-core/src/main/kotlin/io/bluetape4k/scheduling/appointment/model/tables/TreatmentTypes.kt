package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * 진료 카테고리.
 */
object TreatmentCategory {
    const val TREATMENT = "TREATMENT"
    const val PROCEDURE = "PROCEDURE"
    const val CONSULTATION = "CONSULTATION"
}

/**
 * 상담 방식. CONSULTATION 카테고리에서만 사용.
 */
object ConsultationMethod {
    const val IN_PERSON = "IN_PERSON"
    const val PHONE = "PHONE"
    const val VIDEO = "VIDEO"
}

object TreatmentTypes : LongIdTable("scheduling_treatment_types") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val category = varchar("category", 30).default(TreatmentCategory.TREATMENT)
    val defaultDurationMinutes = integer("default_duration_minutes")
    val requiredProviderType = varchar("required_provider_type", 30).default(ProviderType.DOCTOR)
    val consultationMethod = varchar("consultation_method", 30).nullable()
    val requiresEquipment = bool("requires_equipment").default(false)
    val maxConcurrentPatients = integer("max_concurrent_patients").nullable()
}
