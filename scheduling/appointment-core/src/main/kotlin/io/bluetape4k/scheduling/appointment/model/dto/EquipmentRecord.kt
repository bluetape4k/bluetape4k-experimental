package io.bluetape4k.scheduling.appointment.model.dto

import java.io.Serializable

data class EquipmentRecord(
    val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val usageDurationMinutes: Int,
    val quantity: Int = 1,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
