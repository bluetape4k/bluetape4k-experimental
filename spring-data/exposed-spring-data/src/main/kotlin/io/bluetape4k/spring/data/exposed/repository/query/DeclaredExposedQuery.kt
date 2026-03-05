package io.bluetape4k.spring.data.exposed.repository.query

import io.bluetape4k.spring.data.exposed.repository.support.ExposedEntityInformation
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.data.repository.query.RepositoryQuery

/**
 * [@Query][io.bluetape4k.spring.data.exposed.annotation.Query] 어노테이션으로 지정한 raw SQL을 실행합니다.
 * 위치 기반 파라미터(?1, ?2, ...)를 지원합니다.
 *
 * 결과 매핑: SELECT id 컬럼에서 ID를 읽어 EntityClass.findById로 로드합니다.
 */
class DeclaredExposedQuery<E : Entity<ID>, ID : Any>(
    private val queryMethod: ExposedQueryMethod,
    private val entityInformation: ExposedEntityInformation<E, ID>,
) : RepositoryQuery {

    private val entityClass: EntityClass<ID, E> = entityInformation.entityClass
    private val rawSql: String = queryMethod.getAnnotatedQuery()
        ?: error("@Query annotation is required for DeclaredExposedQuery")

    override fun getQueryMethod(): ExposedQueryMethod = queryMethod

    @Suppress("UNCHECKED_CAST")
    override fun execute(parameters: Array<out Any>): Any? {
        val sql = bindParameters(rawSql, parameters)
        val tx = TransactionManager.current()

        return tx.exec(sql, emptyList()) { rs ->
            val results = mutableListOf<E>()
            while (rs.next()) {
                runCatching {
                    // id 컬럼에서 값을 읽어 EntityClass로 로드
                    val idVal = rs.getObject("id") ?: rs.getObject(1)
                    if (idVal != null) {
                        entityClass.findById(idVal as ID)?.let { results.add(it) }
                    }
                }
            }
            results
        } ?: emptyList<E>()
    }

    private fun bindParameters(sql: String, parameters: Array<out Any?>): String {
        var result = sql
        parameters.forEachIndexed { index, param ->
            val placeholder = "?${index + 1}"
            val value = when (param) {
                null -> "NULL"
                is String -> "'${param.replace("'", "''")}'"
                is Number -> param.toString()
                is Boolean -> if (param) "1" else "0"
                else -> "'$param'"
            }
            result = result.replace(placeholder, value)
        }
        return result
    }
}
