package io.bluetape4k.scheduling.appointment.repository

import io.bluetape4k.logging.KLogging
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStateHistory
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStateHistoryRecord
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * 예약 상태 변경 이력 저장소.
 *
 * 예약의 모든 상태 전이를 기록하고 조회합니다.
 */
class AppointmentStateHistoryRepository {
    companion object : KLogging()

    /**
     * 상태 변경 이력을 저장합니다.
     *
     * @param record 상태 변경 이력 (ID는 null)
     * @return 저장된 이력 (ID 포함)
     */
    fun save(record: AppointmentStateHistoryRecord): AppointmentStateHistoryRecord {
        val id = AppointmentStateHistory.insertAndGetId {
            it[appointmentId] = record.appointmentId
            it[fromState] = record.fromState
            it[toState] = record.toState
            it[reason] = record.reason
            it[note] = record.note
            it[changedBy] = record.changedBy
        }.value
        return record.copy(id = id)
    }

    /**
     * 예약의 모든 상태 변경 이력을 조회합니다 (시간순).
     *
     * @param appointmentId 예약 ID
     * @return 상태 변경 이력 목록
     */
    fun findByAppointmentId(appointmentId: Long): List<AppointmentStateHistoryRecord> =
        AppointmentStateHistory
            .selectAll()
            .where { AppointmentStateHistory.appointmentId eq appointmentId }
            .orderBy(AppointmentStateHistory.changedAt)
            .map { it.toRecord() }

    private fun ResultRow.toRecord() = AppointmentStateHistoryRecord(
        id = this[AppointmentStateHistory.id].value,
        appointmentId = this[AppointmentStateHistory.appointmentId].value,
        fromState = this[AppointmentStateHistory.fromState],
        toState = this[AppointmentStateHistory.toState],
        reason = this[AppointmentStateHistory.reason],
        note = this[AppointmentStateHistory.note],
        changedBy = this[AppointmentStateHistory.changedBy],
        changedAt = this[AppointmentStateHistory.changedAt],
    )
}
