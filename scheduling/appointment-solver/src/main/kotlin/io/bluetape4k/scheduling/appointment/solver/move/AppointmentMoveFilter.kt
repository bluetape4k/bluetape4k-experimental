package io.bluetape4k.scheduling.appointment.solver.move

import ai.timefold.solver.core.api.score.director.ScoreDirector
import ai.timefold.solver.core.impl.heuristic.move.Move
import ai.timefold.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter
import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import io.bluetape4k.scheduling.appointment.solver.domain.ScheduleSolution
import java.time.LocalDate

/**
 * 명백히 불가능한 이동을 사전 필터링하여 Solver 탐색 공간을 축소합니다.
 *
 * 의사 부재, 클리닉 휴진, 공휴일, 비영업일 등 Hard Constraint를 반드시 위반하는
 * 이동을 미리 제거하여 Solver 성능을 향상시킵니다.
 *
 * 필터 조건:
 * - 클리닉 전일 휴진 ([ClinicClosureRecord.isFullDay])
 * - 비영업일 ([OperatingHoursRecord.isActive] = false)
 * - 공휴일 (클리닉이 [ClinicFact.openOnHolidays] = false인 경우)
 * - 의사 전일 부재 ([DoctorAbsenceRecord] startTime = null)
 * - 의사-클리닉 소속 불일치
 * - 의사 진료 유형 불일치 ([DoctorFact.providerType])
 */
class AppointmentMoveFilter : SelectionFilter<ScheduleSolution, Move<ScheduleSolution>> {

    companion object : KLogging()

    override fun accept(
        scoreDirector: ScoreDirector<ScheduleSolution>,
        selection: Move<ScheduleSolution>,
    ): Boolean {
        val solution = scoreDirector.workingSolution

        for (entity in selection.planningEntities) {
            if (entity !is AppointmentPlanning) continue

            val values = selection.planningValues

            // Move에서 변경될 날짜와 의사 ID를 추출 (null이면 기존 값 유지)
            val targetDate = values.filterIsInstance<LocalDate>().firstOrNull()
                ?: entity.appointmentDate
            val targetDoctorId = values.filterIsInstance<Long>().firstOrNull()
                ?: entity.doctorId

            if (targetDate != null && !isDateAcceptable(targetDate, targetDoctorId, solution)) {
                return false
            }
            if (targetDoctorId != null && !isDoctorAcceptable(targetDoctorId, entity, solution)) {
                return false
            }
        }

        return true
    }

    /**
     * 지정된 날짜가 예약 가능한 날인지 검증합니다.
     *
     * @param date 검증할 날짜
     * @param doctorId 담당 의사 ID (null이면 의사 부재 체크 생략)
     * @param solution 현재 솔루션 (Problem Facts 포함)
     * @return 날짜가 유효하면 true
     */
    private fun isDateAcceptable(
        date: LocalDate,
        doctorId: Long?,
        solution: ScheduleSolution,
    ): Boolean {
        // 1. 전일 클리닉 휴진 체크
        if (solution.closures.any { it.closureDate == date && it.isFullDay }) {
            return false
        }

        // 2. 비영업일 체크 (해당 요일에 isActive=true인 OperatingHours가 없으면 거부)
        val dayOfWeek = date.dayOfWeek
        if (solution.operatingHours.none { it.dayOfWeek == dayOfWeek && it.isActive }) {
            return false
        }

        // 3. 공휴일 체크 (openOnHolidays=false인 클리닉은 공휴일에 운영 안 함)
        if (!solution.clinic.openOnHolidays && solution.holidays.any { it.holidayDate == date }) {
            return false
        }

        // 4. 의사 전일 부재 체크 (startTime=null이면 전일 부재)
        if (doctorId != null) {
            if (solution.doctorAbsences.any { abs ->
                    abs.doctorId == doctorId &&
                        abs.absenceDate == date &&
                        abs.startTime == null
                }) {
                return false
            }
        }

        return true
    }

    /**
     * 지정된 의사가 해당 예약을 담당할 수 있는지 검증합니다.
     *
     * @param doctorId 검증할 의사 ID
     * @param entity 배정 대상 예약 Planning Entity
     * @param solution 현재 솔루션 (Problem Facts 포함)
     * @return 의사 배정이 유효하면 true
     */
    private fun isDoctorAcceptable(
        doctorId: Long,
        entity: AppointmentPlanning,
        solution: ScheduleSolution,
    ): Boolean {
        val doctor = solution.doctors.find { it.id == doctorId } ?: return false

        // 클리닉 소속 체크
        if (doctor.clinicId != entity.clinicId) {
            return false
        }

        // 진료 유형 체크
        if (doctor.providerType != entity.requiredProviderType) {
            return false
        }

        return true
    }
}
