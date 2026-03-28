package io.bluetape4k.scheduling.appointment.event

import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class AppointmentEventLogger {
    @EventListener
    fun onCreated(event: AppointmentDomainEvent.Created) {
        saveEventLog(
            eventType = "Created",
            entityId = event.appointmentId,
            clinicId = event.clinicId,
            payloadJson = """{"appointmentId":${event.appointmentId},"clinicId":${event.clinicId}}"""
        )
    }

    @EventListener
    fun onStatusChanged(event: AppointmentDomainEvent.StatusChanged) {
        val reasonPart = event.reason?.let { ""","reason":${jsonString(it)}""" } ?: ""
        saveEventLog(
            eventType = "StatusChanged",
            entityId = event.appointmentId,
            clinicId = event.clinicId,
            payloadJson = """{"appointmentId":${event.appointmentId},"clinicId":${event.clinicId},"fromState":"${event.fromState}","toState":"${event.toState}"$reasonPart}"""
        )
    }

    @EventListener
    fun onCancelled(event: AppointmentDomainEvent.Cancelled) {
        saveEventLog(
            eventType = "Cancelled",
            entityId = event.appointmentId,
            clinicId = event.clinicId,
            payloadJson = """{"appointmentId":${event.appointmentId},"clinicId":${event.clinicId},"reason":${jsonString(event.reason)}}"""
        )
    }

    @EventListener
    fun onRescheduled(event: AppointmentDomainEvent.Rescheduled) {
        saveEventLog(
            eventType = "Rescheduled",
            entityId = event.originalId,
            clinicId = event.clinicId,
            payloadJson = """{"originalId":${event.originalId},"newId":${event.newId},"clinicId":${event.clinicId}}"""
        )
    }

    private fun saveEventLog(
        eventType: String,
        entityId: Long,
        clinicId: Long,
        payloadJson: String,
    ) {
        transaction {
            AppointmentEventLogs.insert {
                it[AppointmentEventLogs.eventType] = eventType
                it[AppointmentEventLogs.entityType] = "Appointment"
                it[AppointmentEventLogs.entityId] = entityId
                it[AppointmentEventLogs.clinicId] = clinicId
                it[AppointmentEventLogs.payloadJson] = payloadJson
            }
        }
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
