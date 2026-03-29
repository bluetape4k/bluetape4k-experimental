package io.bluetape4k.scheduling.appointment.service

import io.bluetape4k.scheduling.appointment.model.tables.AppointmentNotes
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStateHistory
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
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
import io.bluetape4k.support.requireNotNull
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ClosureRescheduleServiceTest {
    companion object {
        private lateinit var db: Database
        private val slotService = SlotCalculationService()
        private val rescheduleService = ClosureRescheduleService(slotService)

        // 월요일
        private val MONDAY = LocalDate.of(2026, 3, 23)
        // 화요일
        private val TUESDAY = LocalDate.of(2026, 3, 24)

        private val allTables = arrayOf(
            Holidays,
            Clinics,
            ClinicDefaultBreakTimes,
            OperatingHoursTable,
            BreakTimes,
            ClinicClosures,
            Doctors,
            DoctorSchedules,
            DoctorAbsences,
            Equipments,
            TreatmentTypes,
            TreatmentEquipments,
            ConsultationTopics,
            Appointments,
            AppointmentNotes,
            AppointmentStateHistory,
            RescheduleCandidates
        )

        @JvmStatic
        @BeforeAll
        fun setup() {
            db = Database.connect("jdbc:h2:mem:reschedule_test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
            transaction {
                SchemaUtils.create(*allTables)
            }
        }
    }

    @BeforeEach
    fun cleanUp() {
        transaction {
            AppointmentStateHistory.deleteAll()
            RescheduleCandidates.deleteAll()
            AppointmentNotes.deleteAll()
            Appointments.deleteAll()
            TreatmentEquipments.deleteAll()
            ConsultationTopics.deleteAll()
            TreatmentTypes.deleteAll()
            Equipments.deleteAll()
            DoctorAbsences.deleteAll()
            DoctorSchedules.deleteAll()
            Doctors.deleteAll()
            ClinicClosures.deleteAll()
            BreakTimes.deleteAll()
            OperatingHoursTable.deleteAll()
            Clinics.deleteAll()
            Holidays.deleteAll()
        }
    }

    /**
     * 기본 데이터 삽입: 병원 + 의사 + 진료유형 + 월/화 스케줄 + 월요일 예약 1건
     */
    private fun insertDataWithAppointment(): Triple<Long, Long, Long> =
        transaction {
            val clinicId =
                Clinics.insert {
                    it[name] = "Test Clinic"
                    it[slotDurationMinutes] = 30
                    it[maxConcurrentPatients] = 1
                }[Clinics.id].value

            // 월요일, 화요일 영업시간
            for (day in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)) {
                OperatingHoursTable.insert {
                    it[OperatingHoursTable.clinicId] = clinicId
                    it[dayOfWeek] = day
                    it[openTime] = LocalTime.of(9, 0)
                    it[closeTime] = LocalTime.of(18, 0)
                    it[isActive] = true
                }
            }

            val doctorId =
                Doctors.insert {
                    it[Doctors.clinicId] = clinicId
                    it[name] = "Dr. Kim"
                }[Doctors.id].value

            // 월요일, 화요일 의사 스케줄
            for (day in listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)) {
                DoctorSchedules.insert {
                    it[DoctorSchedules.doctorId] = doctorId
                    it[dayOfWeek] = day
                    it[startTime] = LocalTime.of(9, 0)
                    it[endTime] = LocalTime.of(18, 0)
                }
            }

            val treatmentTypeId =
                TreatmentTypes.insert {
                    it[TreatmentTypes.clinicId] = clinicId
                    it[name] = "General Checkup"
                    it[defaultDurationMinutes] = 30
                }[TreatmentTypes.id].value

            // 월요일 09:00 예약
            Appointments.insert {
                it[Appointments.clinicId] = clinicId
                it[Appointments.doctorId] = doctorId
                it[Appointments.treatmentTypeId] = treatmentTypeId
                it[patientName] = "홍길동"
                it[patientPhone] = "010-1234-5678"
                it[appointmentDate] = MONDAY
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(9, 30)
                it[status] = AppointmentState.CONFIRMED
            }

            Triple(clinicId, doctorId, treatmentTypeId)
        }

    @Test
    fun `1 - 임시휴진 시 활성 예약이 PENDING_RESCHEDULE로 전환된다`() {
        val (clinicId, _, _) = insertDataWithAppointment()

        rescheduleService.processClosureReschedule(clinicId, MONDAY)

        transaction {
            val appointments =
                Appointments.selectAll()
                    .where { Appointments.clinicId eq clinicId }
                    .toList()

            appointments shouldHaveSize 1
            appointments[0][Appointments.status] shouldBeEqualTo AppointmentState.PENDING_RESCHEDULE
        }
    }

    @Test
    fun `2 - 재배정 후보가 다음 날짜에서 탐색된다`() {
        val (clinicId, _, _) = insertDataWithAppointment()

        val result = rescheduleService.processClosureReschedule(clinicId, MONDAY, searchDays = 1)

        // 화요일 후보가 생성되어야 함
        result.size shouldBeEqualTo 1
        val candidates = result.values.first()
        candidates.isEmpty().shouldBeFalse()
        candidates.all { it.candidateDate == TUESDAY }.shouldBeTrue()
    }

    @Test
    fun `3 - 관리자가 후보를 선택하면 새 예약 생성 및 원래 예약 RESCHEDULED`() {
        val (clinicId, _, _) = insertDataWithAppointment()

        val result = rescheduleService.processClosureReschedule(clinicId, MONDAY, searchDays = 1)
        val firstCandidate = result.values.first().first()

        val newAppointmentId = rescheduleService.confirmReschedule(firstCandidate.id!!)

        transaction {
            // 원래 예약이 RESCHEDULED
            val originalAppointment =
                Appointments.selectAll()
                    .where { Appointments.status eq AppointmentState.RESCHEDULED }
                    .firstOrNull()
            originalAppointment.shouldNotBeNull()

            // 새 예약이 CONFIRMED
            val newAppointment =
                Appointments.selectAll()
                    .where { Appointments.id eq newAppointmentId }
                    .first()
            newAppointment[Appointments.status] shouldBeEqualTo AppointmentState.CONFIRMED
            newAppointment[Appointments.appointmentDate] shouldBeEqualTo TUESDAY
            newAppointment[Appointments.patientName] shouldBeEqualTo "홍길동"
            newAppointment[Appointments.rescheduleFromId].shouldNotBeNull()
        }
    }

    @Test
    fun `4 - 자동 재배정은 가장 높은 우선순위 후보를 선택한다`() {
        val (clinicId, _, _) = insertDataWithAppointment()

        val result = rescheduleService.processClosureReschedule(clinicId, MONDAY, searchDays = 1)
        val originalAppointmentId = result.keys.first()

        val newAppointmentId = rescheduleService.autoReschedule(originalAppointmentId)

        newAppointmentId.shouldNotBeNull()

        transaction {
            val newAppointment =
                Appointments.selectAll()
                    .where { Appointments.id eq newAppointmentId.requireNotNull("newAppointmentId") }
                    .first()
            // 가장 이른 슬롯 (화요일 09:00)이 선택되어야 함
            newAppointment[Appointments.appointmentDate] shouldBeEqualTo TUESDAY
            newAppointment[Appointments.startTime] shouldBeEqualTo LocalTime.of(9, 0)
        }
    }

    @Test
    fun `5 - 활성 예약이 없으면 빈 결과 반환`() {
        val (clinicId, _, _) = insertDataWithAppointment()

        // 예약이 없는 화요일에 대해 처리
        val result = rescheduleService.processClosureReschedule(clinicId, TUESDAY)

        result.shouldBeEmpty()
    }

    @Test
    fun `6 - 후보가 없으면 autoReschedule은 null 반환`() {
        transaction {
            val clinicId =
                Clinics.insert {
                    it[name] = "Empty Clinic"
                    it[slotDurationMinutes] = 30
                    it[maxConcurrentPatients] = 1
                }[Clinics.id].value

            OperatingHoursTable.insert {
                it[OperatingHoursTable.clinicId] = clinicId
                it[dayOfWeek] = DayOfWeek.MONDAY
                it[openTime] = LocalTime.of(9, 0)
                it[closeTime] = LocalTime.of(18, 0)
                it[isActive] = true
            }

            val doctorId =
                Doctors.insert {
                    it[Doctors.clinicId] = clinicId
                    it[name] = "Dr. Park"
                }[Doctors.id].value

            DoctorSchedules.insert {
                it[DoctorSchedules.doctorId] = doctorId
                it[dayOfWeek] = DayOfWeek.MONDAY
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(18, 0)
            }

            val treatmentTypeId =
                TreatmentTypes.insert {
                    it[TreatmentTypes.clinicId] = clinicId
                    it[name] = "Checkup"
                    it[defaultDurationMinutes] = 30
                }[TreatmentTypes.id].value

            val appointmentId =
                Appointments.insert {
                    it[Appointments.clinicId] = clinicId
                    it[Appointments.doctorId] = doctorId
                    it[Appointments.treatmentTypeId] = treatmentTypeId
                    it[patientName] = "김환자"
                    it[appointmentDate] = MONDAY
                    it[startTime] = LocalTime.of(9, 0)
                    it[endTime] = LocalTime.of(9, 30)
                    it[status] = AppointmentState.PENDING_RESCHEDULE
                }[Appointments.id].value

            // 후보 없이 autoReschedule 호출
            val result = rescheduleService.autoReschedule(appointmentId)
            (result == null).shouldBeTrue()
        }
    }
}
