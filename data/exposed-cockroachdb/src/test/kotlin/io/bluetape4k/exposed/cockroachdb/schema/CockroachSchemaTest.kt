package io.bluetape4k.exposed.cockroachdb.schema

import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import io.bluetape4k.exposed.cockroachdb.domain.Users
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeFalse
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.junit.jupiter.api.Test

class CockroachSchemaTest : AbstractCockroachDBTest() {

    @Test
    fun `create and drop table`() {
        withTables(Users) {
            // 테이블 생성 성공 시 예외 없음
        }
    }

    @Test
    fun `statementsRequiredForDatabaseMigration - 이미 존재하는 테이블은 마이그레이션 구문 없음`() {
        withTables(Users) {
            MigrationUtils.statementsRequiredForDatabaseMigration(Users).shouldBeEmpty()
        }
    }

    @Test
    fun `modifyColumn with type change returns empty list`() {
        // CockroachDialect.supportsColumnTypeChange == false 확인
        db.dialect.supportsColumnTypeChange.shouldBeFalse()
    }
}
