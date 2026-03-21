package io.bluetape4k.scheduling.appointment.model.dto

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class RescheduleCandidateRecord(
    val id: Long? = null,
    val originalAppointmentId: Long,
    val candidateDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val doctorId: Long,
    val priority: Int = 0,
    val selected: Boolean = false,
    val createdAt: Instant? = null,
)
