package io.bluetape4k.scheduling.appointment.api.dto

import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class AppointmentResponse(
    val id: Long,
    val clinicId: Long,
    val doctorId: Long,
    val treatmentTypeId: Long,
    val equipmentId: Long?,
    val patientName: String,
    val patientPhone: String?,
    val appointmentDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val status: String,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

fun AppointmentRecord.toResponse(): AppointmentResponse = AppointmentResponse(
    id = id!!,
    clinicId = clinicId,
    doctorId = doctorId,
    treatmentTypeId = treatmentTypeId,
    equipmentId = equipmentId,
    patientName = patientName,
    patientPhone = patientPhone,
    appointmentDate = appointmentDate,
    startTime = startTime,
    endTime = endTime,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
