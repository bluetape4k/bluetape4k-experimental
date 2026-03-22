package io.bluetape4k.scheduling.appointment.model.dto

data class DoctorRecord(
    val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val specialty: String? = null,
    val providerType: String = "DOCTOR",
    val maxConcurrentPatients: Int? = null,
)
