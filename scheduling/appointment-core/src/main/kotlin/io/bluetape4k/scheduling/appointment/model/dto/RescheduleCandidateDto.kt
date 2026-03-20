package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class RescheduleCandidateDto(
    override val id: Long? = null,
    val originalAppointmentId: Long,
    val candidateDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val doctorId: Long,
    val priority: Int = 0,
    val selected: Boolean = false,
    val createdAt: Instant? = null,
) : HasIdentifier<Long>
