package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.scheduling.appointment.model.tables.Appointments
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp
import java.io.Serializable
import java.time.Instant

/**
 * 알림 발송 이력 테이블.
 */
object NotificationHistoryTable : LongIdTable("scheduling_notification_history") {
    val appointmentId = reference("appointment_id", Appointments, onDelete = ReferenceOption.CASCADE)
    val channelType = varchar("channel_type", 30)
    val eventType = varchar("event_type", 50)
    val recipient = varchar("recipient", 255).nullable()
    val payloadJson = text("payload_json")
    val status = varchar("status", 20).default(NotificationStatus.SUCCESS)
    val errorMessage = text("error_message").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

/**
 * 알림 발송 상태.
 */
object NotificationStatus {
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}

/**
 * 알림 이벤트 타입.
 */
object NotificationEventType {
    const val CREATED = "CREATED"
    const val CONFIRMED = "CONFIRMED"
    const val CANCELLED = "CANCELLED"
    const val RESCHEDULED = "RESCHEDULED"
    const val REMINDER_DAY_BEFORE = "REMINDER_DAY_BEFORE"
    const val REMINDER_SAME_DAY = "REMINDER_SAME_DAY"
}

/**
 * 알림 발송 이력 레코드.
 */
data class NotificationHistoryRecord(
    val id: Long? = null,
    val appointmentId: Long,
    val channelType: String,
    val eventType: String,
    val recipient: String? = null,
    val payloadJson: String,
    val status: String = NotificationStatus.SUCCESS,
    val errorMessage: String? = null,
    val createdAt: Instant? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
