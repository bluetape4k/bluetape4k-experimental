package io.bluetape4k.exposed.bigquery.query

import io.bluetape4k.exposed.bigquery.AbstractBigQueryTest
import io.bluetape4k.exposed.bigquery.domain.Events
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeEmpty
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Exposed Query 객체를 [withBigQuery]에 전달하여 BigQuery 에뮬레이터에서 실행하는 테스트.
 *
 * H2(PostgreSQL 모드)로 SQL을 생성한 뒤 REST API로 에뮬레이터에 전달합니다.
 * 결과는 [io.bluetape4k.exposed.bigquery.BigQueryResultRow]로 반환되며 Column 참조로 타입 안전하게 접근합니다.
 */
class SelectQueryTest: AbstractBigQueryTest() {

    companion object: KLogging()

    private fun insertFixtures() {
        val fixtures = listOf(
            Triple(1L, 100L, "kr"),
            Triple(2L, 101L, "kr"),
            Triple(3L, 200L, "us"),
            Triple(4L, 201L, "us"),
            Triple(5L, 300L, "eu"),
        )
        fixtures.forEach { (id, userId, region) ->
            runRawQuery(
                """
                INSERT INTO events (event_id, user_id, event_type, region, amount, occurred_at)
                VALUES ($id, $userId, 'PURCHASE', '$region', 10.00, TIMESTAMP '2024-01-01 00:00:00 UTC')
                """
            )
        }
    }

    @Test
    fun `selectAll - withBigQuery로 전체 이벤트 조회`() {
        withEventsTable {
            insertFixtures()

            val rows = Events.selectAll().withBigQuery().toList()

            rows.shouldNotBeEmpty()
            rows.size shouldBeEqualTo 5
        }
    }

    @Test
    fun `where - withBigQuery로 리전 필터 후 Column 접근`() {
        withEventsTable {
            insertFixtures()

            val rows = Events.selectAll()
                .where { Events.region eq "kr" }
                .withBigQuery()
                .toList()

            rows.size shouldBeEqualTo 2
            rows.all { it[Events.region] == "kr" }.shouldBeTrue()
        }
    }

    @Test
    fun `orderBy - withBigQuery로 userId 내림차순 정렬`() {
        withEventsTable {
            insertFixtures()

            val rows = Events.selectAll()
                .orderBy(Events.userId, SortOrder.DESC)
                .withBigQuery()
                .toList()

            rows.first()[Events.userId] shouldBeEqualTo 300L
        }
    }

    @Test
    fun `count - withBigQuery로 리전별 이벤트 수`() {
        withEventsTable {
            insertFixtures()

            val countExpr = Events.eventId.count()
            val response = runQuery(
                Events.select(Events.region, countExpr)
                    .groupBy(Events.region)
                    .orderBy(Events.region)
            )
            response.rows.shouldNotBeEmpty()
            val krRow = response.rows.find { it.f[0].v.toString() == "kr" }!!
            krRow.f[1].v.toString().toLong() shouldBeEqualTo 2L
        }
    }

    @Test
    fun `sum - withBigQuery로 리전별 매출 합계 후 Column 접근`() {
        withEventsTable {
            insertFixtures()

            val sumExpr = Events.amount.sum()
            val response = runQuery(
                Events.select(Events.region, sumExpr)
                    .groupBy(Events.region)
            )
            response.rows.shouldNotBeEmpty()
            val krRow = response.rows.find { it.f[0].v.toString() == "kr" }!!
            BigDecimal(krRow.f[1].v.toString()).compareTo(BigDecimal("20.00")) shouldBeEqualTo 0
        }
    }
}
