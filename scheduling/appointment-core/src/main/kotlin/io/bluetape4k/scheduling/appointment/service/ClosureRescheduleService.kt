package io.bluetape4k.scheduling.appointment.service

import io.bluetape4k.scheduling.appointment.model.dto.RescheduleCandidateDto
import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import io.bluetape4k.scheduling.appointment.model.tables.RescheduleCandidates
import io.bluetape4k.scheduling.appointment.service.model.SlotQuery
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
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
) {
    companion object {
        private val ACTIVE_STATUSES = listOf("REQUESTED", "CONFIRMED")
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
    ): Map<Long, List<RescheduleCandidateDto>> =
        transaction {
            // 1. 해당 날짜의 활성 예약 조회
            val affectedAppointments =
                Appointments
                    .selectAll()
                    .where {
                        (Appointments.clinicId eq clinicId) and
                            (Appointments.appointmentDate eq closureDate) and
                            (Appointments.status inList ACTIVE_STATUSES)
                    }.toList()

            if (affectedAppointments.isEmpty()) {
                return@transaction emptyMap()
            }

            // 2. 영향받는 예약을 PENDING_RESCHEDULE로 전환
            Appointments.update(
                where = {
                    (Appointments.clinicId eq clinicId) and
                        (Appointments.appointmentDate eq closureDate) and
                        (Appointments.status inList ACTIVE_STATUSES)
                }
            ) {
                it[status] = "PENDING_RESCHEDULE"
            }

            // 3. 각 예약에 대해 재배정 후보 탐색
            val result = mutableMapOf<Long, List<RescheduleCandidateDto>>()

            for (appointment in affectedAppointments) {
                val appointmentId = appointment[Appointments.id].value
                val doctorId = appointment[Appointments.doctorId].value
                val treatmentTypeId = appointment[Appointments.treatmentTypeId].value

                val candidates = mutableListOf<RescheduleCandidateDto>()
                var priority = 0

                // closureDate 다음날부터 searchDays일간 탐색
                for (dayOffset in 1..searchDays) {
                    val candidateDate = closureDate.plusDays(dayOffset.toLong())
                    val slots = slotCalculationService.findAvailableSlots(
                        SlotQuery(clinicId, doctorId, treatmentTypeId, candidateDate)
                    )

                    for (slot in slots) {
                        val candidateId =
                            RescheduleCandidates
                                .insert {
                                    it[originalAppointmentId] = appointmentId
                                    it[RescheduleCandidates.candidateDate] = candidateDate
                                    it[startTime] = slot.startTime
                                    it[endTime] = slot.endTime
                                    it[RescheduleCandidates.doctorId] = doctorId
                                    it[RescheduleCandidates.priority] = priority
                                }[RescheduleCandidates.id]
                                .value

                        candidates.add(
                            RescheduleCandidateDto(
                                id = candidateId,
                                originalAppointmentId = appointmentId,
                                candidateDate = candidateDate,
                                startTime = slot.startTime,
                                endTime = slot.endTime,
                                doctorId = doctorId,
                                priority = priority,
                            )
                        )
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
            // 1. 후보 조회
            val candidate =
                RescheduleCandidates
                    .selectAll()
                    .where { RescheduleCandidates.id eq candidateId }
                    .firstOrNull()
                    ?: throw IllegalArgumentException("Reschedule candidate not found: $candidateId")

            val originalAppointmentId = candidate[RescheduleCandidates.originalAppointmentId].value
            val candidateDate = candidate[RescheduleCandidates.candidateDate]
            val startTime = candidate[RescheduleCandidates.startTime]
            val endTime = candidate[RescheduleCandidates.endTime]
            val doctorId = candidate[RescheduleCandidates.doctorId].value

            // 2. 원래 예약 조회
            val originalAppointment =
                Appointments
                    .selectAll()
                    .where { Appointments.id eq originalAppointmentId }
                    .first()

            // 3. 새 예약 생성
            val newAppointmentId =
                Appointments
                    .insert {
                        it[clinicId] = originalAppointment[Appointments.clinicId]
                        it[Appointments.doctorId] = doctorId
                        it[treatmentTypeId] = originalAppointment[Appointments.treatmentTypeId]
                        it[equipmentId] = originalAppointment[Appointments.equipmentId]
                        it[consultationTopicId] = originalAppointment[Appointments.consultationTopicId]
                        it[consultationMethod] = originalAppointment[Appointments.consultationMethod]
                        it[rescheduleFromId] = originalAppointmentId
                        it[patientName] = originalAppointment[Appointments.patientName]
                        it[patientPhone] = originalAppointment[Appointments.patientPhone]
                        it[patientExternalId] = originalAppointment[Appointments.patientExternalId]
                        it[appointmentDate] = candidateDate
                        it[Appointments.startTime] = startTime
                        it[Appointments.endTime] = endTime
                        it[status] = "CONFIRMED"
                    }[Appointments.id]
                    .value

            // 4. 원래 예약을 RESCHEDULED로 전환
            Appointments.update(where = { Appointments.id eq originalAppointmentId }) {
                it[status] = "RESCHEDULED"
            }

            // 5. 선택된 후보 표시
            RescheduleCandidates.update(where = { RescheduleCandidates.id eq candidateId }) {
                it[selected] = true
            }

            newAppointmentId
        }

    /**
     * 자동 재배정: 가장 높은 우선순위(가장 가까운 날짜/시간)의 후보를 자동 선택합니다.
     *
     * @param originalAppointmentId 원래 예약 ID
     * @return 새로 생성된 예약 ID, 후보가 없으면 null
     */
    fun autoReschedule(originalAppointmentId: Long): Long? =
        transaction {
            val bestCandidate =
                RescheduleCandidates
                    .selectAll()
                    .where {
                        (RescheduleCandidates.originalAppointmentId eq originalAppointmentId) and
                            (RescheduleCandidates.selected eq false)
                    }.orderBy(RescheduleCandidates.priority)
                    .firstOrNull()
                    ?: return@transaction null

            confirmReschedule(bestCandidate[RescheduleCandidates.id].value)
        }
}
