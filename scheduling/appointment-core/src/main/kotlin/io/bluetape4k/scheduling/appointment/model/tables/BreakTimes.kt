package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.time
import java.time.DayOfWeek

object BreakTimes : LongIdTable("scheduling_break_times") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val dayOfWeek = enumerationByName("day_of_week", 10, DayOfWeek::class)
    val startTime = time("start_time")
    val endTime = time("end_time")
}
