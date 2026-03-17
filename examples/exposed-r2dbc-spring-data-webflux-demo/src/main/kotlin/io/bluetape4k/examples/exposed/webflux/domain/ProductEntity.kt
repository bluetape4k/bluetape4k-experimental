package io.bluetape4k.examples.exposed.webflux.domain

import io.bluetape4k.exposed.core.HasIdentifier
import io.bluetape4k.spring.data.exposed.jdbc.annotation.ExposedEntity
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

object Products : LongIdTable("webflux_products") {
    val name = varchar("name", 255)
    val price = decimal("price", 10, 2)
    val stock = integer("stock").default(0)
}

@ExposedEntity
class ProductEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ProductEntity>(Products)

    var name: String by Products.name
    var price: java.math.BigDecimal by Products.price
    var stock: Int by Products.stock
}

data class ProductDto(
    override val id: Long? = null,
    val name: String,
    val price: java.math.BigDecimal,
    val stock: Int = 0,
) : HasIdentifier<Long>

fun ProductEntity.toDto() = ProductDto(id.value, name, price, stock)
