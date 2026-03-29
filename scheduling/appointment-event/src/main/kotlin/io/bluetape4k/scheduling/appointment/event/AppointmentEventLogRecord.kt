package io.bluetape4k.scheduling.appointment.event

import java.io.Serializable
import java.time.Instant

data class AppointmentEventLogRecord(
    val id: Long? = null,
    val eventType: String,
    val entityType: String,
    val entityId: Long,
    val clinicId: Long,
    val payloadJson: String,
    val createdAt: Instant? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
