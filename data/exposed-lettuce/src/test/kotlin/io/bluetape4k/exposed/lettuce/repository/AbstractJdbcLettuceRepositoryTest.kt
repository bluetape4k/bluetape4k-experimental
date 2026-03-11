package io.bluetape4k.exposed.lettuce.repository

import io.bluetape4k.exposed.lettuce.domain.ItemDto
import io.bluetape4k.exposed.lettuce.domain.ItemEntity
import io.bluetape4k.exposed.lettuce.domain.ItemRepository
import io.bluetape4k.exposed.lettuce.domain.Items
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.logging.KLogging
import io.bluetape4k.redis.lettuce.codec.LettuceBinaryCodec
import io.bluetape4k.testcontainers.storage.RedisServer
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * [AbstractJdbcLettuceRepository] 통합 테스트.
 *
 * - H2 in-memory DB (Exposed DAO)
 * - Redis Testcontainers (Lettuce)
 */
class AbstractJdbcLettuceRepositoryTest {

    companion object : KLogging() {
        val redis: RedisServer by lazy { RedisServer.Launcher.redis }

        val redisClient: RedisClient by lazy {
            RedisClient.create(
                RedisServer.Launcher.LettuceLib.getRedisURI(redis.host, redis.port)
            )
        }

        val connection: StatefulRedisConnection<String, ItemDto> by lazy {
            redisClient.connect(LettuceBinaryCodec(BinarySerializers.LZ4Fory))
        }

        @JvmStatic
        @BeforeAll
        fun setupDb() {
            Database.connect(
                url = "jdbc:h2:mem:test-exposed-lettuce;DB_CLOSE_DELAY=-1;MODE=MySQL",
                driver = "org.h2.Driver",
            )
            transaction {
                SchemaUtils.create(Items)
            }
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            runCatching { connection.close() }
            runCatching { redisClient.shutdown() }
        }
    }

    private lateinit var repo: ItemRepository

    @BeforeEach
    fun setUp() {
        transaction { Items.deleteAll() }
        repo = ItemRepository(connection)
        repo.clearCache()
    }

    @Test
    fun `findById - DB에 없는 ID는 null 반환`() {
        repo.findById(999L).shouldBeNull()
    }

    @Test
    fun `save - WRITE_THROUGH로 저장 후 findById로 조회`() {
        val dto = ItemDto(0L, "Widget", BigDecimal("9.99"))
        // DB에 먼저 row 생성 후 저장 (upsert 시 id 필요)
        val created = repo.createInDb("Widget", BigDecimal("9.99"))

        repo.save(created.id, created)
        val found = repo.findById(created.id)

        found.shouldNotBeNull()
        found.name shouldBeEqualTo "Widget"
        found.price shouldBeEqualTo BigDecimal("9.99")
    }

    @Test
    fun `findById - 캐시 미스 시 DB에서 Read-through 로드`() {
        val created = repo.createInDb("Gadget", BigDecimal("49.99"))

        // 캐시에는 없고 DB에만 있는 상태
        val found = repo.findById(created.id)

        found.shouldNotBeNull()
        found.name shouldBeEqualTo "Gadget"
        found.price shouldBeEqualTo BigDecimal("49.99")
    }

    @Test
    fun `existsById - 저장 후 존재 확인`() {
        val created = repo.createInDb("Tool", BigDecimal("19.99"))
        repo.save(created.id, created)

        repo.existsById(created.id) shouldBeEqualTo true
        repo.existsById(99999L) shouldBeEqualTo false
    }

    @Test
    fun `deleteById - 삭제 후 findById는 null`() {
        val created = repo.createInDb("Toy", BigDecimal("5.00"))
        repo.save(created.id, created)

        repo.deleteById(created.id)

        repo.existsById(created.id) shouldBeEqualTo false
    }

    @Test
    fun `saveAll - 여러 항목 일괄 저장 후 findAllById`() {
        val item1 = repo.createInDb("Item1", BigDecimal("1.00"))
        val item2 = repo.createInDb("Item2", BigDecimal("2.00"))

        repo.saveAll(mapOf(item1.id to item1, item2.id to item2))

        val result = repo.findAllById(setOf(item1.id, item2.id))
        result.size shouldBeEqualTo 2
        result[item1.id]?.name shouldBeEqualTo "Item1"
        result[item2.id]?.name shouldBeEqualTo "Item2"
    }

    @Test
    fun `cacheSize - 저장 후 캐시 크기 확인`() {
        val item1 = repo.createInDb("A", BigDecimal("1.00"))
        val item2 = repo.createInDb("B", BigDecimal("2.00"))
        repo.saveAll(mapOf(item1.id to item1, item2.id to item2))

        repo.cacheSize() shouldBeEqualTo 2L
    }

    @Test
    fun `clearCache - 캐시 비운 후 DB Read-through로 재조회`() {
        val created = repo.createInDb("ClearTest", BigDecimal("3.00"))
        repo.save(created.id, created)

        repo.clearCache()
        repo.cacheSize() shouldBeEqualTo 0L

        // DB에는 남아있으므로 Read-through로 재조회 가능
        val found = repo.findById(created.id)
        found.shouldNotBeNull()
        found.name shouldBeEqualTo "ClearTest"
    }
}
