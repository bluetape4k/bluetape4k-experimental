package io.bluetape4k.scheduling.appointment.service

import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.scheduling.appointment.model.tables.BreakTimes
import io.bluetape4k.scheduling.appointment.model.tables.ClinicClosures
import io.bluetape4k.scheduling.appointment.model.tables.Clinics
import io.bluetape4k.scheduling.appointment.model.tables.DoctorAbsences
import io.bluetape4k.scheduling.appointment.model.tables.DoctorSchedules
import io.bluetape4k.scheduling.appointment.model.tables.Doctors
import io.bluetape4k.scheduling.appointment.model.tables.Equipments
import io.bluetape4k.scheduling.appointment.model.tables.Holidays
import io.bluetape4k.scheduling.appointment.model.tables.OperatingHoursTable
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentEquipments
import io.bluetape4k.scheduling.appointment.model.tables.TreatmentTypes
import io.bluetape4k.scheduling.appointment.service.model.AvailableSlot
import io.bluetape4k.scheduling.appointment.service.model.SlotQuery
import io.bluetape4k.scheduling.appointment.service.model.TimeRange
import io.bluetape4k.scheduling.appointment.service.model.computeEffectiveRanges
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalTime

/**
 * 예약 가능한 슬롯을 계산하는 서비스.
 *
 * JDBC Exposed 트랜잭션을 사용하며, Spring Bean이 아닌 일반 클래스입니다.
 */
