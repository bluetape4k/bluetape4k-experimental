package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.exposed.jdbc.repository.LongJdbcRepository
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotNull
import io.bluetape4k.scheduling.appointment.model.dto.HolidayRecord
import io.bluetape4k.scheduling.appointment.model.tables.Holidays
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.LocalDate

class HolidayRepository : LongJdbcRepository<HolidayRecord> {
    companion object : KLogging()

    override val table = Holidays
    override fun extractId(entity: HolidayRecord): Long = entity.id.requireNotNull("id")
    override fun ResultRow.toEntity(): HolidayRecord = toHolidayRecord()

    fun existsByDate(date: LocalDate): Boolean =
        Holidays.selectAll().where { Holidays.holidayDate eq date }.count() > 0

    fun findByDateRange(dateRange: ClosedRange<LocalDate>): List<HolidayRecord> =
        Holidays
            .selectAll()
            .where {
                (Holidays.holidayDate greaterEq dateRange.start) and
                    (Holidays.holidayDate lessEq dateRange.endInclusive)
            }.map { it.toHolidayRecord() }
}
