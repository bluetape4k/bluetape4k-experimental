package io.bluetape4k.scheduling.appointment.notification

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * [DummyNotificationChannel] 테스트.
 *
 * 알림 발송 시 NotificationHistory에 이력이 저장되는지 검증합니다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DummyNotificationChannelTest {

    private val historyRepository = NotificationHistoryRepository()
    private val channel = DummyNotificationChannel(historyRepository)

    @BeforeAll
    fun setup() {
        NotificationTestSupport.connectH2()
        NotificationTestSupport.createSchema()
    }

    @BeforeEach
    fun cleanup() {
        transaction { NotificationHistoryTable.deleteAll() }
    }

    @Test
    fun `sendCreated - 생성 알림 이력 저장`() {
        // Given
        val appointment = NotificationTestSupport.insertSampleAppointment()

        // When
        channel.sendCreated(appointment)

        // Then
        val histories = transaction { historyRepository.findByAppointmentId(appointment.id!!) }
        histories.shouldHaveSize(1)
        histories[0].eventType.shouldBeEqualTo(NotificationEventType.CREATED)
        histories[0].channelType.shouldBeEqualTo("DUMMY")
        histories[0].status.shouldBeEqualTo(NotificationStatus.SUCCESS)
    }

    @Test
    fun `sendConfirmed - 확정 알림 이력 저장`() {
        val appointment = NotificationTestSupport.insertSampleAppointment()

        channel.sendConfirmed(appointment)

        val histories = transaction { historyRepository.findByAppointmentId(appointment.id!!) }
        histories.shouldHaveSize(1)
        histories[0].eventType.shouldBeEqualTo(NotificationEventType.CONFIRMED)
    }

    @Test
    fun `sendCancelled - 취소 알림 이력 저장`() {
        val appointment = NotificationTestSupport.insertSampleAppointment()

        channel.sendCancelled(appointment, "환자 요청")

        val histories = transaction { historyRepository.findByAppointmentId(appointment.id!!) }
        histories.shouldHaveSize(1)
        histories[0].eventType.shouldBeEqualTo(NotificationEventType.CANCELLED)
    }

    @Test
    fun `sendRescheduled - 재배정 알림 이력 저장`() {
        val original = NotificationTestSupport.insertSampleAppointment(patientName = "원래환자")
        val newAppt = NotificationTestSupport.insertSampleAppointment(patientName = "원래환자")

        channel.sendRescheduled(original, newAppt)

        val histories = transaction { historyRepository.findByAppointmentId(original.id!!) }
        histories.shouldHaveSize(1)
        histories[0].eventType.shouldBeEqualTo(NotificationEventType.RESCHEDULED)
    }

    @Test
    fun `sendReminder - DAY_BEFORE 리마인더 이력 저장`() {
        val appointment = NotificationTestSupport.insertSampleAppointment()

        channel.sendReminder(appointment, ReminderType.DAY_BEFORE)

        val histories = transaction { historyRepository.findByAppointmentId(appointment.id!!) }
        histories.shouldHaveSize(1)
        histories[0].eventType.shouldBeEqualTo(NotificationEventType.REMINDER_DAY_BEFORE)
    }

    @Test
    fun `sendReminder - SAME_DAY 리마인더 이력 저장`() {
        val appointment = NotificationTestSupport.insertSampleAppointment()

        channel.sendReminder(appointment, ReminderType.SAME_DAY)

        val histories = transaction { historyRepository.findByAppointmentId(appointment.id!!) }
        histories.shouldHaveSize(1)
        histories[0].eventType.shouldBeEqualTo(NotificationEventType.REMINDER_SAME_DAY)
    }

    @Test
    fun `여러 이벤트 발송 시 이력 누적`() {
        val appointment = NotificationTestSupport.insertSampleAppointment()

        channel.sendCreated(appointment)
        channel.sendConfirmed(appointment)

        val histories = transaction { historyRepository.findByAppointmentId(appointment.id!!) }
        histories.shouldHaveSize(2)
    }

    @Test
    fun `이력에 recipient 저장`() {
        val appointment = NotificationTestSupport.insertSampleAppointment(patientPhone = "010-9999-8888")

        channel.sendCreated(appointment)

        val histories = transaction { historyRepository.findByAppointmentId(appointment.id!!) }
        histories[0].recipient.shouldNotBeNull()
        histories[0].recipient.shouldBeEqualTo("010-9999-8888")
    }
}
