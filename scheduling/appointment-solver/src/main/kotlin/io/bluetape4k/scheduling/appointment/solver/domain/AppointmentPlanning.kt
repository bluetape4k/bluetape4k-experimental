package io.bluetape4k.scheduling.appointment.solver.domain

import ai.timefold.solver.core.api.domain.entity.PlanningEntity
import ai.timefold.solver.core.api.domain.entity.PlanningPin
import ai.timefold.solver.core.api.domain.variable.PlanningVariable
import io.bluetape4k.scheduling.appointment.solver.move.AppointmentDifficultyComparator
import java.time.LocalDate
import java.time.LocalTime

/**
 * Solver의 Planning Entity.
 *
 * [doctorId], [appointmentDate], [startTime] 세 가지가 Planning Variable이며,
 * Solver가 최적 값을 탐색합니다.
 *
 * [pinned]가 true인 예약(CONFIRMED 등)은 Solver가 이동하지 않습니다.
 */
@PlanningEntity(difficultyComparatorClass = AppointmentDifficultyComparator::class)
class AppointmentPlanning(
    val id: Long = 0L,
    val clinicId: Long = 0L,
    val treatmentTypeId: Long = 0L,
    val equipmentId: Long? = null,
    val patientName: String = "",
    val durationMinutes: Int = 0,
    val requiredProviderType: String = "DOCTOR",
    val requiresEquipment: Boolean = false,
    val originalDoctorId: Long? = null,
    val requestedDate: LocalDate? = null,

    @field:PlanningPin
    val pinned: Boolean = false,

    @field:PlanningVariable(valueRangeProviderRefs = ["doctorRange"])
    var doctorId: Long? = null,

    @field:PlanningVariable(valueRangeProviderRefs = ["dateRange"])
    var appointmentDate: LocalDate? = null,

    @field:PlanningVariable(valueRangeProviderRefs = ["timeSlotRange"])
    var startTime: LocalTime? = null,
) {
    val endTime: LocalTime?
        get() = startTime?.plusMinutes(durationMinutes.toLong())

    /** Timefold requires no-arg constructor */
    @Suppress("unused")
    constructor() : this(id = 0L)

    override fun toString(): String =
        "AppointmentPlanning(id=$id, doctor=$doctorId, date=$appointmentDate, time=$startTime~$endTime, pinned=$pinned)"
}
