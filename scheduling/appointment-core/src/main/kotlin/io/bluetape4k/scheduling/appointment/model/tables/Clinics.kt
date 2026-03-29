package io.bluetape4k.scheduling.appointment.model.tables

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * 병원 정보 테이블.
 *
 * 병원의 기본 설정(슬롯 간격, 타임존, 지역, 최대 동시 환자 수, 휴일 운영)을 저장합니다.
 * 슬롯 계산과 운영 시간 설정의 기준이 됩니다.
 */
object Clinics : LongIdTable("scheduling_clinics") {
    val name = varchar("name", 255)
    val slotDurationMinutes = integer("slot_duration_minutes").default(30)
    val timezone = varchar("timezone", 50).default("UTC")
    val locale = varchar("locale", 20).default("ko-KR")
    val maxConcurrentPatients = integer("max_concurrent_patients").default(1)
    val openOnHolidays = bool("open_on_holidays").default(false)
}
