package io.bluetape4k.scheduling.appointment.solver.benchmark

import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.dto.DoctorScheduleRecord
import io.bluetape4k.scheduling.appointment.model.dto.OperatingHoursRecord
import io.bluetape4k.scheduling.appointment.solver.constraint.AppointmentConstraintProvider
import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import io.bluetape4k.scheduling.appointment.solver.domain.ClinicFact
import io.bluetape4k.scheduling.appointment.solver.domain.DoctorFact
import io.bluetape4k.scheduling.appointment.solver.domain.ScheduleSolution
import io.bluetape4k.scheduling.appointment.solver.domain.TreatmentFact
import io.bluetape4k.scheduling.appointment.solver.domain.generateTimeSlots
import io.bluetape4k.scheduling.appointment.solver.service.AppointmentSolverConfig
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Solver 벤치마크 테스트.
 *
 * 다양한 규모의 문제를 생성하여 Solver 성능을 측정합니다.
 * CI에서는 건너뛰도록 "benchmark" 태그로 분류합니다.
 */
@Tag("benchmark")
class BenchmarkTest {

    companion object : KLogging() {
        private val MONDAY = LocalDate.of(2026, 3, 23)
    }

    @Test
    fun `소규모 - 의사 2명 예약 10건`() {
        val solution = buildSolution(doctorCount = 2, appointmentCount = 10, days = 5)
        val factory = AppointmentSolverConfig.createFactory(timeLimit = Duration.ofSeconds(10))
        val solver = factory.buildSolver()

        val startMillis = System.currentTimeMillis()
        val result = solver.solve(solution)
        val elapsed = System.currentTimeMillis() - startMillis

        result.score.shouldNotBeNull()
        result.score!!.isFeasible.shouldBeTrue()
        elapsed.shouldBeGreaterThan(0L)
        log.info("소규모: score=${result.score}, time=${elapsed}ms")
    }

    @Test
    fun `중규모 - 의사 5명 예약 30건`() {
        val solution = buildSolution(doctorCount = 5, appointmentCount = 30, days = 5)
        val factory = AppointmentSolverConfig.createFactory(timeLimit = Duration.ofSeconds(15))
        val solver = factory.buildSolver()

        val startMillis = System.currentTimeMillis()
        val result = solver.solve(solution)
        val elapsed = System.currentTimeMillis() - startMillis

        result.score.shouldNotBeNull()
        result.score!!.isFeasible.shouldBeTrue()
        log.info("중규모: score=${result.score}, time=${elapsed}ms")
    }

    @Test
    fun `대규모 - 의사 10명 예약 100건`() {
        val solution = buildSolution(doctorCount = 10, appointmentCount = 100, days = 5)
        val factory = AppointmentSolverConfig.createFactory(timeLimit = Duration.ofSeconds(30))
        val solver = factory.buildSolver()

        val startMillis = System.currentTimeMillis()
        val result = solver.solve(solution)
        val elapsed = System.currentTimeMillis() - startMillis

        result.score.shouldNotBeNull()
        log.info("대규모: score=${result.score}, feasible=${result.score!!.isFeasible}, time=${elapsed}ms")
    }

    private fun buildSolution(
        doctorCount: Int,
        appointmentCount: Int,
        days: Int,
    ): ScheduleSolution {
        val clinic = ClinicFact(id = 1L, slotDurationMinutes = 30, maxConcurrentPatients = 1, openOnHolidays = false)

        val doctors = (1..doctorCount).map { i ->
            DoctorFact(id = i.toLong(), clinicId = 1L, providerType = "DOCTOR", maxConcurrentPatients = 1)
        }

        val treatment = TreatmentFact(
            id = 1L, defaultDurationMinutes = 30,
            requiredProviderType = "DOCTOR", requiresEquipment = false, maxConcurrentPatients = null,
        )

        val weekdays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        val operatingHours = weekdays.mapIndexed { idx, day ->
            OperatingHoursRecord(
                id = idx.toLong() + 1, clinicId = 1L, dayOfWeek = day,
                openTime = LocalTime.of(9, 0), closeTime = LocalTime.of(18, 0), isActive = true,
            )
        }

        // 모든 의사에 대해 월~금 근무 스케줄 생성
        var scheduleId = 1L
        val doctorSchedules = doctors.flatMap { doctor ->
            weekdays.map { day ->
                DoctorScheduleRecord(
                    id = scheduleId++,
                    doctorId = doctor.id,
                    dayOfWeek = day,
                    startTime = LocalTime.of(9, 0),
                    endTime = LocalTime.of(18, 0),
                )
            }
        }

        val dates = (0 until days).map { MONDAY.plusDays(it.toLong()) }
        val timeSlots = generateTimeSlots(LocalTime.of(9, 0), LocalTime.of(18, 0), 30)

        val appointments = (1..appointmentCount).map { i ->
            AppointmentPlanning(
                id = i.toLong(),
                clinicId = 1L,
                treatmentTypeId = 1L,
                patientName = "Patient $i",
                durationMinutes = 30,
                requiredProviderType = "DOCTOR",
                // 초기값은 모두 같은 의사/시간에 배치 → Solver가 재배치
                doctorId = doctors[0].id,
                appointmentDate = MONDAY,
                startTime = LocalTime.of(9, 0),
            )
        }

        return ScheduleSolution(
            clinic = clinic,
            doctors = doctors,
            treatments = listOf(treatment),
            operatingHours = operatingHours,
            doctorSchedules = doctorSchedules,
            doctorIds = doctors.map { it.id },
            dateRange = dates,
            timeSlots = timeSlots,
            appointments = appointments,
        )
    }
}
