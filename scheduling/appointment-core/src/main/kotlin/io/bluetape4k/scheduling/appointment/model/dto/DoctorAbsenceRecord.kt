package io.bluetape4k.scheduling.appointment.model.dto

import java.time.LocalDate
import java.time.LocalTime

data class DoctorAbsenceRecord(
    val id: Long? = null,
    val doctorId: Long,
    val absenceDate: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val reason: String? = null,
)
