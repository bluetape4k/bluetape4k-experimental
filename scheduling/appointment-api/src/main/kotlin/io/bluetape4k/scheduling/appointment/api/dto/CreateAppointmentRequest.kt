package io.bluetape4k.scheduling.appointment.api.dto

import java.io.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * 예약 생성 요청.
 *
 * @property clinicId 병원 ID
 * @property doctorId 의사 ID
 * @property treatmentTypeId 진료 유형 ID
 * @property equipmentId 사용할 장비 ID (optional)
 * @property patientName 환자명
 * @property patientPhone 환자 전화번호 (optional)
 * @property appointmentDate 예약 날짜
 * @property startTime 예약 시작 시간
 * @property endTime 예약 종료 시간
 */
data class CreateAppointmentRequest(
    val clinicId: Long,
    val doctorId: Long,
    val treatmentTypeId: Long,
    val equipmentId: Long? = null,
    val patientName: String,
    val patientPhone: String? = null,
    val appointmentDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
