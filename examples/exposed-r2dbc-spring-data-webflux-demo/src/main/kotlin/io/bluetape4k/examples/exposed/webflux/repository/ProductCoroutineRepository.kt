package io.bluetape4k.examples.exposed.webflux.repository

import io.bluetape4k.examples.exposed.webflux.domain.ProductDto
import io.bluetape4k.examples.exposed.webflux.domain.Products
import io.bluetape4k.spring.data.exposed.r2dbc.repository.CoroutineExposedRepository
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow

interface ProductCoroutineRepository : CoroutineExposedRepository<Products, ProductDto, Long> {

    override fun toDomain(row: ResultRow): ProductDto =
        ProductDto(
            id = row[Products.id].value,
            name = row[Products.name],
            price = row[Products.price],
            stock = row[Products.stock],
        )

    override fun toPersistValues(domain: ProductDto): Map<Column<*>, Any?> =
        mapOf(
            Products.name to domain.name,
            Products.price to domain.price,
            Products.stock to domain.stock,
        )
}
