package io.bluetape4k.scheduling.appointment.notification

import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * [NotificationHistoryRepository] 테스트.
 *
 * H2 인메모리 DB로 이력 저장/조회/중복 체크를 검증합니다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationHistoryRepositoryTest {

    private val repository = NotificationHistoryRepository()

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
    fun `save - 이력 저장 후 ID 반환`() {
        // Given
        val appointment = NotificationTestSupport.insertSampleAppointment()
        val record = NotificationHistoryRecord(
            appointmentId = appointment.id!!,
            channelType = "DUMMY",
            eventType = NotificationEventType.CREATED,
            recipient = "010-1234-5678",
            payloadJson = """{"patientName":"홍길동"}""",
        )

        // When
        val saved = transaction { repository.save(record) }

        // Then
        saved.id.shouldNotBeNull()
        saved.eventType.shouldBeEqualTo(NotificationEventType.CREATED)
    }

    @Test
    fun `existsByAppointmentAndEventType - 존재하면 true`() {
        val appointment = NotificationTestSupport.insertSampleAppointment()
        transaction {
            repository.save(
                NotificationHistoryRecord(
                    appointmentId = appointment.id!!,
                    channelType = "DUMMY",
                    eventType = NotificationEventType.REMINDER_DAY_BEFORE,
                    payloadJson = "{}",
                ),
            )
        }

        val exists = transaction {
            repository.existsByAppointmentAndEventType(appointment.id!!, NotificationEventType.REMINDER_DAY_BEFORE)
        }

        exists.shouldBeTrue()
    }

    @Test
    fun `existsByAppointmentAndEventType - 다른 이벤트 타입이면 false`() {
        val appointment = NotificationTestSupport.insertSampleAppointment()
        transaction {
            repository.save(
                NotificationHistoryRecord(
                    appointmentId = appointment.id!!,
                    channelType = "DUMMY",
                    eventType = NotificationEventType.CREATED,
                    payloadJson = "{}",
                ),
            )
        }

        val exists = transaction {
            repository.existsByAppointmentAndEventType(appointment.id!!, NotificationEventType.REMINDER_DAY_BEFORE)
        }

        exists.shouldBeFalse()
    }

    @Test
    fun `existsByAppointmentAndEventType - 이력 없으면 false`() {
        val exists = transaction {
            repository.existsByAppointmentAndEventType(9999L, NotificationEventType.CREATED)
        }

        exists.shouldBeFalse()
    }

    @Test
    fun `findByAppointmentId - 해당 예약의 모든 이력 조회`() {
        val appointment = NotificationTestSupport.insertSampleAppointment()
        transaction {
            repository.save(
                NotificationHistoryRecord(
                    appointmentId = appointment.id!!,
                    channelType = "DUMMY",
                    eventType = NotificationEventType.CREATED,
                    payloadJson = "{}",
                ),
            )
            repository.save(
                NotificationHistoryRecord(
                    appointmentId = appointment.id!!,
                    channelType = "DUMMY",
                    eventType = NotificationEventType.CONFIRMED,
                    payloadJson = "{}",
                ),
            )
        }

        val histories = transaction { repository.findByAppointmentId(appointment.id!!) }

        histories.shouldHaveSize(2)
    }

    @Test
    fun `FAILED 상태 이력은 중복 체크에서 제외`() {
        val appointment = NotificationTestSupport.insertSampleAppointment()
        transaction {
            repository.save(
                NotificationHistoryRecord(
                    appointmentId = appointment.id!!,
                    channelType = "DUMMY",
                    eventType = NotificationEventType.REMINDER_DAY_BEFORE,
                    payloadJson = "{}",
                    status = NotificationStatus.FAILED,
                ),
            )
        }

        val exists = transaction {
            repository.existsByAppointmentAndEventType(appointment.id!!, NotificationEventType.REMINDER_DAY_BEFORE)
        }

        exists.shouldBeFalse()
    }
}
