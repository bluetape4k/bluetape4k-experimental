package io.bluetape4k.scheduling.appointment.api.dto

import java.io.Serializable

/**
 * 예약 상태 변경 요청.
 *
 * @property status 변경할 상태 (REQUESTED, CONFIRMED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED 등)
 * @property reason 상태 변경 사유 (optional, 예: "임시휴진", "의사 확인 완료")
 */
data class UpdateStatusRequest(
    val status: String,
    val reason: String? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
