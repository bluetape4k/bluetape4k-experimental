package io.bluetape4k.scheduling.appointment.api.dto

import io.bluetape4k.scheduling.appointment.service.model.AvailableSlot
import java.time.LocalDate
import java.time.LocalTime

data class SlotResponse(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val doctorId: Long,
    val equipmentIds: List<Long>,
    val remainingCapacity: Int,
)

fun AvailableSlot.toResponse(): SlotResponse = SlotResponse(
    date = date,
    startTime = startTime,
    endTime = endTime,
    doctorId = doctorId,
    equipmentIds = equipmentIds,
    remainingCapacity = remainingCapacity,
)
