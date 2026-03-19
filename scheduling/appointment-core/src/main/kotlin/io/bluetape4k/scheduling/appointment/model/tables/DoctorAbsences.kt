package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.time

object DoctorAbsences : LongIdTable("scheduling_doctor_absences") {
    val doctorId = reference("doctor_id", Doctors, onDelete = ReferenceOption.CASCADE)
    val absenceDate = date("absence_date")
    val startTime = time("start_time").nullable()
    val endTime = time("end_time").nullable()
    val reason = varchar("reason", 500).nullable()
}
