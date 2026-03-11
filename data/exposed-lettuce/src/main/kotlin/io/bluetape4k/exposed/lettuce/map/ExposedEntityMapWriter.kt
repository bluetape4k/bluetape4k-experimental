package io.bluetape4k.exposed.lettuce.map

import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Exposed DAO [EntityClass]를 사용해 DB에 엔티티를 upsert/delete하는 [MapWriter] 구현체.
 *
 * - [write]: 트랜잭션 내에서 [upsert] 함수를 호출해 저장한다.
 * - [delete]: 트랜잭션 내에서 [EntityClass.findById] + [Entity.delete]를 호출한다.
 *
 * @param E Exposed Entity 타입
 * @param ID Entity ID 타입
 * @param V 캐시에 저장된 값 타입
 * @param entityClass Exposed EntityClass (companion object)
 * @param upsert (ID, V) → Unit: ID와 값을 받아 DB에 저장(insert or update)하는 함수
 */
class ExposedEntityMapWriter<E: Entity<ID>, ID: Any, V: Any>(
    private val entityClass: EntityClass<ID, E>,
    private val upsert: (ID, V) -> Unit,
): MapWriter<ID, V> {

    /**
     * [key], [value]를 DB에 저장한다 (insert or update).
     */
    override fun write(key: ID, value: V) {
        transaction {
            upsert(key, value)
        }
    }

    /**
     * [key]에 해당하는 DB 행을 삭제한다.
     * 엔티티가 없으면 아무 작업도 하지 않는다.
     */
    override fun delete(key: ID) {
        transaction {
            entityClass.findById(key)?.delete()
        }
    }

    /**
     * 여러 항목을 단일 트랜잭션 내에서 일괄 저장한다.
     */
    override fun writeAll(entries: Map<ID, V>) {
        if (entries.isEmpty()) return
        transaction {
            entries.forEach { (k, v) -> upsert(k, v) }
        }
    }

    /**
     * 여러 항목을 단일 트랜잭션 내에서 일괄 삭제한다.
     */
    override fun deleteAll(keys: Iterable<ID>) {
        val keyList = keys.toList()
        if (keyList.isEmpty()) return
        transaction {
            keyList.forEach { key ->
                entityClass.findById(key)?.delete()
            }
        }
    }
}

/**
 * [ExposedEntityMapWriter] DSL 생성 함수.
 *
 * ```kotlin
 * val writer = exposedEntityMapWriter(UserEntity) { id, dto ->
 *     val entity = UserEntity.findById(id) ?: UserEntity.new(id) {}
 *     entity.name = dto.name
 *     entity.email = dto.email
 * }
 * ```
 */
fun <E: Entity<ID>, ID: Any, V: Any> exposedEntityMapWriter(
    entityClass: EntityClass<ID, E>,
    upsert: (ID, V) -> Unit,
): ExposedEntityMapWriter<E, ID, V> =
    ExposedEntityMapWriter(entityClass, upsert)
