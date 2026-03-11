package io.bluetape4k.exposed.lettuce.repository

import io.bluetape4k.exposed.lettuce.map.ExposedEntityMapLoader
import io.bluetape4k.exposed.lettuce.map.ExposedEntityMapWriter
import io.bluetape4k.exposed.lettuce.map.LettuceCacheConfig
import io.bluetape4k.exposed.lettuce.map.LettuceLoadedMap
import io.bluetape4k.exposed.lettuce.map.WriteMode
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.codec.RedisCodec
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

/**
 * Exposed DAO + Lettuce Redis 캐시를 결합한 추상 레포지토리.
 *
 * 서브클래스는 [entityClass], [codec], [config], [toValue], [upsert]를 구현하면
 * Read-through/Write-through/Write-behind 캐싱이 자동으로 적용된다.
 *
 * ## 사용 예
 * ```kotlin
 * class UserRepository(connection: StatefulRedisConnection<String, UserDto>) :
 *     AbstractJdbcLettuceRepository<UserEntity, Long, UserDto>(connection) {
 *
 *     override val entityClass = UserEntity
 *     override val codec = ... // RedisCodec<String, UserDto>
 *     override val config = LettuceCacheConfig(cacheName = "users")
 *
 *     override fun toValue(entity: UserEntity) = UserDto(entity.id.value, entity.name, entity.email)
 *
 *     override fun upsert(id: Long, value: UserDto) {
 *         val entity = UserEntity.findById(id) ?: UserEntity.new(id) {}
 *         entity.name = value.name
 *         entity.email = value.email
 *     }
 * }
 * ```
 *
 * @param E Exposed Entity 타입
 * @param ID Entity ID 타입
 * @param V 캐시에 저장할 값(DTO) 타입
 */
abstract class AbstractJdbcLettuceRepository<E: Entity<ID>, ID: Any, V: Any>(
    connection: StatefulRedisConnection<String, V>,
) : AutoCloseable {

    /** Exposed EntityClass (companion object) */
    protected abstract val entityClass: EntityClass<ID, E>

    /** RedisCodec<String, V> */
    protected abstract val codec: RedisCodec<String, V>

    /** [LettuceCacheConfig] 설정 */
    protected abstract val config: LettuceCacheConfig

    /** Entity → V 변환 */
    protected abstract fun toValue(entity: E): V

    /**
     * ID + V를 DB에 upsert한다.
     * 구현 시 Exposed 트랜잭션 바깥에서 호출되므로, [ExposedEntityMapWriter]가 자체 트랜잭션을 열어준다.
     */
    protected abstract fun upsert(id: ID, value: V)

    /** 키 직렬화 함수 (기본: toString()) */
    protected open val keySerializer: (ID) -> String = { it.toString() }

    private val loader by lazy {
        ExposedEntityMapLoader(entityClass, ::toValue)
    }

    private val writer by lazy {
        ExposedEntityMapWriter(entityClass, ::upsert)
    }

    protected val cache: LettuceLoadedMap<ID, V> by lazy {
        LettuceLoadedMap(
            connection = connection,
            codec = codec,
            config = config,
            loader = loader,
            writer = if (config.writeMode != WriteMode.READ_ONLY) writer else null,
            keySerializer = keySerializer,
        )
    }

    /** ID로 값을 조회한다 (캐시 미스 시 DB 로드). */
    fun findById(id: ID): V? = cache.get(id)

    /** 여러 ID로 값을 일괄 조회한다. */
    fun findAllById(ids: Set<ID>): Map<ID, V> = cache.getAll(ids)

    /** 값을 저장한다 (WriteMode에 따라 DB도 갱신). */
    fun save(id: ID, value: V) = cache.put(id, value)

    /** 여러 값을 일괄 저장한다. */
    fun saveAll(entries: Map<ID, V>) = cache.putAll(entries)

    /** ID에 해당하는 항목을 삭제한다. */
    fun deleteById(id: ID) = cache.remove(id)

    /** 여러 ID에 해당하는 항목을 일괄 삭제한다. */
    fun deleteAllById(ids: Set<ID>) = cache.removeAll(ids)

    /** 캐시에 키가 존재하는지 확인한다. */
    fun existsById(id: ID): Boolean = cache.containsKey(id)

    /** 이 cacheName에 속한 Redis 항목 수를 반환한다. */
    fun cacheSize(): Long = cache.size()

    /** 이 cacheName에 속한 모든 Redis 항목을 삭제한다. */
    fun clearCache() = cache.clearAll()

    override fun close() {
        runCatching { cache.close() }
    }
}
