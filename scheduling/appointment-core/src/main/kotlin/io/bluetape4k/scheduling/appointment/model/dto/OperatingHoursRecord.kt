package io.bluetape4k.scheduling.appointment.model.dto

import java.time.DayOfWeek
import java.time.LocalTime

data class OperatingHoursRecord(
    val id: Long? = null,
    val clinicId: Long,
    val dayOfWeek: DayOfWeek,
    val openTime: LocalTime,
    val closeTime: LocalTime,
    val isActive: Boolean = true,
)
