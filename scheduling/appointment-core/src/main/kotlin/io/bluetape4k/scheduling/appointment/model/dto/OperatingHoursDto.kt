package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier
import java.time.DayOfWeek
import java.time.LocalTime

data class OperatingHoursDto(
    override val id: Long? = null,
    val clinicId: Long,
    val dayOfWeek: DayOfWeek,
    val openTime: LocalTime,
    val closeTime: LocalTime,
    val isActive: Boolean = true,
) : HasIdentifier<Long>
