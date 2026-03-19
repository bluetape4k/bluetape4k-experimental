package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier

data class DoctorDto(
    override val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val specialty: String? = null,
    val maxConcurrentPatients: Int? = null,
) : HasIdentifier<Long>
