package io.bluetape4k.examples.exposed.webflux.config

import io.bluetape4k.examples.exposed.webflux.domain.Products
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class DataInitializer(
    private val r2dbcDatabase: R2dbcDatabase,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments): Unit = runBlocking {
        suspendTransaction(r2dbcDatabase) {
            SchemaUtils.create(Products)

            if (Products.selectAll().count() == 0L) {
                Products.insert {
                    it[name] = "Kotlin Coroutines Book"
                    it[price] = BigDecimal("39.99")
                    it[stock] = 100
                }
                Products.insert {
                    it[name] = "Spring WebFlux Guide"
                    it[price] = BigDecimal("49.99")
                    it[stock] = 50
                }
                Products.insert {
                    it[name] = "Reactive Programming"
                    it[price] = BigDecimal("29.99")
                    it[stock] = 200
                }
            }
        }
    }
}
