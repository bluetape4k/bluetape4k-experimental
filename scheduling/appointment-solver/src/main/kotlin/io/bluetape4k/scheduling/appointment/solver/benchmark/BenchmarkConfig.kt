package io.bluetape4k.scheduling.appointment.solver.benchmark

import ai.timefold.solver.benchmark.api.PlannerBenchmarkFactory
import ai.timefold.solver.benchmark.config.PlannerBenchmarkConfig
import ai.timefold.solver.benchmark.config.SolverBenchmarkConfig
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicType
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig
import ai.timefold.solver.core.config.localsearch.LocalSearchType
import ai.timefold.solver.core.config.solver.SolverConfig
import ai.timefold.solver.core.config.solver.termination.TerminationConfig
import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.solver.constraint.AppointmentConstraintProvider
import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import io.bluetape4k.scheduling.appointment.solver.domain.ScheduleSolution
import java.io.File
import java.time.Duration

/**
 * Timefold Benchmark 설정.
 *
 * 여러 Solver 전략을 동일 데이터셋으로 비교하여 최적 구성을 찾습니다.
 */
object BenchmarkConfig : KLogging() {

    private const val BENCHMARK_DIR = "local/benchmark"

    /**
     * 벤치마크 팩토리를 생성합니다.
     *
     * 3가지 전략을 비교합니다:
     * 1. First Fit Decreasing + Late Acceptance
     * 2. First Fit Decreasing + Tabu Search
     * 3. First Fit + Late Acceptance (기본 전략)
     *
     * @param timeLimit 각 벤치마크당 최대 실행 시간
     * @param benchmarkDir 결과 저장 디렉토리
     */
    fun createBenchmarkFactory(
        timeLimit: Duration = Duration.ofSeconds(30),
        benchmarkDir: String = BENCHMARK_DIR,
    ): PlannerBenchmarkFactory {
        val baseSolverConfig = SolverConfig()
            .withSolutionClass(ScheduleSolution::class.java)
            .withEntityClasses(AppointmentPlanning::class.java)
            .withConstraintProviderClass(AppointmentConstraintProvider::class.java)

        val benchmarkConfig = PlannerBenchmarkConfig()
            .withBenchmarkDirectory(File(benchmarkDir))
            .withInheritedSolverBenchmarkConfig(
                SolverBenchmarkConfig()
                    .withSolverConfig(baseSolverConfig)
            )
            .withSolverBenchmarkConfigList(
                listOf(
                    // 전략 1: FFD + Late Acceptance
                    createBenchmarkSolverConfig(
                        name = "FFD_LateAcceptance",
                        chType = ConstructionHeuristicType.FIRST_FIT_DECREASING,
                        lsType = LocalSearchType.LATE_ACCEPTANCE,
                        timeLimit = timeLimit,
                    ),
                    // 전략 2: FFD + Tabu Search
                    createBenchmarkSolverConfig(
                        name = "FFD_TabuSearch",
                        chType = ConstructionHeuristicType.FIRST_FIT_DECREASING,
                        lsType = LocalSearchType.TABU_SEARCH,
                        timeLimit = timeLimit,
                    ),
                    // 전략 3: FF + Late Acceptance
                    createBenchmarkSolverConfig(
                        name = "FF_LateAcceptance",
                        chType = ConstructionHeuristicType.FIRST_FIT,
                        lsType = LocalSearchType.LATE_ACCEPTANCE,
                        timeLimit = timeLimit,
                    ),
                )
            )

        return PlannerBenchmarkFactory.create(benchmarkConfig)
    }

    private fun createBenchmarkSolverConfig(
        name: String,
        chType: ConstructionHeuristicType,
        lsType: LocalSearchType,
        timeLimit: Duration,
    ): SolverBenchmarkConfig =
        SolverBenchmarkConfig()
            .withName(name)
            .withSolverConfig(
                SolverConfig()
                    .withSolutionClass(ScheduleSolution::class.java)
                    .withEntityClasses(AppointmentPlanning::class.java)
                    .withConstraintProviderClass(AppointmentConstraintProvider::class.java)
                    .withTerminationConfig(
                        TerminationConfig().withSpentLimit(timeLimit)
                    )
                    .withPhases(
                        ConstructionHeuristicPhaseConfig()
                            .withConstructionHeuristicType(chType),
                        LocalSearchPhaseConfig()
                            .withLocalSearchType(lsType),
                    )
            )
}
