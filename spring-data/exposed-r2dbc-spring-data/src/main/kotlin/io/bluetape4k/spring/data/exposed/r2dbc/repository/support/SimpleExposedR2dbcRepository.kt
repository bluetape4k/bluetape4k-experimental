package io.bluetape4k.spring.data.exposed.r2dbc.repository.support

import io.bluetape4k.exposed.core.HasIdentifier
import io.bluetape4k.spring.data.exposed.jdbc.repository.support.toExposedOrderBy
import io.bluetape4k.spring.data.exposed.r2dbc.repository.StreamingSuspendExposedRepository
import io.bluetape4k.spring.data.exposed.r2dbc.repository.SuspendExposedPagingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Exposed suspend CRUD/paging/streaming Repository의 기본 구현체입니다.
 *
 * Reflection 없이 Repository가 제공한 매핑 함수([toDomain], [toPersistValues])를 사용해
 * [IdTable] DSL 쿼리를 실행합니다.
 *
 * 현재 `findAll(): Flow<R>`는 Exposed R2DBC row stream을 직접 전달하지 않고,
 * 한 번 `toList()`로 materialize 한 뒤 `Flow`로 감싼다.
 * 따라서 large result set에서는 진짜 streaming/back-pressure 대신 eager loading 특성을 가진다.
 */
@Repository
@Suppress("UNCHECKED_CAST")
class SimpleExposedR2dbcRepository<R: HasIdentifier<ID>, ID: Any>(
    private val table: IdTable<ID>,
    private val toDomainMapper: (ResultRow) -> R,
    private val persistValuesProvider: (R) -> Map<Column<*>, Any?>,
): SuspendExposedPagingRepository<IdTable<ID>, R, ID>,
   StreamingSuspendExposedRepository<IdTable<ID>, R, ID> {

    override fun toDomain(row: ResultRow): R = toDomainMapper(row)

    override fun toPersistValues(domain: R): Map<Column<*>, Any?> = persistValuesProvider(domain)

    override suspend fun <S: R> save(entity: S): S {
        val persisted = inTransaction { persist(entity) }
        return (persisted as? S) ?: entity
    }

    override suspend fun <S: R> saveAll(entities: Iterable<S>): List<S> =
        entities.map { save(it) }

    override suspend fun findById(id: ID): Optional<R> =
        Optional.ofNullable(findByIdOrNull(id))

    override suspend fun findByIdOrNull(id: ID): R? =
        inTransaction { findRowById(id)?.let(::toDomain) }

    override suspend fun existsById(id: ID): Boolean =
        inTransaction { findRowById(id) != null }

    override suspend fun findAllAsList(): List<R> =
        inTransaction { table.selectAll().toList().map(::toDomain) }

    override fun findAll(): Flow<R> =
        flow {
            emitAll(findAllAsList().asFlow())
        }

    override fun streamAll(database: R2dbcDatabase): Flow<R> =
        channelFlow {
            suspendTransaction(database) {
                table.selectAll().collect { row ->
                    send(toDomain(row))
                }
            }
        }

    override suspend fun findAllById(ids: Iterable<ID>): List<R> {
        val idList = ids.toList()
        if (idList.isEmpty()) return emptyList()

        return inTransaction {
            table.selectAll()
                .where { table.id inList idList }
                .toList()
                .map(::toDomain)
        }
    }

    override suspend fun count(): Long = inTransaction { table.selectAll().count() }

    override suspend fun deleteById(id: ID): Unit =
        inTransaction {
            table.deleteWhere { table.id eq id }
        }

    override suspend fun delete(entity: R) {
        entity.id?.let { deleteById(it) }
    }

    override suspend fun deleteAllById(ids: Iterable<ID>) {
        val idList = ids.toList()
        if (idList.isNotEmpty()) {
            inTransaction {
                table.deleteWhere { table.id inList idList }
            }
        }
    }

    override suspend fun deleteAll(entities: Iterable<R>) {
        deleteAllById(entities.mapNotNull { it.id })
    }

    override suspend fun deleteAll(): Unit =
        inTransaction {
            table.deleteAll()
        }

    override suspend fun findAll(pageable: Pageable): Page<R> =
        inTransaction {
            val base = table.selectAll()

            if (pageable.sort.isSorted) {
                base.orderBy(*pageable.sort.toExposedOrderBy(table))
            }

            val total = base.count()
            if (pageable.isUnpaged) {
                val all = base.toList().map(::toDomain)
                PageImpl(all, pageable, total)
            } else {
                val content = base
                    .limit(pageable.pageSize)
                    .offset(pageable.offset)
                    .toList()
                    .map(::toDomain)

                PageImpl(content, pageable, total)
            }
        }

    override suspend fun count(op: () -> Op<Boolean>): Long {
        return inTransaction {
            table.selectAll()
                .where { op() }
                .count()
        }
    }

    override suspend fun exists(op: () -> Op<Boolean>): Boolean {
        return inTransaction {
            !table.selectAll()
                .where { op() }
                .empty()
        }
    }

    private suspend fun findRowById(id: ID): ResultRow? =
        table.selectAll()
            .where { table.id eq id }
            .limit(1)
            .toList()
            .firstOrNull()

    private suspend fun persist(entity: R): R {
        val idValue = entity.id

        return if (idValue == null) {
            val insertedId = table.insertAndGetId { stmt ->
                writePersistValues(stmt, entity)
            }.value
            findRowById(insertedId)?.let(::toDomain) ?: entity
        } else {
            table.update({ table.id eq idValue }) { stmt ->
                writePersistValues(stmt, entity)
            }
            findRowById(idValue)?.let(::toDomain) ?: entity
        }
    }

    private suspend fun <T> inTransaction(block: suspend () -> T): T =
        suspendTransaction { block() }

    private fun writePersistValues(statement: UpdateBuilder<*>, entity: R) {
        toPersistValues(entity).forEach { (column, value) ->
            require(column.table == table && column != table.id) {
                "Persist column '${column.name}' must belong to table '${table.tableName}' and must not be id column"
            }
            statement[column as Column<Any?>] = value
        }
    }
}
