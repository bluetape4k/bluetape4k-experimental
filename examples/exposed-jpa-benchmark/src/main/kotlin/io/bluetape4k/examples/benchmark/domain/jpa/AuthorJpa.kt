package io.bluetape4k.examples.benchmark.domain.jpa

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "authors_jpa")
class AuthorJpa(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(name = "created_at")
    var createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "author", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var books: MutableList<BookJpa> = mutableListOf(),
) {
    fun addBook(book: BookJpa) {
        books.add(book)
        book.author = this
    }

    fun removeBook(book: BookJpa) {
        books.remove(book)
        book.author = null
    }
}
