package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * 알림 발송 이력 Repository.
 */
class NotificationHistoryRepository {
    companion object : KLogging()

    fun save(record: NotificationHistoryRecord): NotificationHistoryRecord {
        val id = NotificationHistoryTable.insert {
            it[appointmentId] = record.appointmentId
            it[channelType] = record.channelType
            it[eventType] = record.eventType
            it[recipient] = record.recipient
            it[payloadJson] = record.payloadJson
            it[status] = record.status
            it[errorMessage] = record.errorMessage
        }[NotificationHistoryTable.id].value
        return record.copy(id = id)
    }

    fun existsByAppointmentAndEventType(appointmentId: Long, eventType: String): Boolean =
        NotificationHistoryTable
            .selectAll()
            .where {
                (NotificationHistoryTable.appointmentId eq appointmentId) and
                    (NotificationHistoryTable.eventType eq eventType) and
                    (NotificationHistoryTable.status eq NotificationStatus.SUCCESS)
            }.count() > 0

    fun findByAppointmentId(appointmentId: Long): List<NotificationHistoryRecord> =
        NotificationHistoryTable
            .selectAll()
            .where { NotificationHistoryTable.appointmentId eq appointmentId }
            .map { it.toNotificationHistoryRecord() }
}

fun ResultRow.toNotificationHistoryRecord() = NotificationHistoryRecord(
    id = this[NotificationHistoryTable.id].value,
    appointmentId = this[NotificationHistoryTable.appointmentId].value,
    channelType = this[NotificationHistoryTable.channelType],
    eventType = this[NotificationHistoryTable.eventType],
    recipient = this[NotificationHistoryTable.recipient],
    payloadJson = this[NotificationHistoryTable.payloadJson],
    status = this[NotificationHistoryTable.status],
    errorMessage = this[NotificationHistoryTable.errorMessage],
    createdAt = this[NotificationHistoryTable.createdAt],
)
