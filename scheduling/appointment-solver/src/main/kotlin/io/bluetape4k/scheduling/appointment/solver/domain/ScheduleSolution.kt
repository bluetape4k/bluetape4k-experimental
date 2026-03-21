package io.bluetape4k.scheduling.appointment.solver.domain

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty
import ai.timefold.solver.core.api.domain.solution.PlanningScore
import ai.timefold.solver.core.api.domain.solution.PlanningSolution
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
import ai.timefold.solver.core.api.domain.solution.ProblemFactProperty
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import io.bluetape4k.scheduling.appointment.model.dto.BreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicClosureRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicDefaultBreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorAbsenceRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorScheduleRecord
import io.bluetape4k.scheduling.appointment.model.dto.HolidayRecord
import io.bluetape4k.scheduling.appointment.model.dto.OperatingHoursRecord
import io.bluetape4k.scheduling.appointment.model.dto.TreatmentEquipmentRecord
import java.time.LocalDate
import java.time.LocalTime

/**
 * Solver의 Planning Solution.
 *
 * 전체 문제 정의(Problem Facts)와 Planning Entity 목록,
 * 그리고 Value Range Provider를 포함합니다.
 */
@PlanningSolution
class ScheduleSolution(

    @field:ProblemFactProperty
    val clinic: ClinicFact = ClinicFact(0, 30, 1, false),

    @field:ProblemFactCollectionProperty
    val doctors: List<DoctorFact> = emptyList(),

    @field:ProblemFactCollectionProperty
    val treatments: List<TreatmentFact> = emptyList(),

    @field:ProblemFactCollectionProperty
    val equipments: List<EquipmentFact> = emptyList(),

    @field:ProblemFactCollectionProperty
    val operatingHours: List<OperatingHoursRecord> = emptyList(),

    @field:ProblemFactCollectionProperty
    val doctorSchedules: List<DoctorScheduleRecord> = emptyList(),

    @field:ProblemFactCollectionProperty
    val doctorAbsences: List<DoctorAbsenceRecord> = emptyList(),

    @field:ProblemFactCollectionProperty
    val breakTimes: List<BreakTimeRecord> = emptyList(),

    @field:ProblemFactCollectionProperty
    val defaultBreakTimes: List<ClinicDefaultBreakTimeRecord> = emptyList(),

    @field:ProblemFactCollectionProperty
    val closures: List<ClinicClosureRecord> = emptyList(),

    @field:ProblemFactCollectionProperty
    val holidays: List<HolidayRecord> = emptyList(),

    @field:ProblemFactCollectionProperty
    val treatmentEquipments: List<TreatmentEquipmentRecord> = emptyList(),

    // --- Value Range Providers ---

    @field:ValueRangeProvider(id = "doctorRange")
    @field:ProblemFactCollectionProperty
    val doctorIds: List<Long> = emptyList(),

    @field:ValueRangeProvider(id = "dateRange")
    @field:ProblemFactCollectionProperty
    val dateRange: List<LocalDate> = emptyList(),

    @field:ValueRangeProvider(id = "timeSlotRange")
    @field:ProblemFactCollectionProperty
    val timeSlots: List<LocalTime> = emptyList(),

    // --- Planning Entities ---

    @field:PlanningEntityCollectionProperty
    val appointments: List<AppointmentPlanning> = emptyList(),

    @field:PlanningScore
    var score: HardSoftScore? = null,
) {
    /** Timefold requires no-arg constructor */
    @Suppress("unused")
    constructor() : this(clinic = ClinicFact(0, 30, 1, false))
}
