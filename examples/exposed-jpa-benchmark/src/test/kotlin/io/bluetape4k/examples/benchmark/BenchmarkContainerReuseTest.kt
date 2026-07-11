package io.bluetape4k.examples.benchmark

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeSameInstanceAs
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldBeZero
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.utility.TestcontainersConfiguration
import java.nio.file.Path
import kotlin.io.path.Path

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
        BenchmarkContainerReuse.isEnabled(
            propertyValue = "true",
            environment = mapOf("CI" to ""),
        ).shouldBeFalse()
        BenchmarkContainerReuse.isEnabled(
            propertyValue = "true",
            environment = mapOf("GITHUB_ACTIONS" to ""),
        ).shouldBeFalse()
    }

    @Test
    fun `application JVM uses one non-reusable PostgreSQL container by default`() {
        BenchmarkPostgreSQL.server shouldBeSameInstanceAs BenchmarkPostgreSQL.server
        BenchmarkPostgreSQL.server.isShouldBeReused.shouldBeFalse()
    }

    @Test
    fun `reusable PostgreSQL container survives the launcher JVM`() {
        assumeTrue(
            !System.getenv().containsKey("CI") && !System.getenv().containsKey("GITHUB_ACTIONS"),
            "Developer-local reuse is intentionally unavailable in CI",
        )
        assumeTrue(
            TestcontainersConfiguration.getInstance().environmentSupportsReuse(),
            "Testcontainers reusable-container support is not enabled locally",
        )

        val containerId = launchProbe(reuse = true)

        try {
            dockerContainerIsRunning(containerId).shouldBeTrue()
        } finally {
            removeContainer(containerId)
        }
    }

    @Test
    fun `non-reusable PostgreSQL container stops with the launcher JVM`() {
        val containerId = launchProbe(reuse = false)

        dockerContainerIsRunning(containerId).shouldBeFalse()
    }

    private fun launchProbe(reuse: Boolean): String {
        val process = ProcessBuilder(
            javaExecutable().toString(),
            "-D${BenchmarkContainerReuse.PROPERTY_NAME}=$reuse",
            "-cp",
            System.getProperty("java.class.path"),
            "io.bluetape4k.examples.benchmark.BenchmarkContainerReuseTestKt",
            "probe-reusable-postgresql",
        ).apply {
            environment().remove("CI")
            environment().remove("GITHUB_ACTIONS")
            redirectErrorStream(true)
        }.start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor().shouldBeZero()
        return output.lineSequence().last { it.startsWith("CONTAINER_ID=") }.substringAfter('=')
    }

    private fun javaExecutable(): Path =
        Path(System.getProperty("java.home"), "bin", "java")

    private fun dockerContainerIsRunning(containerId: String): Boolean {
        val process = ProcessBuilder("docker", "inspect", "-f", "{{.State.Running}}", containerId)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        return process.waitFor() == 0 && output == "true"
    }

    private fun removeContainer(containerId: String) {
        ProcessBuilder("docker", "rm", "-f", containerId).start().waitFor()
    }
}

fun main(args: Array<String>) {
    if (args.singleOrNull() == "probe-reusable-postgresql") {
        println("CONTAINER_ID=${BenchmarkPostgreSQL.server.containerId}")
    }
}
