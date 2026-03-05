package io.bluetape4k.spring.data.exposed.r2dbc.repository.support

import io.bluetape4k.spring.data.exposed.r2dbc.repository.CoroutineExposedRepository
import io.bluetape4k.spring.data.exposed.r2dbc.repository.HasIdentifier
import io.bluetape4k.spring.data.exposed.repository.support.toExposedOrderBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
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
import org.jetbrains.exposed.v1.r2dbc.deleteAll
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insertAndGetId
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.update
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * [CoroutineExposedRepository]의 기본 구현체입니다.
 *
 * Reflection 없이 Repository가 제공한 매핑 함수([toDomain], [idOf], [toPersistValues])를 사용해
 * [IdTable] DSL 쿼리를 실행합니다.
 */
@Repository
@Suppress("UNCHECKED_CAST")
class SimpleCoroutineExposedRepository<R : HasIdentifier<ID>, ID : Any>(
    private val table: IdTable<ID>,
    private val toDomainMapper: (ResultRow) -> R,
    private val persistValuesProvider: (R) -> Map<Column<*>, Any?>,
) : CoroutineExposedRepository<IdTable<ID>, R, ID> {

    override fun toDomain(row: ResultRow): R = toDomainMapper(row)

    override fun toPersistValues(domain: R): Map<Column<*>, Any?> = persistValuesProvider(domain)

    override suspend fun <S : R> save(entity: S): S {
        val persisted = persist(entity)
        return (persisted as? S) ?: entity
    }

    override suspend fun <S : R> saveAll(entities: Iterable<S>): List<S> =
        entities.map { save(it) }

    override suspend fun findById(id: ID): Optional<R> =
        Optional.ofNullable(findByIdOrNull(id))

    override suspend fun findByIdOrNull(id: ID): R? =
        findRowById(id)?.let(::toDomain)

    override suspend fun existsById(id: ID): Boolean =
        findRowById(id) != null

    override fun findAll(): Flow<R> =
        flow {
            emitAll(
                table.selectAll().toList().map(::toDomain)
                    .asFlow()
            )
        }

    override suspend fun findAllById(ids: Iterable<ID>): List<R> {
        val idList = ids.toList()
        if (idList.isEmpty()) return emptyList()

        return table.selectAll()
            .where { table.id inList idList }
            .toList()
            .map(::toDomain)
    }

    override suspend fun count(): Long {
        return table.selectAll().count()
    }

    override suspend fun deleteById(id: ID) {
        table.deleteWhere { table.id eq id }
    }

    override suspend fun delete(entity: R) {
        entity.id?.let { deleteById(it) }
    }

    override suspend fun deleteAllById(ids: Iterable<ID>) {
        val idList = ids.toList()
        if (idList.isNotEmpty()) {
            table.deleteWhere { table.id inList idList }
        }
    }

    override suspend fun deleteAll(entities: Iterable<R>) {
        deleteAllById(entities.mapNotNull { it.id })
    }

    override suspend fun deleteAll() {
        table.deleteAll()
    }

    override suspend fun findAll(pageable: Pageable): Page<R> {
        val base = table.selectAll()

        if (pageable.sort.isSorted) {
            base.orderBy(*pageable.sort.toExposedOrderBy(table))
        }

        val total = base.count()
        if (pageable.isUnpaged) {
            val all = base.toList().map(::toDomain)
            return PageImpl(all, pageable, total)
        }

        val content = base
            .limit(pageable.pageSize)
            .offset(pageable.offset)
            .toList()
            .map(::toDomain)

        return PageImpl(content, pageable, total)
    }

    override suspend fun count(op: () -> Op<Boolean>): Long {
        return table.selectAll()
            .where { op() }
            .count()
    }

    override suspend fun exists(op: () -> Op<Boolean>): Boolean {
        return !table.selectAll()
            .where { op() }
            .empty()
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

    private fun writePersistValues(statement: UpdateBuilder<*>, entity: R) {
        toPersistValues(entity).forEach { (column, value) ->
            require(column.table == table && column != table.id) {
                "Persist column '${column.name}' must belong to table '${table.tableName}' and must not be id column"
            }
            statement[column as Column<Any?>] = value
        }
    }
}
