package io.bluetape4k.scheduling.appointment.solver.service

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord

/**
 * Solver 실행 결과.
 *
 * @param score Solver가 계산한 최종 점수
 * @param appointments 최적화된 예약 목록
 * @param isFeasible Hard Constraint 위반이 없는지 여부
 */
data class SolverResult(
    val score: HardSoftScore,
    val appointments: List<AppointmentRecord>,
    val isFeasible: Boolean,
)
