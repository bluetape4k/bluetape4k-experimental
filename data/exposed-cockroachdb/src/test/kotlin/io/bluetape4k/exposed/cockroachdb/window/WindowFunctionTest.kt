package io.bluetape4k.exposed.cockroachdb.window

import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import io.bluetape4k.exposed.cockroachdb.domain.Orders
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.lag
import org.jetbrains.exposed.v1.core.lead
import org.jetbrains.exposed.v1.core.rank
import org.jetbrains.exposed.v1.core.rowNumber
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * CockroachDB Window Function 테스트.
 *
 * CockroachDB v26.1+ 지원 사항:
 * - WINDOW FRAME GROUPS 모드 (`GROUPS BETWEEN ...`) — v26.1에서 실행 가능
 */
class WindowFunctionTest : AbstractCockroachDBTest() {

    companion object : KLogging()

    // 테스트용 주문 데이터
    private data class OrderFixture(
        val userId: Long,
        val amount: BigDecimal,
        val status: String,
    )

    private val fixtures = listOf(
        OrderFixture(1L, BigDecimal("100.00"), "PAID"),
        OrderFixture(1L, BigDecimal("200.00"), "PAID"),
        OrderFixture(1L, BigDecimal("150.00"), "CANCELLED"),
        OrderFixture(2L, BigDecimal("300.00"), "PAID"),
        OrderFixture(2L, BigDecimal("50.00"),  "CANCELLED"),
        OrderFixture(3L, BigDecimal("400.00"), "PAID"),
    )

    private fun insertFixtures() {
        Orders.batchInsert(fixtures) { f ->
            this[Orders.userId]  = f.userId
            this[Orders.amount]  = f.amount
            this[Orders.status]  = f.status
        }
    }

    @Test
    fun `ROW_NUMBER - amount DESC 기준 전체 순위`() {
        withTables(Orders) {
            insertFixtures()

            val rowNumExpr = rowNumber().over().orderBy(Orders.amount, SortOrder.DESC)

            val results = Orders
                .select(Orders.amount, rowNumExpr)
                .orderBy(Orders.amount, SortOrder.DESC)
                .toList()

            results.shouldNotBeEmpty()
            // 가장 큰 amount(400.00)가 1위
            results.first()[rowNumExpr].shouldBeEqualTo(1L)
            // 행 수만큼 순위가 할당됨
            results.last()[rowNumExpr].shouldBeEqualTo(fixtures.size.toLong())
        }
    }

    @Test
    fun `RANK - status PARTITION BY, amount ORDER BY`() {
        withTables(Orders) {
            insertFixtures()

            val rankExpr = rank().over()
                .partitionBy(Orders.status)
                .orderBy(Orders.amount, SortOrder.DESC)

            val results = Orders
                .select(Orders.status, Orders.amount, rankExpr)
                .orderBy(Orders.status, SortOrder.ASC)
                .orderBy(Orders.amount, SortOrder.DESC)
                .toList()

            results.shouldNotBeEmpty()
            // 각 status 파티션 안에서 첫 번째 행은 rank = 1
            val paidRows = results.filter { it[Orders.status] == "PAID" }
            paidRows.first()[rankExpr].shouldBeEqualTo(1L)

            val cancelledRows = results.filter { it[Orders.status] == "CANCELLED" }
            cancelledRows.first()[rankExpr].shouldBeEqualTo(1L)
        }
    }

