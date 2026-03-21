package io.bluetape4k.scheduling.appointment.solver.constraint

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.Joiners
import io.bluetape4k.scheduling.appointment.model.dto.BreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicClosureRecord
import io.bluetape4k.scheduling.appointment.model.dto.ClinicDefaultBreakTimeRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorAbsenceRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorScheduleRecord
import io.bluetape4k.scheduling.appointment.model.dto.HolidayRecord
import io.bluetape4k.scheduling.appointment.model.dto.OperatingHoursRecord
import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import io.bluetape4k.scheduling.appointment.solver.domain.ClinicFact
import io.bluetape4k.scheduling.appointment.solver.domain.DoctorFact
import io.bluetape4k.scheduling.appointment.solver.domain.EquipmentFact
import io.bluetape4k.scheduling.appointment.solver.domain.TreatmentFact

/**
 * Hard Constraints (H1~H10) for appointment scheduling.
 *
 * 모든 Hard Constraint 위반 시 [HardSoftScore.ONE_HARD] 페널티를 부과합니다.
 */
object HardConstraints {

    /**
     * ClinicFact.maxConcurrentPatients의 기본값.
     * Quad join 제한으로 ClinicFact를 직접 조인할 수 없을 때 사용합니다.
     */
    private const val DEFAULT_CLINIC_MAX_CONCURRENT = 1

    // ----------------------------------------------------------------
    // H1: 예약 시간이 해당 요일 영업시간(isActive=true) 내에 있어야 함
    // ----------------------------------------------------------------
    fun withinOperatingHours(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.appointmentDate != null && it.startTime != null }
            .ifNotExists(
                OperatingHoursRecord::class.java,
                Joiners.equal(
                    { appt -> appt.appointmentDate!!.dayOfWeek },
                    { oh -> oh.dayOfWeek },
                ),
                Joiners.filtering { appt, oh ->
                    oh.isActive &&
                        appt.startTime!! >= oh.openTime &&
                        appt.endTime!! <= oh.closeTime
                },
            )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H1: withinOperatingHours")

    // ----------------------------------------------------------------
    // H2: 예약 시간이 의사 근무 스케줄 내에 있어야 함
    // ----------------------------------------------------------------
    fun withinDoctorSchedule(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.doctorId != null && it.appointmentDate != null && it.startTime != null }
            .ifNotExists(
                DoctorScheduleRecord::class.java,
                Joiners.equal(
                    { appt -> appt.doctorId!! },
                    { ds -> ds.doctorId },
                ),
                Joiners.equal(
                    { appt -> appt.appointmentDate!!.dayOfWeek },
                    { ds -> ds.dayOfWeek },
                ),
                Joiners.filtering { appt, ds ->
                    appt.startTime!! >= ds.startTime && appt.endTime!! <= ds.endTime
                },
            )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H2: withinDoctorSchedule")

    // ----------------------------------------------------------------
    // H3: 의사 부재 기간과 겹치지 않아야 함
    //     startTime==null이면 전일 부재
    // ----------------------------------------------------------------
    fun noDoctorAbsenceConflict(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.doctorId != null && it.appointmentDate != null && it.startTime != null }
            .ifExists(
                DoctorAbsenceRecord::class.java,
                Joiners.equal(
                    { appt -> appt.doctorId!! },
                    { abs -> abs.doctorId },
                ),
                Joiners.equal(
                    { appt -> appt.appointmentDate!! },
                    { abs -> abs.absenceDate },
                ),
                Joiners.filtering { appt, abs ->
                    // 전일 부재이거나 시간 구간이 겹치는 경우
                    val absStart = abs.startTime
                    val absEnd = abs.endTime
                    absStart == null ||
                        (absEnd != null && appt.startTime!! < absEnd && absStart < appt.endTime!!)
                },
            )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H3: noDoctorAbsenceConflict")

    // ----------------------------------------------------------------
    // H4a: 요일별 휴식시간과 겹치지 않아야 함
    // ----------------------------------------------------------------
    fun noBreakTimeConflict(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.appointmentDate != null && it.startTime != null }
            .ifExists(
                BreakTimeRecord::class.java,
                Joiners.equal(
                    { appt -> appt.appointmentDate!!.dayOfWeek },
                    { bt -> bt.dayOfWeek },
                ),
                Joiners.filtering { appt, bt ->
                    appt.startTime!! < bt.endTime && bt.startTime < appt.endTime!!
                },
            )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H4a: noBreakTimeConflict")

    // ----------------------------------------------------------------
    // H4b: 기본 휴식시간과 겹치지 않아야 함
    // ----------------------------------------------------------------
    fun noDefaultBreakTimeConflict(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.appointmentDate != null && it.startTime != null }
            .ifExists(
                ClinicDefaultBreakTimeRecord::class.java,
                Joiners.filtering { appt: AppointmentPlanning, dbt: ClinicDefaultBreakTimeRecord ->
                    appt.startTime!! < dbt.endTime && dbt.startTime < appt.endTime!!
                },
            )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H4b: noDefaultBreakTimeConflict")

    // ----------------------------------------------------------------
    // H5: 임시휴진(전일 또는 부분)과 겹치지 않아야 함
    // ----------------------------------------------------------------
    fun noClinicClosureConflict(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.appointmentDate != null && it.startTime != null }
            .ifExists(
                ClinicClosureRecord::class.java,
                Joiners.equal(
                    { appt -> appt.appointmentDate!! },
                    { cl -> cl.closureDate },
                ),
                Joiners.filtering { appt, cl ->
                    val clStart = cl.startTime
                    val clEnd = cl.endTime
                    cl.isFullDay ||
                        (clStart != null && clEnd != null &&
                            appt.startTime!! < clEnd && clStart < appt.endTime!!)
                },
            )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H5: noClinicClosureConflict")

