package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier

data class TreatmentTypeDto(
    override val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val category: String = "TREATMENT",
    val defaultDurationMinutes: Int,
    val requiredProviderType: String = "DOCTOR",
    val consultationMethod: String? = null,
    val requiresEquipment: Boolean = false,
    val maxConcurrentPatients: Int? = null,
) : HasIdentifier<Long>
