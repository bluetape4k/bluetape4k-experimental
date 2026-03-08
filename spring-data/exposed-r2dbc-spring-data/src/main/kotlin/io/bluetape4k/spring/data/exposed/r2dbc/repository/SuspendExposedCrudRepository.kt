package io.bluetape4k.spring.data.exposed.r2dbc.repository

import io.bluetape4k.exposed.core.HasIdentifier
import kotlinx.coroutines.flow.Flow
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.Repository
import java.util.Optional

/**
 * Exposed [IdTable] 기반 suspend CRUD Repository 계약입니다.
 *
 * 테이블 타입 [T], 도메인 타입 [R], 식별자 타입 [ID]를 분리해
 * DAO Entity 의존 없이 Row/DTO 중심으로 사용할 수 있습니다.
 *
 * ```kotlin
 * interface UserRepository : SuspendExposedCrudRepository<Users, UserDto, Long> {
 *     suspend fun findByName(name: String): List<UserDto>
 * }
 * ```
 */
@NoRepositoryBean
interface SuspendExposedCrudRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any> : Repository<R, ID> {

    suspend fun <S : R> save(entity: S): S
    suspend fun <S : R> saveAll(entities: Iterable<S>): List<S>

    suspend fun findById(id: ID): Optional<R>
    suspend fun findByIdOrNull(id: ID): R?

    suspend fun existsById(id: ID): Boolean

    /**
     * 모든 결과를 메모리에 적재한 뒤 List로 반환합니다.
     *
     * WebFlux 응답처럼 트랜잭션 바깥에서 소비되는 경로에서는 이 메서드를 우선 사용합니다.
     */
    suspend fun findAllAsList(): List<R>

    /**
     * [findAllAsList] 결과를 Flow 형태로 노출합니다.
     *
     * 이 메서드는 진짜 row streaming이 아니라 eager materialization 이후 Flow 래핑입니다.
     * 트랜잭션 경계 밖에서 안전하게 소비할 수 있는 대신, large result set에서는 메모리 사용량이 증가합니다.
     */
    fun findAll(): Flow<R>
    suspend fun findAllById(ids: Iterable<ID>): List<R>

    suspend fun count(): Long

    suspend fun deleteById(id: ID)
    suspend fun delete(entity: R)
    suspend fun deleteAllById(ids: Iterable<ID>)
    suspend fun deleteAll(entities: Iterable<R>)
    suspend fun deleteAll()

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
