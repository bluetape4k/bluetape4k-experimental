package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier
import java.time.LocalTime

data class ClinicDto(
    override val id: Long? = null,
    val name: String,
    val slotDurationMinutes: Int = 30,
    val timezone: String = "Asia/Seoul",
    val maxConcurrentPatients: Int = 1,
    val openOnHolidays: Boolean = false,
    val lunchStartTime: LocalTime? = null,
    val lunchEndTime: LocalTime? = null,
) : HasIdentifier<Long>
