package io.bluetape4k.examples.exposed.webflux.controller

import io.bluetape4k.examples.exposed.webflux.domain.ProductDto
import io.bluetape4k.examples.exposed.webflux.repository.ProductCoroutineRepository
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/products")
class ProductController(
    private val productRepository: ProductCoroutineRepository,
    private val r2dbcDatabase: R2dbcDatabase,
) {

    @GetMapping
    suspend fun findAll(): List<ProductDto> =
        suspendTransaction(r2dbcDatabase) {
            productRepository.findAll(Pageable.unpaged()).content
        }

    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: Long): ProductDto =
        suspendTransaction(r2dbcDatabase) {
            productRepository.findByIdOrNull(id)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: $id")
        }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun create(@RequestBody dto: ProductDto): ProductDto =
        suspendTransaction(r2dbcDatabase) {
            productRepository.save(dto.copy(id = null))
        }

    @PutMapping("/{id}")
    suspend fun update(@PathVariable id: Long, @RequestBody dto: ProductDto): ProductDto {
        return suspendTransaction(r2dbcDatabase) {
            val existing = productRepository.findByIdOrNull(id)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: $id")
            productRepository.save(dto.copy(id = existing.id ?: id))
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(@PathVariable id: Long) {
        suspendTransaction(r2dbcDatabase) {
            val existing = productRepository.findByIdOrNull(id)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: $id")
            productRepository.deleteById(existing.id ?: id)
        }
    }
}
