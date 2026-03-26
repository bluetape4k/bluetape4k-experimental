package io.bluetape4k.exposed.cockroachdb.dialect

import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnDiff
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect

/**
 * CockroachDB Dialect for JetBrains Exposed ORM
 *
 * PostgreSQLDialect 상속하여 CockroachDB 제약사항만 override:
 * - WINDOW FRAME GROUPS 미지원 (v24.1 기준)
 * - ALTER COLUMN TYPE 미지원
 * - multiple generated keys 제한
 */
class CockroachDialect : PostgreSQLDialect(name = dialectName) {

    companion object : KLogging() {
        const val dialectName: String = "CockroachDB"
    }

    // CockroachDB v24.1 기준 WINDOW FRAME GROUPS 미지원
    override val supportsWindowFrameGroupsMode: Boolean = false

    // ALTER COLUMN TYPE 미지원
    override val supportsColumnTypeChange: Boolean get() = false

    // multiple generated keys 제한
    override val supportsMultipleGeneratedKeys: Boolean = false

    /**
     * 타입 변경 시 SQL 생성 안 함 (CockroachDB ALTER COLUMN TYPE 미지원)
     * 타입 변경 외 다른 diff는 PostgreSQL 기본 동작 위임
     */
    // CockroachDB는 ALTER COLUMN TYPE 및 DEFAULT 변경 미지원
    @OptIn(InternalApi::class)
    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> =
        if (columnDiff.type || columnDiff.defaults) emptyList() else super.modifyColumn(column, columnDiff)
}
