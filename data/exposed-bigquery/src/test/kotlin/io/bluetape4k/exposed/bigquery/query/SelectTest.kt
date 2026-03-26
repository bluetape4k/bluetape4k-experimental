package io.bluetape4k.exposed.bigquery.query

import io.bluetape4k.exposed.bigquery.AbstractBigQueryTest
import io.bluetape4k.exposed.bigquery.domain.Events
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class SelectTest : AbstractBigQueryTest() {

    companion object : KLogging()

    private fun Transaction.insertFixtures() {
        val fixtures = listOf(
            Triple(1L, 100L, "kr"),
            Triple(2L, 101L, "kr"),
            Triple(3L, 200L, "us"),
            Triple(4L, 201L, "us"),
            Triple(5L, 300L, "eu"),
        )
        Events.batchInsert(fixtures) { (id, userId, region) ->
            this[Events.eventId] = id
            this[Events.userId] = userId
            this[Events.eventType] = "PURCHASE"
            this[Events.region] = region
            this[Events.amount] = BigDecimal("10.00")
            this[Events.occurredAt] = Instant.now()
        }
    }

    @Test
    fun `selectAll - 전체 이벤트 조회`() {
        withTables(Events) {
            insertFixtures()

            val rows = Events.selectAll().toList()
            rows.shouldNotBeEmpty()
            rows.size shouldBeEqualTo 5
        }
    }

    @Test
    fun `where - 리전 필터`() {
        withTables(Events) {
            insertFixtures()

            val krRows = Events.selectAll()
                .where { Events.region eq "kr" }
                .toList()

            krRows.size shouldBeEqualTo 2
        }
    }

    @Test
    fun `orderBy - userId 내림차순 정렬`() {
        withTables(Events) {
            insertFixtures()

            val rows = Events.selectAll()
                .orderBy(Events.userId, SortOrder.DESC)
                .toList()

            rows.first()[Events.userId] shouldBeEqualTo 300L
        }
    }

    @Test
    fun `count - 리전별 이벤트 수`() {
        withTables(Events) {
            insertFixtures()

            val countExpr = Events.eventId.count()
            val rows = Events
                .select(Events.region, countExpr)
                .groupBy(Events.region)
                .orderBy(Events.region)
                .toList()

            rows.shouldNotBeEmpty()
            rows.find { it[Events.region] == "kr" }!![countExpr] shouldBeEqualTo 2L
        }
    }

    @Test
    fun `sum - 리전별 매출 합계`() {
        withTables(Events) {
            insertFixtures()

            val sumExpr = Events.amount.sum()
            val rows = Events
                .select(Events.region, sumExpr)
                .groupBy(Events.region)
                .toList()

            rows.shouldNotBeEmpty()
            val krSum = rows.find { it[Events.region] == "kr" }!![sumExpr]
            krSum!!.compareTo(BigDecimal("20.00")) shouldBeEqualTo 0
        }
    }
}