class SlotCalculationService {
    /**
     * 주어진 조건에 맞는 예약 가능 슬롯 목록을 반환합니다.
     */
    fun findAvailableSlots(query: SlotQuery): List<AvailableSlot> =
        transaction {
            // 1. Load Clinic
            val clinicRow =
                Clinics.selectAll().where { Clinics.id eq query.clinicId }.firstOrNull()
                    ?: return@transaction emptyList()

            val slotDurationMinutes = clinicRow[Clinics.slotDurationMinutes]
            val clinicMaxConcurrent = clinicRow[Clinics.maxConcurrentPatients]
            val openOnHolidays = clinicRow[Clinics.openOnHolidays]

            // 1-1. Check if date is a national holiday
            if (!openOnHolidays) {
                val isHoliday =
                    Holidays
                        .selectAll()
                        .where { Holidays.holidayDate eq query.date }
                        .count() > 0
                if (isHoliday) {
                    return@transaction emptyList()
                }
            }

            // 2. Check ClinicClosures for full-day closure
            val closures =
                ClinicClosures
                    .selectAll()
                    .where {
                        (ClinicClosures.clinicId eq query.clinicId) and
                            (ClinicClosures.closureDate eq query.date)
                    }.toList()

            val hasFullDayClosure = closures.any { it[ClinicClosures.isFullDay] }
            if (hasFullDayClosure) {
                return@transaction emptyList()
            }

            // 3. Get OperatingHours for clinic + dayOfWeek
            val dayOfWeek = query.date.dayOfWeek
            val opHours =
                OperatingHoursTable
                    .selectAll()
                    .where {
                        (OperatingHoursTable.clinicId eq query.clinicId) and
                            (OperatingHoursTable.dayOfWeek eq dayOfWeek) and
                            (OperatingHoursTable.isActive eq true)
                    }.firstOrNull() ?: return@transaction emptyList()

            val clinicOpen = opHours[OperatingHoursTable.openTime]
            val clinicClose = opHours[OperatingHoursTable.closeTime]

            // 4. Get BreakTimes for clinic + dayOfWeek
            val breakTimeRanges =
                BreakTimes
                    .selectAll()
                    .where {
                        (BreakTimes.clinicId eq query.clinicId) and
                            (BreakTimes.dayOfWeek eq dayOfWeek)
                    }.map { TimeRange(it[BreakTimes.startTime], it[BreakTimes.endTime]) }

            // 5. Get partial closures (isFullDay=false)
            val partialClosureRanges =
                closures
                    .filter { !it[ClinicClosures.isFullDay] }
                    .mapNotNull { row ->
                        val start = row[ClinicClosures.startTime]
                        val end = row[ClinicClosures.endTime]
                        if (start != null && end != null) TimeRange(start, end) else null
                    }

            // 6. Get DoctorSchedules for doctor + dayOfWeek
            val doctorSchedule =
                DoctorSchedules
                    .selectAll()
                    .where {
                        (DoctorSchedules.doctorId eq query.doctorId) and
                            (DoctorSchedules.dayOfWeek eq dayOfWeek)
                    }.firstOrNull() ?: return@transaction emptyList()

            val doctorStart = doctorSchedule[DoctorSchedules.startTime]
            val doctorEnd = doctorSchedule[DoctorSchedules.endTime]

            // 7. Get DoctorAbsences for doctor + date
            val absences =
                DoctorAbsences
                    .selectAll()
                    .where {
                        (DoctorAbsences.doctorId eq query.doctorId) and
                            (DoctorAbsences.absenceDate eq query.date)
                    }.toList()

            val hasFullDayAbsence = absences.any { it[DoctorAbsences.startTime] == null }
            if (hasFullDayAbsence) {
                return@transaction emptyList()
            }

            val doctorAbsenceRanges =
                absences.mapNotNull { row ->
                    val start = row[DoctorAbsences.startTime]
                    val end = row[DoctorAbsences.endTime]
                    if (start != null && end != null) TimeRange(start, end) else null
                }

            // 8. Compute effective ranges
            val effectiveRanges =
                computeEffectiveRanges(
                    clinicOpen = clinicOpen,
                    clinicClose = clinicClose,
                    doctorStart = doctorStart,
                    doctorEnd = doctorEnd,
                    breakTimes = breakTimeRanges,
                    partialClosures = partialClosureRanges,
                    doctorAbsences = doctorAbsenceRanges
                )

            if (effectiveRanges.isEmpty()) {
                return@transaction emptyList()
            }

            // 9. Get TreatmentType -> determine duration
            val treatmentRow =
                TreatmentTypes
                    .selectAll()
                    .where {
                        TreatmentTypes.id eq query.treatmentTypeId
                    }.firstOrNull() ?: return@transaction emptyList()

            val duration = query.requestedDurationMinutes ?: treatmentRow[TreatmentTypes.defaultDurationMinutes]
            val requiresEquipment = treatmentRow[TreatmentTypes.requiresEquipment]
            val treatmentMaxConcurrent = treatmentRow[TreatmentTypes.maxConcurrentPatients]
            val requiredProviderType = treatmentRow[TreatmentTypes.requiredProviderType]

            // Load doctor's maxConcurrentPatients and providerType
            val doctorRow =
                Doctors
                    .selectAll()
                    .where {
                        Doctors.id eq query.doctorId
                    }.firstOrNull() ?: return@transaction emptyList()

            // 9-1. Validate provider type matches treatment requirement
            val doctorProviderType = doctorRow[Doctors.providerType]
            if (doctorProviderType != requiredProviderType) {
                return@transaction emptyList()
            }

            val doctorMaxConcurrent = doctorRow[Doctors.maxConcurrentPatients]

            // 12. Resolve maxConcurrent
            val maxConcurrent = resolveMaxConcurrent(clinicMaxConcurrent, doctorMaxConcurrent, treatmentMaxConcurrent)

            // 10. Generate slot candidates from effective ranges at slotDurationMinutes intervals
            val slotCandidates = mutableListOf<TimeRange>()
            for (range in effectiveRanges) {
                var current = range.start
                while (true) {
                    val slotEnd = current.plusMinutes(duration.toLong())
                    if (slotEnd > range.end) break
                    slotCandidates.add(TimeRange(current, slotEnd))
                    current = current.plusMinutes(slotDurationMinutes.toLong())
                }
            }

            // 14. If treatment requires equipment, load required equipment IDs and their quantities
            val requiredEquipment =
                if (requiresEquipment) {
                    TreatmentEquipments
                        .selectAll()
                        .where {
                            TreatmentEquipments.treatmentTypeId eq query.treatmentTypeId
                        }.map { it[TreatmentEquipments.equipmentId].value }
                } else {
                    emptyList()
                }

            val equipmentQuantities =
                if (requiredEquipment.isNotEmpty()) {
                    Equipments
                        .selectAll()
                        .where {
                            Equipments.id inList requiredEquipment
                        }.associate { it[Equipments.id].value to it[Equipments.quantity] }
                } else {
                    emptyMap()
                }

            // Process each candidate slot
            val availableSlots = mutableListOf<AvailableSlot>()

            for (candidate in slotCandidates) {
                // 11. Count existing appointments that overlap
                val overlappingCount =
                    countOverlappingAppointments(
                        doctorId = query.doctorId,
                        date = query.date,
                        slotStart = candidate.start,
                        slotEnd = candidate.end
                    )

                // 13. Filter slots where existing count < maxConcurrent
                if (overlappingCount >= maxConcurrent) continue

                // 14-15. Check equipment availability
                val availableEquipmentIds =
                    if (requiresEquipment && requiredEquipment.isNotEmpty()) {
                        val available = mutableListOf<Long>()
                        for (eqId in requiredEquipment) {
                            val quantity = equipmentQuantities[eqId] ?: 0
                            val usedCount = countEquipmentUsage(eqId, query.date, candidate.start, candidate.end)
                            if (usedCount < quantity) {
                                available.add(eqId)
                            }
                        }
                        if (available.isEmpty()) continue
                        available
                    } else {
                        emptyList()
                    }

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

    /**
     * 특정 의사의 특정 날짜/시간에 겹치는 예약 수를 카운트합니다.
     * CANCELLED, NO_SHOW 상태는 제외합니다.
     */
    private fun countOverlappingAppointments(
        doctorId: Long,
        date: java.time.LocalDate,
        slotStart: LocalTime,
        slotEnd: LocalTime,
    ): Int =
        Appointments
            .selectAll()
            .where {
                (Appointments.doctorId eq doctorId) and
                    (Appointments.appointmentDate eq date) and
                    (Appointments.startTime less slotEnd) and
                    (Appointments.endTime greater slotStart) and
                    (Appointments.status neq "CANCELLED") and
                    (Appointments.status neq "NO_SHOW")
            }.count()
            .toInt()

    /**
     * 특정 장비의 특정 날짜/시간에 사용 중인 예약 수를 카운트합니다.
     */
    private fun countEquipmentUsage(
        equipmentId: Long,
        date: java.time.LocalDate,
        slotStart: LocalTime,
        slotEnd: LocalTime,
    ): Int =
        Appointments
            .selectAll()
            .where {
                (Appointments.equipmentId eq equipmentId) and
                    (Appointments.appointmentDate eq date) and
                    (Appointments.startTime less slotEnd) and
                    (Appointments.endTime greater slotStart) and
                    (Appointments.status neq "CANCELLED") and
                    (Appointments.status neq "NO_SHOW")
            }.count()
            .toInt()
}
