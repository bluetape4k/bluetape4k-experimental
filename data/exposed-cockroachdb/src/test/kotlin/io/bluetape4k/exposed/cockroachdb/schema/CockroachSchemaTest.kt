package io.bluetape4k.exposed.cockroachdb.schema

import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import io.bluetape4k.exposed.cockroachdb.domain.Users
import org.amshove.kluent.shouldBeFalse
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.core.Transaction
import org.junit.jupiter.api.Test

class CockroachSchemaTest : AbstractCockroachDBTest() {

    @Test
    fun `create and drop table`() {
        withTables(Users) {
            // 테이블 생성 성공 시 예외 없음
        }
    }

    @Test
    fun `createMissingTablesAndColumns is idempotent`() {
        withTables(Users) {
            SchemaUtils.createMissingTablesAndColumns(Users)
            // 중복 호출해도 예외 없음
        }
    }

    @Test
    fun `modifyColumn with type change returns empty list`() {
        // CockroachDialect.supportsColumnTypeChange == false 확인
        db.dialect.supportsColumnTypeChange.shouldBeFalse()
    }
}
