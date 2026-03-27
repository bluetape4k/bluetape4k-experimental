package io.bluetape4k.redis.lettuce.filter

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LettuceCuckooFilterTest : AbstractRedisLettuceTest() {

    private lateinit var cf: LettuceCuckooFilter

    @BeforeEach
    fun setup() {
        cf = LettuceCuckooFilter(
            client.connect(StringCodec.UTF8),
            "cf-${randomName()}",
            CuckooFilterOptions(capacity = 1000L, bucketSize = 4),
        )
        cf.tryInit()
    }

    @AfterEach
    fun teardown() = cf.close()

    @Test
    fun `CuckooFilterOptions - 잘못된 bucketSize 예외`() {
        assertThrows<IllegalArgumentException> { CuckooFilterOptions(bucketSize = 0) }
        assertThrows<IllegalArgumentException> { CuckooFilterOptions(bucketSize = 9) }
    }

    @Test
    fun `insert - contains true`() {
        cf.insert("hello").shouldBeTrue()
        cf.contains("hello").shouldBeTrue()
    }

    @Test
    fun `contains - 없는 원소 false`() {
        cf.contains("not-inserted").shouldBeFalse()
    }

    @Test
    fun `delete - 삽입 후 삭제, 이후 contains false`() {
        cf.insert("world")
        cf.delete("world").shouldBeTrue()
        cf.contains("world").shouldBeFalse()
    }

    @Test
    fun `delete - 없는 원소 삭제 시 false`() {
        cf.delete("ghost").shouldBeFalse()
    }

    @Test
    fun `count - 삽입 및 삭제에 따른 카운트`() {
        cf.insert("a")
        cf.insert("b")
        cf.insert("a")
        cf.count() shouldBeGreaterOrEqualTo 2L
        cf.delete("a")
        cf.count() shouldBeGreaterOrEqualTo 1L
    }

    @Test
    fun `insert 실패 시 기존 원소 유실 없음`() {
        val smallCf = LettuceCuckooFilter(
            client.connect(StringCodec.UTF8),
            "cf-small-${randomName()}",
            CuckooFilterOptions(capacity = 10L, bucketSize = 2, maxIterations = 5),
        )
        smallCf.use {
            it.tryInit()
            val inserted = (1..8).filter { i -> it.insert("item-$i") }
            inserted.forEach { i -> it.contains("item-$i").shouldBeTrue() }
        }
    }
}
