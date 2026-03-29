package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * [ResilientNotificationChannel] 테스트.
 *
 * CircuitBreaker + Retry + Bulkhead 동작을 검증합니다.
 */
class ResilientNotificationChannelTest {

    private lateinit var delegate: NotificationChannel
    private lateinit var resilientChannel: ResilientNotificationChannel

    private val sampleAppointment = AppointmentRecord(
        id = 1L,
        clinicId = 1L,
        doctorId = 1L,
        treatmentTypeId = 1L,
        patientName = "홍길동",
        patientPhone = "010-1234-5678",
        appointmentDate = LocalDate.now().plusDays(1),
        startTime = LocalTime.of(9, 0),
        endTime = LocalTime.of(9, 30),
        status = AppointmentState.CONFIRMED,
    )

    @BeforeEach
    fun setup() {
        delegate = mockk(relaxed = true)
        resilientChannel = ResilientNotificationChannel.create(
            delegate,
            NotificationResilienceProperties(
                retry = NotificationResilienceProperties.RetryProperties(maxAttempts = 2),
            ),
        )
    }

    @Test
    fun `정상 호출 시 delegate로 전달`() {
        resilientChannel.sendCreated(sampleAppointment)

        verify(exactly = 1) { delegate.sendCreated(sampleAppointment) }
    }

    @Test
    fun `channelType은 delegate에서 가져옴`() {
        every { delegate.channelType } returns "FEIGN"

        resilientChannel.channelType.shouldBeEqualTo("FEIGN")
    }

    @Test
    fun `sendConfirmed 정상 전달`() {
        resilientChannel.sendConfirmed(sampleAppointment)

        verify(exactly = 1) { delegate.sendConfirmed(sampleAppointment) }
    }

    @Test
    fun `sendCancelled 정상 전달`() {
        resilientChannel.sendCancelled(sampleAppointment, "환자 요청")

        verify(exactly = 1) { delegate.sendCancelled(sampleAppointment, "환자 요청") }
    }

    @Test
    fun `sendRescheduled 정상 전달`() {
        val newAppt = sampleAppointment.copy(id = 2L)

        resilientChannel.sendRescheduled(sampleAppointment, newAppt)

        verify(exactly = 1) { delegate.sendRescheduled(sampleAppointment, newAppt) }
    }

    @Test
    fun `sendReminder 정상 전달`() {
        resilientChannel.sendReminder(sampleAppointment, ReminderType.DAY_BEFORE)

        verify(exactly = 1) { delegate.sendReminder(sampleAppointment, ReminderType.DAY_BEFORE) }
    }

    @Test
    fun `delegate 실패 시 retry 후 예외 흡수`() {
        every { delegate.sendCreated(any()) } throws RuntimeException("외부 서비스 장애")

        // 예외가 흡수되어 전파되지 않음
        resilientChannel.sendCreated(sampleAppointment)

        // maxAttempts=2 이므로 2번 호출
        verify(exactly = 2) { delegate.sendCreated(sampleAppointment) }
    }

    @Test
    fun `연속 실패 후에도 다른 메서드는 독립 호출 가능`() {
        every { delegate.sendCreated(any()) } throws RuntimeException("장애")

        resilientChannel.sendCreated(sampleAppointment) // 실패 흡수

        // sendConfirmed은 별도로 정상 동작
        resilientChannel.sendConfirmed(sampleAppointment)

        verify(exactly = 1) { delegate.sendConfirmed(sampleAppointment) }
    }
}
