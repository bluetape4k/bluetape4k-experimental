package io.bluetape4k.scheduling.appointment.model.dto

import java.io.Serializable

data class TreatmentEquipmentRecord(
    val id: Long? = null,
    val treatmentTypeId: Long,
    val equipmentId: Long,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
