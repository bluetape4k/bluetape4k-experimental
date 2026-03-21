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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.DayOfWeek
import java.time.LocalDate

class DoctorRepository : LongJdbcRepository<DoctorRecord> {
    companion object : KLogging()

    override val table = Doctors
    override fun extractId(entity: DoctorRecord): Long = entity.id.requireNotNull("id")
    override fun ResultRow.toEntity(): DoctorRecord = toDoctorRecord()

    fun findSchedule(doctorId: Long, dayOfWeek: DayOfWeek): DoctorScheduleRecord? =
        DoctorSchedules
            .selectAll()
            .where {
                (DoctorSchedules.doctorId eq doctorId) and
                    (DoctorSchedules.dayOfWeek eq dayOfWeek)
            }.firstOrNull()?.toDoctorScheduleRecord()

    fun findAbsences(doctorId: Long, date: LocalDate): List<DoctorAbsenceRecord> =
        DoctorAbsences
            .selectAll()
            .where {
                (DoctorAbsences.doctorId eq doctorId) and
                    (DoctorAbsences.absenceDate eq date)
            }.map { it.toDoctorAbsenceRecord() }

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

    fun findAbsencesByDateRange(doctorId: Long, dateRange: ClosedRange<LocalDate>): List<DoctorAbsenceRecord> =
        DoctorAbsences
            .selectAll()
            .where {
                (DoctorAbsences.doctorId eq doctorId) and
                    (DoctorAbsences.absenceDate greaterEq dateRange.start) and
                    (DoctorAbsences.absenceDate lessEq dateRange.endInclusive)
            }.map { it.toDoctorAbsenceRecord() }
}
