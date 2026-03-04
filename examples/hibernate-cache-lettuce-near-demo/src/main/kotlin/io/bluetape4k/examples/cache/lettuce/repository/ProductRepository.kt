package io.bluetape4k.examples.cache.lettuce.repository

import io.bluetape4k.examples.cache.lettuce.domain.Product
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.stereotype.Repository
import jakarta.persistence.QueryHint

@Repository
interface ProductRepository : JpaRepository<Product, Long> {

    @QueryHints(QueryHint(name = "jakarta.persistence.cache.retrieveMode", value = "USE"))
    fun findByName(name: String): List<Product>
}
