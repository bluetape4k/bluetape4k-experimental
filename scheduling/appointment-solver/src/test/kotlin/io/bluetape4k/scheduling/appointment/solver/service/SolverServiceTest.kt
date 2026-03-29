package io.bluetape4k.scheduling.appointment.solver.service

import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentNotes
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
import io.bluetape4k.scheduling.appointment.model.tables.ProviderType
import io.bluetape4k.scheduling.appointment.model.tables.RescheduleCandidates
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentEquipments
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class SolverServiceTest {

    companion object: KLogging() {

        private lateinit var db: Database

        private val solverFactory = AppointmentSolverConfig.createFactory(Duration.ofSeconds(5))
        private val solverService = SolverService(solverFactory = solverFactory)

        private val MONDAY = LocalDate.of(2026, 3, 23)
        private val FRIDAY = LocalDate.of(2026, 3, 27)

        @JvmStatic
        @BeforeAll
        fun setup() {
            db = Database.connect(
                "jdbc:h2:mem:solver_test;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver"
            )
            transaction {
                SchemaUtils.create(
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
                    RescheduleCandidates,
                )
            }
        }
    }

    @BeforeEach
    fun cleanUp() {
        transaction {
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
            ClinicDefaultBreakTimes.deleteAll()
            OperatingHoursTable.deleteAll()
            Clinics.deleteAll()
            Holidays.deleteAll()
        }
    }

    /**
     * 기본 데이터 삽입: 병원 1개, 의사 2명, 영업시간 월~금, 진료유형 1개
     * 반환: (clinicId, doctorId1, doctorId2, treatmentTypeId)
     */
    private data class BaseData(
        val clinicId: Long,
        val doctorId1: Long,
        val doctorId2: Long,
        val treatmentTypeId: Long,
    )

    private fun insertBaseData(maxConcurrentPatients: Int = 1): BaseData = transaction {
        val clinicId = Clinics.insert {
            it[name] = "Test Clinic"
            it[slotDurationMinutes] = 30
            it[Clinics.maxConcurrentPatients] = maxConcurrentPatients
        }[Clinics.id].value

        val weekdays = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        )
        for (day in weekdays) {
            OperatingHoursTable.insert {
                it[OperatingHoursTable.clinicId] = clinicId
                it[dayOfWeek] = day
                it[openTime] = LocalTime.of(9, 0)
                it[closeTime] = LocalTime.of(18, 0)
                it[isActive] = true
            }
        }

        val doctorId1 = Doctors.insert {
            it[Doctors.clinicId] = clinicId
            it[name] = "Dr. Kim"
            it[providerType] = ProviderType.DOCTOR
        }[Doctors.id].value

        val doctorId2 = Doctors.insert {
            it[Doctors.clinicId] = clinicId
            it[name] = "Dr. Park"
            it[providerType] = ProviderType.DOCTOR
        }[Doctors.id].value

        for (day in weekdays) {
            DoctorSchedules.insert {
                it[DoctorSchedules.doctorId] = doctorId1
                it[dayOfWeek] = day
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(18, 0)
            }
            DoctorSchedules.insert {
                it[DoctorSchedules.doctorId] = doctorId2
                it[dayOfWeek] = day
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(18, 0)
            }
        }

        val treatmentTypeId = TreatmentTypes.insert {
            it[TreatmentTypes.clinicId] = clinicId
            it[name] = "General Checkup"
            it[defaultDurationMinutes] = 30
            it[requiredProviderType] = ProviderType.DOCTOR
        }[TreatmentTypes.id].value

        BaseData(clinicId, doctorId1, doctorId2, treatmentTypeId)
    }

    @Test
    fun `1 - Solver가 feasible 해를 반환한다`() {
        val (clinicId, doctorId1, _, treatmentTypeId) = insertBaseData()

        transaction {
            Appointments.insert {
                it[Appointments.clinicId] = clinicId
                it[Appointments.doctorId] = doctorId1
                it[Appointments.treatmentTypeId] = treatmentTypeId
                it[patientName] = "Patient A"
                it[appointmentDate] = MONDAY
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(9, 30)
                it[status] = AppointmentState.REQUESTED
            }
            Appointments.insert {
                it[Appointments.clinicId] = clinicId
                it[Appointments.doctorId] = doctorId1
                it[Appointments.treatmentTypeId] = treatmentTypeId
                it[patientName] = "Patient B"
                it[appointmentDate] = MONDAY
                it[startTime] = LocalTime.of(9, 30)
                it[endTime] = LocalTime.of(10, 0)
                it[status] = AppointmentState.REQUESTED
            }
        }

        val result = solverService.optimize(clinicId, MONDAY..FRIDAY, Duration.ofSeconds(5))

        result.isFeasible.shouldBeTrue()
        result.appointments shouldHaveSize 2
    }

    @Test
    fun `2 - 동시 환자 수 초과 시 Solver가 재배치한다`() {
        val (clinicId, doctorId1, _, treatmentTypeId) = insertBaseData(maxConcurrentPatients = 1)

        transaction {
            repeat(3) { i ->
                Appointments.insert {
                    it[Appointments.clinicId] = clinicId
                    it[Appointments.doctorId] = doctorId1
                    it[Appointments.treatmentTypeId] = treatmentTypeId
                    it[patientName] = "Patient $i"
                    it[appointmentDate] = MONDAY
                    it[startTime] = LocalTime.of(9, 0)
                    it[endTime] = LocalTime.of(9, 30)
                    it[status] = AppointmentState.REQUESTED
                }
            }
        }

        val result = solverService.optimize(clinicId, MONDAY..FRIDAY, Duration.ofSeconds(5))

        result.isFeasible.shouldBeTrue()
        val startTimes = result.appointments.map { it.startTime }.toSet()
        (startTimes.size == result.appointments.size).shouldBeTrue()
    }

    @Test
    fun `3 - optimizeReschedule로 휴진 재배정을 수행한다`() {
        val (clinicId, doctorId1, _, treatmentTypeId) = insertBaseData()

        transaction {
            Appointments.insert {
                it[Appointments.clinicId] = clinicId
                it[Appointments.doctorId] = doctorId1
                it[Appointments.treatmentTypeId] = treatmentTypeId
                it[patientName] = "Reschedule Patient"
                it[appointmentDate] = MONDAY
                it[startTime] = LocalTime.of(9, 0)
                it[endTime] = LocalTime.of(9, 30)
                it[status] = AppointmentState.PENDING_RESCHEDULE
            }
        }

        val result = solverService.optimizeReschedule(
            clinicId = clinicId,
            closureDate = MONDAY,
            searchDays = 5,
            timeLimit = Duration.ofSeconds(5),
        )

        result.isFeasible.shouldBeTrue()
        val rescheduled = result.appointments
        rescheduled.all { it.appointmentDate >= MONDAY }.shouldBeTrue()
    }

    @Test
    fun `4 - 예약이 없으면 빈 결과 반환`() {
        val (clinicId, _, _, _) = insertBaseData()

        val result = solverService.optimize(clinicId, MONDAY..FRIDAY, Duration.ofSeconds(5))

        result.appointments.shouldBeEmpty()
        result.isFeasible.shouldBeTrue()
    }
}
