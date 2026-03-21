package io.bluetape4k.scheduling.appointment.api.dto

import java.time.LocalDate
import java.time.LocalTime

data class CreateAppointmentRequest(
    val clinicId: Long,
    val doctorId: Long,
    val treatmentTypeId: Long,
    val equipmentId: Long? = null,
    val patientName: String,
    val patientPhone: String? = null,
    val appointmentDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
