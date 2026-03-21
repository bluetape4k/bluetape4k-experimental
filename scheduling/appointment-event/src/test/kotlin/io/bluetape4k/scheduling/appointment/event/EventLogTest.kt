package io.bluetape4k.scheduling.appointment.event

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldHaveSize
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventLogTest {
    private lateinit var logger: AppointmentEventLogger

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:test_event_log;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(AppointmentEventLogs)
            AppointmentEventLogs.deleteAll()
        }
        logger = AppointmentEventLogger()
    }

    @Test
    fun `Created 이벤트가 DB에 저장된다`() {
        val event = AppointmentDomainEvent.Created(appointmentId = 1L, clinicId = 10L)

        logger.onCreated(event)

        transaction {
            val rows = AppointmentEventLogs.selectAll().toList()
            rows shouldHaveSize 1
            val row = rows.first()
            row[AppointmentEventLogs.eventType] shouldBeEqualTo "Created"
            row[AppointmentEventLogs.entityType] shouldBeEqualTo "Appointment"
            row[AppointmentEventLogs.entityId] shouldBeEqualTo 1L
            row[AppointmentEventLogs.clinicId] shouldBeEqualTo 10L
            row[AppointmentEventLogs.payloadJson].contains("\"appointmentId\":1").shouldBeTrue()
        }
    }

    @Test
    fun `StatusChanged 이벤트가 DB에 저장된다`() {
        val event =
            AppointmentDomainEvent.StatusChanged(
                appointmentId = 2L,
                clinicId = 20L,
                fromState = "REQUESTED",
                toState = "CONFIRMED",
                reason = "의사 승인"
            )

        logger.onStatusChanged(event)

        transaction {
            val rows = AppointmentEventLogs.selectAll().toList()
            rows shouldHaveSize 1
            val row = rows.first()
            row[AppointmentEventLogs.eventType] shouldBeEqualTo "StatusChanged"
            row[AppointmentEventLogs.entityId] shouldBeEqualTo 2L
            row[AppointmentEventLogs.clinicId] shouldBeEqualTo 20L
            val payload = row[AppointmentEventLogs.payloadJson]
            payload.contains("\"fromState\":\"REQUESTED\"").shouldBeTrue()
            payload.contains("\"toState\":\"CONFIRMED\"").shouldBeTrue()
            payload.contains("\"reason\":\"의사 승인\"").shouldBeTrue()
        }
    }

    @Test
    fun `StatusChanged 이벤트 reason이 null이면 payload에 포함되지 않는다`() {
        val event =
            AppointmentDomainEvent.StatusChanged(
                appointmentId = 3L,
                clinicId = 30L,
                fromState = "CONFIRMED",
                toState = "CHECKED_IN",
                reason = null
            )

        logger.onStatusChanged(event)

        transaction {
            val rows = AppointmentEventLogs.selectAll().toList()
            rows shouldHaveSize 1
            val payload = rows.first()[AppointmentEventLogs.payloadJson]
            payload.contains("reason").shouldBeFalse()
        }
    }

    @Test
    fun `Cancelled 이벤트가 DB에 저장된다`() {
        val event =
            AppointmentDomainEvent.Cancelled(
                appointmentId = 4L,
                clinicId = 40L,
                reason = "환자 요청 취소"
            )

        logger.onCancelled(event)

        transaction {
            val rows = AppointmentEventLogs.selectAll().toList()
            rows shouldHaveSize 1
            val row = rows.first()
            row[AppointmentEventLogs.eventType] shouldBeEqualTo "Cancelled"
            row[AppointmentEventLogs.entityId] shouldBeEqualTo 4L
            row[AppointmentEventLogs.clinicId] shouldBeEqualTo 40L
            row[AppointmentEventLogs.payloadJson].contains("\"reason\":\"환자 요청 취소\"").shouldBeTrue()
        }
    }

    @Test
    fun `여러 이벤트가 순차적으로 저장된다`() {
        logger.onCreated(AppointmentDomainEvent.Created(appointmentId = 100L, clinicId = 1L))
        logger.onStatusChanged(
            AppointmentDomainEvent.StatusChanged(
                appointmentId = 100L,
                clinicId = 1L,
                fromState = "REQUESTED",
                toState = "CONFIRMED"
            )
        )
        logger.onCancelled(
            AppointmentDomainEvent.Cancelled(
                appointmentId = 100L,
                clinicId = 1L,
                reason = "취소"
            )
        )

        transaction {
            val rows = AppointmentEventLogs.selectAll().toList()
            rows shouldHaveSize 3
            rows[0][AppointmentEventLogs.eventType] shouldBeEqualTo "Created"
            rows[1][AppointmentEventLogs.eventType] shouldBeEqualTo "StatusChanged"
            rows[2][AppointmentEventLogs.eventType] shouldBeEqualTo "Cancelled"
        }
    }
}
