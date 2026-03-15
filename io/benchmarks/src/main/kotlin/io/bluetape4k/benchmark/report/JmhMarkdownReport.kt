package io.bluetape4k.benchmark.report

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.time.LocalDate

/**
 * JMH JSON 결과를 Markdown 표로 변환하는 간단한 CLI 입니다.
 */
fun main(args: Array<String>) {
    require(args.size >= 2) {
        "usage: JmhMarkdownReportKt <input-json> <output-md> [title]"
    }

    val input = File(args[0])
    val output = File(args[1])
    val title = args.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "JMH Benchmark Report"

    writeJmhMarkdownReport(input = input, output = output, title = title)
}

internal fun writeJmhMarkdownReport(
    input: File,
    output: File,
    title: String,
) {
    require(input.exists()) { "input json not found: ${input.absolutePath}" }

    val mapper = jacksonObjectMapper()
    val rows: List<JmhRow> = mapper.readValue(input)
    output.parentFile?.mkdirs()
    output.writeText(buildMarkdown(title, input, rows))
}

private fun buildMarkdown(
    title: String,
    input: File,
    rows: List<JmhRow>,
): String {
    if (rows.isEmpty()) {
        return """
            |# $title
            |
            |Measured on ${LocalDate.now()}.
            |
            |Input: `${input.absolutePath}`
            |
            |No benchmark rows found.
            |""".trimMargin()
    }

    val mode = rows.first().mode
    val jdkVersion = rows.first().jdkVersion
    val groups = rows.groupBy { benchmarkGroup(it.benchmark) }
        .toSortedMap()

    return buildString {
        appendLine("# $title")
        appendLine()
        appendLine("Measured on ${LocalDate.now()}.")
        appendLine()
        appendLine("## Setup")
        appendLine()
        appendLine("- Input: `${input.absolutePath}`")
        appendLine("- JMH: `${rows.first().jmhVersion}`")
        appendLine("- JDK: `${jdkVersion}`")
        appendLine("- Mode: `${mode}`")
        appendLine("- Warmup: `${rows.first().warmupIterations}` x `${rows.first().warmupTime}`")
        appendLine("- Measurement: `${rows.first().measurementIterations}` x `${rows.first().measurementTime}`")
        appendLine("- Forks: `${rows.first().forks}`")
        appendLine()

        groups.forEach { (group, items) ->
            appendLine("## $group")
            appendLine()
            appendLine("| Benchmark | Params | Score |")
            appendLine("|---|---|---:|")
            items.sortedWith(compareBy({ shortBenchmarkName(it.benchmark) }, { formatParams(it.params) }))
                .forEach { row ->
                    appendLine(
                        "| `${shortBenchmarkName(row.benchmark)}` | `${formatParams(row.params)}` | `${formatScore(row)}` |"
                    )
                }
            appendLine()
        }
    }
}

private fun benchmarkGroup(benchmark: String): String =
    benchmark.substringBeforeLast('.').substringAfterLast('.')

private fun shortBenchmarkName(benchmark: String): String =
    benchmark.substringAfterLast('.')

private fun formatParams(params: Map<String, String>): String =
    if (params.isEmpty()) "-" else params.entries
        .sortedBy { it.key }
        .joinToString(", ") { "${it.key}=${it.value}" }

private fun formatScore(row: JmhRow): String =
    "%.3f %s".format(row.primaryMetric.score, row.primaryMetric.scoreUnit)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class JmhRow(
    val jmhVersion: String,
    val benchmark: String,
    val mode: String,
    val forks: Int,
    val jdkVersion: String,
    val warmupIterations: Int,
    val warmupTime: String,
    val measurementIterations: Int,
    val measurementTime: String,
    val params: Map<String, String> = emptyMap(),
    val primaryMetric: PrimaryMetric,
)

@JsonIgnoreProperties(ignoreUnknown = true)
internal data class PrimaryMetric(
    val score: Double,
    val scoreUnit: String,
)
