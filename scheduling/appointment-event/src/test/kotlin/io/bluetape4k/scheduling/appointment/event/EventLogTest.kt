package io.bluetape4k.scheduling.appointment.event

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
            assertEquals(1, rows.size)
            val row = rows.first()
            assertEquals("Created", row[AppointmentEventLogs.eventType])
            assertEquals("Appointment", row[AppointmentEventLogs.entityType])
            assertEquals(1L, row[AppointmentEventLogs.entityId])
            assertEquals(10L, row[AppointmentEventLogs.clinicId])
            assertTrue(row[AppointmentEventLogs.payloadJson].contains("\"appointmentId\":1"))
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
            assertEquals(1, rows.size)
            val row = rows.first()
            assertEquals("StatusChanged", row[AppointmentEventLogs.eventType])
            assertEquals(2L, row[AppointmentEventLogs.entityId])
            assertEquals(20L, row[AppointmentEventLogs.clinicId])
            val payload = row[AppointmentEventLogs.payloadJson]
            assertTrue(payload.contains("\"fromState\":\"REQUESTED\""))
            assertTrue(payload.contains("\"toState\":\"CONFIRMED\""))
            assertTrue(payload.contains("\"reason\":\"의사 승인\""))
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
            assertEquals(1, rows.size)
            val payload = rows.first()[AppointmentEventLogs.payloadJson]
            assertTrue(!payload.contains("reason"))
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
            assertEquals(1, rows.size)
            val row = rows.first()
            assertEquals("Cancelled", row[AppointmentEventLogs.eventType])
            assertEquals(4L, row[AppointmentEventLogs.entityId])
            assertEquals(40L, row[AppointmentEventLogs.clinicId])
            assertTrue(row[AppointmentEventLogs.payloadJson].contains("\"reason\":\"환자 요청 취소\""))
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
            assertEquals(3, rows.size)
            assertEquals("Created", rows[0][AppointmentEventLogs.eventType])
            assertEquals("StatusChanged", rows[1][AppointmentEventLogs.eventType])
            assertEquals("Cancelled", rows[2][AppointmentEventLogs.eventType])
        }
    }
}
