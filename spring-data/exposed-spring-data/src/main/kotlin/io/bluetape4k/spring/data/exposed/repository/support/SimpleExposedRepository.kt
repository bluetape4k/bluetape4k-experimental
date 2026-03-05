package io.bluetape4k.spring.data.exposed.repository.support

import io.bluetape4k.spring.data.exposed.repository.ExposedRepository
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.springframework.data.domain.Example
import org.springframework.data.domain.ExampleMatcher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.query.FluentQuery
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.function.Function
import java.util.stream.Stream

/**
 * [ExposedRepository]의 기본 CRUD 구현체입니다.
 * 모든 Exposed DAO 연산은 트랜잭션 내에서 실행됩니다.
 */
/** Exposed Spring Boot 4 Starter가 등록하는 SpringTransactionManager 빈 이름 */
internal const val EXPOSED_TRANSACTION_MANAGER = "springTransactionManager"

@Repository
@Transactional(transactionManager = EXPOSED_TRANSACTION_MANAGER, readOnly = true)
@Suppress("UNCHECKED_CAST")
open class SimpleExposedRepository<E : Entity<ID>, ID : Any>(
    val entityInformation: ExposedEntityInformation<E, ID>,
) : ExposedRepository<E, ID> {

    val entityClass: EntityClass<ID, E> get() = entityInformation.entityClass
    val table: IdTable<ID> get() = entityInformation.table

    // ============================================================
    // CrudRepository
    // ============================================================

    @Transactional(transactionManager = EXPOSED_TRANSACTION_MANAGER)
    override fun <S : E> save(entity: S): S = entity // Exposed DAO는 변경 감지 자동 수행

    @Transactional(transactionManager = EXPOSED_TRANSACTION_MANAGER)
    override fun <S : E> saveAll(entities: Iterable<S>): List<S> = entities.toList()

    override fun findById(id: ID): Optional<E> =
        Optional.ofNullable(entityClass.findById(id))

    override fun existsById(id: ID): Boolean =
        entityClass.findById(id) != null

    override fun findAll(): List<E> =
        entityClass.all().toList()

    override fun findAll(sort: Sort): List<E> {
        if (sort.isUnsorted) return findAll()
        return entityClass.all()
            .orderBy(*sort.toExposedOrderBy(table))
            .toList()
    }

    override fun findAllById(ids: Iterable<ID>): List<E> {
        val idList = ids.toList()
        if (idList.isEmpty()) return emptyList()
        return entityClass.forIds(idList).toList()
    }

    override fun count(): Long = entityClass.count()

    @Transactional(transactionManager = EXPOSED_TRANSACTION_MANAGER)
    override fun deleteById(id: ID) {
        entityClass.findById(id)?.delete()
    }

    @Transactional(transactionManager = EXPOSED_TRANSACTION_MANAGER)
    override fun delete(entity: E) {
        entity.delete()
    }

    @Transactional(transactionManager = EXPOSED_TRANSACTION_MANAGER)
    override fun deleteAllById(ids: Iterable<ID>) {
        ids.forEach { id -> entityClass.findById(id)?.delete() }
    }

    @Transactional(transactionManager = EXPOSED_TRANSACTION_MANAGER)
    override fun deleteAll(entities: Iterable<E>) {
        entities.forEach { it.delete() }
    }

    @Transactional(transactionManager = EXPOSED_TRANSACTION_MANAGER)
    override fun deleteAll() {
        entityClass.all().forEach { it.delete() }
    }

    // ============================================================
    // PagingAndSortingRepository
    // ============================================================

    override fun findAll(pageable: Pageable): Page<E> {
        if (pageable.isUnpaged) {
            val all = findAll(pageable.sort)
            return PageImpl(all, pageable, all.size.toLong())
        }
        val query = entityClass.all()
        if (pageable.sort.isSorted) {
            query.orderBy(*pageable.sort.toExposedOrderBy(table))
        }
        val total = entityClass.count()
        val content = query
            .limit(pageable.pageSize)
            .offset(pageable.offset)
            .toList()
        return PageImpl(content, pageable, total)
    }

    // ============================================================
    // ExposedRepository DSL extensions
    // ============================================================

    override fun findAll(op: () -> Op<Boolean>): List<E> =
        entityClass.find(op).toList()

    override fun count(op: () -> Op<Boolean>): Long =
        entityClass.find(op).count()

    override fun exists(op: () -> Op<Boolean>): Boolean =
        !entityClass.find(op).empty()

    // ============================================================
    // QueryByExampleExecutor
    // ============================================================

    override fun <S : E> findOne(example: Example<S>): Optional<S> =
        Optional.ofNullable(findByExample(example).firstOrNull())

    override fun <S : E> findAll(example: Example<S>): List<S> =
        findByExample(example)

    override fun <S : E> findAll(example: Example<S>, sort: Sort): List<S> {
        val results = findByExample(example)
        if (sort.isUnsorted) return results
        return results.sortedWith(ExampleSortComparator(sort))
    }

    override fun <S : E> findAll(example: Example<S>, pageable: Pageable): Page<S> {
        val all = findByExample(example)
        val sorted = if (pageable.sort.isSorted) all.sortedWith(ExampleSortComparator(pageable.sort)) else all
        val start = pageable.offset.toInt().coerceAtMost(sorted.size)
        val end = (start + pageable.pageSize).coerceAtMost(sorted.size)
        return PageImpl(sorted.subList(start, end), pageable, sorted.size.toLong())
    }

    override fun <S : E> count(example: Example<S>): Long =
        findByExample(example).size.toLong()

    override fun <S : E> exists(example: Example<S>): Boolean =
        findByExample(example).isNotEmpty()

    override fun <S : E, R> findBy(
        example: Example<S>,
        queryFunction: Function<FluentQuery.FetchableFluentQuery<S>, R>,
    ): R {
        val results = findByExample(example)
        @Suppress("UNCHECKED_CAST")
        return queryFunction.apply(SimpleFluentQuery(results) as FluentQuery.FetchableFluentQuery<S>)
    }

    // ============================================================
    // Internal helpers
    // ============================================================

    private fun <S : E> findByExample(example: Example<S>): List<S> {
        val conditions = buildExampleConditions(example.probe, example.matcher)
        return if (conditions == null) {
            entityClass.all().toList() as List<S>
        } else {
            entityClass.find { conditions }.toList() as List<S>
        }
    }

    private fun buildExampleConditions(
        probe: E,
        matcher: ExampleMatcher,
    ): Op<Boolean>? {
        var combined: Op<Boolean>? = null

        for (col in table.columns) {
            if (col == table.id) continue
            val field = runCatching {
                probe.javaClass.getDeclaredField(col.name).also { it.isAccessible = true }
            }.getOrNull() ?: continue

            val value = field.get(probe) ?: continue
            val condition = (col as Column<Any>).eq(value)
            combined = if (combined == null) condition else combined.and(condition)
        }
        return combined
    }
}

