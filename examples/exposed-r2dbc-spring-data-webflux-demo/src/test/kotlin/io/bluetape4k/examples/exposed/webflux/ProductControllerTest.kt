package io.bluetape4k.examples.exposed.webflux

import io.bluetape4k.examples.exposed.webflux.domain.ProductDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.test.web.reactive.server.expectBodyList
import java.math.BigDecimal

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ProductControllerTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setup() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    @Order(1)
    fun `GET products returns list`() {
        webTestClient.get().uri("/products")
            .exchange()
            .expectStatus().isOk
            .expectBodyList<ProductDto>()
            .hasSize(3)  // DataInitializer가 3개 생성, @BeforeEach에서 초기화
    }

    @Test
    fun `POST product creates new entity`() {
        val dto = ProductDto(name = "New Product", price = BigDecimal("15.00"), stock = 10)
        webTestClient.post().uri("/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectStatus().isCreated
            .expectBody<ProductDto>()
            .consumeWith { result ->
                assertThat(result.responseBody?.id).isNotNull()
                assertThat(result.responseBody?.name).isEqualTo("New Product")
            }
    }

    @Test
    fun `GET product by id returns entity`() {
        // 먼저 생성
        val dto = ProductDto(name = "Findable", price = BigDecimal("5.00"), stock = 1)
        val created = webTestClient.post().uri("/products")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(dto)
            .exchange().expectBody<ProductDto>().returnResult().responseBody!!

        webTestClient.get().uri("/products/${created.id}")
            .exchange()
            .expectStatus().isOk
            .expectBody<ProductDto>()
            .consumeWith { result ->
                assertThat(result.responseBody?.name).isEqualTo("Findable")
            }
    }

    @Test
    fun `PUT product updates entity`() {
        val dto = ProductDto(name = "Before Update", price = BigDecimal("5.00"), stock = 1)
        val created = webTestClient.post().uri("/products")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(dto)
            .exchange().expectBody<ProductDto>().returnResult().responseBody!!

        val updated = dto.copy(name = "After Update", price = BigDecimal("10.00"))
        webTestClient.put().uri("/products/${created.id}")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(updated)
            .exchange()
            .expectStatus().isOk
            .expectBody<ProductDto>()
            .consumeWith { result ->
                assertThat(result.responseBody?.name).isEqualTo("After Update")
            }
    }

    @Test
    fun `DELETE product removes entity`() {
        val dto = ProductDto(name = "To Be Deleted", price = BigDecimal("1.00"), stock = 1)
        val created = webTestClient.post().uri("/products")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(dto)
            .exchange().expectBody<ProductDto>().returnResult().responseBody!!

        webTestClient.delete().uri("/products/${created.id}")
            .exchange()
            .expectStatus().isNoContent()

        webTestClient.get().uri("/products/${created.id}")
            .exchange()
            .expectStatus().isNotFound
    }
}
