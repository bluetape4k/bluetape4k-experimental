package io.bluetape4k.exposed.lettuce.repository

import io.bluetape4k.exposed.lettuce.map.ExposedEntityMapLoader
import io.bluetape4k.exposed.lettuce.map.ExposedEntityMapWriter
import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import io.bluetape4k.exposed.lettuce.map.LettuceLoadedMap
import io.lettuce.core.RedisClient
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.BatchInsertStatement
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import java.io.Closeable

/**
 * Exposed DSL + Lettuce Redis 캐시를 결합한 추상 레포지토리.
 *
 * 서브클래스는 4개 추상 멤버를 구현한다:
 * - [table]: Exposed [IdTable]
 * - [ResultRow.toEntity]: ResultRow → E 변환
 * - [UpdateStatement.updateEntity]: UPDATE 컬럼 매핑
 * - [BatchInsertStatement.insertEntity]: INSERT 컬럼 매핑
 *
 * @param ID PK 타입
 * @param E 엔티티(DTO) 타입
 * @param client Lettuce [RedisClient]
 * @param config [LettuceCacheConfig] 설정
 */
abstract class AbstractJdbcLettuceRepository<ID : Comparable<ID>, E : Any>(
    client: RedisClient,
    config: LettuceCacheConfig = LettuceCacheConfig.READ_WRITE_THROUGH,
) : Closeable {

    abstract val table: IdTable<ID>
    abstract fun ResultRow.toEntity(): E
    abstract fun UpdateStatement.updateEntity(entity: E)
    abstract fun BatchInsertStatement.insertEntity(entity: E)

    open fun serializeKey(id: ID): String = id.toString()

    protected val cache: LettuceLoadedMap<ID, E> by lazy {
        LettuceLoadedMap(
            client = client,
            loader = ExposedEntityMapLoader(
                table = table,
                toEntity = { row -> with(this@AbstractJdbcLettuceRepository) { row.toEntity() } },
            ),
            writer = ExposedEntityMapWriter(
                table = table,
                writeMode = config.writeMode,
                updateEntity = { stmt, e -> with(this@AbstractJdbcLettuceRepository) { stmt.updateEntity(e) } },
                insertEntity = { stmt, e -> with(this@AbstractJdbcLettuceRepository) { stmt.insertEntity(e) } },
                retryAttempts = config.writeRetryAttempts,
                retryInterval = config.writeRetryInterval,
            ),
            config = config,
            keySerializer = ::serializeKey,
        )
    }

    fun findById(id: ID): E? = cache[id]
    fun findAll(ids: Collection<ID>): Map<ID, E> = cache.getAll(ids.toSet())
    fun save(id: ID, entity: E) { cache[id] = entity }
    fun delete(id: ID) { cache.delete(id) }
    fun deleteAll(ids: Collection<ID>) { cache.deleteAll(ids) }
    fun clearCache() { cache.clear() }

    override fun close() = cache.close()
}
