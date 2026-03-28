package io.bluetape4k.exposed.ignite3

import io.bluetape4k.exposed.ignite3.dialect.IgniteDataTypeProvider
import io.bluetape4k.exposed.ignite3.dialect.IgniteDialect
import io.bluetape4k.exposed.ignite3.dialect.IgniteFunctionProvider
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class IgniteDialectContractTest {

    @Test
    fun `IgniteDialect 는 제약사항 플래그를 노출한다`() {
        val dialect = IgniteDialect()

        dialect.supportsMultipleGeneratedKeys.shouldBeFalse()
        dialect.supportsCreateSequence.shouldBeFalse()
        dialect.supportsOnUpdate.shouldBeFalse()
        dialect.supportsSetDefaultReferenceOption.shouldBeFalse()
        dialect.supportsRestrictReferenceOption.shouldBeFalse()
        dialect.supportsColumnTypeChange.shouldBeFalse()
        dialect.supportsCreateSchema.shouldBeTrue()
    }

    @Test
    fun `IgniteDataTypeProvider 는 Ignite 친화 타입을 반환한다`() {
        IgniteDataTypeProvider.integerAutoincType() shouldBeEqualTo "INT"
        IgniteDataTypeProvider.longAutoincType() shouldBeEqualTo "BIGINT"
        IgniteDataTypeProvider.textType() shouldBeEqualTo "VARCHAR(65536)"
        IgniteDataTypeProvider.largeTextType() shouldBeEqualTo "VARCHAR(65536)"
        IgniteDataTypeProvider.blobType() shouldBeEqualTo "VARBINARY"
        IgniteDataTypeProvider.uuidType() shouldBeEqualTo "UUID"
        IgniteDataTypeProvider.dateTimeType() shouldBeEqualTo "TIMESTAMP"
        IgniteDataTypeProvider.binaryType() shouldBeEqualTo "VARBINARY"
        IgniteDataTypeProvider.binaryType(32) shouldBeEqualTo "VARBINARY(32)"
        IgniteDataTypeProvider.hexToDb("ABCD") shouldBeEqualTo "X'ABCD'"
    }

    @Test
    fun `IgniteDataTypeProvider uuidToDB 는 UUID 객체를 그대로 바인딩한다`() {
        val uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        IgniteDataTypeProvider.uuidToDB(uuid) shouldBeEqualTo uuid
    }

    @Test
    fun `IgniteFunctionProvider random 은 RAND 문법을 사용한다`() {
        IgniteFunctionProvider.random(seed = null) shouldBeEqualTo "RAND()"
        IgniteFunctionProvider.random(seed = 7) shouldBeEqualTo "RAND(7)"
    }
}
