package io.bluetape4k.exposed.cockroachdb.schema

import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import io.bluetape4k.exposed.cockroachdb.dialect.CockroachDialect
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldBeFalse
import org.junit.jupiter.api.Test

class DialectVerificationTest : AbstractCockroachDBTest() {

    @Test
    fun `db dialect should be CockroachDialect`() {
        db.dialect shouldBeInstanceOf CockroachDialect::class
    }

    @Test
    fun `supportsWindowFrameGroupsMode should be false`() {
        db.dialect.supportsWindowFrameGroupsMode.shouldBeFalse()
    }

    @Test
    fun `supportsColumnTypeChange should be false`() {
        db.dialect.supportsColumnTypeChange.shouldBeFalse()
    }

    @Test
    fun `supportsMultipleGeneratedKeys should be false`() {
        db.dialect.supportsMultipleGeneratedKeys.shouldBeFalse()
    }
}
