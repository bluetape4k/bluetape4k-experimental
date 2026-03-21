package io.bluetape4k.scheduling.appointment.service.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * 예약 가능한 슬롯 정보.
 *
 * @property date 날짜
 * @property startTime 슬롯 시작 시간
 * @property endTime 슬롯 종료 시간
 * @property doctorId 의사 ID
 * @property equipmentIds 사용 가능한 장비 ID 목록
 * @property remainingCapacity 남은 동시 수용 인원
 */
data class AvailableSlot(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val doctorId: Long,
    val equipmentIds: List<Long> = emptyList(),
    val remainingCapacity: Int,
)
