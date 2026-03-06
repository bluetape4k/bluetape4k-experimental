package io.bluetape4k.examples.benchmark.service

import io.bluetape4k.examples.benchmark.domain.jpa.AuthorJpa
import io.bluetape4k.examples.benchmark.domain.jpa.BookJpa
import io.bluetape4k.examples.benchmark.dto.AuthorDto
import io.bluetape4k.examples.benchmark.dto.BookDto
import io.bluetape4k.examples.benchmark.dto.CreateAuthorRequest
import io.bluetape4k.examples.benchmark.repository.jpa.AuthorJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class JpaAuthorService(
    private val authorRepository: AuthorJpaRepository,
) {

    @Transactional(readOnly = true)
    fun findAll(): List<AuthorDto> =
        authorRepository.findAllWithBooks().map { it.toDto() }

    @Transactional(readOnly = true)
    fun findById(id: Long): AuthorDto? =
        authorRepository.findByIdWithBooks(id)?.toDto()

    fun create(request: CreateAuthorRequest): AuthorDto {
        val author = AuthorJpa(
            name = request.name,
            email = request.email,
        )
        request.books.forEach { bookDto ->
            author.addBook(
                BookJpa(
                    title = bookDto.title,
                    isbn = bookDto.isbn,
                    price = bookDto.price,
                )
            )
        }
        return authorRepository.saveAndFlush(author).toDto()
    }

    fun update(id: Long, request: CreateAuthorRequest): AuthorDto? {
        val author = authorRepository.findByIdWithBooks(id) ?: return null
        author.name = request.name
        author.email = request.email
        return authorRepository.saveAndFlush(author).toDto()
    }

    fun delete(id: Long): Boolean {
        if (!authorRepository.existsById(id)) return false
        authorRepository.deleteById(id)
        return true
    }

    fun bulkCreate(requests: List<CreateAuthorRequest>): List<AuthorDto> {
        val authors = requests.map { request ->
            val author = AuthorJpa(
                name = request.name,
                email = request.email,
            )
            request.books.forEach { bookDto ->
                author.addBook(
                    BookJpa(
                        title = bookDto.title,
                        isbn = bookDto.isbn,
                        price = bookDto.price,
                    )
                )
            }
            author
        }
        return authorRepository.saveAllAndFlush(authors).map { it.toDto() }
    }

    private fun AuthorJpa.toDto(): AuthorDto = AuthorDto(
        id = this.id,
        name = this.name,
        email = this.email,
        books = this.books.map { it.toDto() },
    )

    private fun BookJpa.toDto(): BookDto = BookDto(
        id = this.id,
        title = this.title,
        isbn = this.isbn,
        price = this.price,
    )
}
