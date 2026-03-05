package io.bluetape4k.examples.exposed.webflux.config

import io.bluetape4k.examples.exposed.webflux.domain.ProductEntity
import io.bluetape4k.examples.exposed.webflux.domain.Products
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class DataInitializer : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        transaction {
            SchemaUtils.create(Products)

            if (ProductEntity.count() == 0L) {
                ProductEntity.new {
                    name = "Kotlin Coroutines Book"
                    price = BigDecimal("39.99")
                    stock = 100
                }
                ProductEntity.new {
                    name = "Spring WebFlux Guide"
                    price = BigDecimal("49.99")
                    stock = 50
                }
                ProductEntity.new {
                    name = "Reactive Programming"
                    price = BigDecimal("29.99")
                    stock = 200
                }
            }
        }
    }
}
