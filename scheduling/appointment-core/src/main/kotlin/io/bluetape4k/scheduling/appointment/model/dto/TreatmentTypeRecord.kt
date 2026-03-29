package io.bluetape4k.scheduling.appointment.model.dto

import java.io.Serializable

data class TreatmentTypeRecord(
    val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val category: String = "TREATMENT",
    val defaultDurationMinutes: Int,
    val requiredProviderType: String = "DOCTOR",
    val consultationMethod: String? = null,
    val requiresEquipment: Boolean = false,
    val maxConcurrentPatients: Int? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
