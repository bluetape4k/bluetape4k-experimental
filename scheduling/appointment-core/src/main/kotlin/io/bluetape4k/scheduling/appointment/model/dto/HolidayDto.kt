package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier
import java.time.LocalDate

data class HolidayDto(
    override val id: Long? = null,
    val holidayDate: LocalDate,
    val name: String,
    val recurring: Boolean = false,
) : HasIdentifier<Long>
