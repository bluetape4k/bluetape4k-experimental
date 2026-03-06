package io.bluetape4k.examples.benchmark.repository.exposed

import io.bluetape4k.examples.benchmark.domain.exposed.AuthorEntity
import io.bluetape4k.examples.benchmark.domain.exposed.Authors
import io.bluetape4k.examples.benchmark.domain.exposed.BookEntity
import io.bluetape4k.examples.benchmark.domain.exposed.Books
import io.bluetape4k.examples.benchmark.dto.AuthorDto
import io.bluetape4k.examples.benchmark.dto.BookDto
import io.bluetape4k.examples.benchmark.dto.CreateAuthorRequest
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository

@Repository
class AuthorExposedRepository {

    fun findAll(): List<AuthorDto> = transaction {
        // .with()로 books를 한 번에 eager-load하여 N+1 방지
        AuthorEntity.all().with(AuthorEntity::books).map { it.toDto() }
    }

    fun findById(id: Long): AuthorDto? = transaction {
        AuthorEntity.findById(id)?.toDto()
    }

    fun create(request: CreateAuthorRequest): AuthorDto = transaction {
        val author = AuthorEntity.new {
            name = request.name
            email = request.email
        }
        request.books.forEach { bookDto ->
            BookEntity.new {
                title = bookDto.title
                isbn = bookDto.isbn
                price = bookDto.price
                this.author = author
            }
        }
        author.toDto()
    }

    fun update(id: Long, request: CreateAuthorRequest): AuthorDto? = transaction {
        val author = AuthorEntity.findById(id) ?: return@transaction null
        author.name = request.name
        author.email = request.email
        author.toDto()
    }

    fun delete(id: Long): Boolean = transaction {
        val author = AuthorEntity.findById(id) ?: return@transaction false
        author.delete()
        true
    }

    fun bulkCreate(requests: List<CreateAuthorRequest>): List<AuthorDto> = transaction {
        val createdAuthors = requests.map { request ->
            val author = AuthorEntity.new {
                name = request.name
                email = request.email
            }
            request.books.forEach { bookDto ->
                BookEntity.new {
                    title = bookDto.title
                    isbn = bookDto.isbn
                    price = bookDto.price
                    this.author = author
                }
            }
            author
        }
        // 생성된 author 목록의 books를 한 번에 eager-load하여 N+1 방지
        AuthorEntity.forIds(createdAuthors.map { it.id.value }).with(AuthorEntity::books).map { it.toDto() }
    }

    private fun AuthorEntity.toDto(): AuthorDto = AuthorDto(
        id = this.id.value,
        name = this.name,
        email = this.email,
        books = this.books.map { it.toDto() },
    )

    private fun BookEntity.toDto(): BookDto = BookDto(
        id = this.id.value,
        title = this.title,
        isbn = this.isbn,
        price = this.price,
    )
}
