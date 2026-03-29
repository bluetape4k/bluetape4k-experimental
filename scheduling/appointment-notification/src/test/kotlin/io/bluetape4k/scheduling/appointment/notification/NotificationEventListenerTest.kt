package io.bluetape4k.scheduling.appointment.notification

import io.bluetape4k.scheduling.appointment.event.AppointmentDomainEvent
import io.bluetape4k.scheduling.appointment.model.dto.AppointmentRecord
import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentState
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.LocalTime

/**
 * [NotificationEventListener] 테스트.
 *
 * MockK로 NotificationChannel과 AppointmentRepository를 모킹하여
 * 이벤트 수신 시 올바른 알림 메서드가 호출되는지 검증합니다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationEventListenerTest {

    private val notificationChannel = mockk<NotificationChannel>(relaxed = true)
    private val appointmentRepository = mockk<AppointmentRepository>()
    private val properties = NotificationProperties()

    private val listener = NotificationEventListener(notificationChannel, appointmentRepository, properties)

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

    @BeforeAll
    fun setup() {
        NotificationTestSupport.connectH2()
        NotificationTestSupport.createSchema()
    }

    @BeforeEach
    fun cleanup() {
        clearMocks(notificationChannel, appointmentRepository)
    }

    @Test
    fun `onCreated - 예약 생성 알림 발송`() {
        // Given
        every { appointmentRepository.findByIdOrNull(1L) } returns sampleAppointment

        // When
        transaction {
            listener.onCreated(AppointmentDomainEvent.Created(appointmentId = 1L, clinicId = 1L))
        }

        // Then
        verify(exactly = 1) { notificationChannel.sendCreated(sampleAppointment) }
    }

    @Test
    fun `onCreated - 예약 없으면 알림 미발송`() {
        every { appointmentRepository.findByIdOrNull(99L) } returns null

        transaction {
            listener.onCreated(AppointmentDomainEvent.Created(appointmentId = 99L, clinicId = 1L))
        }

        verify(exactly = 0) { notificationChannel.sendCreated(any()) }
    }

    @Test
    fun `onCreated - 설정 비활성화 시 알림 미발송`() {
        val disabledProps = NotificationProperties(enabled = false)
        val disabledListener = NotificationEventListener(notificationChannel, appointmentRepository, disabledProps)

        disabledListener.onCreated(AppointmentDomainEvent.Created(appointmentId = 1L, clinicId = 1L))

        verify(exactly = 0) { notificationChannel.sendCreated(any()) }
    }

    @Test
    fun `onCreated - created 이벤트만 비활성화`() {
        val props = NotificationProperties(events = NotificationProperties.EventProperties(created = false))
        val listenerWithDisabledCreated = NotificationEventListener(notificationChannel, appointmentRepository, props)

        listenerWithDisabledCreated.onCreated(AppointmentDomainEvent.Created(appointmentId = 1L, clinicId = 1L))

        verify(exactly = 0) { notificationChannel.sendCreated(any()) }
    }

    @Test
    fun `onStatusChanged - CONFIRMED 상태 변경 시 알림`() {
        every { appointmentRepository.findByIdOrNull(1L) } returns sampleAppointment

        transaction {
            listener.onStatusChanged(
                AppointmentDomainEvent.StatusChanged(
                    appointmentId = 1L,
                    clinicId = 1L,
                    fromState = "REQUESTED",
                    toState = "CONFIRMED",
                ),
            )
        }

        verify(exactly = 1) { notificationChannel.sendConfirmed(sampleAppointment) }
    }

    @Test
    fun `onStatusChanged - CONFIRMED 외 상태는 무시`() {
        transaction {
            listener.onStatusChanged(
                AppointmentDomainEvent.StatusChanged(
                    appointmentId = 1L,
                    clinicId = 1L,
                    fromState = "REQUESTED",
                    toState = "NO_SHOW",
                ),
            )
        }

        verify(exactly = 0) { notificationChannel.sendConfirmed(any()) }
    }

    @Test
    fun `onCancelled - 취소 알림 발송`() {
        every { appointmentRepository.findByIdOrNull(1L) } returns sampleAppointment

        transaction {
            listener.onCancelled(
                AppointmentDomainEvent.Cancelled(appointmentId = 1L, clinicId = 1L, reason = "환자 요청"),
            )
        }

        verify(exactly = 1) { notificationChannel.sendCancelled(sampleAppointment, "환자 요청") }
    }

    @Test
    fun `onRescheduled - 재배정 알림 발송`() {
        val newAppointment = sampleAppointment.copy(id = 2L, appointmentDate = LocalDate.now().plusDays(3))
        every { appointmentRepository.findByIdOrNull(1L) } returns sampleAppointment
        every { appointmentRepository.findByIdOrNull(2L) } returns newAppointment

        transaction {
            listener.onRescheduled(
                AppointmentDomainEvent.Rescheduled(originalId = 1L, newId = 2L, clinicId = 1L),
            )
        }

        verify(exactly = 1) { notificationChannel.sendRescheduled(sampleAppointment, newAppointment) }
    }
}
