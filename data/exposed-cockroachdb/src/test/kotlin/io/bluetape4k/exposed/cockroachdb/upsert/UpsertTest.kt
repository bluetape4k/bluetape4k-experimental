package io.bluetape4k.exposed.cockroachdb.upsert

import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import io.bluetape4k.exposed.cockroachdb.domain.Products
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class UpsertTest : AbstractCockroachDBTest() {

    companion object : KLogging()

    @Test
    fun `insertOrIgnore - 같은 sku 두 번 삽입 시 두 번째는 무시`() {
        withTables(Products) {
            // 첫 번째 삽입
            Products.upsert(
                Products.sku,
                onUpdateExclude = listOf(Products.name, Products.price, Products.stock)
            ) {
                it[sku] = "SKU-001"
                it[name] = "Widget A"
                it[price] = BigDecimal("9.99")
                it[stock] = 100
            }

            // 같은 sku로 두 번째 삽입 — onUpdateExclude로 모든 컬럼 제외 → 업데이트 없음
            Products.upsert(
                Products.sku,
                onUpdateExclude = listOf(Products.name, Products.price, Products.stock)
            ) {
                it[sku] = "SKU-001"
                it[name] = "Widget A Updated"
                it[price] = BigDecimal("19.99")
                it[stock] = 999
            }

            val rows = Products.selectAll().toList()
            rows shouldHaveSize 1

            val row = rows.first()
            row[Products.sku].shouldBeEqualTo("SKU-001")
            row[Products.name].shouldBeEqualTo("Widget A")
            row[Products.price].shouldBeEqualTo(BigDecimal("9.99"))
            row[Products.stock].shouldBeEqualTo(100)
        }
    }

    @Test
    fun `upsert - 같은 sku 삽입 시 stock 업데이트`() {
        withTables(Products) {
            // 초기 삽입
            Products.upsert(Products.sku) {
                it[sku] = "SKU-002"
                it[name] = "Gadget B"
                it[price] = BigDecimal("29.99")
                it[stock] = 50
            }

            // 같은 sku로 upsert — stock을 업데이트
            Products.upsert(
                Products.sku,
                onUpdate = { it[Products.stock] = Products.stock + 25 }
            ) {
                it[sku] = "SKU-002"
                it[name] = "Gadget B"
                it[price] = BigDecimal("29.99")
                it[stock] = 25
            }

            val rows = Products.selectAll().where { Products.sku eq "SKU-002" }.toList()
            rows shouldHaveSize 1
            rows.first()[Products.stock].shouldBeEqualTo(75)
        }
    }

    @Test
    fun `upsert multiple rows - 여러 행 upsert`() {
        withTables(Products) {
            val initialData = listOf(
                Triple("SKU-010", "Item A", 10),
                Triple("SKU-011", "Item B", 20),
                Triple("SKU-012", "Item C", 30),
            )

            // 초기 삽입
            Products.batchUpsert(initialData, Products.sku) { (sku, name, stock) ->
                this[Products.sku] = sku
                this[Products.name] = name
                this[Products.price] = BigDecimal("5.00")
                this[Products.stock] = stock
            }

            Products.selectAll().toList() shouldHaveSize 3

            // stock 업데이트용 upsert 데이터
            val updatedData = listOf(
                Triple("SKU-010", "Item A", 110),
                Triple("SKU-011", "Item B", 220),
                Triple("SKU-013", "Item D", 40),  // 신규 행
            )

            Products.batchUpsert(updatedData, Products.sku) { (sku, name, stock) ->
                this[Products.sku] = sku
                this[Products.name] = name
                this[Products.price] = BigDecimal("5.00")
                this[Products.stock] = stock
            }

            val allRows = Products.selectAll().orderBy(Products.sku).toList()
            allRows shouldHaveSize 4

            val rowA = allRows.first { it[Products.sku] == "SKU-010" }
            rowA[Products.stock].shouldBeEqualTo(110)

            val rowB = allRows.first { it[Products.sku] == "SKU-011" }
            rowB[Products.stock].shouldBeEqualTo(220)

            val rowC = allRows.first { it[Products.sku] == "SKU-012" }
            rowC[Products.stock].shouldBeEqualTo(30)  // 변경 없음

            val rowD = allRows.first { it[Products.sku] == "SKU-013" }
            rowD[Products.stock].shouldBeEqualTo(40)  // 신규 삽입
        }
    }
}
