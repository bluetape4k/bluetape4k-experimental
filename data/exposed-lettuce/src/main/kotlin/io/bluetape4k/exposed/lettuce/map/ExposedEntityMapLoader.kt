package io.bluetape4k.exposed.lettuce.map

import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Exposed DAO [EntityClass]를 사용해 DB에서 엔티티를 로드하는 [MapLoader] 구현체.
 *
 * 트랜잭션 내에서 [EntityClass.findById]를 호출하며, 결과를 [transform] 함수로 변환한다.
 *
 * @param E Exposed Entity 타입
 * @param ID Entity ID 타입
 * @param V 캐시에 저장할 값 타입
 * @param entityClass Exposed EntityClass (companion object)
 * @param transform Entity → V 변환 함수
 */
class ExposedEntityMapLoader<E: Entity<ID>, ID: Any, V: Any>(
    private val entityClass: EntityClass<ID, E>,
    private val transform: (E) -> V,
): MapLoader<ID, V> {

    /**
     * [key]로 DB에서 엔티티를 조회하고 [transform]으로 변환해 반환한다.
     * 엔티티가 없으면 null을 반환한다.
     */
    override fun load(key: ID): V? =
        transaction {
            entityClass.findById(key)?.let(transform)
        }
}

/**
 * [ExposedEntityMapLoader] DSL 생성 함수.
 *
 * ```kotlin
 * val loader = exposedEntityMapLoader(UserEntity) { entity ->
 *     UserDto(entity.id.value, entity.name, entity.email)
 * }
 * ```
 */
fun <E: Entity<ID>, ID: Any, V: Any> exposedEntityMapLoader(
    entityClass: EntityClass<ID, E>,
    transform: (E) -> V,
): ExposedEntityMapLoader<E, ID, V> =
    ExposedEntityMapLoader(entityClass, transform)
