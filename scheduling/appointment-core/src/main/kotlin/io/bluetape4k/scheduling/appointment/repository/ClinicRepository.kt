package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.exposed.jdbc.repository.LongJdbcRepository
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotNull
import io.bluetape4k.scheduling.appointment.model.dto.BreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicClosureRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicDefaultBreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicRecord
import io.bluetape4k.scheduling.appointment.model.dto.OperatingHoursRecord
import io.bluetape4k.scheduling.appointment.model.tables.BreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.ClinicClosures
import io.bluetape4k.scheduling.appointment.model.tables.ClinicDefaultBreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.model.tables.OperatingHoursTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 병원 정보 저장소.
 *
 * 병원의 기본 정보, 운영 시간, 휴시간, 휴진 정보를 조회합니다.
 */
class ClinicRepository : LongJdbcRepository<ClinicRecord> {
    companion object : KLogging()

    override val table = Clinics
    override fun extractId(entity: ClinicRecord): Long = entity.id.requireNotNull("id")
    override fun ResultRow.toEntity(): ClinicRecord = toClinicRecord()

    /**
     * 병원의 특정 요일 운영 시간을 조회합니다.
     *
     * @param clinicId 병원 ID
     * @param dayOfWeek 요일
     * @return 운영 시간 정보 (없으면 null)
     */
    fun findOperatingHours(clinicId: Long, dayOfWeek: DayOfWeek): OperatingHoursRecord? =
        OperatingHoursTable
            .selectAll()
            .where { OperatingHoursTable.clinicId eq clinicId }
            .andWhere { OperatingHoursTable.dayOfWeek eq dayOfWeek }
            .andWhere { OperatingHoursTable.isActive eq true }
            .firstOrNull()?.toOperatingHoursRecord()

    /**
     * 병원의 기본 휴시간을 조회합니다.
     *
     * 기본 휴시간은 모든 영업일에 동일하게 적용됩니다 (점심시간 등).
     *
     * @param clinicId 병원 ID
     * @return 휴시간 목록
     */
    fun findDefaultBreakTimes(clinicId: Long): List<ClinicDefaultBreakTimeRecord> =
        ClinicDefaultBreakTimes
            .selectAll()
            .where { ClinicDefaultBreakTimes.clinicId eq clinicId }
            .map { it.toClinicDefaultBreakTimeRecord() }

    /**
     * 병원의 특정 요일 휴시간을 조회합니다.
     *
     * @param clinicId 병원 ID
     * @param dayOfWeek 요일
     * @return 휴시간 목록
     */
    fun findBreakTimes(clinicId: Long, dayOfWeek: DayOfWeek): List<BreakTimeRecord> =
        BreakTimes
            .selectAll()
            .where { BreakTimes.clinicId eq clinicId }
            .andWhere { BreakTimes.dayOfWeek eq dayOfWeek }
            .map { it.toBreakTimeRecord() }

    /**
     * 특정 날짜의 병원 휴진 정보를 조회합니다.
     *
     * @param clinicId 병원 ID
     * @param date 조회 날짜
     * @return 휴진 정보 목록 (전일 휴진, 부분 휴진 포함)
     */
    fun findClosures(clinicId: Long, date: LocalDate): List<ClinicClosureRecord> =
        ClinicClosures
            .selectAll()
            .where { ClinicClosures.clinicId eq clinicId }
            .andWhere { ClinicClosures.closureDate eq date }
            .map { it.toClinicClosureRecord() }

    fun findAllOperatingHours(clinicId: Long): List<OperatingHoursRecord> =
        OperatingHoursTable
            .selectAll()
            .where { OperatingHoursTable.clinicId eq clinicId }
            .map { it.toOperatingHoursRecord() }

    fun findAllBreakTimes(clinicId: Long): List<BreakTimeRecord> =
        BreakTimes
            .selectAll()
            .where { BreakTimes.clinicId eq clinicId }
            .map { it.toBreakTimeRecord() }

    /**
     * 병원의 기간별 휴진 정보를 조회합니다.
     *
     * @param clinicId 병원 ID
     * @param dateRange 조회 기간
     * @return 휴진 정보 목록
     */
    fun findClosuresByDateRange(clinicId: Long, dateRange: ClosedRange<LocalDate>): List<ClinicClosureRecord> =
        ClinicClosures
            .selectAll()
            .where { ClinicClosures.clinicId eq clinicId }
            .andWhere { ClinicClosures.closureDate greaterEq dateRange.start }
            .andWhere { ClinicClosures.closureDate lessEq dateRange.endInclusive }
            .map { it.toClinicClosureRecord() }
}
