package io.bluetape4k.scheduling.appointment.model.dto

import io.bluetape4k.exposed.core.HasIdentifier
import java.time.LocalDate
import java.time.LocalTime

data class ClinicClosureDto(
    override val id: Long? = null,
    val clinicId: Long,
    val closureDate: LocalDate,
    val reason: String? = null,
    val isFullDay: Boolean = true,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
) : HasIdentifier<Long>
