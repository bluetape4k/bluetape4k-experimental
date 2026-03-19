package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier
import java.time.DayOfWeek
import java.time.LocalTime

data class BreakTimeDto(
    override val id: Long? = null,
    val clinicId: Long,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
) : HasIdentifier<Long>
