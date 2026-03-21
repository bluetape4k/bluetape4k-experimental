package io.bluetape4k.scheduling.appointment.solver.service

import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.solver.SolverConfig
import io.bluetape4k.scheduling.appointment.solver.constraint.AppointmentConstraintProvider
import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import io.bluetape4k.scheduling.appointment.solver.domain.ScheduleSolution
import java.time.Duration

/**
 * Timefold SolverFactory 생성 유틸리티.
 */
object AppointmentSolverConfig {

    fun createFactory(timeLimit: Duration = Duration.ofSeconds(30)): SolverFactory<ScheduleSolution> =
        SolverFactory.create(
            SolverConfig()
                .withSolutionClass(ScheduleSolution::class.java)
                .withEntityClasses(AppointmentPlanning::class.java)
                .withConstraintProviderClass(AppointmentConstraintProvider::class.java)
                .withTerminationSpentLimit(timeLimit)
        )
}
