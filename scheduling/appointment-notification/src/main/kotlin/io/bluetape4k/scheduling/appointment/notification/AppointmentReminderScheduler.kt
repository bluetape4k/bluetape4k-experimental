package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.info
import io.bluetape4k.logging.warn
import io.bluetape4k.redis.lettuce.leader.LettuceLeaderGroupElection
import io.bluetape4k.scheduling.appointment.model.tables.AppointmentStatus
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 예약 리마인더 스케줄러.
 *
 * 매시간 실행되어 내일/오늘 예약 중 CONFIRMED 상태인 예약에 리마인더를 발송합니다.
 * HA 환경에서는 [LettuceLeaderGroupElection]을 통해 리더로 선출된 인스턴스만 실행합니다.
 * 이미 발송한 리마인더는 중복 방지합니다.
 */
@Component
class AppointmentReminderScheduler(
    private val notificationChannel: NotificationChannel,
    private val appointmentRepository: AppointmentRepository,
    private val historyRepository: NotificationHistoryRepository,
    private val properties: NotificationProperties,
    private val leaderElection: LettuceLeaderGroupElection?,
) {
    companion object : KLogging() {
        private const val LEADER_LOCK_NAME = "scheduling:reminder-scheduler"
    }

    @Scheduled(fixedRate = 3600000) // 1시간
    fun checkReminders() {
        if (!properties.enabled || !properties.reminder.enabled) return

        if (leaderElection != null) {
            try {
                leaderElection.runIfLeader(LEADER_LOCK_NAME) {
                    log.debug { "리더 선출됨 — 리마인더 실행" }
                    doCheckReminders()
                }
            } catch (e: IllegalStateException) {
                log.debug { "리더 선출 실패 — 다른 인스턴스가 실행 중" }
            }
        } else {
            doCheckReminders()
        }
    }

    private fun doCheckReminders() {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)

        if (properties.reminder.dayBefore) {
            sendReminders(tomorrow, ReminderType.DAY_BEFORE, NotificationEventType.REMINDER_DAY_BEFORE)
        }

        if (properties.reminder.sameDay) {
            sendReminders(today, ReminderType.SAME_DAY, NotificationEventType.REMINDER_SAME_DAY)
        }
    }

    private fun sendReminders(date: LocalDate, reminderType: ReminderType, eventType: String) {
        val confirmedAppointments = transaction {
            appointmentRepository.findActiveByClinicAndDate(
                clinicId = 0L,
                date = date,
                activeStatuses = listOf(AppointmentStatus.CONFIRMED),
            )
        }

        var sent = 0
        for (appointment in confirmedAppointments) {
            val alreadySent = transaction {
                historyRepository.existsByAppointmentAndEventType(appointment.id!!, eventType)
            }
            if (alreadySent) continue

            try {
                notificationChannel.sendReminder(appointment, reminderType)
                sent++
            } catch (e: Exception) {
                log.warn(e) { "리마인더 발송 실패: appointmentId=${appointment.id}, type=$reminderType" }
            }
        }

        if (sent > 0) {
            log.info { "리마인더 발송 완료: date=$date, type=$reminderType, count=$sent" }
        }
    }
}
