package io.bluetape4k.scheduling.appointment.service.model

import java.time.LocalDate

/**
 * 예약 가능 슬롯 조회 요청 데이터.
 *
 * @property clinicId 병원 ID
 * @property doctorId 의사 ID
 * @property treatmentTypeId 진료 유형 ID
 * @property date 조회 날짜
 * @property requestedDurationMinutes 요청 진료 시간(분). null이면 진료 유형의 기본값 사용.
 */
data class SlotQuery(
    val clinicId: Long,
    val doctorId: Long,
    val treatmentTypeId: Long,
    val date: LocalDate,
    val requestedDurationMinutes: Int? = null,
)
