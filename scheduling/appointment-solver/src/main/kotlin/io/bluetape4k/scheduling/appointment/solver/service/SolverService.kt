package io.bluetape4k.scheduling.appointment.solver.service

import ai.timefold.solver.core.api.solver.SolverFactory
import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStatus
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import io.bluetape4k.scheduling.appointment.repository.ClinicRepository
import io.bluetape4k.scheduling.appointment.repository.DoctorRepository
import io.bluetape4k.scheduling.appointment.repository.HolidayRepository
import io.bluetape4k.scheduling.appointment.repository.TreatmentTypeRepository
import io.bluetape4k.scheduling.appointment.solver.converter.SolutionConverter
import io.bluetape4k.scheduling.appointment.solver.domain.ScheduleSolution
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Duration
import java.time.LocalDate

/**
 * Solver 실행 진입점.
 *
 * Repository에서 데이터를 로딩하고 Solver를 실행하여 최적화된 예약 배치를 반환합니다.
 */
class SolverService(
    private val clinicRepository: ClinicRepository = ClinicRepository(),
    private val doctorRepository: DoctorRepository = DoctorRepository(),
    private val appointmentRepository: AppointmentRepository = AppointmentRepository(),
    private val treatmentTypeRepository: TreatmentTypeRepository = TreatmentTypeRepository(),
    private val holidayRepository: HolidayRepository = HolidayRepository(),
    private val solverFactory: SolverFactory<ScheduleSolution> = AppointmentSolverConfig.createFactory(),
) {
    companion object: KLogging()

    /**
     * 특정 클리닉의 주어진 날짜 범위에 대해 배치 최적화를 실행합니다.
     *
     * @param clinicId 대상 클리닉
     * @param dateRange 최적화 대상 날짜 범위
     * @param timeLimit 최대 Solver 실행 시간
     * @return 최적화된 예약 배치 결과
     */
    fun optimize(
        clinicId: Long,
        dateRange: ClosedRange<LocalDate>,
        timeLimit: Duration = Duration.ofSeconds(30),
    ): SolverResult {
        val solution = loadSolution(clinicId, dateRange)
        val factory = if (timeLimit != Duration.ofSeconds(30)) {
            AppointmentSolverConfig.createFactory(timeLimit)
        } else {
            solverFactory
        }

        val solver = factory.buildSolver()
        val result = solver.solve(solution)

        val originalMap = transaction {
            appointmentRepository.findByClinicAndDateRange(clinicId, dateRange)
                .associateBy { it.id!! }
        }

        val optimizedAppointments = SolutionConverter.extractResults(result, originalMap)
        val score = result.score!!

        return SolverResult(
            score = score,
            appointments = optimizedAppointments,
            isFeasible = score.isFeasible,
        )
    }

    /**
     * 임시휴진에 의한 재스케줄을 전역 최적화로 수행합니다.
     *
     * @param clinicId 대상 클리닉
     * @param closureDate 휴진 날짜
     * @param searchDays 후보 탐색 일수
     * @param timeLimit 최대 Solver 실행 시간
     * @return 최적화된 예약 배치 결과
     */
    fun optimizeReschedule(
        clinicId: Long,
        closureDate: LocalDate,
        searchDays: Int = 7,
        timeLimit: Duration = Duration.ofSeconds(30),
    ): SolverResult {
        val dateRange = closureDate..closureDate.plusDays(searchDays.toLong())
        return optimize(clinicId, dateRange, timeLimit)
    }

    private fun loadSolution(clinicId: Long, dateRange: ClosedRange<LocalDate>): ScheduleSolution =
        transaction {
            val clinic = clinicRepository.findByIdOrNull(clinicId)
                ?: throw IllegalArgumentException("Clinic not found: $clinicId")

            val doctors = doctorRepository.findByClinicId(clinicId)
            val appointments = appointmentRepository.findByClinicAndDateRange(clinicId, dateRange)
            val treatments = treatmentTypeRepository.findByClinicId(clinicId)
            val equipments = treatmentTypeRepository.findEquipmentsByClinicId(clinicId)
            val operatingHours = clinicRepository.findAllOperatingHours(clinicId)
            val doctorSchedules = doctors.flatMap { doctorRepository.findAllSchedules(it.id!!) }
            val doctorAbsences = doctors.flatMap { doctorRepository.findAbsencesByDateRange(it.id!!, dateRange) }
            val breakTimes = clinicRepository.findAllBreakTimes(clinicId)
            val defaultBreakTimes = clinicRepository.findDefaultBreakTimes(clinicId)
            val closures = clinicRepository.findClosuresByDateRange(clinicId, dateRange)
            val holidays = holidayRepository.findByDateRange(dateRange)
            val treatmentEquipments = treatmentTypeRepository.findAllTreatmentEquipments(clinicId)

            SolutionConverter.buildSolution(
                clinic = clinic,
                doctors = doctors,
                appointments = appointments,
                treatments = treatments,
                equipments = equipments,
                operatingHours = operatingHours,
                doctorSchedules = doctorSchedules,
                doctorAbsences = doctorAbsences,
                breakTimes = breakTimes,
                defaultBreakTimes = defaultBreakTimes,
                closures = closures,
                holidays = holidays,
                treatmentEquipments = treatmentEquipments,
                dateRange = dateRange,
            )
        }
}
