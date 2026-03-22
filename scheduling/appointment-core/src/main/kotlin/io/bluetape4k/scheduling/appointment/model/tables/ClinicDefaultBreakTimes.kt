package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.time

/**
 * 병원 기본 휴식시간 테이블.
 *
 * 요일 구분 없이 모든 영업일에 동일하게 적용되는 휴식시간입니다.
 * 하루에 여러 번의 휴식시간을 설정할 수 있습니다. (예: 점심, 오후 티타임 등)
 */
object ClinicDefaultBreakTimes : LongIdTable("scheduling_clinic_default_break_times") {
    val clinicId = reference("clinic_id", Clinics, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val startTime = time("start_time")
    val endTime = time("end_time")
}
