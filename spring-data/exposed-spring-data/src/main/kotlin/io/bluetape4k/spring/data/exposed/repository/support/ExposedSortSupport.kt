package io.bluetape4k.spring.data.exposed.repository.support

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.springframework.data.domain.Sort

/**
 * Spring Data [Sort]를 Exposed [SortOrder] 쌍 배열로 변환합니다.
 */
fun Sort.toExposedOrderBy(table: Table): Array<Pair<Expression<*>, SortOrder>> {
    val result = mutableListOf<Pair<Expression<*>, SortOrder>>()
    for (order in this) {
        val col: Column<*> = table.columns.firstOrNull { col ->
            col.name.equals(order.property, ignoreCase = true) ||
                col.name.equals(toSnakeCase(order.property), ignoreCase = true)
        } ?: continue // 알 수 없는 컬럼은 건너뜀

        val sortOrder = if (order.isAscending) SortOrder.ASC else SortOrder.DESC
        result.add(col to sortOrder)
    }
    return result.toTypedArray()
}

private fun toSnakeCase(camelCase: String): String =
    camelCase.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
