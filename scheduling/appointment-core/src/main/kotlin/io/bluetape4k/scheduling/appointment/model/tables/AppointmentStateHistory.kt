package io.bluetape4k.scheduling.appointment.model.tables

import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import java.io.Serializable
import java.time.Instant

/**
 * 예약 상태 변경 이력 테이블.
 *
 * 예약의 모든 상태 전이를 추적합니다.
 * 상태 변경 사유, 변경자, 시간 정보를 기록해 감사(audit) 및 분석에 사용됩니다.
 */
object AppointmentStateHistory : LongIdTable("scheduling_appointment_state_history") {
    val appointmentId = reference("appointment_id", Appointments, onDelete = ReferenceOption.CASCADE)
    val fromState = appointmentState("from_state")
    val toState = appointmentState("to_state")
    val reason = text("reason").nullable()
    val note = text("note").nullable()
    val changedBy = varchar("changed_by", 100).nullable()
    val changedAt = timestamp("changed_at").defaultExpression(CurrentTimestamp)

    init {
        // 예약별 상태 이력 시간순 조회
        index("idx_appointment_state_history_appointment_changed_at", false, appointmentId, changedAt)
    }
}

/**
 * 예약 상태 변경 이력 레코드.
 *
 * @property appointmentId 예약 ID
 * @property fromState 이전 상태
 * @property toState 변경된 새로운 상태
 * @property reason 상태 변경 사유 (예: "임시휴진", "사용자 취소")
 * @property note 추가 노트
 * @property changedBy 상태 변경 주체 (사용자 ID, 시스템명 등)
 * @property changedAt 상태 변경 시각
 */
data class AppointmentStateHistoryRecord(
    val id: Long? = null,
    val appointmentId: Long,
    val fromState: AppointmentState,
    val toState: AppointmentState,
    val reason: String? = null,
    val note: String? = null,
    val changedBy: String? = null,
    val changedAt: Instant? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
