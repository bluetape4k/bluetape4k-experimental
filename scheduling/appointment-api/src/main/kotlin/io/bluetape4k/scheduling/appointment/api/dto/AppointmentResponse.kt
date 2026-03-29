package io.bluetape4k.scheduling.appointment.api.dto

import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * 예약 응답.
 *
 * 예약 조회, 생성, 상태 변경의 응답으로 사용됩니다.
 *
 * @property id 예약 ID
 * @property clinicId 병원 ID
 * @property doctorId 의사 ID
 * @property treatmentTypeId 진료 유형 ID
 * @property equipmentId 사용 장비 ID
 * @property patientName 환자명
 * @property patientPhone 환자 전화번호
 * @property appointmentDate 예약 날짜
 * @property startTime 예약 시작 시간
 * @property endTime 예약 종료 시간
 * @property status 예약 상태 (PENDING, REQUESTED, CONFIRMED 등)
 * @property timezone 타임존
 * @property locale 지역/언어 설정
 * @property createdAt 생성 시각
 * @property updatedAt 마지막 업데이트 시각
 */
data class AppointmentResponse(
    val id: Long,
    val clinicId: Long,
    val doctorId: Long,
    val treatmentTypeId: Long,
    val equipmentId: Long?,
    val patientName: String,
    val patientPhone: String?,
    val appointmentDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val status: String,
    val timezone: String? = null,
    val locale: String? = null,
    val createdAt: Instant?,
    val updatedAt: Instant?,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

fun AppointmentRecord.toResponse(
    timezone: String? = null,
    locale: String? = null,
): AppointmentResponse = AppointmentResponse(
    id = id!!,
    clinicId = clinicId,
    doctorId = doctorId,
    treatmentTypeId = treatmentTypeId,
    equipmentId = equipmentId,
    patientName = patientName,
    patientPhone = patientPhone,
    appointmentDate = appointmentDate,
    startTime = startTime,
    endTime = endTime,
    status = status.name,
    timezone = timezone,
    locale = locale,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
