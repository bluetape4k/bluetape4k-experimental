package io.bluetape4k.spring.data.exposed.r2dbc.repository.support

import io.bluetape4k.spring.data.exposed.r2dbc.repository.CoroutineExposedRepository
import io.bluetape4k.spring.data.exposed.repository.support.ExposedEntityInformation
import io.bluetape4k.spring.data.exposed.repository.support.toExposedOrderBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * [CoroutineExposedRepository]의 기본 CRUD 구현체입니다.
 *
 * JDBC DAO 연산을 `withContext(Dispatchers.IO)` + `transaction {}` 으로 래핑하여
 * 코루틴 환경에서 안전하게 사용할 수 있도록 합니다.
 */
@Repository
@Suppress("UNCHECKED_CAST")
open class SimpleCoroutineExposedRepository<E : Entity<ID>, ID : Any>(
    val entityInformation: ExposedEntityInformation<E, ID>,
) : CoroutineExposedRepository<E, ID> {

    val entityClass: EntityClass<ID, E> get() = entityInformation.entityClass
    val table: IdTable<ID> get() = entityInformation.table

    private suspend fun <T> io(block: () -> T): T =
        withContext(Dispatchers.IO) { transaction { block() } }

    // ============================================================
    // CRUD
    // ============================================================

    override suspend fun <S : E> save(entity: S): S = entity

    override suspend fun <S : E> saveAll(entities: Iterable<S>): List<S> = entities.toList()

    override suspend fun findById(id: ID): Optional<E> =
        Optional.ofNullable(io { entityClass.findById(id) })

    override suspend fun findByIdOrNull(id: ID): E? =
        io { entityClass.findById(id) }

    override suspend fun existsById(id: ID): Boolean =
        io { entityClass.findById(id) != null }

    override fun findAll(): Flow<E> =
        // JDBC는 lazy stream을 지원하지 않으므로 즉시 로드 후 Flow로 변환
        transaction { entityClass.all().toList() }.asFlow()

    override suspend fun findAllList(): List<E> =
        io { entityClass.all().toList() }

    override suspend fun findAll(sort: Sort): List<E> = io {
        if (sort.isUnsorted) {
            entityClass.all().toList()
        } else {
            entityClass.all().orderBy(*sort.toExposedOrderBy(table)).toList()
        }
    }

    override suspend fun findAllById(ids: Iterable<ID>): List<E> {
        val idList = ids.toList()
        if (idList.isEmpty()) return emptyList()
        return io { entityClass.forIds(idList).toList() }
    }

    override suspend fun count(): Long = io { entityClass.count() }

    override suspend fun deleteById(id: ID): Unit = io { entityClass.findById(id)?.delete() }

    override suspend fun delete(entity: E): Unit = io { entity.delete() }

    override suspend fun deleteAllById(ids: Iterable<ID>): Unit = io {
        ids.forEach { entityClass.findById(it)?.delete() }
    }

    override suspend fun deleteAll(entities: Iterable<E>): Unit = io {
        entities.forEach { it.delete() }
    }

    override suspend fun deleteAll(): Unit = io {
        entityClass.all().forEach { it.delete() }
    }

    // ============================================================
    // 페이징
    // ============================================================

    override suspend fun findAll(pageable: Pageable): Page<E> = io {
        if (pageable.isUnpaged) {
            val all = entityClass.all().toList()
            return@io PageImpl(all, pageable, all.size.toLong())
        }
        val query = entityClass.all()
        if (pageable.sort.isSorted) {
            query.orderBy(*pageable.sort.toExposedOrderBy(table))
        }
        val total = entityClass.count()
        val content = query.limit(pageable.pageSize).offset(pageable.offset).toList()
        PageImpl(content, pageable, total)
    }

    // ============================================================
    // Exposed DSL 확장
    // ============================================================

    override suspend fun findAll(op: () -> Op<Boolean>): List<E> =
        io { entityClass.find(op).toList() }

    override suspend fun count(op: () -> Op<Boolean>): Long =
        io { entityClass.find(op).count() }

    override suspend fun exists(op: () -> Op<Boolean>): Boolean =
        io { !entityClass.find(op).empty() }
}
