package io.bluetape4k.scheduling.appointment.model.dto

import java.time.DayOfWeek
import java.time.LocalTime

data class DoctorScheduleRecord(
    val id: Long? = null,
    val doctorId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
