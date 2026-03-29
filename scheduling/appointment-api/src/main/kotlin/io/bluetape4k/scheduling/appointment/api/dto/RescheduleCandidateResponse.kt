package io.bluetape4k.scheduling.appointment.api.dto

import io.bluetape4k.scheduling.appointment.model.dto.RescheduleCandidateRecord
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * 재배정 후보 응답.
 *
 * @property id 재배정 후보 ID
 * @property originalAppointmentId 원본 예약 ID
 * @property candidateDate 재배정 후보 날짜
 * @property startTime 시작 시간
 * @property endTime 종료 시간
 * @property doctorId 재배정 예정 의사 ID
 * @property priority 우선순위 (낮을수록 우수)
 * @property selected 선택 여부
 */
data class RescheduleCandidateResponse(
    val id: Long,
    val originalAppointmentId: Long,
    val candidateDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val doctorId: Long,
    val priority: Int,
    val selected: Boolean,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

fun RescheduleCandidateRecord.toResponse(): RescheduleCandidateResponse = RescheduleCandidateResponse(
    id = id!!,
    originalAppointmentId = originalAppointmentId,
    candidateDate = candidateDate,
    startTime = startTime,
    endTime = endTime,
    doctorId = doctorId,
    priority = priority,
    selected = selected,
)
