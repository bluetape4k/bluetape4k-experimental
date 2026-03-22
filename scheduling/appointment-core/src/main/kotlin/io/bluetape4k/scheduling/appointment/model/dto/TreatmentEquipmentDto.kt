package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier

data class TreatmentEquipmentDto(
    override val id: Long? = null,
    val treatmentTypeId: Long,
    val equipmentId: Long,
) : HasIdentifier<Long>
