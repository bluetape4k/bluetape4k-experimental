package io.bluetape4k.exposed.cockroachdb.schema

import io.bluetape4k.exposed.cockroachdb.AbstractCockroachDBTest
import io.bluetape4k.exposed.cockroachdb.dialect.CockroachDialect
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test

class DialectVerificationTest : AbstractCockroachDBTest() {

    @Test
    fun `db dialect should be CockroachDialect`() {
        db.dialect shouldBeInstanceOf CockroachDialect::class
    }

    @Test
    fun `supportsWindowFrameGroupsMode should be true on v26_1+`() {
        db.dialect.supportsWindowFrameGroupsMode.shouldBeTrue()
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
