package io.bluetape4k.redis.lettuce.hll

import io.bluetape4k.redis.lettuce.AbstractRedisLettuceTest
import io.lettuce.core.codec.StringCodec
import org.amshove.kluent.shouldBeGreaterOrEqualTo
import org.amshove.kluent.shouldBeInRange
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LettuceHyperLogLogTest : AbstractRedisLettuceTest() {

    private lateinit var hll: LettuceHyperLogLog<String>

    @BeforeEach
    fun setup() {
        hll = LettuceHyperLogLog(client.connect(StringCodec.UTF8), "hll-${randomName()}")
    }

    @AfterEach
    fun teardown() = hll.close()

    @Test
    fun `add - 새 원소 추가 시 true`() {
        hll.add("a", "b", "c").shouldBeTrue()
    }

    @Test
    fun `count - 추가한 원소 수 근사값 반환`() {
        hll.add("a", "b", "c", "a")
        hll.count() shouldBeInRange 2L..4L
    }

    @Test
    fun `countWith - 두 HLL 합산 카운트`() {
        val hll2 = LettuceHyperLogLog(client.connect(StringCodec.UTF8), "hll2-${randomName()}")
        hll2.use {
            hll.add("a", "b")
            it.add("c", "d")
            hll.countWith(it) shouldBeInRange 3L..5L
        }
    }

    @Test
    fun `mergeWith - dest에 병합`() {
        val hll2 = LettuceHyperLogLog(client.connect(StringCodec.UTF8), "hll2-${randomName()}")
        val dest = "merged-${randomName()}"
        hll2.use {
            hll.add("a", "b")
            it.add("c", "d")
            hll.mergeWith(dest, it)
        }
        val destHll = LettuceHyperLogLog(client.connect(StringCodec.UTF8), dest)
        destHll.use { it.count() shouldBeGreaterOrEqualTo 1L }
    }
}
