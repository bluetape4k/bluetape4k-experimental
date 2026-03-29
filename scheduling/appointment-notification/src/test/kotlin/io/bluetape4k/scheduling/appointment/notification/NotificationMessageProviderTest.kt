package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * [NotificationMessageProvider] 테스트.
 */
class NotificationMessageProviderTest {

    private val appointment = AppointmentRecord(
        id = 1L,
        clinicId = 1L,
        doctorId = 1L,
        treatmentTypeId = 1L,
        patientName = "홍길동",
        patientPhone = "010-1234-5678",
        appointmentDate = LocalDate.of(2026, 3, 21),
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(9, 30),
        status = AppointmentState.CONFIRMED,
    )

    @Test
    fun `한국어 생성 메시지`() {
        val msg = NotificationMessageProvider.createdMessage(appointment, Locale.KOREAN)

        msg.shouldNotBeNull()
        msg.shouldContain("예약이 생성되었습니다")
        msg.shouldContain("홍길동")
    }

    @Test
    fun `영어 생성 메시지`() {
        val msg = NotificationMessageProvider.createdMessage(appointment, Locale.ENGLISH)

        msg.shouldContain("Appointment created")
        msg.shouldContain("홍길동")
    }

    @Test
    fun `일본어 확정 메시지`() {
        val msg = NotificationMessageProvider.confirmedMessage(appointment, Locale.JAPANESE)

        msg.shouldContain("予約が確定されました")
    }

    @Test
    fun `한국어 취소 메시지 - 사유 포함`() {
        val msg = NotificationMessageProvider.cancelledMessage(appointment, "환자 요청", Locale.KOREAN)

        msg.shouldContain("예약이 취소되었습니다")
        msg.shouldContain("환자 요청")
    }

    @Test
    fun `영어 취소 메시지 - 사유 없음`() {
        val msg = NotificationMessageProvider.cancelledMessage(appointment, null, Locale.ENGLISH)

        msg.shouldContain("No reason provided")
    }

    @Test
    fun `한국어 재배정 메시지`() {
        val newAppt = appointment.copy(
            id = 2L,
            appointmentDate = LocalDate.of(2026, 3, 25),
            startTime = LocalTime.of(14, 0),
        )
        val msg = NotificationMessageProvider.rescheduledMessage(appointment, newAppt, Locale.KOREAN)

        msg.shouldContain("예약이 변경되었습니다")
        msg.shouldContain("홍길동")
    }

    @Test
    fun `한국어 리마인더 메시지 - 전일`() {
        val msg = NotificationMessageProvider.reminderMessage(appointment, ReminderType.DAY_BEFORE, Locale.KOREAN)

        msg.shouldContain("전일")
        msg.shouldContain("리마인더")
    }

    @Test
    fun `영어 리마인더 메시지 - Same day`() {
        val msg = NotificationMessageProvider.reminderMessage(appointment, ReminderType.SAME_DAY, Locale.ENGLISH)

        msg.shouldContain("Same day")
        msg.shouldContain("reminder")
    }

    @Test
    fun `중국어 생성 메시지`() {
        val msg = NotificationMessageProvider.createdMessage(appointment, Locale.CHINESE)

        msg.shouldContain("预约已创建")
    }
}
