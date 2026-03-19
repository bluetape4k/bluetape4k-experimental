package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.time
import java.time.DayOfWeek

object OperatingHoursTable : LongIdTable("scheduling_operating_hours") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val dayOfWeek = enumerationByName("day_of_week", 10, DayOfWeek::class)
    val openTime = time("open_time")
    val closeTime = time("close_time")
    val isActive = bool("is_active").default(true)
}
