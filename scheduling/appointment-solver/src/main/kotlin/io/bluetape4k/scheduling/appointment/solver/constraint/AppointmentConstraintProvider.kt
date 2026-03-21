package io.bluetape4k.scheduling.appointment.solver.constraint

import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider

/**
 * 예약 스케줄링의 모든 제약 조건을 조합하는 ConstraintProvider.
 *
 * Hard Constraints (H1~H10): 위반 시 해가 무효
 * Soft Constraints (S1~S6): 최적화 목표
 */
class AppointmentConstraintProvider : ConstraintProvider {

    override fun defineConstraints(factory: ConstraintFactory): Array<Constraint> = arrayOf(
        // Hard Constraints
        HardConstraints.withinOperatingHours(factory),
        HardConstraints.withinDoctorSchedule(factory),
        HardConstraints.noDoctorAbsenceConflict(factory),
        HardConstraints.noBreakTimeConflict(factory),
        HardConstraints.noDefaultBreakTimeConflict(factory),
        HardConstraints.noClinicClosureConflict(factory),
        HardConstraints.noHolidayConflict(factory),
        HardConstraints.maxConcurrentPatientsPerDoctor(factory),
        HardConstraints.equipmentAvailability(factory),
        HardConstraints.providerTypeMatch(factory),
        HardConstraints.doctorBelongsToClinic(factory),

        // Soft Constraints
        SoftConstraints.doctorLoadBalance(factory),
        SoftConstraints.minimizeGaps(factory),
        SoftConstraints.preferOriginalDoctor(factory),
        SoftConstraints.preferEarlySlot(factory),
        SoftConstraints.equipmentUtilization(factory),
        SoftConstraints.preferRequestedDate(factory),
    )
}
