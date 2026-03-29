package io.bluetape4k.scheduling.appointment.model.dto

import java.io.Serializable

data class DoctorRecord(
    val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val specialty: String? = null,
    val providerType: String = "DOCTOR",
    val maxConcurrentPatients: Int? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
