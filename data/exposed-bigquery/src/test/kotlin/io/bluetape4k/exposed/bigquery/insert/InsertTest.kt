package io.bluetape4k.exposed.bigquery.insert

import io.bluetape4k.exposed.bigquery.AbstractBigQueryH2Test
import io.bluetape4k.exposed.bigquery.domain.Events
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldHaveSize
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class InsertTest: AbstractBigQueryH2Test() {

    companion object: KLogging()

    private data class EventFixture(
        val eventId: Long,
        val userId: Long,
        val eventType: String,
        val region: String,
        val amount: BigDecimal?,
        val occurredAt: Instant,
    )

    private val fixtures = listOf(
        EventFixture(1L, 100L, "PURCHASE", "kr", BigDecimal("9900.00"), Instant.now()),
        EventFixture(2L, 100L, "VIEW", "kr", null, Instant.now()),
        EventFixture(3L, 200L, "PURCHASE", "us", BigDecimal("49.99"), Instant.now()),
        EventFixture(4L, 200L, "CLICK", "us", null, Instant.now()),
        EventFixture(5L, 300L, "PURCHASE", "eu", BigDecimal("19.90"), Instant.now()),
    )

    @Test
    fun `batchInsert - 이벤트 대량 적재`() {
        withTables(Events) {
            Events.batchInsert(fixtures) { f ->
                this[Events.eventId] = f.eventId
                this[Events.userId] = f.userId
                this[Events.eventType] = f.eventType
                this[Events.region] = f.region
                this[Events.amount] = f.amount
                this[Events.occurredAt] = f.occurredAt
            }

            val rows = Events.selectAll().toList()
            rows shouldHaveSize fixtures.size
        }
    }

    @Test
    fun `batchInsert - 리전별 집계 검증`() {
        withTables(Events) {
            Events.batchInsert(fixtures) { f ->
                this[Events.eventId] = f.eventId
                this[Events.userId] = f.userId
                this[Events.eventType] = f.eventType
                this[Events.region] = f.region
                this[Events.amount] = f.amount
                this[Events.occurredAt] = f.occurredAt
            }

            val krRows = Events.selectAll().where { Events.region eq "kr" }.toList()
            krRows shouldHaveSize 2

            val purchaseRows = Events.selectAll().where { Events.eventType eq "PURCHASE" }.toList()
            purchaseRows shouldHaveSize 3
        }
    }
}
