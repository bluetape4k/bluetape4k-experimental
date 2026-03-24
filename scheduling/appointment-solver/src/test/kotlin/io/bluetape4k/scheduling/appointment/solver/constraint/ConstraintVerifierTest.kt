package io.bluetape4k.scheduling.appointment.solver.constraint

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier
import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.dto.ClinicClosureRecord
import io.bluetape4k.scheduling.appointment.model.dto.DoctorAbsenceRecord
import io.bluetape4k.scheduling.appointment.model.dto.OperatingHoursRecord
import io.bluetape4k.scheduling.appointment.solver.domain.AppointmentPlanning
import io.bluetape4k.scheduling.appointment.solver.domain.DoctorFact
import io.bluetape4k.scheduling.appointment.solver.domain.ScheduleSolution
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Timefold ConstraintVerifier를 이용한 개별 제약 조건 단위 테스트.
 *
 * 데이터베이스 없이 각 Hard/Soft Constraint가 올바르게 페널티를 부과하는지 검증합니다.
 */
class ConstraintVerifierTest {

    companion object: KLogging()

    private val constraintVerifier = ConstraintVerifier
        .build(
            AppointmentConstraintProvider(),
            ScheduleSolution::class.java,
            AppointmentPlanning::class.java,
        )

    /** 테스트 기준일: 2026-03-23 (월요일) */
    private val monday: LocalDate = LocalDate.of(2026, 3, 23)

    // ── 공통 헬퍼 ──────────────────────────────────────────────────────────────

    private fun appointment(
        id: Long = 1L,
        clinicId: Long = 10L,
        doctorId: Long? = 100L,
        appointmentDate: LocalDate? = monday,
        startTime: LocalTime? = LocalTime.of(9, 0),
        durationMinutes: Int = 30,
        requiredProviderType: String = "DOCTOR",
        originalDoctorId: Long? = null,
        treatmentTypeId: Long = 1L,
    ): AppointmentPlanning = AppointmentPlanning(
        id = id,
        clinicId = clinicId,
        treatmentTypeId = treatmentTypeId,
        durationMinutes = durationMinutes,
        requiredProviderType = requiredProviderType,
        originalDoctorId = originalDoctorId,
        doctorId = doctorId,
        appointmentDate = appointmentDate,
        startTime = startTime,
    )

    // ══════════════════════════════════════════════════════════════════════════
    // H1: withinOperatingHours
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `H1 - 영업시간 외 예약 시 페널티`() {
        // 영업시간: 09:00~18:00, 예약: 08:00~08:30 → 영업시간 외
        val appt = appointment(startTime = LocalTime.of(8, 0))
        val operatingHours = OperatingHoursRecord(
            clinicId = 10L,
            dayOfWeek = DayOfWeek.MONDAY,
            openTime = LocalTime.of(9, 0),
            closeTime = LocalTime.of(18, 0),
            isActive = true,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.withinOperatingHours(factory)
            }
            .given(appt, operatingHours)
            .penalizesBy(1)
    }

    @Test
    fun `H1 - 영업시간 내 예약 시 페널티 없음`() {
        // 영업시간: 09:00~18:00, 예약: 09:00~09:30 → 정상
        val appt = appointment(startTime = LocalTime.of(9, 0))
        val operatingHours = OperatingHoursRecord(
            clinicId = 10L,
            dayOfWeek = DayOfWeek.MONDAY,
            openTime = LocalTime.of(9, 0),
            closeTime = LocalTime.of(18, 0),
            isActive = true,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.withinOperatingHours(factory)
            }
            .given(appt, operatingHours)
            .penalizesBy(0)
    }

