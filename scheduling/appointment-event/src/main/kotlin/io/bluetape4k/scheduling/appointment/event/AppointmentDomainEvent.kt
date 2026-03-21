package io.bluetape4k.scheduling.appointment.event

import org.springframework.context.ApplicationEvent
import java.time.Instant

sealed class AppointmentDomainEvent(
    source: Any,
    val occurredAt: Instant = Instant.now(),
) : ApplicationEvent(source) {
    data class Created(
        val appointmentId: Long,
        val clinicId: Long,
    ) : AppointmentDomainEvent(appointmentId)

    data class StatusChanged(
        val appointmentId: Long,
        val clinicId: Long,
        val fromState: String,
        val toState: String,
        val reason: String? = null,
    ) : AppointmentDomainEvent(appointmentId)

    data class Cancelled(
        val appointmentId: Long,
        val clinicId: Long,
        val reason: String,
    ) : AppointmentDomainEvent(appointmentId)

    data class Rescheduled(
        val originalId: Long,
        val newId: Long,
        val clinicId: Long,
    ) : AppointmentDomainEvent(originalId)
}
