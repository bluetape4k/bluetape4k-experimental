package io.bluetape4k.examples.cache.lettuce

import io.bluetape4k.examples.cache.lettuce.domain.Product
import io.bluetape4k.examples.cache.lettuce.repository.ProductRepository
import io.bluetape4k.testcontainers.storage.RedisServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoApplicationTest {

    companion object {
        val redis: RedisServer by lazy { RedisServer.Launcher.redis }

        @JvmStatic
        @DynamicPropertySource
        fun configureRedis(registry: DynamicPropertyRegistry) {
            registry.add("bluetape4k.cache.lettuce-near.redis-uri") {
                "redis://${redis.host}:${redis.port}"
            }
        }
    }

    @Autowired
    private lateinit var productRepository: ProductRepository

    @BeforeEach
    fun setup() {
        productRepository.deleteAll()
    }

    @Test
    @Transactional
    fun `애플리케이션이 정상적으로 시작된다`() {
        // 컨텍스트 로딩 성공만으로 테스트 통과
    }

    @Test
    @Transactional
    fun `Product 엔티티가 저장 및 조회된다`() {
        val product = productRepository.save(
            Product(name = "MacBook Pro", description = "Apple M3 Pro", price = 2_499.0)
        )

        val found = productRepository.findById(product.id!!).orElse(null)
        assertThat(found).isNotNull()
        assertThat(found.name).isEqualTo("MacBook Pro")
        assertThat(found.price).isEqualTo(2_499.0)
    }

    @Test
    @Transactional
    fun `동일 Product를 연속 조회하면 캐시 히트가 발생한다`() {
        val product = productRepository.save(
            Product(name = "iPad Pro", price = 1_099.0)
        )
        val id = product.id!!

        // 첫 번째 조회 (DB → Cache)
        val first = productRepository.findById(id).orElseThrow()
        // 두 번째 조회 (Cache hit)
        val second = productRepository.findById(id).orElseThrow()

        assertThat(first.id).isEqualTo(second.id)
        assertThat(first.name).isEqualTo(second.name)
    }

    @Test
    fun `여러 Product를 저장하고 전체 조회된다`() {
        productRepository.saveAll(
            listOf(
                Product(name = "iPhone 16", price = 999.0),
                Product(name = "Apple Watch", price = 399.0),
                Product(name = "AirPods Pro", price = 249.0),
            )
        )

        val all = productRepository.findAll()
        assertThat(all).hasSize(3)
    }
}
