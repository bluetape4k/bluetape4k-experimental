package io.bluetape4k.spring.data.exposed.r2dbc.repository

import kotlinx.coroutines.flow.Flow
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.Repository
import java.util.Optional

/**
 * Exposed [IdTable] 기반 코루틴 Repository 인터페이스입니다.
 *
 * 테이블 타입 [T], 도메인 타입 [R], 식별자 타입 [ID]를 분리해
 * DAO Entity 의존 없이 Row/DTO 중심으로 사용할 수 있습니다.
 *
 * ```kotlin
 * interface UserRepository : CoroutineExposedRepository<Users, UserDto, Long> {
 *     suspend fun findByName(name: String): List<UserDto>
 * }
 * ```
 */
@NoRepositoryBean
interface CoroutineExposedRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any> : Repository<R, ID> {

    // ============================================================
    // CRUD 기본 메서드
    // ============================================================

    suspend fun <S : R> save(entity: S): S
    suspend fun <S : R> saveAll(entities: Iterable<S>): List<S>

    suspend fun findById(id: ID): Optional<R>
    suspend fun findByIdOrNull(id: ID): R?

    suspend fun existsById(id: ID): Boolean

    fun findAll(): Flow<R>
    suspend fun findAllById(ids: Iterable<ID>): List<R>

    suspend fun count(): Long

    suspend fun deleteById(id: ID)
    suspend fun delete(entity: R)
    suspend fun deleteAllById(ids: Iterable<ID>)
    suspend fun deleteAll(entities: Iterable<R>)
    suspend fun deleteAll()

    // ============================================================
    // 페이징
    // ============================================================

    suspend fun findAll(pageable: Pageable): Page<R>

    // ============================================================
    // Exposed DSL 확장
    // ============================================================

    /**
     * ResultRow를 도메인 객체 [R]로 변환합니다.
     * 각 Repository 인터페이스에서 반드시 재정의해야 합니다.
     */
    fun toDomain(row: ResultRow): R

    /**
     * 저장/수정 시 사용할 컬럼 값을 제공합니다.
     * ID 컬럼은 제외하고 반환해야 합니다.
     */
    fun toPersistValues(domain: R): Map<Column<*>, Any?>

    suspend fun count(op: () -> Op<Boolean>): Long
    suspend fun exists(op: () -> Op<Boolean>): Boolean
}
