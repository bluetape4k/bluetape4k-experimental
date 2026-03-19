package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier

data class EquipmentDto(
    override val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val usageDurationMinutes: Int,
    val quantity: Int = 1,
) : HasIdentifier<Long>
