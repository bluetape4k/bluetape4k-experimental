package io.bluetape4k.scheduling.appointment.solver.domain

/**
 * 진료 유형 Problem Fact.
 */
data class TreatmentFact(
    val id: Long,
    val defaultDurationMinutes: Int,
    val requiredProviderType: String,
    val requiresEquipment: Boolean,
    val maxConcurrentPatients: Int?,
)
