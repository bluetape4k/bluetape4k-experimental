package io.bluetape4k.scheduling.appointment.api.dto

import io.bluetape4k.scheduling.appointment.service.model.AvailableSlot
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * 가용 슬롯 응답.
 *
 * @property date 슬롯 날짜
 * @property startTime 슬롯 시작 시간
 * @property endTime 슬롯 종료 시간
 * @property doctorId 의사 ID
 * @property equipmentIds 가용한 장비 ID 목록
 * @property remainingCapacity 남은 동시 환자 수
 */
data class SlotResponse(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val doctorId: Long,
    val equipmentIds: List<Long>,
    val remainingCapacity: Int,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

fun AvailableSlot.toResponse(): SlotResponse = SlotResponse(
    date = date,
    startTime = startTime,
    endTime = endTime,
    doctorId = doctorId,
    equipmentIds = equipmentIds,
    remainingCapacity = remainingCapacity,
)
