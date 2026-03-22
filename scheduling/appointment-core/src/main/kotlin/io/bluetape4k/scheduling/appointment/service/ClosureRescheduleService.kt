package io.bluetape4k.scheduling.appointment.service

import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.model.dto.RescheduleCandidateRecord
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStatus
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import io.bluetape4k.scheduling.appointment.repository.RescheduleCandidateRepository
import io.bluetape4k.scheduling.appointment.service.model.SlotQuery
import io.bluetape4k.support.requireNotNull
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate

/**
 * 임시휴진 시 영향받는 예약의 재배정을 처리하는 서비스.
 *
 * 1. 영향받는 예약을 PENDING_RESCHEDULE로 전환
 * 2. 각 예약에 대해 재배정 후보 슬롯 탐색
 * 3. 관리자가 후보를 선택하면 새 예약 생성 + 원래 예약 RESCHEDULED 처리
 */
class ClosureRescheduleService(
    private val slotCalculationService: SlotCalculationService,
    private val appointmentRepository: AppointmentRepository = AppointmentRepository(),
    private val rescheduleCandidateRepository: RescheduleCandidateRepository = RescheduleCandidateRepository(),
) {
    companion object: KLogging() {
        private val ACTIVE_STATUSES = AppointmentStatus.ACTIVE_STATUSES
    }

    /**
     * 임시휴진 선언 시 해당 날짜의 활성 예약을 PENDING_RESCHEDULE로 전환하고
     * 각 예약에 대해 재배정 후보를 탐색합니다.
     *
     * @param clinicId 병원 ID
     * @param closureDate 휴진 날짜
     * @param searchDays 후보 탐색 일수 (기본 7일)
     * @return 영향받은 예약 ID → 후보 목록
     */
    fun processClosureReschedule(
        clinicId: Long,
        closureDate: LocalDate,
        searchDays: Int = 7,
    ): Map<Long, List<RescheduleCandidateRecord>> =
        transaction {
            val affected = appointmentRepository.findActiveByClinicAndDate(clinicId, closureDate, ACTIVE_STATUSES)
            if (affected.isEmpty()) return@transaction emptyMap()

            appointmentRepository.updateStatusByClinicAndDate(
                clinicId,
                closureDate,
                ACTIVE_STATUSES,
                AppointmentStatus.PENDING_RESCHEDULE
            )

            val result = mutableMapOf<Long, List<RescheduleCandidateRecord>>()

            for (appointment in affected) {
                val appointmentId = appointment.id!!
                val candidates = mutableListOf<RescheduleCandidateRecord>()
                var priority = 0

                for (dayOffset in 1..searchDays) {
                    val candidateDate = closureDate.plusDays(dayOffset.toLong())
                    val slots = slotCalculationService.findAvailableSlots(
                        SlotQuery(clinicId, appointment.doctorId, appointment.treatmentTypeId, candidateDate)
                    )

                    for (slot in slots) {
                        val rcRecord = RescheduleCandidateRecord(
                            originalAppointmentId = appointmentId,
                            candidateDate = candidateDate,
                            startTime = slot.startTime,
                            endTime = slot.endTime,
                            doctorId = appointment.doctorId,
                            priority = priority,
                        )
                        val saved = rescheduleCandidateRepository.save(rcRecord)
                        candidates.add(saved)
                        priority++
                    }
                }
                result[appointmentId] = candidates
            }
            result
        }

    /**
     * 관리자가 재배정 후보를 선택하여 확정합니다.
     * 원래 예약은 RESCHEDULED로 전환하고, 새 예약을 생성합니다.
     *
     * @param candidateId 선택한 후보 ID
     * @return 새로 생성된 예약 ID
     */
    fun confirmReschedule(candidateId: Long): Long =
        transaction {
            val candidate = rescheduleCandidateRepository.findByIdOrNull(candidateId)
                ?: throw IllegalArgumentException("Reschedule candidate not found: $candidateId")

            val original = appointmentRepository.findByIdOrNull(candidate.originalAppointmentId)
                ?: throw IllegalArgumentException("Original appointment not found: ${candidate.originalAppointmentId}")

            val appointmentRecord = AppointmentRecord(
                clinicId = original.clinicId,
                doctorId = candidate.doctorId,
                treatmentTypeId = original.treatmentTypeId,
                equipmentId = original.equipmentId,
                consultationTopicId = original.consultationTopicId,
                consultationMethod = original.consultationMethod,
                rescheduleFromId = original.id,
                patientName = original.patientName,
                patientPhone = original.patientPhone,
                patientExternalId = original.patientExternalId,
                appointmentDate = candidate.candidateDate,
                startTime = candidate.startTime,
                endTime = candidate.endTime,
                status = AppointmentStatus.CONFIRMED,
            )

            val newAppointment = appointmentRepository.save(appointmentRecord)
            appointmentRepository.updateStatus(original.id!!, AppointmentStatus.RESCHEDULED)
            rescheduleCandidateRepository.markSelected(candidateId)

            newAppointment.id.requireNotNull("newAppointment.id")
        }

    /**
     * 자동 재배정: 가장 높은 우선순위(가장 가까운 날짜/시간)의 후보를 자동 선택합니다.
     *
     * @param originalAppointmentId 원래 예약 ID
     * @return 새로 생성된 예약 ID, 후보가 없으면 null
     */
    fun autoReschedule(originalAppointmentId: Long): Long? =
        transaction {
            val best = rescheduleCandidateRepository.findBestCandidate(originalAppointmentId)
                ?: return@transaction null
            confirmReschedule(best.id!!)
        }
}
