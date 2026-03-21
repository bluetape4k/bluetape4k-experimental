package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * 더미 알림 채널.
 *
 * 로그 출력 + NotificationHistory 테이블에 이력 저장.
 * 운영 환경에서는 Feign 기반 외부 서비스 호출 구현체로 교체합니다.
 */
class DummyNotificationChannel(
    private val historyRepository: NotificationHistoryRepository,
) : NotificationChannel {

    companion object : KLogging()

    override val channelType: String = "DUMMY"

    override fun sendCreated(appointment: AppointmentRecord) {
        log.info { "[DUMMY] 예약 생성 알림: patient=${appointment.patientName}, date=${appointment.appointmentDate}" }
        saveHistory(appointment, NotificationEventType.CREATED)
    }

    override fun sendConfirmed(appointment: AppointmentRecord) {
        log.info { "[DUMMY] 예약 확정 알림: patient=${appointment.patientName}, date=${appointment.appointmentDate}" }
        saveHistory(appointment, NotificationEventType.CONFIRMED)
    }

    override fun sendCancelled(appointment: AppointmentRecord, reason: String?) {
        log.info { "[DUMMY] 예약 취소 알림: patient=${appointment.patientName}, reason=$reason" }
        saveHistory(appointment, NotificationEventType.CANCELLED)
    }

    override fun sendRescheduled(original: AppointmentRecord, newAppointment: AppointmentRecord) {
        log.info { "[DUMMY] 재배정 알림: patient=${original.patientName}, newDate=${newAppointment.appointmentDate}" }
        saveHistory(original, NotificationEventType.RESCHEDULED)
    }

    override fun sendReminder(appointment: AppointmentRecord, reminderType: ReminderType) {
        val eventType = when (reminderType) {
            ReminderType.DAY_BEFORE -> NotificationEventType.REMINDER_DAY_BEFORE
            ReminderType.SAME_DAY -> NotificationEventType.REMINDER_SAME_DAY
        }
        log.info { "[DUMMY] 리마인더 알림: patient=${appointment.patientName}, type=$reminderType" }
        saveHistory(appointment, eventType)
    }

    private fun saveHistory(appointment: AppointmentRecord, eventType: String) {
        transaction {
            historyRepository.save(
                NotificationHistoryRecord(
                    appointmentId = appointment.id!!,
                    channelType = channelType,
                    eventType = eventType,
                    recipient = appointment.patientPhone,
                    payloadJson = """{"patientName":"${appointment.patientName}","date":"${appointment.appointmentDate}"}""",
                    status = NotificationStatus.SUCCESS,
                )
            )
        }
    }
}
