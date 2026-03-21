package io.bluetape4k.scheduling.appointment.solver.domain

/**
 * 의사 정보 Problem Fact.
 */
data class DoctorFact(
    val id: Long,
    val clinicId: Long,
    val providerType: String,
    val maxConcurrentPatients: Int?,
)
