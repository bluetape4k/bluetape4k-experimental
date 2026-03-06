package io.bluetape4k.examples.benchmark.domain.exposed

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

class AuthorEntity(id: EntityID<Long>): LongEntity(id) {
    companion object: LongEntityClass<AuthorEntity>(Authors)

    var name by Authors.name
    var email by Authors.email
    var createdAt by Authors.createdAt
    val books by BookEntity referrersOn Books.authorId
}

class BookEntity(id: EntityID<Long>): LongEntity(id) {
    companion object: LongEntityClass<BookEntity>(Books)

    var title by Books.title
    var isbn by Books.isbn
    var price by Books.price
    var author by AuthorEntity referencedOn Books.authorId
}
