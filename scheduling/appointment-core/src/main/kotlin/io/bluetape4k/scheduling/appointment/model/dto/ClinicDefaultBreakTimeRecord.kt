package io.bluetape4k.scheduling.appointment.model.dto

import java.time.LocalTime

data class ClinicDefaultBreakTimeRecord(
    val id: Long? = null,
    val clinicId: Long,
    val name: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
)
