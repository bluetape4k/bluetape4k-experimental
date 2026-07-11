package io.bluetape4k.examples.benchmark

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
    fun `application JVM uses one non-reusable PostgreSQL container by default`() {
        BenchmarkPostgreSQL.server shouldBeSameInstanceAs BenchmarkPostgreSQL.server
        BenchmarkPostgreSQL.server.isShouldBeReused.shouldBeFalse()
    }
}
