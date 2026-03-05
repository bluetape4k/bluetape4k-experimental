package io.bluetape4k.examples.exposed.webflux.controller

import io.bluetape4k.examples.exposed.webflux.domain.ProductDto
import io.bluetape4k.examples.exposed.webflux.domain.ProductEntity
import io.bluetape4k.examples.exposed.webflux.domain.toDto
import io.bluetape4k.examples.exposed.webflux.repository.ProductCoroutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
) {

    @GetMapping
    fun findAll(): Flow<ProductDto> =
        productRepository.findAll().map { transaction { it.toDto() } }

    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: Long): ProductDto {
        return productRepository.findByIdOrNull(id)?.let { transaction { it.toDto() } }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: $id")
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun create(@RequestBody dto: ProductDto): ProductDto {
        val entity = transaction {
            ProductEntity.new {
                name = dto.name
                price = dto.price
                stock = dto.stock
            }
        }
        return transaction { entity.toDto() }
    }

    @PutMapping("/{id}")
    suspend fun update(@PathVariable id: Long, @RequestBody dto: ProductDto): ProductDto {
        val entity = productRepository.findByIdOrNull(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: $id")
        transaction {
            entity.name = dto.name
            entity.price = dto.price
            entity.stock = dto.stock
        }
        return transaction { entity.toDto() }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(@PathVariable id: Long) {
        val entity = productRepository.findByIdOrNull(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: $id")
        productRepository.delete(entity)
    }
}