    @Test
    fun `H1 - isActive=false 영업시간 레코드는 무시되어 페널티 발생`() {
        // isActive=false → 해당 요일은 운영 안 함으로 간주 → 페널티
        val appt = appointment(startTime = LocalTime.of(9, 0))
        val operatingHours = OperatingHoursRecord(
            clinicId = 10L,
            dayOfWeek = DayOfWeek.MONDAY,
            openTime = LocalTime.of(9, 0),
            closeTime = LocalTime.of(18, 0),
            isActive = false,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.withinOperatingHours(factory)
            }
            .given(appt, operatingHours)
            .penalizesBy(1)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // H3: noDoctorAbsenceConflict
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `H3 - 의사 전일 부재 날짜에 예약 시 페널티`() {
        val appt = appointment(doctorId = 100L, startTime = LocalTime.of(10, 0))
        // startTime=null → 전일 부재
        val absence = DoctorAbsenceRecord(
            doctorId = 100L,
            absenceDate = monday,
            startTime = null,
            endTime = null,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.noDoctorAbsenceConflict(factory)
            }
            .given(appt, absence)
            .penalizesBy(1)
    }

    @Test
    fun `H3 - 의사 부분 부재 시간과 예약 시간이 겹치면 페널티`() {
        // 예약: 10:00~10:30, 부재: 09:30~10:15 → 겹침
        val appt = appointment(doctorId = 100L, startTime = LocalTime.of(10, 0), durationMinutes = 30)
        val absence = DoctorAbsenceRecord(
            doctorId = 100L,
            absenceDate = monday,
            startTime = LocalTime.of(9, 30),
            endTime = LocalTime.of(10, 15),
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.noDoctorAbsenceConflict(factory)
            }
            .given(appt, absence)
            .penalizesBy(1)
    }

    @Test
    fun `H3 - 의사 부재 없으면 페널티 없음`() {
        val appt = appointment(doctorId = 100L, startTime = LocalTime.of(10, 0))
        // 부재 없음 → given에 absence 미포함

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.noDoctorAbsenceConflict(factory)
            }
            .given(appt)
            .penalizesBy(0)
    }

