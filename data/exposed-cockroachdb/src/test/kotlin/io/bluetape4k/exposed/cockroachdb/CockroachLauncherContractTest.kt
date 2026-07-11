package io.bluetape4k.exposed.cockroachdb

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test

class CockroachLauncherContractTest: AbstractCockroachDBTest() {

    @Test
    fun `module JVM uses one non-reusable Cockroach launcher`() {
        cockroach shouldBeSameInstanceAs cockroach
        cockroach.isShouldBeReused.shouldBeFalse()
    }
}
