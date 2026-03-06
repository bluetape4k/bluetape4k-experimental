package io.bluetape4k.examples.benchmark

import io.bluetape4k.examples.benchmark.dto.BookDto
import io.bluetape4k.examples.benchmark.dto.CreateAuthorRequest
import io.bluetape4k.examples.benchmark.dto.AuthorDto
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity
import java.math.BigDecimal

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExposedCrudTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private lateinit var client: RestClient

    private var emailCounter = 0

    @BeforeEach
    fun setup() {
        client = RestClient.builder()
            .baseUrl("http://localhost:$port/api/exposed")
            .build()
    }

    private fun uniqueEmail(): String = "exposed-${++emailCounter}-${System.nanoTime()}@test.com"

    @Test
    fun `POST creates author with books`() {
        val request = CreateAuthorRequest(
            name = "Exposed Author",
            email = uniqueEmail(),
            books = listOf(
                BookDto(title = "Kotlin in Action", isbn = "E${System.nanoTime()}", price = BigDecimal("39.99")),
            ),
        )
        val response = client.post()
            .uri("/authors")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity<AuthorDto>()

        response.statusCode shouldBeEqualTo HttpStatus.CREATED
        response.body?.id.shouldNotBeNull()
        response.body?.name shouldBeEqualTo "Exposed Author"
        response.body?.books?.size shouldBeEqualTo 1
    }

    @Test
    fun `GET returns author by id`() {
        val request = CreateAuthorRequest(
            name = "Find Author",
            email = uniqueEmail(),
        )
        val created = client.post()
            .uri("/authors")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity<AuthorDto>().body!!

        val response = client.get()
            .uri("/authors/${created.id}")
            .retrieve()
            .toEntity<AuthorDto>()

        response.statusCode shouldBeEqualTo HttpStatus.OK
        response.body?.name shouldBeEqualTo "Find Author"
    }

    @Test
    fun `GET returns all authors`() {
        val request = CreateAuthorRequest(name = "List Author", email = uniqueEmail())
        client.post()
            .uri("/authors")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toBodilessEntity()

        val response = client.get()
            .uri("/authors")
            .retrieve()
            .toEntity<List<AuthorDto>>()

        response.statusCode shouldBeEqualTo HttpStatus.OK
        response.body.shouldNotBeNull()
    }

    @Test
    fun `PUT updates author`() {
        val request = CreateAuthorRequest(name = "Original", email = uniqueEmail())
        val created = client.post()
            .uri("/authors")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity<AuthorDto>().body!!

        val updateRequest = CreateAuthorRequest(name = "Updated", email = created.email)
        val response = client.put()
            .uri("/authors/${created.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(updateRequest)
            .retrieve()
            .toEntity<AuthorDto>()

        response.statusCode shouldBeEqualTo HttpStatus.OK
        response.body?.name shouldBeEqualTo "Updated"
    }

    @Test
    fun `DELETE removes author`() {
        val request = CreateAuthorRequest(name = "To Delete", email = uniqueEmail())
        val created = client.post()
            .uri("/authors")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .toEntity<AuthorDto>().body!!

        val response = client.delete()
            .uri("/authors/${created.id}")
            .retrieve()
            .toBodilessEntity()

        response.statusCode shouldBeEqualTo HttpStatus.NO_CONTENT
    }

    @Test
    fun `POST bulk creates multiple authors`() {
        val requests = listOf(
            CreateAuthorRequest(name = "Bulk 1", email = uniqueEmail()),
            CreateAuthorRequest(name = "Bulk 2", email = uniqueEmail()),
        )
        val response = client.post()
            .uri("/authors/bulk")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requests)
            .retrieve()
            .toEntity<List<AuthorDto>>()

        response.statusCode shouldBeEqualTo HttpStatus.CREATED
        response.body?.size shouldBeEqualTo 2
    }
}
