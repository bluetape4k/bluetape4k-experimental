package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.time
import java.time.DayOfWeek

object DoctorSchedules : LongIdTable("scheduling_doctor_schedules") {
    val doctorId = reference("doctor_id", Doctors, onDelete = ReferenceOption.CASCADE)
    val dayOfWeek = enumerationByName("day_of_week", 10, DayOfWeek::class)
    val startTime = time("start_time")
    val endTime = time("end_time")
}
