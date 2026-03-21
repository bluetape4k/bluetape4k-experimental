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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.DayOfWeek
import java.time.LocalDate

class ClinicRepository : LongJdbcRepository<ClinicRecord> {
    companion object : KLogging()

    override val table = Clinics
    override fun extractId(entity: ClinicRecord): Long = entity.id.requireNotNull("id")
    override fun ResultRow.toEntity(): ClinicRecord = toClinicRecord()

    fun findOperatingHours(clinicId: Long, dayOfWeek: DayOfWeek): OperatingHoursRecord? =
        OperatingHoursTable
            .selectAll()
            .where {
                (OperatingHoursTable.clinicId eq clinicId) and
                    (OperatingHoursTable.dayOfWeek eq dayOfWeek) and
                    (OperatingHoursTable.isActive eq true)
            }.firstOrNull()?.toOperatingHoursRecord()

    fun findDefaultBreakTimes(clinicId: Long): List<ClinicDefaultBreakTimeRecord> =
        ClinicDefaultBreakTimes
            .selectAll()
            .where { ClinicDefaultBreakTimes.clinicId eq clinicId }
            .map { it.toClinicDefaultBreakTimeRecord() }

    fun findBreakTimes(clinicId: Long, dayOfWeek: DayOfWeek): List<BreakTimeRecord> =
        BreakTimes
            .selectAll()
            .where {
                (BreakTimes.clinicId eq clinicId) and
                    (BreakTimes.dayOfWeek eq dayOfWeek)
            }.map { it.toBreakTimeRecord() }

    fun findClosures(clinicId: Long, date: LocalDate): List<ClinicClosureRecord> =
        ClinicClosures
            .selectAll()
            .where {
                (ClinicClosures.clinicId eq clinicId) and
                    (ClinicClosures.closureDate eq date)
            }.map { it.toClinicClosureRecord() }
}
