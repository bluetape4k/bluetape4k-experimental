package io.bluetape4k.scheduling.appointment.solver.converter

import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.model.dto.BreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicClosureRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicDefaultBreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorAbsenceRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorScheduleRecord
import io.bluetape4k.scheduling.appointment.model.dto.EquipmentRecord
import io.bluetape4k.scheduling.appointment.model.dto.HolidayRecord
import io.bluetape4k.scheduling.appointment.model.dto.OperatingHoursRecord
import io.bluetape4k.scheduling.appointment.model.dto.TreatmentEquipmentRecord
import io.bluetape4k.scheduling.appointment.model.dto.TreatmentTypeRecord
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import io.bluetape4k.scheduling.appointment.solver.domain.ClinicFact
import io.bluetape4k.scheduling.appointment.solver.domain.DoctorFact
import io.bluetape4k.scheduling.appointment.solver.domain.EquipmentFact
import io.bluetape4k.scheduling.appointment.solver.domain.ScheduleSolution
import io.bluetape4k.scheduling.appointment.solver.domain.TreatmentFact
import io.bluetape4k.scheduling.appointment.solver.domain.generateTimeSlots
import java.time.LocalDate
import java.time.LocalTime

/**
 * appointment-core의 Record 클래스와 Solver Planning 도메인 간 변환을 담당합니다.
 */
object SolutionConverter: KLogging() {

    private val PINNED_STATUSES = AppointmentState.PINNED_STATUSES

    /**
     * core Record들을 조합하여 [ScheduleSolution]을 생성합니다.
     */
    fun buildSolution(
        clinic: ClinicRecord,
        doctors: List<DoctorRecord>,
        appointments: List<AppointmentRecord>,
        treatments: List<TreatmentTypeRecord>,
        equipments: List<EquipmentRecord>,
        operatingHours: List<OperatingHoursRecord>,
        doctorSchedules: List<DoctorScheduleRecord>,
        doctorAbsences: List<DoctorAbsenceRecord>,
        breakTimes: List<BreakTimeRecord>,
        defaultBreakTimes: List<ClinicDefaultBreakTimeRecord>,
        closures: List<ClinicClosureRecord>,
        holidays: List<HolidayRecord>,
        treatmentEquipments: List<TreatmentEquipmentRecord>,
        dateRange: ClosedRange<LocalDate>,
    ): ScheduleSolution {
        val clinicFact = clinic.toClinicFact()
        val doctorFacts = doctors.map { it.toDoctorFact() }
        val treatmentFacts = treatments.map { it.toTreatmentFact() }
        val equipmentFacts = equipments.map { it.toEquipmentFact() }

        val treatmentMap = treatments.associateBy { it.id }

        val planningEntities = appointments.map { appointment ->
            val treatment = treatmentMap[appointment.treatmentTypeId]
            val isPinned = appointment.status in PINNED_STATUSES

            AppointmentPlanning(
                id = appointment.id ?: 0L,
                clinicId = appointment.clinicId,
                treatmentTypeId = appointment.treatmentTypeId,
                equipmentId = appointment.equipmentId,
                patientName = appointment.patientName,
                durationMinutes = treatment?.defaultDurationMinutes ?: 30,
                requiredProviderType = treatment?.requiredProviderType ?: "DOCTOR",
                requiresEquipment = treatment?.requiresEquipment ?: false,
                originalDoctorId = appointment.doctorId,
                requestedDate = appointment.appointmentDate,
                pinned = isPinned,
                doctorId = appointment.doctorId,
                appointmentDate = appointment.appointmentDate,
                startTime = appointment.startTime,
            )
        }

        // 날짜 범위 생성
        val dates = generateDateList(dateRange)

        // 이산 시간 슬롯 생성 (모든 영업시간의 최소 open ~ 최대 close)
        val timeSlots = generateTimeSlotsFromOperatingHours(operatingHours, clinic.slotDurationMinutes)

        return ScheduleSolution(
            clinic = clinicFact,
            doctors = doctorFacts,
            treatments = treatmentFacts,
            equipments = equipmentFacts,
            operatingHours = operatingHours,
            doctorSchedules = doctorSchedules,
            doctorAbsences = doctorAbsences,
            breakTimes = breakTimes,
            defaultBreakTimes = defaultBreakTimes,
            closures = closures,
            holidays = holidays,
            treatmentEquipments = treatmentEquipments,
            doctorIds = doctorFacts.map { it.id },
            dateRange = dates,
            timeSlots = timeSlots,
            appointments = planningEntities,
        )
    }

    /**
     * Solver 결과를 [AppointmentRecord] 목록으로 변환합니다.
     * pinned 되지 않은 예약만 반환합니다.
     */
    fun extractResults(
        solution: ScheduleSolution,
        originalAppointments: Map<Long, AppointmentRecord>,
    ): List<AppointmentRecord> =
        solution.appointments
            .filter { !it.pinned && it.doctorId != null && it.appointmentDate != null && it.startTime != null }
            .map { planning ->
                val original = originalAppointments[planning.id]
                AppointmentRecord(
                    id = planning.id,
                    clinicId = planning.clinicId,
                    doctorId = planning.doctorId!!,
                    treatmentTypeId = planning.treatmentTypeId,
                    equipmentId = planning.equipmentId,
                    consultationTopicId = original?.consultationTopicId,
                    consultationMethod = original?.consultationMethod,
                    rescheduleFromId = original?.rescheduleFromId,
                    patientName = planning.patientName,
                    patientPhone = original?.patientPhone,
                    patientExternalId = original?.patientExternalId,
                    appointmentDate = planning.appointmentDate!!,
                    startTime = planning.startTime!!,
                    endTime = planning.endTime!!,
                    status = original?.status ?: AppointmentState.REQUESTED,
                )
            }

    private fun generateDateList(range: ClosedRange<LocalDate>): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = range.start
        while (current <= range.endInclusive) {
            dates.add(current)
            current = current.plusDays(1)
        }
        return dates
    }

    private fun generateTimeSlotsFromOperatingHours(
        operatingHours: List<OperatingHoursRecord>,
        slotDurationMinutes: Int,
    ): List<LocalTime> {
        if (operatingHours.isEmpty()) return emptyList()

        val earliestOpen = operatingHours.minOf { it.openTime }
        val latestClose = operatingHours.maxOf { it.closeTime }
        return generateTimeSlots(earliestOpen, latestClose, slotDurationMinutes)
    }

    private fun ClinicRecord.toClinicFact() = ClinicFact(
        id = id ?: 0L,
        slotDurationMinutes = slotDurationMinutes,
        maxConcurrentPatients = maxConcurrentPatients,
        openOnHolidays = openOnHolidays,
    )

    private fun DoctorRecord.toDoctorFact() = DoctorFact(
        id = id ?: 0L,
        clinicId = clinicId,
        providerType = providerType,
        maxConcurrentPatients = maxConcurrentPatients,
    )

    private fun TreatmentTypeRecord.toTreatmentFact() = TreatmentFact(
        id = id ?: 0L,
        defaultDurationMinutes = defaultDurationMinutes,
        requiredProviderType = requiredProviderType,
        requiresEquipment = requiresEquipment,
        maxConcurrentPatients = maxConcurrentPatients,
    )

    private fun EquipmentRecord.toEquipmentFact() = EquipmentFact(
        id = id ?: 0L,
        usageDurationMinutes = usageDurationMinutes,
        quantity = quantity,
    )
}
