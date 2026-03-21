package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord

/**
 * 알림 채널 인터페이스.
 *
 * 외부 알림 서비스(이메일, SMS, Push 등)를 추상화합니다.
 * 기본 구현체는 [DummyNotificationChannel]이며,
 * 운영 환경에서는 Feign 기반 구현체로 교체합니다.
 */
interface NotificationChannel {

    /** 채널 타입 식별자 (DUMMY, EMAIL, SMS, PUSH 등) */
    val channelType: String

    fun sendCreated(appointment: AppointmentRecord)

    fun sendConfirmed(appointment: AppointmentRecord)

    fun sendCancelled(appointment: AppointmentRecord, reason: String?)

    fun sendRescheduled(original: AppointmentRecord, newAppointment: AppointmentRecord)

    fun sendReminder(appointment: AppointmentRecord, reminderType: ReminderType)
}

/**
 * 리마인더 유형.
 */
enum class ReminderType {
    DAY_BEFORE,
    SAME_DAY,
}
