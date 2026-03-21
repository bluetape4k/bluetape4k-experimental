package io.bluetape4k.scheduling.appointment.service

import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import io.bluetape4k.scheduling.appointment.repository.ClinicRepository
import io.bluetape4k.scheduling.appointment.repository.DoctorRepository
import io.bluetape4k.scheduling.appointment.repository.HolidayRepository
import io.bluetape4k.scheduling.appointment.repository.TreatmentTypeRepository
import io.bluetape4k.scheduling.appointment.service.model.AvailableSlot
import io.bluetape4k.scheduling.appointment.service.model.SlotQuery
import io.bluetape4k.scheduling.appointment.service.model.TimeRange
import io.bluetape4k.scheduling.appointment.service.model.computeEffectiveRanges
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * 예약 가능한 슬롯을 계산하는 서비스.
 *
 * JDBC Exposed 트랜잭션을 사용하며, Spring Bean이 아닌 일반 클래스입니다.
 */
class SlotCalculationService(
    private val clinicRepository: ClinicRepository = ClinicRepository(),
    private val doctorRepository: DoctorRepository = DoctorRepository(),
    private val treatmentTypeRepository: TreatmentTypeRepository = TreatmentTypeRepository(),
    private val appointmentRepository: AppointmentRepository = AppointmentRepository(),
    private val holidayRepository: HolidayRepository = HolidayRepository(),
) {
    companion object : KLogging()

    /**
     * 주어진 조건에 맞는 예약 가능 슬롯 목록을 반환합니다.
     */
    fun findAvailableSlots(query: SlotQuery): List<AvailableSlot> =
        transaction {
            // 1. Load Clinic
            val clinic = clinicRepository.findByIdOrNull(query.clinicId)
                ?: return@transaction emptyList()

            // 1-1. Check if date is a national holiday
            if (!clinic.openOnHolidays && holidayRepository.existsByDate(query.date)) {
                return@transaction emptyList()
            }

            // 2. Check ClinicClosures for full-day closure
            val closures = clinicRepository.findClosures(query.clinicId, query.date)
            if (closures.any { it.isFullDay }) {
                return@transaction emptyList()
            }

            // 3. Get OperatingHours for clinic + dayOfWeek
            val dayOfWeek = query.date.dayOfWeek
            val opHours = clinicRepository.findOperatingHours(query.clinicId, dayOfWeek)
                ?: return@transaction emptyList()

            // 4. Get BreakTimes for clinic + dayOfWeek
            val dayBreakRanges = clinicRepository.findBreakTimes(query.clinicId, dayOfWeek)
                .map { TimeRange(it.startTime, it.endTime) }

            // 4-1. 병원 기본 휴식시간 (모든 영업일에 동일 적용, 복수 설정 가능)
            val defaultBreakRanges = clinicRepository.findDefaultBreakTimes(query.clinicId)
                .map { TimeRange(it.startTime, it.endTime) }

            val breakTimeRanges = dayBreakRanges + defaultBreakRanges

            // 5. Get partial closures (isFullDay=false)
            val partialClosureRanges = closures
                .filter { !it.isFullDay }
                .mapNotNull { closure ->
                    val start = closure.startTime
                    val end = closure.endTime
                    if (start != null && end != null) TimeRange(start, end) else null
                }

            // 6. Get DoctorSchedule
            val doctorSchedule = doctorRepository.findSchedule(query.doctorId, dayOfWeek)
                ?: return@transaction emptyList()

            // 7. Get DoctorAbsences
            val absences = doctorRepository.findAbsences(query.doctorId, query.date)
            if (absences.any { it.startTime == null }) {
                return@transaction emptyList()
            }
            val doctorAbsenceRanges = absences.mapNotNull { absence ->
                val start = absence.startTime
                val end = absence.endTime
                if (start != null && end != null) TimeRange(start, end) else null
            }

            // 8. Compute effective ranges
            val effectiveRanges = computeEffectiveRanges(
                clinicOpen = opHours.openTime,
                clinicClose = opHours.closeTime,
                doctorStart = doctorSchedule.startTime,
                doctorEnd = doctorSchedule.endTime,
                breakTimes = breakTimeRanges,
                partialClosures = partialClosureRanges,
                doctorAbsences = doctorAbsenceRanges
            )
            if (effectiveRanges.isEmpty()) return@transaction emptyList()

            // 9. Get TreatmentType
            val treatment = treatmentTypeRepository.findByIdOrNull(query.treatmentTypeId)
                ?: return@transaction emptyList()

            val duration = query.requestedDurationMinutes ?: treatment.defaultDurationMinutes

            // 9-1. Load doctor and validate provider type
            val doctor = doctorRepository.findByIdOrNull(query.doctorId)
                ?: return@transaction emptyList()
            if (doctor.providerType != treatment.requiredProviderType) {
                return@transaction emptyList()
            }

            // 12. Resolve maxConcurrent
            val maxConcurrent = resolveMaxConcurrent(
                clinic.maxConcurrentPatients, doctor.maxConcurrentPatients, treatment.maxConcurrentPatients
            )

            // 10. Generate slot candidates from effective ranges at slotDurationMinutes intervals
            val slotCandidates = mutableListOf<TimeRange>()
            for (range in effectiveRanges) {
                var current = range.start
                while (true) {
                    val slotEnd = current.plusMinutes(duration.toLong())
                    if (slotEnd > range.end) break
                    slotCandidates.add(TimeRange(current, slotEnd))
                    current = current.plusMinutes(clinic.slotDurationMinutes.toLong())
                }
            }

            // 14. If treatment requires equipment, load required equipment IDs and quantities
            val requiredEquipment = if (treatment.requiresEquipment) {
                treatmentTypeRepository.findRequiredEquipmentIds(query.treatmentTypeId)
            } else emptyList()

            val equipmentQuantities = treatmentTypeRepository.findEquipmentQuantities(requiredEquipment)

            // Process each candidate slot
            val availableSlots = mutableListOf<AvailableSlot>()
            for (candidate in slotCandidates) {
                // 11. Count existing appointments that overlap
                val overlappingCount = appointmentRepository.countOverlapping(
                    query.doctorId, query.date, candidate.start, candidate.end
                )

                // 13. Filter slots where existing count < maxConcurrent
                if (overlappingCount >= maxConcurrent) continue

                // 14-15. Check equipment availability
                val availableEquipmentIds = if (treatment.requiresEquipment && requiredEquipment.isNotEmpty()) {
                    val available = mutableListOf<Long>()
                    for (eqId in requiredEquipment) {
                        val quantity = equipmentQuantities[eqId] ?: 0
                        val usedCount = appointmentRepository.countEquipmentUsage(eqId, query.date, candidate.start, candidate.end)
                        if (usedCount < quantity) available.add(eqId)
                    }
                    if (available.isEmpty()) continue
                    available
                } else emptyList()

                availableSlots.add(
                    AvailableSlot(
                        date = query.date,
                        startTime = candidate.start,
                        endTime = candidate.end,
                        doctorId = query.doctorId,
                        equipmentIds = availableEquipmentIds,
                        remainingCapacity = maxConcurrent - overlappingCount
                    )
                )
            }
            availableSlots
        }
}
