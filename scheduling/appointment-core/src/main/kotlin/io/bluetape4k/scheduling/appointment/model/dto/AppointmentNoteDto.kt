package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier
import java.time.Instant

data class AppointmentNoteDto(
    override val id: Long? = null,
    val appointmentId: Long,
    val noteType: String,
    val content: String,
    val createdBy: String? = null,
    val createdAt: Instant? = null,
) : HasIdentifier<Long>
