package io.bluetape4k.scheduling.appointment.model.dto

data class TreatmentEquipmentRecord(
    val id: Long? = null,
    val treatmentTypeId: Long,
    val equipmentId: Long,
)
