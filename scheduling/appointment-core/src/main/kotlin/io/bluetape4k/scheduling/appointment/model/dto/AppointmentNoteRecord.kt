package io.bluetape4k.scheduling.appointment.model.dto

import java.io.Serializable
import java.time.Instant

data class AppointmentNoteRecord(
    val id: Long? = null,
    val appointmentId: Long,
    val noteType: String,
    val content: String,
    val createdBy: String? = null,
    val createdAt: Instant? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
