package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.scheduling.appointment.event.AppointmentDomainEvent
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 도메인 이벤트를 구독하여 알림을 발송하는 리스너.
 *
 * [NotificationProperties] 설정에 따라 이벤트별 on/off를 제어합니다.
 */
@Component
class NotificationEventListener(
    private val notificationChannel: NotificationChannel,
    private val appointmentRepository: AppointmentRepository,
    private val properties: NotificationProperties,
) {
    companion object : KLogging()

    @EventListener
    fun onCreated(event: AppointmentDomainEvent.Created) {
        if (!properties.enabled || !properties.events.created) return
        log.debug { "알림 처리: 예약 생성 appointmentId=${event.appointmentId}" }

        val appointment = transaction { appointmentRepository.findByIdOrNull(event.appointmentId) } ?: return
        notificationChannel.sendCreated(appointment)
    }

    @EventListener
    fun onStatusChanged(event: AppointmentDomainEvent.StatusChanged) {
        if (!properties.enabled) return

        when (event.toState) {
            "CONFIRMED" -> {
                if (!properties.events.confirmed) return
                log.debug { "알림 처리: 예약 확정 appointmentId=${event.appointmentId}" }
                val appointment = transaction { appointmentRepository.findByIdOrNull(event.appointmentId) } ?: return
                notificationChannel.sendConfirmed(appointment)
            }
        }
    }

    @EventListener
    fun onCancelled(event: AppointmentDomainEvent.Cancelled) {
        if (!properties.enabled || !properties.events.cancelled) return
        log.debug { "알림 처리: 예약 취소 appointmentId=${event.appointmentId}" }

        val appointment = transaction { appointmentRepository.findByIdOrNull(event.appointmentId) } ?: return
        notificationChannel.sendCancelled(appointment, event.reason)
    }

    @EventListener
    fun onRescheduled(event: AppointmentDomainEvent.Rescheduled) {
        if (!properties.enabled || !properties.events.rescheduled) return
        log.debug { "알림 처리: 재배정 완료 original=${event.originalId}, new=${event.newId}" }

        val original = transaction { appointmentRepository.findByIdOrNull(event.originalId) } ?: return
        val newAppt = transaction { appointmentRepository.findByIdOrNull(event.newId) } ?: return
        notificationChannel.sendRescheduled(original, newAppt)
    }
}
