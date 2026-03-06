package io.bluetape4k.examples.benchmark.repository.jpa

import io.bluetape4k.examples.benchmark.domain.jpa.BookJpa
import org.springframework.data.jpa.repository.JpaRepository

interface BookJpaRepository: JpaRepository<BookJpa, Long>
