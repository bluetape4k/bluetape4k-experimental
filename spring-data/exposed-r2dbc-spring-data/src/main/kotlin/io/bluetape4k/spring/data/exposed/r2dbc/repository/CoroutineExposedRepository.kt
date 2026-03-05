package io.bluetape4k.spring.data.exposed.r2dbc.repository

import kotlinx.coroutines.flow.Flow
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.dao.Entity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.Repository
import java.util.*

/**
 * Exposed DAO Entity를 코루틴으로 사용하는 Repository 인터페이스입니다.
 *
 * 내부적으로 JDBC DAO를 `withContext(Dispatchers.IO)` + `transaction {}` 으로 래핑합니다.
 * 이를 통해 suspend 함수 형태로 코루틴 친화적인 API를 제공합니다.
 *
 * ```kotlin
 * interface UserRepository : CoroutineExposedRepository<UserEntity, Long> {
 *     suspend fun findByName(name: String): List<UserEntity>
 * }
 * ```
 */
@NoRepositoryBean
interface CoroutineExposedRepository<E : Entity<ID>, ID : Any> : Repository<E, ID> {

    // ============================================================
    // CRUD 기본 메서드
    // ============================================================

    suspend fun <S : E> save(entity: S): S
    suspend fun <S : E> saveAll(entities: Iterable<S>): List<S>

    suspend fun findById(id: ID): Optional<E>
    suspend fun findByIdOrNull(id: ID): E?

    suspend fun existsById(id: ID): Boolean

    fun findAll(): Flow<E>
    suspend fun findAllList(): List<E>
    suspend fun findAll(sort: Sort): List<E>
    suspend fun findAllById(ids: Iterable<ID>): List<E>

    suspend fun count(): Long

    suspend fun deleteById(id: ID)
    suspend fun delete(entity: E)
    suspend fun deleteAllById(ids: Iterable<ID>)
    suspend fun deleteAll(entities: Iterable<E>)
    suspend fun deleteAll()

    // ============================================================
    // 페이징
    // ============================================================

    suspend fun findAll(pageable: Pageable): Page<E>

    // ============================================================
    // Exposed DSL 확장
    // ============================================================

    suspend fun findAll(op: () -> Op<Boolean>): List<E>
    suspend fun count(op: () -> Op<Boolean>): Long
    suspend fun exists(op: () -> Op<Boolean>): Boolean
}
