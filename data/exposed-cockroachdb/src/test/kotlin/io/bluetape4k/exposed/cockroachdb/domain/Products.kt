package io.bluetape4k.exposed.cockroachdb.domain

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object Products : LongIdTable("products") {
    val sku = varchar("sku", 50).uniqueIndex()
    val name = varchar("name", 200)
    val price = decimal("price", 10, 2)
    val stock = integer("stock").default(0)
}
