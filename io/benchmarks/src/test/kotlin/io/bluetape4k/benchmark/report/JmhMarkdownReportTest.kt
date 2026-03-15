package io.bluetape4k.benchmark.report

import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldBeTrue
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test

class JmhMarkdownReportTest {

    @Test
    fun `writeJmhMarkdownReport renders grouped markdown with sorted params`() {
        val workDir = Files.createTempDirectory("jmh-report")
        val input = workDir.resolve("results.json")
        val output = workDir.resolve("reports/benchmark.md")

        try {
            Files.writeString(
                input,
                """
                [
                  {
                    "jmhVersion": "1.37",
                    "benchmark": "io.bluetape4k.benchmark.serializer.BinarySerializerBenchmark.roundTrip",
                    "mode": "thrpt",
                    "forks": 1,
                    "jdkVersion": "25",
                    "warmupIterations": 3,
                    "warmupTime": "1 s",
                    "measurementIterations": 5,
                    "measurementTime": "1 s",
                    "params": {
                      "serializer": "kryo5",
                      "compressor": "lz4"
                    },
                    "primaryMetric": {
                      "score": 1234.5678,
                      "scoreUnit": "ops/s"
                    }
                  }
                ]
                """.trimIndent()
            )

            writeJmhMarkdownReport(
                input = input.toFile(),
                output = output.toFile(),
                title = "Serializer Benchmarks",
            )

            Files.exists(output).shouldBeTrue()
            val markdown = output.readText()
            markdown shouldContain "# Serializer Benchmarks"
            markdown shouldContain "## BinarySerializerBenchmark"
            markdown shouldContain "`compressor=lz4, serializer=kryo5`"
            markdown shouldContain "`1234.568 ops/s`"
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(output.parent)
            Files.deleteIfExists(input)
            Files.deleteIfExists(workDir)
        }
    }

    @Test
    fun `writeJmhMarkdownReport writes empty message for empty rows`() {
        val workDir = Files.createTempDirectory("jmh-empty-report")
        val input = workDir.resolve("empty.json")
        val output = workDir.resolve("report.md")

        try {
            Files.writeString(input, "[]")

            writeJmhMarkdownReport(
                input = input.toFile(),
                output = output.toFile(),
                title = "Empty Benchmarks",
            )

            val markdown = output.readText()
            markdown shouldContain "# Empty Benchmarks"
            markdown shouldContain "No benchmark rows found."
        } finally {
            Files.deleteIfExists(output)
            Files.deleteIfExists(input)
            Files.deleteIfExists(workDir)
        }
    }
}
