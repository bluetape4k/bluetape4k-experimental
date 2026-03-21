package io.bluetape4k.scheduling.appointment.model.dto

data class ConsultationTopicRecord(
    val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val description: String? = null,
    val defaultDurationMinutes: Int = 30,
)
