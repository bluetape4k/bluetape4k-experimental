package io.bluetape4k.exposed.ignite3.dialect

import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.vendors.FunctionProvider

/**
 * Apache Ignite 3 FunctionProvider
 *
 * 기본 SQL 표준 구현을 그대로 사용하되, Ignite 3에서 지원하지 않는 함수를 오버라이드합니다.
 */
object IgniteFunctionProvider : FunctionProvider() {

    // RANDOM() — Ignite 3는 RAND() 사용
    override fun random(seed: Int?): String = "RAND(${seed?.toString().orEmpty()})"

    // CHAR_LENGTH → LENGTH
    override fun <T : String?> charLength(
        expr: Expression<T>,
        queryBuilder: QueryBuilder,
    ): Unit = queryBuilder {
        append("LENGTH(")
        append(expr)
        append(")")
    }
}
