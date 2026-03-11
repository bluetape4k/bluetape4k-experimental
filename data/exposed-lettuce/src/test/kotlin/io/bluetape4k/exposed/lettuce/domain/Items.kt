package io.bluetape4k.exposed.lettuce.domain

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

object Items : LongIdTable("items") {
    val name = varchar("name", 255)
    val price = decimal("price", 10, 2)
}

class ItemEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ItemEntity>(Items)

    var name: String by Items.name
    var price: java.math.BigDecimal by Items.price
}

data class ItemDto(
    val id: Long,
    val name: String,
    val price: java.math.BigDecimal,
) : java.io.Serializable
