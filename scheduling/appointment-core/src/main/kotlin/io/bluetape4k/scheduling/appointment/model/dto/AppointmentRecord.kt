package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class AppointmentRecord(
    val id: Long? = null,
    val clinicId: Long,
    val doctorId: Long,
    val treatmentTypeId: Long,
    val equipmentId: Long? = null,
    val consultationTopicId: Long? = null,
    val consultationMethod: String? = null,
    val rescheduleFromId: Long? = null,
    val patientName: String,
    val patientPhone: String? = null,
    val patientExternalId: String? = null,
    val appointmentDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val status: AppointmentState = AppointmentState.REQUESTED,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
