package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.exposed.jdbc.repository.LongJdbcRepository
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotNull
import io.bluetape4k.scheduling.appointment.model.dto.DoctorAbsenceRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorScheduleRecord
import io.bluetape4k.scheduling.appointment.model.tables.DoctorAbsences
import io.bluetape4k.scheduling.appointment.model.tables.DoctorSchedules
import io.bluetape4k.scheduling.appointment.model.tables.Doctors
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 의사/전문상담사 정보 저장소.
 *
 * 의사의 기본 정보, 운영 스케줄, 휴무 정보를 조회합니다.
 */
class DoctorRepository : LongJdbcRepository<DoctorRecord> {
    companion object : KLogging()

    override val table = Doctors
    override fun extractId(entity: DoctorRecord): Long = entity.id.requireNotNull("id")
    override fun ResultRow.toEntity(): DoctorRecord = toDoctorRecord()

    /**
     * 특정 요일의 의사 운영 스케줄을 조회합니다.
     *
     * @param doctorId 의사 ID
     * @param dayOfWeek 요일
     * @return 운영 스케줄 (없으면 null)
     */
    fun findSchedule(doctorId: Long, dayOfWeek: DayOfWeek): DoctorScheduleRecord? =
        DoctorSchedules
            .selectAll()
            .where { DoctorSchedules.doctorId eq doctorId }
            .andWhere { DoctorSchedules.dayOfWeek eq dayOfWeek }
            .firstOrNull()?.toDoctorScheduleRecord()

    /**
     * 특정 날짜의 의사 휴무 시간대를 조회합니다.
     *
     * @param doctorId 의사 ID
     * @param date 조회 날짜
     * @return 휴무 기간 목록
     */
    fun findAbsences(doctorId: Long, date: LocalDate): List<DoctorAbsenceRecord> =
        DoctorAbsences
            .selectAll()
            .where { DoctorAbsences.doctorId eq doctorId }
            .andWhere { DoctorAbsences.absenceDate eq date }
            .map { it.toDoctorAbsenceRecord() }

    /**
     * 병원의 모든 의사를 조회합니다.
     *
     * @param clinicId 병원 ID
     * @return 의사 목록
     */
    fun findByClinicId(clinicId: Long): List<DoctorRecord> =
        Doctors
            .selectAll()
            .where { Doctors.clinicId eq clinicId }
            .map { it.toDoctorRecord() }

    fun findAllSchedules(doctorId: Long): List<DoctorScheduleRecord> =
        DoctorSchedules
            .selectAll()
            .where { DoctorSchedules.doctorId eq doctorId }
            .map { it.toDoctorScheduleRecord() }

    /**
     * 의사의 기간별 휴무 정보를 조회합니다.
     *
     * @param doctorId 의사 ID
     * @param dateRange 조회 기간
     * @return 휴무 기간 목록
     */
    fun findAbsencesByDateRange(doctorId: Long, dateRange: ClosedRange<LocalDate>): List<DoctorAbsenceRecord> =
        DoctorAbsences
            .selectAll()
            .where { DoctorAbsences.doctorId eq doctorId }
            .andWhere { DoctorAbsences.absenceDate greaterEq dateRange.start }
            .andWhere { DoctorAbsences.absenceDate lessEq dateRange.endInclusive }
            .map { it.toDoctorAbsenceRecord() }
}
