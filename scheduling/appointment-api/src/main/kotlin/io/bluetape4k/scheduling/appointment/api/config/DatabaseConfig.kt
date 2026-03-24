package io.bluetape4k.scheduling.appointment.api.config

import io.bluetape4k.scheduling.appointment.event.AppointmentEventLogs
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentNotes
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.scheduling.appointment.model.tables.BreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.ClinicClosures
import io.bluetape4k.scheduling.appointment.model.tables.ClinicDefaultBreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.model.tables.ConsultationTopics
import io.bluetape4k.scheduling.appointment.model.tables.DoctorAbsences
import io.bluetape4k.scheduling.appointment.model.tables.DoctorSchedules
import io.bluetape4k.scheduling.appointment.model.tables.Doctors
import io.bluetape4k.scheduling.appointment.model.tables.Equipments
import io.bluetape4k.scheduling.appointment.model.tables.Holidays
import io.bluetape4k.scheduling.appointment.model.tables.OperatingHoursTable
import io.bluetape4k.scheduling.appointment.model.tables.RescheduleCandidates
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentEquipments
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order

/**
 * 데이터베이스 스키마 초기화 설정.
 *
 * - Flyway 활성(`spring.flyway.enabled=true`): Flyway SQL 마이그레이션이 스키마를 관리.
 *   → `flyway` 또는 `postgresql` profile 사용.
 * - Flyway 비활성(기본): Exposed SchemaUtils가 스키마를 생성.
 *   → 개발/테스트 환경 (H2 in-memory).
 */
@Configuration(proxyBeanMethods = false)
class SchemaInitConfig {
    /**
     * Flyway가 비활성일 때 Exposed SchemaUtils로 스키마 생성.
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = ["spring.flyway.enabled"], havingValue = "false", matchIfMissing = true)
    fun schemaInitializer(): ApplicationRunner =
        ApplicationRunner {
            transaction {
                SchemaUtils.create(
                    Clinics,
                    OperatingHoursTable,
                    ClinicDefaultBreakTimes,
                    BreakTimes,
                    ClinicClosures,
                    Doctors,
                    DoctorSchedules,
                    DoctorAbsences,
                    TreatmentTypes,
                    Equipments,
                    TreatmentEquipments,
                    ConsultationTopics,
                    Holidays,
                    Appointments,
                    AppointmentNotes,
                    RescheduleCandidates,
                    AppointmentEventLogs
                )
            }
        }
}
