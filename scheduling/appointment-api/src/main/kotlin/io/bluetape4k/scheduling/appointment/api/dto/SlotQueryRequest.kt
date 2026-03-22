package io.bluetape4k.scheduling.appointment.api.dto

import java.time.LocalDate

data class SlotQueryRequest(
    val doctorId: Long,
    val treatmentTypeId: Long,
    val date: LocalDate,
    val requestedDurationMinutes: Int? = null,
)
