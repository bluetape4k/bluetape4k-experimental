package io.bluetape4k.scheduling.appointment.model.dto

import java.io.Serializable

data class ConsultationTopicRecord(
    val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val description: String? = null,
    val defaultDurationMinutes: Int = 30,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
