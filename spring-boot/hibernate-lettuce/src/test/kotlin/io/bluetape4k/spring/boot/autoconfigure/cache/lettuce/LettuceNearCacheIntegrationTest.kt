package io.bluetape4k.spring.boot.autoconfigure.cache.lettuce

import io.bluetape4k.testcontainers.storage.RedisServer
import jakarta.persistence.Cacheable
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(
    classes = [LettuceNearCacheIntegrationTest.TestConfig::class],
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "bluetape4k.cache.lettuce-near.metrics.enabled=true",
        "bluetape4k.cache.lettuce-near.metrics.enable-caffeine-stats=true",
    ]
)
class LettuceNearCacheIntegrationTest {

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

    @Configuration
    @EnableAutoConfiguration
    class TestConfig

    @Autowired
    private lateinit var itemRepository: TestItemRepository

    @Test
    @Transactional
    fun `엔티티가 저장되고 조회된다`() {
        val item = itemRepository.save(TestItem(name = "TestItem"))
        val found = itemRepository.findById(item.id!!).orElse(null)
        found.shouldNotBeNull()
        found.name shouldBeEqualTo "TestItem"
    }
}

@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
class TestItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    var name: String = "",
)

interface TestItemRepository : JpaRepository<TestItem, Long>
