package io.bluetape4k.scheduling.appointment.model.tables

import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * 예약 정보 테이블.
 *
 * 환자의 예약 정보(예약 날짜, 시간, 진료 유형, 할당된 의사/장비)를 저장합니다.
 * 상태는 [AppointmentState]로 추적되며, 동시 예약 수와 장비 사용량을 인덱싱해 슬롯 계산을 가속화합니다.
 */
object Appointments : LongIdTable("scheduling_appointments") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val doctorId = reference("doctor_id", Doctors, onDelete = ReferenceOption.CASCADE)
    val treatmentTypeId = reference("treatment_type_id", TreatmentTypes, onDelete = ReferenceOption.CASCADE)
    val equipmentId = optReference("equipment_id", Equipments, onDelete = ReferenceOption.SET_NULL)
    val patientName = varchar("patient_name", 255)
    val patientPhone = varchar("patient_phone", 50).nullable()
    val patientExternalId = varchar("patient_external_id", 255).nullable()
    val appointmentDate = date("appointment_date")
    val startTime = time("start_time")
    val endTime = time("end_time")
    val consultationTopicId = optReference("consultation_topic_id", ConsultationTopics, onDelete = ReferenceOption.SET_NULL)
    val consultationMethod = varchar("consultation_method", 30).nullable()
    val rescheduleFromId = long("reschedule_from_id").nullable()
    val status = appointmentState("status").clientDefault { AppointmentState.REQUESTED }
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    init {
        // 의사별 날짜 조회 (중복 체크, 슬롯 조회)
        index("idx_appointments_doctor_date", false, doctorId, appointmentDate)
        // 병원별 날짜+상태 조회 (활성 예약 목록, 상태 일괄 업데이트)
        index("idx_appointments_clinic_date_status", false, clinicId, appointmentDate, status)
        // 장비별 날짜 조회 (장비 사용량 체크)
        index("idx_appointments_equipment_date", false, equipmentId, appointmentDate)
        // 날짜+상태 조회 (전체 활성 예약 조회, 리마인더 스케줄러)
        index("idx_appointments_date_status", false, appointmentDate, status)
    }
}
