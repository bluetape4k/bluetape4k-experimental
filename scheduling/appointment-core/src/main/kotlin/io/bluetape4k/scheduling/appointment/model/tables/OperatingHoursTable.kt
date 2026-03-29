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

    init {
        // 병원+요일 조회 (운영시간 확인, 슬롯 계산)
        index("idx_operating_hours_clinic_day_active", false, clinicId, dayOfWeek, isActive)
    }
}