/**
 * In-memory 정렬을 위한 Comparator (QueryByExample 결과 정렬용)
 */
private class ExampleSortComparator<E>(private val sort: Sort) : Comparator<E> {
    @Suppress("UNCHECKED_CAST")
    override fun compare(a: E, b: E): Int {
        for (order in sort) {
            val field = runCatching {
                a!!.javaClass.getDeclaredField(order.property).also { it.isAccessible = true }
            }.getOrNull() ?: continue

            val va = field.get(a) as? Comparable<Any> ?: continue
            val vb = field.get(b) as? Comparable<Any> ?: continue
            val cmp = va.compareTo(vb)
            if (cmp != 0) return if (order.isAscending) cmp else -cmp
        }
        return 0
    }
}

/**
 * FluentQuery 최소 구현 (findBy 지원용)
 */
private class SimpleFluentQuery<E : Any>(private val results: List<E>) :
    FluentQuery.FetchableFluentQuery<E> {

    override fun sortBy(sort: Sort): FluentQuery.FetchableFluentQuery<E> = this

    override fun <R : Any> `as`(projectionType: Class<R>): FluentQuery.FetchableFluentQuery<R> =
        SimpleFluentQuery(results.map { projectionType.cast(it) })

    override fun project(properties: MutableCollection<String>): FluentQuery.FetchableFluentQuery<E> = this

    override fun first(): Optional<E> = Optional.ofNullable(results.firstOrNull())

    override fun firstValue(): E? = results.firstOrNull()

    override fun one(): Optional<E> = Optional.ofNullable(results.singleOrNull())

    override fun oneValue(): E? = results.singleOrNull()

    override fun all(): List<E> = results

    override fun page(pageable: Pageable): Page<E> {
        val start = pageable.offset.toInt().coerceAtMost(results.size)
        val end = (start + pageable.pageSize).coerceAtMost(results.size)
        return PageImpl(results.subList(start, end), pageable, results.size.toLong())
    }

    override fun count(): Long = results.size.toLong()

    override fun exists(): Boolean = results.isNotEmpty()

    override fun stream(): Stream<E> = results.stream()
}
