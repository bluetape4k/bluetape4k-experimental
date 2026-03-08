package io.bluetape4k.spring.data.exposed.r2dbc.repository

import io.bluetape4k.exposed.core.HasIdentifier
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.NoRepositoryBean

/**
 * 페이징 조회를 지원하는 Exposed suspend Repository 계약입니다.
 */
@NoRepositoryBean
interface SuspendExposedPagingRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any> :
    SuspendExposedCrudRepository<T, R, ID> {

    suspend fun findAll(pageable: Pageable): Page<R>
}
