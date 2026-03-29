package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.exposed.jdbc.repository.LongJdbcRepository
import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import io.bluetape4k.support.requireNotNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.LocalTime

/**
 * 예약 정보 저장소.
 *
 * Exposed JDBC를 사용하여 예약 조회, 생성, 상태 업데이트를 처리합니다.
 * 동시 예약 수 및 장비 사용량 검증, 기간별/상태별 조회 등을 지원합니다.
 */
class AppointmentRepository : LongJdbcRepository<AppointmentRecord> {
    companion object : KLogging()

    override val table = Appointments
    override fun extractId(entity: AppointmentRecord): Long = entity.id.requireNotNull("id")
    override fun ResultRow.toEntity(): AppointmentRecord = toAppointmentRecord()

    /**
     * 의사와 시간대에 겹치는 예약 개수를 반환합니다.
     *
     * 취소되거나 미내원 상태는 제외합니다.
     *
     * @param doctorId 의사 ID
     * @param date 조회 날짜
     * @param slotStart 시간대 시작
     * @param slotEnd 시간대 종료
     * @return 겹치는 예약 개수
     */
    fun countOverlapping(
        doctorId: Long,
        date: LocalDate,
        slotStart: LocalTime,
        slotEnd: LocalTime,
    ): Int =
        Appointments
            .selectAll()
            .where { Appointments.doctorId eq doctorId }
            .andWhere { Appointments.appointmentDate eq date }
            .andWhere { Appointments.startTime less slotEnd }
            .andWhere { Appointments.endTime greater slotStart }
            .andWhere { Appointments.status neq AppointmentState.CANCELLED }
            .andWhere { Appointments.status neq AppointmentState.NO_SHOW }
            .count()
            .toInt()

    /**
     * 장비와 시간대에 사용 중인 예약 개수를 반환합니다.
     *
     * @param equipmentId 장비 ID
     * @param date 조회 날짜
     * @param slotStart 시간대 시작
     * @param slotEnd 시간대 종료
     * @return 장비 사용 중인 예약 개수
     */
    fun countEquipmentUsage(
        equipmentId: Long,
        date: LocalDate,
        slotStart: LocalTime,
        slotEnd: LocalTime,
    ): Int =
        Appointments
            .selectAll()
            .where { Appointments.equipmentId eq equipmentId }
            .andWhere { Appointments.appointmentDate eq date }
            .andWhere { Appointments.startTime less slotEnd }
            .andWhere { Appointments.endTime greater slotStart }
            .andWhere { Appointments.status neq AppointmentState.CANCELLED }
            .andWhere { Appointments.status neq AppointmentState.NO_SHOW }
            .count()
            .toInt()

    /**
     * 병원의 특정 날짜 활성 예약을 조회합니다.
     *
     * @param clinicId 병원 ID
     * @param date 조회 날짜
     * @param activeStatuses 필터링할 상태 목록 (기본값: REQUESTED, CONFIRMED)
     * @return 예약 목록
     */
    fun findActiveByClinicAndDate(
        clinicId: Long,
        date: LocalDate,
        activeStatuses: List<AppointmentState> = AppointmentState.ACTIVE_STATUSES,
    ): List<AppointmentRecord> =
        Appointments
            .selectAll()
            .where { Appointments.clinicId eq clinicId }
            .andWhere { Appointments.appointmentDate eq date }
            .andWhere { Appointments.status inList activeStatuses }
            .map { it.toAppointmentRecord() }

    /**
     * 병원의 특정 날짜 예약 상태를 일괄 변경합니다.
     *
     * @param clinicId 병원 ID
     * @param date 대상 날짜
     * @param fromStatuses 현재 상태 목록 (이 중 하나인 예약만 업데이트)
     * @param toStatus 변경할 새로운 상태
     * @return 업데이트된 예약 개수
     */
    fun updateStatusByClinicAndDate(
        clinicId: Long,
        date: LocalDate,
        fromStatuses: List<AppointmentState>,
        toStatus: AppointmentState,
    ): Int =
        Appointments.update(
            where = {
                (Appointments.clinicId eq clinicId) and
                    (Appointments.appointmentDate eq date) and
                    (Appointments.status inList fromStatuses)
            }
        ) {
            it[status] = toStatus
        }

    /**
     * 특정 날짜의 모든 활성 예약을 조회합니다.
     *
     * @param date 조회 날짜
     * @param activeStatuses 필터링할 상태 목록
     * @return 예약 목록
     */
    fun findActiveByDate(
        date: LocalDate,
        activeStatuses: List<AppointmentState> = AppointmentState.ACTIVE_STATUSES,
    ): List<AppointmentRecord> =
        Appointments
            .selectAll()
            .where { Appointments.appointmentDate eq date }
            .andWhere { Appointments.status inList activeStatuses }
            .map { it.toAppointmentRecord() }

    /**
     * 예약을 생성합니다.
     *
     * @param record 예약 레코드 (ID는 null)
     * @return 생성된 예약 (ID 포함)
     */
    fun save(record: AppointmentRecord): AppointmentRecord {
        val id = Appointments.insertAndGetId {
            it[clinicId] = record.clinicId
            it[doctorId] = record.doctorId
            it[treatmentTypeId] = record.treatmentTypeId
            it[equipmentId] = record.equipmentId
            it[consultationTopicId] = record.consultationTopicId
            it[consultationMethod] = record.consultationMethod
            it[rescheduleFromId] = record.rescheduleFromId
            it[patientName] = record.patientName
            it[patientPhone] = record.patientPhone
            it[patientExternalId] = record.patientExternalId
            it[appointmentDate] = record.appointmentDate
            it[startTime] = record.startTime
            it[endTime] = record.endTime
            it[status] = record.status
        }.value
        return record.copy(id = id)
    }

    /**
     * 예약의 상태를 변경합니다.
     *
     * @param appointmentId 예약 ID
     * @param newStatus 새로운 상태
     * @return 업데이트된 행 개수
     */
    fun updateStatus(appointmentId: Long, newStatus: AppointmentState): Int =
        Appointments.update(where = { Appointments.id eq appointmentId }) {
            it[status] = newStatus
        }

    /**
     * 병원의 기간별 예약을 조회합니다.
     *
     * 취소 및 미내원 상태는 제외합니다.
     *
     * @param clinicId 병원 ID
     * @param dateRange 조회 기간
     * @return 예약 목록
     */
    fun findByClinicAndDateRange(clinicId: Long, dateRange: ClosedRange<LocalDate>): List<AppointmentRecord> =
        Appointments
            .selectAll()
            .where { Appointments.clinicId eq clinicId }
            .andWhere { Appointments.appointmentDate greaterEq dateRange.start }
            .andWhere { Appointments.appointmentDate lessEq dateRange.endInclusive }
            .andWhere { Appointments.status neq AppointmentState.CANCELLED }
            .andWhere { Appointments.status neq AppointmentState.NO_SHOW }
            .map { it.toAppointmentRecord() }
}
