package io.bluetape4k.scheduling.appointment.solver.service

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord

/**
 * Solver 실행 결과.
 *
 * @param score Solver가 계산한 최종 점수
 * @param appointments 최적화된 예약 목록
 * @param isFeasible Hard Constraint 위반이 없는지 여부
 * @param solveTimeMillis Solver 실행 시간 (밀리초)
 * @param entityCount 최적화 대상 엔티티 수
 * @param pinnedCount 고정(pinned) 엔티티 수
 */
data class SolverResult(
    val score: HardSoftScore,
    val appointments: List<AppointmentRecord>,
    val isFeasible: Boolean,
    val solveTimeMillis: Long = 0L,
    val entityCount: Int = 0,
    val pinnedCount: Int = 0,
)
