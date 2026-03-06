package io.bluetape4k.examples.benchmark.domain.exposed

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentTimestamp
import org.jetbrains.exposed.v1.javatime.timestamp

object Authors: LongIdTable("authors") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object Books: LongIdTable("books") {
    val title = varchar("title", 500)
    val isbn = varchar("isbn", 20).uniqueIndex()
    val price = decimal("price", 10, 2)
    val authorId = reference("author_id", Authors, onDelete = ReferenceOption.CASCADE)
}