    // ----------------------------------------------------------------
    // H6: 공휴일에 예약 불가 (clinic.openOnHolidays=false인 경우)
    // ----------------------------------------------------------------
    fun noHolidayConflict(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.appointmentDate != null }
            .join(ClinicFact::class.java)
            .filter { _, clinic -> !clinic.openOnHolidays }
            .ifExists(
                HolidayRecord::class.java,
                Joiners.equal(
                    { appt, _ -> appt.appointmentDate!! },
                    { h -> h.holidayDate },
                ),
            )
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H6: noHolidayConflict")

    // ----------------------------------------------------------------
    // H7: 같은 의사의 동시 환자 수 제한
    //     resolveMaxConcurrent = treatmentMax ?: doctorMax ?: clinicMax
    //
    //     maxConcurrent가 1인 의사에 대해 시간이 겹치는 예약 쌍이 있으면 위반.
    //     Timefold Quad(4-way join) 제한으로 ClinicFact는 ifExists로 처리.
    // ----------------------------------------------------------------
    fun maxConcurrentPatientsPerDoctor(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.doctorId != null && it.appointmentDate != null && it.startTime != null }
            .join(
                AppointmentPlanning::class.java,
                Joiners.equal(
                    AppointmentPlanning::doctorId,
                    AppointmentPlanning::doctorId,
                ),
                Joiners.equal(
                    AppointmentPlanning::appointmentDate,
                    AppointmentPlanning::appointmentDate,
                ),
                Joiners.overlapping(
                    AppointmentPlanning::startTime,
                    AppointmentPlanning::endTime,
                    AppointmentPlanning::startTime,
                    AppointmentPlanning::endTime,
                ),
                Joiners.lessThan(
                    AppointmentPlanning::id,
                    AppointmentPlanning::id,
                ),
            )
            .join(
                DoctorFact::class.java,
                Joiners.equal(
                    { appt, _ -> appt.doctorId!! },
                    DoctorFact::id,
                ),
            )
            .join(
                TreatmentFact::class.java,
                Joiners.equal(
                    { appt, _, _ -> appt.treatmentTypeId },
                    TreatmentFact::id,
                ),
            )
            .filter { appt, other, doctor, treatment ->
                // treatmentMax와 doctorMax로 먼저 판단.
                // 둘 다 null이면 clinicMax 기본값(1)을 적용하여 동시 예약 불허.
                val maxConcurrent = resolveMaxConcurrent(
                    DEFAULT_CLINIC_MAX_CONCURRENT,
                    doctor.maxConcurrentPatients,
                    treatment.maxConcurrentPatients,
                )
                maxConcurrent < 2
            }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H7: maxConcurrentPatientsPerDoctor")

    // ----------------------------------------------------------------
    // H8: 같은 시간에 장비 사용 수가 quantity 이하
    //
    //     동일 장비 + 동일 날짜 + 시간 겹침인 예약 쌍을 세고,
    //     quantity=1이면 동시 사용 불가
    // ----------------------------------------------------------------
    fun equipmentAvailability(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter {
                it.equipmentId != null &&
                    it.appointmentDate != null &&
                    it.startTime != null
            }
            .join(
                AppointmentPlanning::class.java,
                Joiners.equal(
                    AppointmentPlanning::equipmentId,
                    AppointmentPlanning::equipmentId,
                ),
                Joiners.equal(
                    AppointmentPlanning::appointmentDate,
                    AppointmentPlanning::appointmentDate,
                ),
                Joiners.overlapping(
                    AppointmentPlanning::startTime,
                    AppointmentPlanning::endTime,
                    AppointmentPlanning::startTime,
                    AppointmentPlanning::endTime,
                ),
                Joiners.lessThan(
                    AppointmentPlanning::id,
                    AppointmentPlanning::id,
                ),
            )
            .join(
                EquipmentFact::class.java,
                Joiners.equal(
                    { appt, _ -> appt.equipmentId!! },
                    EquipmentFact::id,
                ),
            )
            .filter { _, _, equipment ->
                // 쌍이 존재 = 최소 2개 동시 사용, quantity < 2이면 위반
                equipment.quantity < 2
            }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H8: equipmentAvailability")

    // ----------------------------------------------------------------
    // H9: 의사의 providerType이 진료의 requiredProviderType과 일치
    // ----------------------------------------------------------------
    fun providerTypeMatch(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.doctorId != null }
            .join(
                DoctorFact::class.java,
                Joiners.equal(
                    { appt -> appt.doctorId!! },
                    DoctorFact::id,
                ),
            )
            .filter { appt, doctor ->
                appt.requiredProviderType != doctor.providerType
            }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H9: providerTypeMatch")

    // ----------------------------------------------------------------
    // H10: 할당된 의사가 해당 클리닉 소속
    // ----------------------------------------------------------------
    fun doctorBelongsToClinic(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { it.doctorId != null }
            .join(
                DoctorFact::class.java,
                Joiners.equal(
                    { appt -> appt.doctorId!! },
                    DoctorFact::id,
                ),
            )
            .filter { appt, doctor ->
                appt.clinicId != doctor.clinicId
            }
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("H10: doctorBelongsToClinic")

    /**
     * 3-level cascade로 maxConcurrent 값을 결정합니다.
     * treatmentMax > doctorMax > clinicMax 순으로 우선합니다.
     */
    private fun resolveMaxConcurrent(
        clinicMax: Int,
        doctorMax: Int?,
        treatmentMax: Int?,
    ): Int = treatmentMax ?: doctorMax ?: clinicMax
}
