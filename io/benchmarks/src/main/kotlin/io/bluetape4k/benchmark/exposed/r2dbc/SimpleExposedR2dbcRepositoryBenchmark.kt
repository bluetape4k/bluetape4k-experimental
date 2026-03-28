package io.bluetape4k.benchmark.exposed.r2dbc

import io.bluetape4k.exposed.core.HasIdentifier
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.spring.data.exposed.r2dbc.repository.support.SimpleExposedR2dbcRepository
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.migration.r2dbc.MigrationUtils
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * `SimpleExposedR2dbcRepository` 핵심 CRUD/paging 경로의 비용을 측정합니다.
 *
 * Spring 컨텍스트 없이 repository 구현체를 직접 세워
 * 내부 `suspendTransaction` 비용과 기본 CRUD/paging 성능을 비교할 수 있게 구성합니다.
 */
object BenchmarkUsers: LongIdTable("benchmark_users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255)
    val age = integer("age")
}

data class BenchmarkUser(
    override val id: Long? = null,
    val name: String,
    val email: String,
    val age: Int,
): HasIdentifier<Long>

private fun benchmarkUserFrom(row: ResultRow): BenchmarkUser =
    BenchmarkUser(
        id = row[BenchmarkUsers.id].value,
        name = row[BenchmarkUsers.name],
        email = row[BenchmarkUsers.email],
        age = row[BenchmarkUsers.age],
    )

private fun benchmarkPersistValues(domain: BenchmarkUser): Map<Column<*>, Any?> =
    mapOf(
        BenchmarkUsers.name to domain.name,
        BenchmarkUsers.email to domain.email,
        BenchmarkUsers.age to domain.age,
    )

private fun newRepository(): SimpleExposedR2dbcRepository<BenchmarkUser, Long> =
    SimpleExposedR2dbcRepository(
        table = BenchmarkUsers,
        toDomainMapper = ::benchmarkUserFrom,
        persistValuesProvider = ::benchmarkPersistValues,
        idExtractor = { it.id },
    )

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class SimpleExposedR2dbcRepositoryReadBenchmark {

    @Param("100", "1000")
    var datasetSize: Int = 100

    private lateinit var repository: SimpleExposedR2dbcRepository<BenchmarkUser, Long>
    private lateinit var pageable: Pageable
    private var hotUserId: Long = -1L

    @Setup
    fun setUp() {
        runSuspendIO {
            connectDatabase()
            repository = newRepository()
            pageable = PageRequest.of(0, 20)
            resetTable(datasetSize)
            hotUserId = datasetSize.toLong().coerceAtLeast(1L)
        }
    }

    @TearDown
    fun tearDown() {
        runSuspendIO {
            suspendTransaction {
                BenchmarkUsers.deleteAll()
            }
        }
    }

    /**
     * hot row 단건 조회 경로를 측정합니다.
     */
    @Benchmark
    fun findByIdHot(): BenchmarkUser? {
        var result: BenchmarkUser? = null
        runSuspendIO {
            result = repository.findByIdOrNull(hotUserId)
        }
        return result
    }

    /**
     * count 경로를 측정합니다.
     */
    @Benchmark
    fun countAll(): Long {
        var result = 0L
        runSuspendIO {
            result = repository.count()
        }
        return result
    }

    /**
     * 첫 페이지 조회 경로를 측정합니다.
     */
    @Benchmark
    fun findFirstPage(): Int {
        var result = 0
        runSuspendIO {
            result = repository.findAll(pageable).content.size
        }
        return result
    }

    private suspend fun resetTable(size: Int) {
        suspendTransaction {
            migrate(BenchmarkUsers)
            BenchmarkUsers.deleteAll()
            repeat(size) { index ->
                BenchmarkUsers.insertAndGetId {
                    it[name] = "User-$index"
                    it[email] = "user-$index@example.com"
                    it[age] = 20 + (index % 30)
                }
            }
        }
    }

    private companion object {
        private var connected = false

        private fun connectDatabase() {
            if (!connected) {
                R2dbcDatabase.connect(
                    url = "r2dbc:h2:mem:///benchmark_exposed_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=LEGACY",
                    driver = "h2",
                )
                connected = true
            }
        }
    }
}

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class SimpleExposedR2dbcRepositoryWriteBenchmark {

    private lateinit var repository: SimpleExposedR2dbcRepository<BenchmarkUser, Long>
    private val sequence = AtomicInteger()

    @Setup
    fun setUp() {
        runSuspendIO {
            connectDatabase()
            repository = newRepository()
            suspendTransaction {
                migrate(BenchmarkUsers)
                BenchmarkUsers.deleteAll()
            }
            sequence.set(0)
        }
    }

    @TearDown
    fun tearDown() {
        runSuspendIO {
            suspendTransaction {
                BenchmarkUsers.deleteAll()
            }
        }
    }

    /**
     * 신규 row 저장 경로를 측정합니다.
     */
    @Benchmark
    fun saveNewUser(): Long? {
        var result: Long? = null
        runSuspendIO {
            val id = sequence.incrementAndGet()
            result = repository.save(
                BenchmarkUser(
                    name = "Bench User $id",
                    email = "bench-$id@example.com",
                    age = 20 + (id % 30),
                )
            ).id
        }
        return result
    }

    private companion object {
        private var connected = false

        private fun connectDatabase() {
            if (!connected) {
                R2dbcDatabase.connect(
                    url = "r2dbc:h2:mem:///benchmark_exposed_write_${UUID.randomUUID()};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=LEGACY",
                    driver = "h2",
                )
                connected = true
            }
        }
    }
}

private suspend fun migrate(vararg tables: LongIdTable) {
    val statements = MigrationUtils.statementsRequiredForDatabaseMigration(*tables, withLogs = false)
    if (statements.isNotEmpty()) {
        org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager.current().execInBatch(statements)
    }
}
