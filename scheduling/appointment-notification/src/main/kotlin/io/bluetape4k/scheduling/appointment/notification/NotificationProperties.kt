package io.bluetape4k.scheduling.appointment.notification

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 알림 설정 프로퍼티.
 *
 * ```yaml
 * scheduling:
 *   notification:
 *     enabled: true
 *     events:
 *       created: true
 *       confirmed: true
 *       cancelled: true
 *       rescheduled: true
 *     reminder:
 *       enabled: true
 *       day-before: true
 *       same-day: true
 *       same-day-hours-before: 2
 * ```
 */
@ConfigurationProperties(prefix = "scheduling.notification")
data class NotificationProperties(
    val enabled: Boolean = true,
    val events: EventProperties = EventProperties(),
    val reminder: ReminderProperties = ReminderProperties(),
) {
    data class EventProperties(
        val created: Boolean = true,
        val confirmed: Boolean = true,
        val cancelled: Boolean = true,
        val rescheduled: Boolean = true,
    )

    data class ReminderProperties(
        val enabled: Boolean = true,
        val dayBefore: Boolean = true,
        val sameDay: Boolean = true,
        val sameDayHoursBefore: Int = 2,
    )
}
