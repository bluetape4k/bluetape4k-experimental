package io.bluetape4k.exposed.bigquery.insert

import io.bluetape4k.exposed.bigquery.AbstractBigQueryTest
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class InsertTest: AbstractBigQueryTest() {

    companion object: KLogging()

    private data class EventFixture(
        val eventId: Long,
        val userId: Long,
        val eventType: String,
        val region: String,
        val amount: BigDecimal?,
    )

    private val fixtures = listOf(
        EventFixture(1L, 100L, "PURCHASE", "kr", BigDecimal("9900.00")),
        EventFixture(2L, 100L, "VIEW", "kr", null),
        EventFixture(3L, 200L, "PURCHASE", "us", BigDecimal("49.99")),
        EventFixture(4L, 200L, "CLICK", "us", null),
        EventFixture(5L, 300L, "PURCHASE", "eu", BigDecimal("19.90")),
    )

    private fun insertFixtures() {
        fixtures.forEach { f ->
            val amountSql = f.amount?.toPlainString() ?: "NULL"
            runQuery(
                """
                INSERT INTO events (event_id, user_id, event_type, region, amount, occurred_at)
                VALUES (${f.eventId}, ${f.userId}, '${f.eventType}', '${f.region}', $amountSql, TIMESTAMP '2024-01-01 00:00:00 UTC')
                """
            )
        }
    }

    @Test
    fun `batchInsert - 이벤트 대량 적재`() {
        withEventsTable {
            insertFixtures()

            val response = runQuery("SELECT COUNT(*) as cnt FROM events")
            val count = response.rows.first().f.first().v.toString().toLong()
            count shouldBeEqualTo fixtures.size.toLong()
        }
    }

    @Test
    fun `batchInsert - 리전별 집계 검증`() {
        withEventsTable {
            insertFixtures()

            val krResponse = runQuery("SELECT COUNT(*) as cnt FROM events WHERE region = 'kr'")
            val krCount = krResponse.rows.first().f.first().v.toString().toLong()
            krCount shouldBeEqualTo 2L

            val purchaseResponse = runQuery("SELECT COUNT(*) as cnt FROM events WHERE event_type = 'PURCHASE'")
            val purchaseCount = purchaseResponse.rows.first().f.first().v.toString().toLong()
            purchaseCount shouldBeEqualTo 3L
        }
    }
}
