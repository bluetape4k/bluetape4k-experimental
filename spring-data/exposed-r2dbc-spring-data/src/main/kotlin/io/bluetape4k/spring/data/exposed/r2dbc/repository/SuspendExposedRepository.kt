package io.bluetape4k.spring.data.exposed.r2dbc.repository

import io.bluetape4k.exposed.core.HasIdentifier
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.springframework.data.repository.NoRepositoryBean

/**
 * Exposed suspend Repository의 호환용 집계 인터페이스입니다.
 *
 * 신규 코드는 [SuspendExposedCrudRepository], [SuspendExposedPagingRepository],
 * [StreamingSuspendExposedRepository] 중 필요한 기능만 선택해 상속하는 것을 권장합니다.
 */
@NoRepositoryBean
@Deprecated(
    message = "Use SuspendExposedCrudRepository or SuspendExposedPagingRepository instead.",
    replaceWith = ReplaceWith("SuspendExposedPagingRepository<T, R, ID>"),
)
interface SuspendExposedRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any> :
    SuspendExposedPagingRepository<T, R, ID>
