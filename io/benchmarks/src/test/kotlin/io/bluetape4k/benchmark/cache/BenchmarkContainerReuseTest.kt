package io.bluetape4k.benchmark.cache

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldBeTrue
import org.junit.jupiter.api.Test

class BenchmarkContainerReuseTest {

    @Test
    fun `container reuse is disabled by default`() {
        BenchmarkContainerReuse.isEnabled(
            propertyValue = null,
            environment = emptyMap(),
        ).shouldBeFalse()
    }

    @Test
    fun `developer can explicitly enable container reuse locally`() {
        BenchmarkContainerReuse.isEnabled(
            propertyValue = "true",
            environment = emptyMap(),
        ).shouldBeTrue()
    }

    @Test
    fun `CI cannot enable container reuse`() {
        BenchmarkContainerReuse.isEnabled(
            propertyValue = "true",
            environment = mapOf("CI" to "1"),
        ).shouldBeFalse()
        BenchmarkContainerReuse.isEnabled(
            propertyValue = "true",
            environment = mapOf("GITHUB_ACTIONS" to "true"),
        ).shouldBeFalse()
    }

    @Test
    fun `benchmark JVM uses one non-reusable Redis container by default`() {
        BenchmarkRedisSupport.redis shouldBeSameInstanceAs BenchmarkRedisSupport.redis
        BenchmarkRedisSupport.redis.isShouldBeReused.shouldBeFalse()
    }
}
