package io.bluetape4k.scheduling.appointment.event

import io.bluetape4k.exposed.core.HasIdentifier
import java.time.Instant

data class AppointmentEventLogDto(
    override val id: Long? = null,
    val eventType: String,
    val entityType: String,
    val entityId: Long,
    val clinicId: Long,
    val payloadJson: String,
    val createdAt: Instant? = null,
) : HasIdentifier<Long>
