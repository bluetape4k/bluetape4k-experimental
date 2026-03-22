package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date

/**
 * 국가 공휴일. 기본적으로 모든 병원이 휴진이며,
 * [Clinics.openOnHolidays]가 true인 병원만 예외적으로 영업.
 */
object Holidays : LongIdTable("scheduling_holidays") {
    val holidayDate = date("holiday_date").uniqueIndex()
    val name = varchar("name", 255)
    val recurring = bool("recurring").default(false)
}
