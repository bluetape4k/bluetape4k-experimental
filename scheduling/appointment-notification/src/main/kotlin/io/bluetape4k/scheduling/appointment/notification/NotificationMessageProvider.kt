package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * 다국어 알림 메시지 제공자.
 *
 * 클리닉 locale에 따라 알림 메시지를 생성합니다.
 */
object NotificationMessageProvider {

    fun createdMessage(appointment: AppointmentRecord, locale: Locale): String {
        val date = formatDate(appointment, locale)
        val time = formatTime(appointment, locale)
        return when (locale.language) {
            "ko" -> "예약이 생성되었습니다. 환자: ${appointment.patientName}, 일시: $date $time"
            "ja" -> "予約が作成されました。患者: ${appointment.patientName}, 日時: $date $time"
            "zh" -> "预约已创建。患者: ${appointment.patientName}, 日期: $date $time"
            else -> "Appointment created. Patient: ${appointment.patientName}, Date: $date $time"
        }
    }

    fun confirmedMessage(appointment: AppointmentRecord, locale: Locale): String {
        val date = formatDate(appointment, locale)
        val time = formatTime(appointment, locale)
        return when (locale.language) {
            "ko" -> "예약이 확정되었습니다. 환자: ${appointment.patientName}, 일시: $date $time"
            "ja" -> "予約が確定されました。患者: ${appointment.patientName}, 日時: $date $time"
            "zh" -> "预约已确认。患者: ${appointment.patientName}, 日期: $date $time"
            else -> "Appointment confirmed. Patient: ${appointment.patientName}, Date: $date $time"
        }
    }

    fun cancelledMessage(appointment: AppointmentRecord, reason: String?, locale: Locale): String {
        val reasonText = reason ?: defaultReasonText(locale)
        return when (locale.language) {
            "ko" -> "예약이 취소되었습니다. 환자: ${appointment.patientName}, 사유: $reasonText"
            "ja" -> "予約がキャンセルされました。患者: ${appointment.patientName}, 理由: $reasonText"
            "zh" -> "预约已取消。患者: ${appointment.patientName}, 原因: $reasonText"
            else -> "Appointment cancelled. Patient: ${appointment.patientName}, Reason: $reasonText"
        }
    }

    fun rescheduledMessage(
        original: AppointmentRecord,
        newAppointment: AppointmentRecord,
        locale: Locale,
    ): String {
        val newDate = formatDate(newAppointment, locale)
        val newTime = formatTime(newAppointment, locale)
        return when (locale.language) {
            "ko" -> "예약이 변경되었습니다. 환자: ${original.patientName}, 변경 일시: $newDate $newTime"
            "ja" -> "予約が変更されました。患者: ${original.patientName}, 変更日時: $newDate $newTime"
            "zh" -> "预约已变更。患者: ${original.patientName}, 新日期: $newDate $newTime"
            else -> "Appointment rescheduled. Patient: ${original.patientName}, New date: $newDate $newTime"
        }
    }

    fun reminderMessage(appointment: AppointmentRecord, reminderType: ReminderType, locale: Locale): String {
        val date = formatDate(appointment, locale)
        val time = formatTime(appointment, locale)
        val typeText = reminderTypeText(reminderType, locale)
        return when (locale.language) {
            "ko" -> "[$typeText] 예약 리마인더. 환자: ${appointment.patientName}, 일시: $date $time"
            "ja" -> "[$typeText] 予約リマインダー。患者: ${appointment.patientName}, 日時: $date $time"
            "zh" -> "[$typeText] 预约提醒。患者: ${appointment.patientName}, 日期: $date $time"
            else -> "[$typeText] Appointment reminder. Patient: ${appointment.patientName}, Date: $date $time"
        }
    }

    private fun formatDate(appointment: AppointmentRecord, locale: Locale): String =
        appointment.appointmentDate.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
        )

    private fun formatTime(appointment: AppointmentRecord, locale: Locale): String =
        appointment.startTime.format(
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
        )

    private fun defaultReasonText(locale: Locale): String = when (locale.language) {
        "ko" -> "사유 미기재"
        "ja" -> "理由未記載"
        "zh" -> "未说明原因"
        else -> "No reason provided"
    }

    private fun reminderTypeText(type: ReminderType, locale: Locale): String = when (type) {
        ReminderType.DAY_BEFORE -> when (locale.language) {
            "ko" -> "전일"
            "ja" -> "前日"
            "zh" -> "前一天"
            else -> "Day before"
        }
        ReminderType.SAME_DAY -> when (locale.language) {
            "ko" -> "당일"
            "ja" -> "当日"
            "zh" -> "当天"
            else -> "Same day"
        }
    }
}
