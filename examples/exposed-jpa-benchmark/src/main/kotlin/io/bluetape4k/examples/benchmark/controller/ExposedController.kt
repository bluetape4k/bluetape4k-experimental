package io.bluetape4k.examples.benchmark.controller

import io.bluetape4k.examples.benchmark.dto.AuthorDto
import io.bluetape4k.examples.benchmark.dto.CreateAuthorRequest
import io.bluetape4k.examples.benchmark.service.ExposedAuthorService
import org.springframework.http.HttpStatus
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
@RequestMapping("/api/exposed/authors")
class ExposedController(
    private val authorService: ExposedAuthorService,
) {

    @GetMapping
    fun findAll(): ResponseEntity<List<AuthorDto>> =
        ResponseEntity.ok(authorService.findAll())

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<AuthorDto> {
        val author = authorService.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(author)
    }

    @PostMapping
    fun create(@RequestBody request: CreateAuthorRequest): ResponseEntity<AuthorDto> {
        val created = authorService.create(request)
        return ResponseEntity.created(URI.create("/api/exposed/authors/${created.id}")).body(created)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody request: CreateAuthorRequest): ResponseEntity<AuthorDto> {
        val updated = authorService.update(id, request) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        if (!authorService.delete(id)) return ResponseEntity.notFound().build()
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/bulk")
    fun bulkCreate(@RequestBody requests: List<CreateAuthorRequest>): ResponseEntity<List<AuthorDto>> {
        val created = authorService.bulkCreate(requests)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }
}