    @Test
    fun `H3 - 다른 의사 부재는 페널티 없음`() {
        val appt = appointment(doctorId = 100L, startTime = LocalTime.of(10, 0))
        val absence = DoctorAbsenceRecord(
            doctorId = 999L, // 다른 의사
            absenceDate = monday,
            startTime = null,
            endTime = null,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.noDoctorAbsenceConflict(factory)
            }
            .given(appt, absence)
            .penalizesBy(0)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // H5: noClinicClosureConflict
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `H5 - 전일 휴진 날짜에 예약 시 페널티`() {
        val appt = appointment(startTime = LocalTime.of(10, 0))
        val closure = ClinicClosureRecord(
            clinicId = 10L,
            closureDate = monday,
            isFullDay = true,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.noClinicClosureConflict(factory)
            }
            .given(appt, closure)
            .penalizesBy(1)
    }

    @Test
    fun `H5 - 부분 휴진 시간과 예약 시간이 겹치면 페널티`() {
        // 예약: 13:00~13:30, 부분 휴진: 12:00~14:00
        val appt = appointment(startTime = LocalTime.of(13, 0), durationMinutes = 30)
        val closure = ClinicClosureRecord(
            clinicId = 10L,
            closureDate = monday,
            isFullDay = false,
            startTime = LocalTime.of(12, 0),
            endTime = LocalTime.of(14, 0),
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.noClinicClosureConflict(factory)
            }
            .given(appt, closure)
            .penalizesBy(1)
    }

    @Test
    fun `H5 - 다른 날짜 휴진은 페널티 없음`() {
        val appt = appointment(startTime = LocalTime.of(10, 0))
        val closure = ClinicClosureRecord(
            clinicId = 10L,
            closureDate = monday.plusDays(1), // 다음날
            isFullDay = true,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.noClinicClosureConflict(factory)
            }
            .given(appt, closure)
            .penalizesBy(0)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // H9: providerTypeMatch
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `H9 - 의사 providerType이 requiredProviderType과 다르면 페널티`() {
        val appt = appointment(doctorId = 100L, requiredProviderType = "DOCTOR")
        val doctor = DoctorFact(
            id = 100L,
            clinicId = 10L,
            providerType = "NURSE", // 불일치
            maxConcurrentPatients = null,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.providerTypeMatch(factory)
            }
            .given(appt, doctor)
            .penalizesBy(1)
    }

    @Test
    fun `H9 - 의사 providerType이 requiredProviderType과 같으면 페널티 없음`() {
        val appt = appointment(doctorId = 100L, requiredProviderType = "DOCTOR")
        val doctor = DoctorFact(
            id = 100L,
            clinicId = 10L,
            providerType = "DOCTOR", // 일치
            maxConcurrentPatients = null,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.providerTypeMatch(factory)
            }
            .given(appt, doctor)
            .penalizesBy(0)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // H10: doctorBelongsToClinic
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `H10 - 의사가 다른 클리닉 소속이면 페널티`() {
        val appt = appointment(clinicId = 10L, doctorId = 100L)
        val doctor = DoctorFact(
            id = 100L,
            clinicId = 99L, // 다른 클리닉
            providerType = "DOCTOR",
            maxConcurrentPatients = null,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.doctorBelongsToClinic(factory)
            }
            .given(appt, doctor)
            .penalizesBy(1)
    }

    @Test
    fun `H10 - 의사가 같은 클리닉 소속이면 페널티 없음`() {
        val appt = appointment(clinicId = 10L, doctorId = 100L)
        val doctor = DoctorFact(
            id = 100L,
            clinicId = 10L, // 같은 클리닉
            providerType = "DOCTOR",
            maxConcurrentPatients = null,
        )

        constraintVerifier
            .verifyThat { _, factory ->
                HardConstraints.doctorBelongsToClinic(factory)
            }
            .given(appt, doctor)
            .penalizesBy(0)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // S1: doctorLoadBalance
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `S1 - 같은 의사에게 같은 날 예약이 2개이면 페널티`() {
        val appt1 = appointment(id = 1L, doctorId = 100L, startTime = LocalTime.of(9, 0))
        val appt2 = appointment(id = 2L, doctorId = 100L, startTime = LocalTime.of(10, 0))

        constraintVerifier
            .verifyThat { _, factory ->
                SoftConstraints.doctorLoadBalance(factory)
            }
            .given(appt1, appt2)
            .penalizesBy(1)
    }

    @Test
    fun `S1 - 서로 다른 의사에게 배정된 예약은 페널티 없음`() {
        val appt1 = appointment(id = 1L, doctorId = 100L, startTime = LocalTime.of(9, 0))
        val appt2 = appointment(id = 2L, doctorId = 101L, startTime = LocalTime.of(9, 0))

        constraintVerifier
            .verifyThat { _, factory ->
                SoftConstraints.doctorLoadBalance(factory)
            }
            .given(appt1, appt2)
            .penalizesBy(0)
    }

    @Test
    fun `S1 - 같은 의사라도 다른 날 예약이면 페널티 없음`() {
        val appt1 = appointment(id = 1L, doctorId = 100L, appointmentDate = monday, startTime = LocalTime.of(9, 0))
        val appt2 =
            appointment(id = 2L, doctorId = 100L, appointmentDate = monday.plusDays(1), startTime = LocalTime.of(9, 0))

        constraintVerifier
            .verifyThat { _, factory ->
                SoftConstraints.doctorLoadBalance(factory)
            }
            .given(appt1, appt2)
            .penalizesBy(0)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // S3: preferOriginalDoctor
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `S3 - 재스케줄 시 원래 의사와 다른 의사에게 배정되면 페널티`() {
        // originalDoctorId=100, 현재 doctorId=200 → 페널티
        val appt = appointment(doctorId = 200L, originalDoctorId = 100L)

        constraintVerifier
            .verifyThat { _, factory ->
                SoftConstraints.preferOriginalDoctor(factory)
            }
            .given(appt)
            .penalizesBy(1)
    }

    @Test
    fun `S3 - 원래 의사와 같은 의사에게 배정되면 페널티 없음`() {
        val appt = appointment(doctorId = 100L, originalDoctorId = 100L)

        constraintVerifier
            .verifyThat { _, factory ->
                SoftConstraints.preferOriginalDoctor(factory)
            }
            .given(appt)
            .penalizesBy(0)
    }

    @Test
    fun `S3 - originalDoctorId가 null이면 최초 배정으로 페널티 없음`() {
        val appt = appointment(doctorId = 100L, originalDoctorId = null)

        constraintVerifier
            .verifyThat { _, factory ->
                SoftConstraints.preferOriginalDoctor(factory)
            }
            .given(appt)
            .penalizesBy(0)
    }
}
