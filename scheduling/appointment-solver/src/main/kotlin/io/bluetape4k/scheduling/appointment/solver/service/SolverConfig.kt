package io.bluetape4k.scheduling.appointment.solver.service

import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicType
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig
import ai.timefold.solver.core.config.localsearch.LocalSearchType
import ai.timefold.solver.core.config.solver.SolverConfig
import ai.timefold.solver.core.config.solver.termination.TerminationConfig
import io.bluetape4k.scheduling.appointment.solver.constraint.AppointmentConstraintProvider
import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import io.bluetape4k.scheduling.appointment.solver.domain.ScheduleSolution
import java.time.Duration

/**
 * Timefold SolverFactory 생성 유틸리티.
 *
 * Construction Heuristic(First Fit Decreasing) + Local Search(Late Acceptance) 2단계 최적화를 구성합니다.
 */
object AppointmentSolverConfig {

    /**
     * 기본 Solver Factory를 생성합니다.
     *
     * @param timeLimit 최대 Solver 실행 시간
     * @param unimprovedTimeLimit 개선 없는 최대 시간 (이 시간 동안 점수가 개선되지 않으면 조기 종료)
     */
    fun createFactory(
        timeLimit: Duration = Duration.ofSeconds(30),
        unimprovedTimeLimit: Duration = timeLimit.dividedBy(2),
    ): SolverFactory<ScheduleSolution> =
        SolverFactory.create(
            SolverConfig()
                .withSolutionClass(ScheduleSolution::class.java)
                .withEntityClasses(AppointmentPlanning::class.java)
                .withConstraintProviderClass(AppointmentConstraintProvider::class.java)
                .withTerminationConfig(
                    TerminationConfig()
                        .withSpentLimit(timeLimit)
                        .withUnimprovedSpentLimit(unimprovedTimeLimit)
                )
                .withPhases(
                    // Phase 1: Construction Heuristic — 초기 해 생성
                    ConstructionHeuristicPhaseConfig()
                        .withConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT_DECREASING),

                    // Phase 2: Local Search — 해 개선
                    LocalSearchPhaseConfig()
                        .withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE),
                )
        )

    /**
     * 빠른 테스트용 Solver Factory를 생성합니다.
     * Construction Heuristic만 수행하여 빠르게 feasible 해를 찾습니다.
     */
    fun createQuickFactory(timeLimit: Duration = Duration.ofSeconds(5)): SolverFactory<ScheduleSolution> =
        SolverFactory.create(
            SolverConfig()
                .withSolutionClass(ScheduleSolution::class.java)
                .withEntityClasses(AppointmentPlanning::class.java)
                .withConstraintProviderClass(AppointmentConstraintProvider::class.java)
                .withTerminationConfig(
                    TerminationConfig()
                        .withSpentLimit(timeLimit)
                )
                .withPhases(
                    ConstructionHeuristicPhaseConfig()
                        .withConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT_DECREASING),
                )
        )
}
