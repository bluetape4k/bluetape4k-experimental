import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
}

dependencies {
    implementation(libs.bluetape4k.cache.lettuce)
    implementation(libs.bluetape4k.spring.boot3.exposed.r2dbc)
    implementation(libs.bluetape4k.io)
    implementation(libs.bluetape4k.junit5)
    implementation(libs.commons.compress)
    implementation(libs.kryo5)
    implementation(libs.fory.kotlin)
    implementation(libs.lz4.java)
    implementation(libs.snappy.java)
    implementation(libs.zstd.jni)
    implementation(libs.chronicle.wire)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.msgpack)

    implementation(libs.kotlinx.benchmark.runtime)
    implementation(libs.kotlinx.benchmark.runtimejvm)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.exposed.migration.r2dbc)
    implementation(libs.bluetape4k.testcontainers)
    implementation(libs.testcontainers.lib)
    implementation(libs.h2.v2)
    implementation(libs.r2dbc.h2)

    testImplementation(kotlin("test"))
}

allOpen {
    annotation("kotlinx.benchmark.State")
}

benchmark {
    targets {
        register("main")
    }

    configurations {
        named("main") {
            warmups = 5
            iterations = 10
            iterationTime = 1
            iterationTimeUnit = "s"
            outputTimeUnit = "us"
            mode = "avgt"
            includes = mutableListOf(".*Benchmark")
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name.contains("Benchmark", ignoreCase = true)) {
        jvmArgs(
            "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.util=ALL-UNNAMED",
            "--illegal-access=permit",
        )
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name.contains("Benchmark", ignoreCase = true)) {
        jvmArgs("-Djmh.ignoreLock=true")
    }
}

val sourceSets = extensions.getByType<SourceSetContainer>()
val benchmarkClasspath = files(
    layout.buildDirectory.dir("benchmarks/main/classes"),
    layout.buildDirectory.dir("benchmarks/main/resources"),
    sourceSets.named("main").get().output,
    configurations.runtimeClasspath,
)

fun benchmarkJvmArgs() = listOf(
    "-Djmh.ignoreLock=true",
    "--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED",
    "--add-exports=java.base/jdk.internal.util=ALL-UNNAMED",
    "--illegal-access=permit",
)

fun benchmarkProperty(name: String, defaultValue: String): Provider<String> =
    providers.gradleProperty(name).orElse(defaultValue)

fun benchmarkReportPath(defaultFileName: String): String {
    val custom = providers.gradleProperty("benchmark.resultFile").orNull
    if (!custom.isNullOrBlank()) return layout.projectDirectory.file(custom).asFile.absolutePath

    val tag = providers.gradleProperty("benchmark.tag").orNull
    val fileName = if (tag.isNullOrBlank()) {
        defaultFileName
    } else {
        val dot = defaultFileName.lastIndexOf('.')
        if (dot >= 0) {
            defaultFileName.substring(0, dot) + "-$tag" + defaultFileName.substring(dot)
        } else {
            "$defaultFileName-$tag"
        }
    }
    return layout.buildDirectory.file("reports/benchmarks/$fileName").get().asFile.absolutePath
}

fun markdownPathFromJson(jsonPath: String): String =
    if (jsonPath.endsWith(".json")) jsonPath.removeSuffix(".json") + ".md" else "$jsonPath.md"

fun configureJmhExec(
    task: JavaExec,
    includePattern: String,
    defaultResultFileName: String,
    profiler: String? = null,
) {
    task.group = "benchmark"
    task.dependsOn("mainBenchmarkCompile")
    task.classpath = benchmarkClasspath
    task.mainClass.set("org.openjdk.jmh.Main")
    task.jvmArgs(benchmarkJvmArgs())

    val warmups = benchmarkProperty("benchmark.warmups", "3").get()
    val iterations = benchmarkProperty("benchmark.iterations", "5").get()
    val warmupTime = benchmarkProperty("benchmark.warmupSeconds", "1s").get()
    val measureTime = benchmarkProperty("benchmark.measureSeconds", "1s").get()
    val forks = benchmarkProperty("benchmark.forks", "1").get()
    val timeUnit = benchmarkProperty("benchmark.timeUnit", "us").get()
    val mode = benchmarkProperty("benchmark.mode", "avgt").get()
    val format = benchmarkProperty("benchmark.format", "json").get()
    val include = benchmarkProperty("benchmark.include", includePattern).get()
    val reportPath = benchmarkReportPath(defaultResultFileName)

    task.doFirst {
        File(reportPath).parentFile?.mkdirs()
    }

    task.args(
        include,
        "-f", forks,
        "-wi", warmups,
        "-i", iterations,
        "-w", warmupTime,
        "-r", measureTime,
        "-tu", timeUnit,
        "-bm", mode,
        "-rf", format,
        "-rff", reportPath,
    )
    if (!profiler.isNullOrBlank()) {
        task.args("-prof", profiler)
    }
}

tasks.register<JavaExec>("jmhJson") {
    description = "Run serializer benchmarks with JMH JSON output."
    configureJmhExec(this, ".*BinarySerializerBenchmark.*", "jmh-results.json")
}

tasks.register<JavaExec>("jmhGc") {
    description = "Run serializer benchmarks with the JMH GC profiler enabled."
    configureJmhExec(this, ".*BinarySerializerBenchmark.*", "jmh-gc-results.json", profiler = "gc")
}

tasks.register<JavaExec>("serializerSizeSnapshot") {
    group = "benchmark"
    description = "Write serialized byte sizes for each serializer and payload scale."
    dependsOn("classes")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("io.bluetape4k.benchmark.serializer.SerializerMetricsSnapshotKt")
    args(layout.buildDirectory.file("reports/benchmarks/serialized-size.json").get().asFile.absolutePath)
}

tasks.register<JavaExec>("comboJmhJson") {
    description = "Run serializer+compressor combination benchmarks with JMH JSON output."
    configureJmhExec(this, ".*BinarySerializerCompressorBenchmark.*", "combo-jmh-results.json")
}

tasks.register<JavaExec>("comboJmhGc") {
    description = "Run serializer+compressor combination benchmarks with the JMH GC profiler."
    configureJmhExec(this, ".*BinarySerializerCompressorBenchmark.*", "combo-jmh-gc-results.json", profiler = "gc")
}

tasks.register<JavaExec>("comboSizeSnapshot") {
    group = "benchmark"
    description = "Write serialized byte sizes for serializer+compressor combinations."
    dependsOn("classes")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("io.bluetape4k.benchmark.serializer.SerializerCompressorMetricsSnapshotKt")
    args(layout.buildDirectory.file("reports/benchmarks/combo-serialized-size.json").get().asFile.absolutePath)
}

tasks.register<JavaExec>("benchmarkCustom") {
    description = "Run arbitrary JMH benchmarks via -Pbenchmark.include and optional -Pbenchmark.* overrides."
    configureJmhExec(this, ".*", "custom-jmh-results.json")
}

tasks.register("benchmarkSuite") {
    group = "benchmark"
    description = "Run serializer snapshots and both serializer JMH suites."
    dependsOn(
        "serializerSizeSnapshot",
        "comboSizeSnapshot",
        "jmhJson",
        "comboJmhJson",
    )
}

tasks.register<JavaExec>("benchmarkQuick") {
    group = "benchmark"
    description = "Run a short smoke benchmark suitable for regression checks in local development."
    dependsOn("mainBenchmarkCompile")
    classpath = benchmarkClasspath
    mainClass.set("org.openjdk.jmh.Main")
    jvmArgs(benchmarkJvmArgs())
    val reportPath = benchmarkReportPath("quick-jmh-results.json")
    doFirst {
        File(reportPath).parentFile?.mkdirs()
    }
    args(
        benchmarkProperty("benchmark.include", ".*BinarySerializerBenchmark.*").get(),
        "-f", "1",
        "-wi", "1",
        "-i", "1",
        "-w", "1s",
        "-r", "1s",
        "-tu", benchmarkProperty("benchmark.timeUnit", "us").get(),
        "-bm", benchmarkProperty("benchmark.mode", "avgt").get(),
        "-rf", "json",
        "-rff", reportPath,
    )
}

tasks.register("benchmarkInfo") {
    group = "benchmark"
    description = "Print benchmark task usage and active -Pbenchmark.* overrides."
    doLast {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        println("Benchmark helper [$timestamp]")
        println("Tasks:")
        println(" - ./gradlew :benchmarks:benchmarkQuick")
        println(" - ./gradlew :benchmarks:jmhJson")
        println(" - ./gradlew :benchmarks:jmhGc")
        println(" - ./gradlew :benchmarks:comboJmhJson")
        println(" - ./gradlew :benchmarks:comboJmhGc")
        println(" - ./gradlew :benchmarks:benchmarkCustom -Pbenchmark.include='.*MyBenchmark.*'")
        println(" - ./gradlew :benchmarks:benchmarkSuite")
        println(" - ./gradlew :benchmarks:benchmarkMarkdown -Pbenchmark.inputJson=build/reports/benchmarks/custom-jmh-results.json -Pbenchmark.outputMd=build/reports/benchmarks/custom-jmh-results.md")
        println()
        println("Supported overrides:")
        println(" -Pbenchmark.include=<regex>")
        println(" -Pbenchmark.warmups=<count>")
        println(" -Pbenchmark.iterations=<count>")
        println(" -Pbenchmark.warmupSeconds=<duration, e.g. 1s>")
        println(" -Pbenchmark.measureSeconds=<duration, e.g. 2s>")
        println(" -Pbenchmark.forks=<count>")
        println(" -Pbenchmark.mode=<avgt|thrpt|sample|ss>")
        println(" -Pbenchmark.timeUnit=<ns|us|ms|s>")
        println(" -Pbenchmark.format=<json|text|csv|scsv|latex>")
        println(" -Pbenchmark.resultFile=<relative path from io/benchmarks>")
        println(" -Pbenchmark.tag=<suffix appended to default report file>")
        println(" -Pbenchmark.inputJson=<relative path from io/benchmarks>")
        println(" -Pbenchmark.outputMd=<relative path from io/benchmarks>")
        println(" -Pbenchmark.title=<markdown report title>")
    }
}

tasks.register<JavaExec>("benchmarkMarkdown") {
    group = "benchmark"
    description = "Convert a JMH JSON result file into a Markdown summary."
    dependsOn("classes")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("io.bluetape4k.benchmark.report.JmhMarkdownReportKt")

    val inputJson = providers.gradleProperty("benchmark.inputJson")
        .orElse("build/reports/benchmarks/custom-jmh-results.json")
        .get()
    val outputMd = providers.gradleProperty("benchmark.outputMd")
        .orElse("build/reports/benchmarks/custom-jmh-results.md")
        .get()
    val title = providers.gradleProperty("benchmark.title")
        .orElse("JMH Benchmark Report")
        .get()

    args(
        layout.projectDirectory.file(inputJson).asFile.absolutePath,
        layout.projectDirectory.file(outputMd).asFile.absolutePath,
        title,
    )
}

tasks.register<JavaExec>("benchmarkCustomReport") {
    group = "benchmark"
    description = "Run benchmarkCustom and immediately convert its JSON output into Markdown."
    dependsOn("benchmarkCustom")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("io.bluetape4k.benchmark.report.JmhMarkdownReportKt")

    val inputJson = providers.gradleProperty("benchmark.resultFile")
        .orElse(benchmarkReportPath("custom-jmh-results.json"))
        .get()
    val outputMd = providers.gradleProperty("benchmark.outputMd")
        .orElse(markdownPathFromJson(inputJson))
        .get()
    val title = providers.gradleProperty("benchmark.title")
        .orElse("JMH Benchmark Report")
        .get()

    args(
        if (inputJson.startsWith("/")) inputJson else layout.projectDirectory.file(inputJson).asFile.absolutePath,
        if (outputMd.startsWith("/")) outputMd else layout.projectDirectory.file(outputMd).asFile.absolutePath,
        title,
    )
}
