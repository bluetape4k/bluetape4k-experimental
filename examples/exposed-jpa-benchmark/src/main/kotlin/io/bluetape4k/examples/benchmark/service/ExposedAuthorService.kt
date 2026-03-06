package io.bluetape4k.examples.benchmark.service

import io.bluetape4k.examples.benchmark.dto.AuthorDto
import io.bluetape4k.examples.benchmark.dto.CreateAuthorRequest
import io.bluetape4k.examples.benchmark.repository.exposed.AuthorExposedRepository
import org.springframework.stereotype.Service

@Service
class ExposedAuthorService(
    private val authorRepository: AuthorExposedRepository,
) {

    fun findAll(): List<AuthorDto> = authorRepository.findAll()

    fun findById(id: Long): AuthorDto? = authorRepository.findById(id)

    fun create(request: CreateAuthorRequest): AuthorDto = authorRepository.create(request)

    fun update(id: Long, request: CreateAuthorRequest): AuthorDto? = authorRepository.update(id, request)

    fun delete(id: Long): Boolean = authorRepository.delete(id)

    fun bulkCreate(requests: List<CreateAuthorRequest>): List<AuthorDto> = authorRepository.bulkCreate(requests)
}
