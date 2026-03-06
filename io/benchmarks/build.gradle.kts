plugins {
    kotlin("plugin.allopen")
    id(Plugins.kotlinx_benchmark) version Plugins.Versions.kotlinx_benchmark
}

import org . gradle . api . tasks . SourceSetContainer
        import org . gradle . kotlin . dsl . getByType

        dependencies {
            implementation(Libs.bluetape4k_io)
            implementation(Libs.commons_compress)
            implementation(Libs.kryo5)
            implementation(Libs.fory_kotlin)
            implementation(Libs.lz4_java)
            implementation(Libs.snappy_java)
            implementation(Libs.zstd_jni)
            implementation(Libs.chronicle_wire)
            implementation(Libs.jackson_module_kotlin)
            implementation(Libs.jackson_dataformat_msgpack)

            implementation(Libs.kotlinx_benchmark_runtime)
            implementation(Libs.kotlinx_benchmark_runtime_jvm)

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
            includes = mutableListOf("BinarySerializerBenchmark")
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

tasks.register<JavaExec>("jmhJson") {
    group = "benchmark"
    description = "Run serializer benchmarks with JMH JSON output."
    dependsOn("mainBenchmarkCompile")
    classpath = benchmarkClasspath
    mainClass.set("org.openjdk.jmh.Main")
    jvmArgs(benchmarkJvmArgs())
    args(
        ".*BinarySerializerBenchmark.*",
        "-f", "1",
        "-wi", "3",
        "-i", "5",
        "-w", "1s",
        "-r", "1s",
        "-tu", "us",
        "-bm", "avgt",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/benchmarks/jmh-results.json").get().asFile.absolutePath,
    )
}

tasks.register<JavaExec>("jmhGc") {
    group = "benchmark"
    description = "Run serializer benchmarks with the JMH GC profiler enabled."
    dependsOn("mainBenchmarkCompile")
    classpath = benchmarkClasspath
    mainClass.set("org.openjdk.jmh.Main")
    jvmArgs(benchmarkJvmArgs())
    args(
        ".*BinarySerializerBenchmark.*",
        "-f", "1",
        "-wi", "2",
        "-i", "3",
        "-w", "1s",
        "-r", "1s",
        "-tu", "us",
        "-bm", "avgt",
        "-prof", "gc",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/benchmarks/jmh-gc-results.json").get().asFile.absolutePath,
    )
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
    group = "benchmark"
    description = "Run serializer+compressor combination benchmarks with JMH JSON output."
    dependsOn("mainBenchmarkCompile")
    classpath = benchmarkClasspath
    mainClass.set("org.openjdk.jmh.Main")
    jvmArgs(benchmarkJvmArgs())
    args(
        ".*BinarySerializerCompressorBenchmark.*",
        "-f", "1",
        "-wi", "2",
        "-i", "4",
        "-w", "1s",
        "-r", "1s",
        "-tu", "us",
        "-bm", "avgt",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/benchmarks/combo-jmh-results.json").get().asFile.absolutePath,
    )
}

tasks.register<JavaExec>("comboJmhGc") {
    group = "benchmark"
    description = "Run serializer+compressor combination benchmarks with the JMH GC profiler."
    dependsOn("mainBenchmarkCompile")
    classpath = benchmarkClasspath
    mainClass.set("org.openjdk.jmh.Main")
    jvmArgs(benchmarkJvmArgs())
    args(
        ".*BinarySerializerCompressorBenchmark.*",
        "-f", "1",
        "-wi", "1",
        "-i", "2",
        "-w", "1s",
        "-r", "1s",
        "-tu", "us",
        "-bm", "avgt",
        "-prof", "gc",
        "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/benchmarks/combo-jmh-gc-results.json").get().asFile.absolutePath,
    )
}

tasks.register<JavaExec>("comboSizeSnapshot") {
    group = "benchmark"
    description = "Write serialized byte sizes for serializer+compressor combinations."
    dependsOn("classes")
    classpath = sourceSets.named("main").get().runtimeClasspath
    mainClass.set("io.bluetape4k.benchmark.serializer.SerializerCompressorMetricsSnapshotKt")
    args(layout.buildDirectory.file("reports/benchmarks/combo-serialized-size.json").get().asFile.absolutePath)
}
