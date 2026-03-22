package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.time
import org.jetbrains.exposed.v1.javatime.timestamp

/**
 * 재배정 후보 슬롯. 임시휴진 시 영향받는 예약에 대해 자동 탐색된 후보군.
 */
object RescheduleCandidates : LongIdTable("scheduling_reschedule_candidates") {
    val originalAppointmentId = reference("original_appointment_id", Appointments, onDelete = ReferenceOption.CASCADE)
    val candidateDate = date("candidate_date")
    val startTime = time("start_time")
    val endTime = time("end_time")
    val doctorId = reference("doctor_id", Doctors, onDelete = ReferenceOption.CASCADE)
    val priority = integer("priority").default(0)
    val selected = bool("selected").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}
