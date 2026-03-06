package io.bluetape4k.examples.benchmark.repository.jpa

import io.bluetape4k.examples.benchmark.domain.jpa.AuthorJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AuthorJpaRepository: JpaRepository<AuthorJpa, Long> {

    @Query("SELECT DISTINCT a FROM AuthorJpa a LEFT JOIN FETCH a.books WHERE a.id = :id")
    fun findByIdWithBooks(id: Long): AuthorJpa?

    @Query("SELECT DISTINCT a FROM AuthorJpa a LEFT JOIN FETCH a.books")
    fun findAllWithBooks(): List<AuthorJpa>
}
