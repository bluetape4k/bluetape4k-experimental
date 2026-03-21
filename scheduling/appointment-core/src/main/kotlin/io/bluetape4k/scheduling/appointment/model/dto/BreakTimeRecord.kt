package io.bluetape4k.scheduling.appointment.model.dto

import java.time.DayOfWeek
import java.time.LocalTime

data class BreakTimeRecord(
    val id: Long? = null,
    val clinicId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
