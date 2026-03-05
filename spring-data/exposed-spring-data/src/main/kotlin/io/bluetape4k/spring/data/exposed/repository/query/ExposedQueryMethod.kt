package io.bluetape4k.spring.data.exposed.repository.query

import io.bluetape4k.spring.data.exposed.annotation.Query
import org.springframework.data.projection.ProjectionFactory
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.query.QueryMethod
import java.lang.reflect.Method

/**
 * Exposed Repository 메서드에 대한 메타데이터를 표현합니다.
 */
class ExposedQueryMethod(
    method: Method,
    metadata: RepositoryMetadata,
    factory: ProjectionFactory,
) : QueryMethod(method, metadata, factory) {

    private val queryAnnotation: Query? = method.getAnnotation(Query::class.java)

    /** @Query 어노테이션이 존재하는지 여부 */
    val isAnnotatedQuery: Boolean get() = queryAnnotation != null

    /** @Query 어노테이션의 SQL 문자열 (없으면 null) */
    fun getAnnotatedQuery(): String? = queryAnnotation?.value

    /** @Query 어노테이션의 count 쿼리 문자열 (없으면 null) */
    fun getCountQuery(): String? = queryAnnotation?.countQuery?.takeIf { it.isNotBlank() }
}
