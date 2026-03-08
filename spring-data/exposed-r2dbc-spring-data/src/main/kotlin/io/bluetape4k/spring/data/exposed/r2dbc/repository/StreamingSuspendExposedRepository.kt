package io.bluetape4k.spring.data.exposed.r2dbc.repository

import io.bluetape4k.exposed.core.HasIdentifier
import kotlinx.coroutines.flow.Flow
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.springframework.data.repository.NoRepositoryBean

/**
 * 트랜잭션 안에서 직접 소비되는 streaming read 전용 계약입니다.
 *
 * 반환되는 [Flow]는 메서드 내부에서 [R2dbcDatabase]를 사용해 transaction을 열고
 * row를 순차적으로 emit 합니다. 따라서 WebFlux처럼 트랜잭션 밖에서 lazy query를 그대로
 * 직렬화하다가 깨지는 문제를 피하면서도, eager materialization 대신 row-by-row 소비를 허용합니다.
 */
@NoRepositoryBean
interface StreamingSuspendExposedRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any> :
    SuspendExposedCrudRepository<T, R, ID> {

    /**
     * 지정한 [database]에서 모든 row를 순차적으로 읽어 [Flow]로 방출합니다.
     */
    fun streamAll(database: R2dbcDatabase): Flow<R>
}
