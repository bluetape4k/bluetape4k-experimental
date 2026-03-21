package io.bluetape4k.scheduling.appointment.api.dto

import io.bluetape4k.scheduling.appointment.model.dto.RescheduleCandidateRecord
import java.time.LocalDate
import java.time.LocalTime

data class RescheduleCandidateResponse(
    val id: Long,
    val originalAppointmentId: Long,
    val candidateDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val doctorId: Long,
    val priority: Int,
    val selected: Boolean,
)

fun RescheduleCandidateRecord.toResponse(): RescheduleCandidateResponse = RescheduleCandidateResponse(
    id = id!!,
    originalAppointmentId = originalAppointmentId,
    candidateDate = candidateDate,
    startTime = startTime,
    endTime = endTime,
    doctorId = doctorId,
    priority = priority,
    selected = selected,
)
