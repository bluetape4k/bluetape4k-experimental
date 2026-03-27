package io.bluetape4k.exposed.tsrange

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

object EventTable : LongIdTable("events") {
    val name = varchar("name", 100)
    val period = tstzRange("period")
}

class TstzRangeColumnTypeTest {

    private lateinit var db: Database

    @BeforeEach
    fun setUp() {
        db = Database.connect(
            url = "jdbc:h2:mem:tsrange_test;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        transaction(db) {
            SchemaUtils.create(EventTable)
        }
    }

    @AfterEach
    fun tearDown() {
        transaction(db) {
            SchemaUtils.drop(EventTable)
        }
    }

    @Test
    fun `범위 저장 및 조회 - 기본 경계`() {
        val start = Instant.parse("2024-01-01T00:00:00Z")
        val end = Instant.parse("2024-12-31T23:59:59Z")
        val range = TimestampRange(start, end)

        transaction(db) {
            EventTable.insert {
                it[name] = "2024 연간 이벤트"
                it[period] = range
            }

            val row = EventTable.selectAll().single()
            val result = row[EventTable.period]
            result.shouldNotBeNull()
            result.start shouldBeEqualTo start
            result.end shouldBeEqualTo end
            result.lowerInclusive.shouldBeTrue()
            result.upperInclusive.shouldBeFalse()
        }
    }

    @Test
    fun `범위 저장 및 조회 - 양쪽 포함 경계`() {
        val start = Instant.parse("2024-06-01T09:00:00Z")
        val end = Instant.parse("2024-06-01T18:00:00Z")
        val range = TimestampRange(start, end, lowerInclusive = true, upperInclusive = true)

        transaction(db) {
            EventTable.insert {
                it[name] = "회의"
                it[period] = range
            }

            val row = EventTable.selectAll().single()
            val result = row[EventTable.period]
            result.shouldNotBeNull()
            result.start shouldBeEqualTo start
            result.end shouldBeEqualTo end
            result.lowerInclusive.shouldBeTrue()
            result.upperInclusive.shouldBeTrue()
        }
    }

    @Test
    fun `범위 저장 및 조회 - 양쪽 미포함 경계`() {
        val start = Instant.parse("2024-03-01T00:00:00Z")
        val end = Instant.parse("2024-03-31T23:59:59Z")
        val range = TimestampRange(start, end, lowerInclusive = false, upperInclusive = false)

        transaction(db) {
            EventTable.insert {
                it[name] = "3월 이벤트"
                it[period] = range
            }

            val row = EventTable.selectAll().single()
            val result = row[EventTable.period]
            result.shouldNotBeNull()
            result.start shouldBeEqualTo start
            result.end shouldBeEqualTo end
            result.lowerInclusive.shouldBeFalse()
            result.upperInclusive.shouldBeFalse()
        }
    }

    @Test
    fun `TimestampRange contains - 범위 내 시각`() {
        val range = TimestampRange(
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-12-31T23:59:59Z"),
        )

        range.contains(Instant.parse("2024-06-15T12:00:00Z")).shouldBeTrue()
        range.contains(Instant.parse("2024-01-01T00:00:00Z")).shouldBeTrue()  // lowerInclusive
        range.contains(Instant.parse("2024-12-31T23:59:59Z")).shouldBeFalse() // upperExclusive
        range.contains(Instant.parse("2023-12-31T23:59:59Z")).shouldBeFalse()
        range.contains(Instant.parse("2025-01-01T00:00:00Z")).shouldBeFalse()
    }

    @Test
    fun `TimestampRange overlaps - 겹치는 범위`() {
        val range1 = TimestampRange(
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-06-30T23:59:59Z"),
        )
        val range2 = TimestampRange(
            Instant.parse("2024-06-01T00:00:00Z"),
            Instant.parse("2024-12-31T23:59:59Z"),
        )

        range1.overlaps(range2).shouldBeTrue()
        range2.overlaps(range1).shouldBeTrue()
    }

    @Test
    fun `TimestampRange overlaps - 겹치지 않는 범위`() {
        val range1 = TimestampRange(
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-06-01T00:00:00Z"),
        )
        val range2 = TimestampRange(
            Instant.parse("2024-06-01T00:00:00Z"),
            Instant.parse("2024-12-31T23:59:59Z"),
        )

        // range1 upper=exclusive, range2 lower=inclusive 이지만 end == start 이므로 겹치지 않음
        range1.overlaps(range2).shouldBeFalse()
    }

    @Test
    fun `여러 범위 저장 및 조회`() {
        val ranges = listOf(
            "Q1" to TimestampRange(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-04-01T00:00:00Z"),
            ),
            "Q2" to TimestampRange(
                Instant.parse("2024-04-01T00:00:00Z"),
                Instant.parse("2024-07-01T00:00:00Z"),
            ),
            "Q3" to TimestampRange(
                Instant.parse("2024-07-01T00:00:00Z"),
                Instant.parse("2024-10-01T00:00:00Z"),
            ),
        )

        transaction(db) {
            ranges.forEach { (name, range) ->
                EventTable.insert {
                    it[EventTable.name] = name
                    it[period] = range
                }
            }

            val rows = EventTable.selectAll().toList()
            rows.size shouldBeEqualTo 3
        }
    }
}
