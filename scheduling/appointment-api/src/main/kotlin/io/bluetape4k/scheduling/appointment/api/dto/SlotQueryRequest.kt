package io.bluetape4k.scheduling.appointment.api.dto

import java.io.Serializable
import java.time.LocalDate

/**
 * 슬롯 조회 요청.
 *
 * @property doctorId 의사 ID
 * @property treatmentTypeId 진료 유형 ID
 * @property date 조회 날짜
 * @property requestedDurationMinutes 요청한 진료 시간 (분, optional - 진료 유형 기본값 사용)
 */
data class SlotQueryRequest(
    val doctorId: Long,
    val treatmentTypeId: Long,
    val date: LocalDate,
    val requestedDurationMinutes: Int? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
