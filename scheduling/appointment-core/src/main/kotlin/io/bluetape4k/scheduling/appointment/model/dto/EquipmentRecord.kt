package io.bluetape4k.scheduling.appointment.model.dto

data class EquipmentRecord(
    val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val usageDurationMinutes: Int,
    val quantity: Int = 1,
)
