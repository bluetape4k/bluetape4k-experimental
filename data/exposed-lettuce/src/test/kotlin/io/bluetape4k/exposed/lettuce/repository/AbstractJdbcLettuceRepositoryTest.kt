package io.bluetape4k.exposed.lettuce.repository

import io.bluetape4k.exposed.lettuce.domain.ItemDto
import io.bluetape4k.exposed.lettuce.domain.ItemRepository
import io.bluetape4k.exposed.lettuce.domain.ItemTable
import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.storage.RedisServer
import io.lettuce.core.RedisClient
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * [AbstractJdbcLettuceRepository] 통합 테스트.
 *
 * - H2 in-memory DB (Exposed DSL)
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

        @JvmStatic
        @BeforeAll
        fun setupDb() {
            Database.connect(
                url = "jdbc:h2:mem:test-exposed-lettuce;DB_CLOSE_DELAY=-1;MODE=MySQL",
                driver = "org.h2.Driver",
            )
            transaction {
                SchemaUtils.create(ItemTable)
            }
        }

        @JvmStatic
        @AfterAll
        fun tearDownAll() {
            runCatching { redisClient.shutdown() }
        }
    }

    private lateinit var repo: ItemRepository

    @BeforeEach
    fun setUp() {
        transaction { ItemTable.deleteAll() }
        repo = ItemRepository(redisClient)
        repo.clearCache()
    }

    @Test
    fun `findById - DB에 없는 ID는 null 반환`() {
        repo.findById(999L).shouldBeNull()
    }

    @Test
    fun `save - WRITE_THROUGH로 저장 후 findById로 조회`() {
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
    fun `delete - 삭제 후 findById는 null`() {
        val created = repo.createInDb("Toy", BigDecimal("5.00"))
        repo.save(created.id, created)

        repo.delete(created.id)

        repo.findById(created.id).shouldBeNull()
    }

    @Test
    fun `findAll - 여러 항목 일괄 조회`() {
        val item1 = repo.createInDb("Item1", BigDecimal("1.00"))
        val item2 = repo.createInDb("Item2", BigDecimal("2.00"))

        repo.save(item1.id, item1)
        repo.save(item2.id, item2)

        val result = repo.findAll(setOf(item1.id, item2.id))
        result.size shouldBeEqualTo 2
        result[item1.id]?.name shouldBeEqualTo "Item1"
        result[item2.id]?.name shouldBeEqualTo "Item2"
    }

    @Test
    fun `clearCache - 캐시 비운 후 DB Read-through로 재조회`() {
        val created = repo.createInDb("ClearTest", BigDecimal("3.00"))
        repo.save(created.id, created)

        repo.clearCache()

        // DB에는 남아있으므로 Read-through로 재조회 가능
        val found = repo.findById(created.id)
        found.shouldNotBeNull()
        found.name shouldBeEqualTo "ClearTest"
    }

    @Test
    fun `write-behind - 캐시 저장 후 DB에 비동기로 반영됨`() {
        val wbRepo = ItemRepository(redisClient, LettuceCacheConfig.WRITE_BEHIND)
        wbRepo.clearCache()

        // DB에 먼저 row 생성 (batchInsert가 id 포함 삽입이므로 기존 row 있어야 update 가능)
        val created = wbRepo.createInDb("WBItem", BigDecimal("7.77"))

        // cache에 저장 → write-behind 큐에 적재
        wbRepo.save(created.id, created)

        // flush 대기 (write-behind delay 기본 1000ms)
        Thread.sleep(2000L)

        // DB에서 직접 조회하여 반영 확인
        val dbRow = transaction {
            ItemTable.selectAll()
                .where { ItemTable.id eq created.id }
                .singleOrNull()
        }
        dbRow.shouldNotBeNull()
        dbRow[ItemTable.name] shouldBeEqualTo "WBItem"

        wbRepo.close()
    }

    @Test
    fun `NONE writeMode - Redis 전용, DB 쓰기 없음`() {
        val noneRepo = ItemRepository(redisClient, LettuceCacheConfig.READ_ONLY)
        noneRepo.clearCache()

        // DB에 없는 항목을 캐시에만 저장
        val dto = ItemDto(99999L, "CacheOnly", BigDecimal("0.01"))
        noneRepo.save(dto.id, dto)

        // 캐시에서 조회됨
        val found = noneRepo.findById(dto.id)
        found.shouldNotBeNull()
        found.name shouldBeEqualTo "CacheOnly"

        // DB에는 없음
        val dbRow = transaction {
            ItemTable.selectAll()
                .where { ItemTable.id eq dto.id }
                .singleOrNull()
        }
        dbRow.shouldBeNull()

        noneRepo.close()
    }
}
