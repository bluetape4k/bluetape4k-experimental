package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.time

object ClinicClosures : LongIdTable("scheduling_clinic_closures") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val closureDate = date("closure_date")
    val reason = varchar("reason", 500).nullable()
    val isFullDay = bool("is_full_day").default(true)
    val startTime = time("start_time").nullable()
    val endTime = time("end_time").nullable()

    init {
        // 병원+날짜 조회 (임시휴진 확인, 날짜 범위 조회)
        index("idx_clinic_closures_clinic_date", false, clinicId, closureDate)
    }
}
