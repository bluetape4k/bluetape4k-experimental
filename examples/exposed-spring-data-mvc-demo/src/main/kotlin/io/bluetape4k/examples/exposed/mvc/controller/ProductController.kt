package io.bluetape4k.examples.exposed.mvc.controller

import io.bluetape4k.examples.exposed.mvc.domain.ProductDto
import io.bluetape4k.examples.exposed.mvc.domain.ProductEntity
import io.bluetape4k.examples.exposed.mvc.domain.toDto
import io.bluetape4k.examples.exposed.mvc.repository.ProductRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/products")
class ProductController(
    private val productRepository: ProductRepository,
) {

    @GetMapping
    fun findAll(): List<ProductDto> =
        transaction { productRepository.findAll().map { it.toDto() } }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<ProductDto> {
        val entity = transaction { productRepository.findById(id).orElse(null) }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(transaction { entity.toDto() })
    }

    @PostMapping
    fun create(@RequestBody dto: ProductDto): ResponseEntity<ProductDto> {
        val entity = transaction {
            ProductEntity.new {
                name = dto.name
                price = dto.price
                stock = dto.stock
            }
        }
        val created = transaction { entity.toDto() }
        return ResponseEntity.created(URI.create("/products/${created.id}")).body(created)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody dto: ProductDto): ResponseEntity<ProductDto> {
        val entity = transaction { productRepository.findById(id).orElse(null) }
            ?: return ResponseEntity.notFound().build()
        transaction {
            entity.name = dto.name
            entity.price = dto.price
            entity.stock = dto.stock
        }
        return ResponseEntity.ok(transaction { entity.toDto() })
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        val entity = transaction { productRepository.findById(id).orElse(null) }
            ?: return ResponseEntity.notFound().build()
        transaction { entity.delete() }
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/search")
    fun findByName(name: String): List<ProductDto> =
        transaction { productRepository.findByName(name).map { it.toDto() } }
}
