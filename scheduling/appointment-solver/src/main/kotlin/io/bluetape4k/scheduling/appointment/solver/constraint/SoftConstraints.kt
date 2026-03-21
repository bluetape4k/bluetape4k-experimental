package io.bluetape4k.scheduling.appointment.solver.constraint

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.Joiners
import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import java.time.temporal.ChronoUnit

object SoftConstraints {

    /**
     * S1: 의사 간 예약 수 분산 (MEDIUM weight=100).
     *
     * 같은 날짜에 같은 의사에게 할당된 예약 쌍마다 페널티를 부과하여 특정 의사에게 부하가 집중되는 것을 방지합니다.
     */
    fun doctorLoadBalance(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .join(
                AppointmentPlanning::class.java,
                Joiners.equal(AppointmentPlanning::doctorId),
                Joiners.equal(AppointmentPlanning::appointmentDate),
                Joiners.lessThan(AppointmentPlanning::id),
            )
            .penalize(HardSoftScore.ofSoft(100))
            .asConstraint("S1: doctorLoadBalance")

    /**
     * S2: 의사별 하루 스케줄의 빈 시간 간격 최소화 (LOW weight=10).
     *
     * 같은 의사/날짜에서 예약이 연속되지 않는 쌍의 간격(분)을 페널티로 부과합니다.
     */
    fun minimizeGaps(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .join(
                AppointmentPlanning::class.java,
                Joiners.equal(AppointmentPlanning::doctorId),
                Joiners.equal(AppointmentPlanning::appointmentDate),
                Joiners.lessThan(AppointmentPlanning::id),
            )
            .filter { a, b ->
                val aEnd = a.endTime
                val bStart = b.startTime
                val bEnd = b.endTime
                val aStart = a.startTime
                // 두 예약이 겹치지 않는 경우에만 gap 계산
                aEnd != null && bStart != null && bEnd != null && aStart != null &&
                    (aEnd < bStart || bEnd < aStart)
            }
            .penalize(HardSoftScore.ofSoft(10)) { a, b ->
                val aEnd = a.endTime
                val bStart = b.startTime
                val bEnd = b.endTime
                val aStart = a.startTime
                if (aEnd != null && bStart != null && aEnd <= bStart) {
                    ChronoUnit.MINUTES.between(aEnd, bStart).toInt()
                } else if (bEnd != null && aStart != null && bEnd <= aStart) {
                    ChronoUnit.MINUTES.between(bEnd, aStart).toInt()
                } else {
                    0
                }
            }
            .asConstraint("S2: minimizeGaps")

    /**
     * S3: 재스케줄 시 원래 의사 유지 (HIGH weight=1000).
     *
     * doctorId가 originalDoctorId와 다르면 페널티를 부과합니다.
     * originalDoctorId가 null인 경우(최초 배정)는 적용하지 않습니다.
     */
    fun preferOriginalDoctor(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { a -> a.originalDoctorId != null && a.doctorId != a.originalDoctorId }
            .penalize(HardSoftScore.ofSoft(1000))
            .asConstraint("S3: preferOriginalDoctor")

    /**
     * S4: PENDING_RESCHEDULE 예약은 빠른 날짜/시간 선호 (LOW weight=10).
     *
     * requestedDate가 non-null인 예약을 재스케줄 대상으로 간주합니다.
     * appointmentDate가 dateRange 시작일(requestedDate)로부터 멀수록 페널티를 부과합니다.
     */
    fun preferEarlySlot(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { a -> a.requestedDate != null && a.appointmentDate != null }
            .penalize(HardSoftScore.ofSoft(10)) { a ->
                val days = ChronoUnit.DAYS.between(a.requestedDate, a.appointmentDate)
                maxOf(0, days.toInt())
            }
            .asConstraint("S4: preferEarlySlot")

    /**
     * S5: 장비 사용 시간대를 연속 배치 (LOW weight=5).
     *
     * 같은 장비를 사용하는 예약 간의 간격(분)을 페널티로 부과합니다.
     */
    fun equipmentUtilization(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { a -> a.requiresEquipment && a.equipmentId != null }
            .join(
                AppointmentPlanning::class.java,
                Joiners.equal(AppointmentPlanning::equipmentId),
                Joiners.equal(AppointmentPlanning::appointmentDate),
                Joiners.lessThan(AppointmentPlanning::id),
            )
            .filter { a, b -> b.requiresEquipment }
            .filter { a, b ->
                val aEnd = a.endTime
                val bStart = b.startTime
                val bEnd = b.endTime
                val aStart = a.startTime
                aEnd != null && bStart != null && bEnd != null && aStart != null &&
                    (aEnd < bStart || bEnd < aStart)
            }
            .penalize(HardSoftScore.ofSoft(5)) { a, b ->
                val aEnd = a.endTime
                val bStart = b.startTime
                val bEnd = b.endTime
                val aStart = a.startTime
                if (aEnd != null && bStart != null && aEnd <= bStart) {
                    ChronoUnit.MINUTES.between(aEnd, bStart).toInt()
                } else if (bEnd != null && aStart != null && bEnd <= aStart) {
                    ChronoUnit.MINUTES.between(bEnd, aStart).toInt()
                } else {
                    0
                }
            }
            .asConstraint("S5: equipmentUtilization")

    /**
     * S6: 환자가 요청한 날짜에 가까울수록 선호 (HIGH weight=500).
     *
     * |appointmentDate - requestedDate| 일수를 페널티로 부과합니다.
     * requestedDate가 null인 경우는 적용하지 않습니다.
     */
    fun preferRequestedDate(factory: ConstraintFactory): Constraint =
        factory.forEach(AppointmentPlanning::class.java)
            .filter { a -> a.requestedDate != null && a.appointmentDate != null }
            .penalize(HardSoftScore.ofSoft(500)) { a ->
                Math.abs(ChronoUnit.DAYS.between(a.requestedDate, a.appointmentDate)).toInt()
            }
            .asConstraint("S6: preferRequestedDate")
}
