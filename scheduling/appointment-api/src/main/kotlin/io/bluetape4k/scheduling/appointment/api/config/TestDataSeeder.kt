package io.bluetape4k.scheduling.appointment.api.config

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.model.tables.DoctorSchedules
import io.bluetape4k.scheduling.appointment.model.tables.Doctors
import io.bluetape4k.scheduling.appointment.model.tables.OperatingHoursTable
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Gatling 스트레스 테스트를 위한 기본 시드 데이터 생성.
 *
 * 클리닉, 의사, 영업시간, 의사 스케줄, 진료 유형을 생성합니다.
 * 이미 데이터가 존재하면 건너뜁니다.
 */
@Configuration(proxyBeanMethods = false)
class TestDataSeederConfig {
    companion object : KLogging() {
        private val WEEKDAYS =
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        private val OPEN_TIME = LocalTime.of(9, 0)
        private val CLOSE_TIME = LocalTime.of(18, 0)
        private const val DOCTOR_COUNT = 3
    }

    @Bean
    @Order(2)
    fun testDataSeeder(): ApplicationRunner =
        ApplicationRunner {
            transaction {
                if (Clinics.selectAll().count() > 0) {
                    log.info { "시드 데이터가 이미 존재합니다. 건너뜁니다." }
                    return@transaction
                }

                log.info { "스트레스 테스트용 시드 데이터를 생성합니다." }

                val clinicId =
                    Clinics
                        .insert {
                            it[name] = "Test Clinic"
                            it[slotDurationMinutes] = 30
                            it[maxConcurrentPatients] = 1
                        }[Clinics.id]
                        .value

                for (day in WEEKDAYS) {
                    OperatingHoursTable.insert {
                        it[OperatingHoursTable.clinicId] = clinicId
                        it[dayOfWeek] = day
                        it[openTime] = OPEN_TIME
                        it[closeTime] = CLOSE_TIME
                        it[isActive] = true
                    }
                }

                for (i in 1..DOCTOR_COUNT) {
                    val doctorId =
                        Doctors
                            .insert {
                                it[Doctors.clinicId] = clinicId
                                it[name] = "Doctor $i"
                                it[providerType] = "DOCTOR"
                            }[Doctors.id]
                            .value

                    for (day in WEEKDAYS) {
                        DoctorSchedules.insert {
                            it[DoctorSchedules.doctorId] = doctorId
                            it[dayOfWeek] = day
                            it[startTime] = OPEN_TIME
                            it[endTime] = CLOSE_TIME
                        }
                    }
                }

                TreatmentTypes.insert {
                    it[TreatmentTypes.clinicId] = clinicId
                    it[name] = "General Checkup"
                    it[defaultDurationMinutes] = 30
                    it[requiredProviderType] = "DOCTOR"
                    it[requiresEquipment] = false
                }

                log.info { "시드 데이터 생성 완료: clinic=$clinicId, doctors=$DOCTOR_COUNT" }
            }
        }
}
