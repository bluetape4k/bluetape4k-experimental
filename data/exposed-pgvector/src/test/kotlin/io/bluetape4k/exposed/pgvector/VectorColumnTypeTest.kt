package io.bluetape4k.exposed.pgvector

import com.pgvector.PGvector
import io.bluetape4k.exposed.tests.AbstractExposedTest
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNear
import org.amshove.kluent.shouldNotBeNull
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * pgvector 컬럼 타입 및 거리 연산 통합 테스트.
 *
 * pgvector 전용 컨테이너(`pgvector/pgvector:pg16`)를 사용한다.
 * `TestDB.POSTGRESQL`은 pgvector 확장이 없으므로 별도 컨테이너를 사용한다.
 */
class VectorColumnTypeTest : AbstractExposedTest() {

    companion object : KLogging() {
        private const val DIMENSION = 3

        @JvmStatic
        val pgvectorContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer(
                DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
            ).apply { start() }

        @JvmStatic
        val db: Database by lazy {
            Database.connect(
                url = pgvectorContainer.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = pgvectorContainer.username,
                password = pgvectorContainer.password,
            ).also { db ->
                transaction(db) {
                    exec("CREATE EXTENSION IF NOT EXISTS vector")
                    PGvector.addVectorType(connection.connection as java.sql.Connection)
                }
            }
        }
    }

    object Embeddings : LongIdTable("embeddings") {
        val name = varchar("name", 255)
        val embedding = vector("embedding", DIMENSION)
    }

    /**
     * pgvector 전용 테이블 생성/삭제를 처리하는 헬퍼.
     *
     * [PGvector.addVectorType]을 매 트랜잭션마다 호출해야 한다.
     */
    private fun withVectorTables(vararg tables: Table, statement: JdbcTransaction.() -> Unit) {
        transaction(db) {
            runCatching { SchemaUtils.drop(*tables) }
            SchemaUtils.create(*tables)
        }
        try {
            transaction(db) {
                PGvector.addVectorType(connection.connection as java.sql.Connection)
                statement()
            }
        } finally {
            transaction(db) {
                runCatching { SchemaUtils.drop(*tables) }
            }
        }
    }

    @Test
    fun `벡터 저장 및 조회`() {
        val vector = floatArrayOf(1.0f, 2.0f, 3.0f)

        withVectorTables(Embeddings) {
            Embeddings.insert {
                it[name] = "test"
                it[embedding] = vector
            }

            val row = Embeddings.selectAll().single()
            row[Embeddings.name] shouldBeEqualTo "test"

            val result = row[Embeddings.embedding]
            result.shouldNotBeNull()
            result.size shouldBeEqualTo DIMENSION
            result[0].toDouble().shouldBeNear(1.0, 0.001)
            result[1].toDouble().shouldBeNear(2.0, 0.001)
            result[2].toDouble().shouldBeNear(3.0, 0.001)
        }
    }

    @Test
    fun `코사인 거리 기준 유사도 검색`() {
        withVectorTables(Embeddings) {
            Embeddings.insert {
                it[name] = "a"
                it[embedding] = floatArrayOf(1.0f, 0.0f, 0.0f)
            }
            Embeddings.insert {
                it[name] = "b"
                it[embedding] = floatArrayOf(0.0f, 1.0f, 0.0f)
            }
            Embeddings.insert {
                it[name] = "c"
                it[embedding] = floatArrayOf(0.9f, 0.1f, 0.0f)
            }

            val results = Embeddings
                .selectAll()
                .orderBy(Embeddings.embedding.cosineDistance(Embeddings.embedding) to SortOrder.ASC)
                .map { it[Embeddings.name] }

            results.size shouldBeEqualTo 3
        }
    }

    @Test
    fun `L2 거리 기준 유사도 검색`() {
        withVectorTables(Embeddings) {
            Embeddings.insert {
                it[name] = "origin"
                it[embedding] = floatArrayOf(0.0f, 0.0f, 0.0f)
            }
            Embeddings.insert {
                it[name] = "near"
                it[embedding] = floatArrayOf(1.0f, 0.0f, 0.0f)
            }
            Embeddings.insert {
                it[name] = "far"
                it[embedding] = floatArrayOf(10.0f, 10.0f, 10.0f)
            }

            val results = Embeddings
                .selectAll()
                .orderBy(Embeddings.embedding.l2Distance(Embeddings.embedding) to SortOrder.ASC)
                .map { it[Embeddings.name] }

            results.size shouldBeEqualTo 3
        }
    }

    @Test
    fun `VectorColumnType dimension이 0이면 예외 발생`() {
        assertThrows<IllegalArgumentException> {
            VectorColumnType(0)
        }
    }

    @Test
    fun `VectorColumnType dimension이 음수이면 예외 발생`() {
        assertThrows<IllegalArgumentException> {
            VectorColumnType(-1)
        }
    }

    @Test
    fun `VectorColumnType sqlType 검증`() {
        val columnType = VectorColumnType(128)
        columnType.sqlType() shouldBeEqualTo "VECTOR(128)"
    }

    @Test
    fun `여러 벡터 저장 후 전체 조회`() {
        withVectorTables(Embeddings) {
            repeat(5) { i ->
                Embeddings.insert {
                    it[name] = "item-$i"
                    it[embedding] = floatArrayOf(i.toFloat(), (i * 2).toFloat(), (i * 3).toFloat())
                }
            }

            val results = Embeddings.selectAll().toList()
            results.size shouldBeEqualTo 5
        }
    }
}