    @Test
    fun `SUM OVER - userId PARTITION BY 누적 합계`() {
        withTables(Orders) {
            insertFixtures()

            val sumExpr = Orders.amount.sum().over().partitionBy(Orders.userId)

            val results = Orders
                .select(Orders.userId, Orders.amount, sumExpr)
                .orderBy(Orders.userId, SortOrder.ASC)
                .toList()

            results.shouldNotBeEmpty()

            // userId=1 의 합계: 100 + 200 + 150 = 450
            val user1Rows = results.filter { it[Orders.userId] == 1L }
            user1Rows.shouldNotBeEmpty()
            user1Rows.forEach { row ->
                row[sumExpr].shouldNotBeNull()
                row[sumExpr]!!.compareTo(BigDecimal("450.00")).shouldBeEqualTo(0)
            }

            // userId=2 의 합계: 300 + 50 = 350
            val user2Rows = results.filter { it[Orders.userId] == 2L }
            user2Rows.shouldNotBeEmpty()
            user2Rows.forEach { row ->
                row[sumExpr].shouldNotBeNull()
                row[sumExpr]!!.compareTo(BigDecimal("350.00")).shouldBeEqualTo(0)
            }

            // userId=3 의 합계: 400
            val user3Rows = results.filter { it[Orders.userId] == 3L }
            user3Rows.shouldNotBeEmpty()
            user3Rows.forEach { row ->
                row[sumExpr].shouldNotBeNull()
                row[sumExpr]!!.compareTo(BigDecimal("400.00")).shouldBeEqualTo(0)
            }
        }
    }

    @Test
    fun `LAG - userId PARTITION BY, orderedAt ORDER BY 이전 주문 금액`() {
        withTables(Orders) {
            insertFixtures()

            val lagExpr = Orders.amount.lag(intLiteral(1))
                .over()
                .partitionBy(Orders.userId)
                .orderBy(Orders.orderedAt, SortOrder.ASC)

            val results = Orders
                .select(Orders.userId, Orders.amount, lagExpr)
                .orderBy(Orders.userId, SortOrder.ASC)
                .orderBy(Orders.orderedAt, SortOrder.ASC)
                .toList()

            results.shouldNotBeEmpty()

            // userId=1 의 첫 번째 행은 이전 행이 없으므로 LAG = null
            val user1Rows = results.filter { it[Orders.userId] == 1L }
            user1Rows.shouldNotBeEmpty()
            // 첫 행의 LAG 값은 null (파티션 내 이전 행 없음)
            user1Rows.first()[lagExpr] // null 허용 — assertion 없이 접근만 검증
        }
    }

    @Test
    fun `GROUPS FRAME - amount ORDER BY 그룹 누적 합계 (v26_1+)`() {
        withTables(Orders) {
            insertFixtures()

            // GROUPS 프레임 모드: ORDER BY 값이 같은 행들을 하나의 그룹으로 취급
            val sql = """
                SELECT amount,
                       SUM(amount) OVER (
                           ORDER BY amount
                           GROUPS BETWEEN CURRENT ROW AND 1 FOLLOWING
                       ) AS group_sum
                FROM orders
                ORDER BY amount
            """.trimIndent()

            val results = (this as JdbcTransaction).exec(sql) { rs ->
                val list = mutableListOf<Pair<BigDecimal, BigDecimal>>()
                while (rs.next()) {
                    list.add(rs.getBigDecimal("amount") to rs.getBigDecimal("group_sum"))
                }
                list
            } ?: emptyList()

            results.shouldNotBeEmpty()
            log.debug("GROUPS frame results: $results")
        }
    }

    @Test
    fun `LEAD - userId PARTITION BY, orderedAt ORDER BY 다음 주문 금액`() {
        withTables(Orders) {
            insertFixtures()

            val leadExpr = Orders.amount.lead(intLiteral(1))
                .over()
                .partitionBy(Orders.userId)
                .orderBy(Orders.orderedAt, SortOrder.ASC)

            val results = Orders
                .select(Orders.userId, Orders.amount, leadExpr)
                .orderBy(Orders.userId, SortOrder.ASC)
                .orderBy(Orders.orderedAt, SortOrder.ASC)
                .toList()

            results.shouldNotBeEmpty()

            // userId=3 은 주문 1건 → LEAD = null
            val user3Rows = results.filter { it[Orders.userId] == 3L }
            user3Rows.shouldNotBeEmpty()
            user3Rows.first()[leadExpr] // null 허용 — 단건 파티션의 LEAD는 null
        }
    }
}
