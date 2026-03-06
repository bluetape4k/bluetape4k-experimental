package io.bluetape4k.benchmark.serializer

import io.bluetape4k.io.serializer.BinarySerializer
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class BinarySerializerCompressorBenchmark {

    @Param(
        "Jdk",
        "Jdk+BZip2",
        "Jdk+Deflate",
        "Jdk+GZip",
        "Jdk+LZ4",
        "Jdk+Snappy",
        "Jdk+Zstd",
        "Kryo",
        "Kryo+BZip2",
        "Kryo+Deflate",
        "Kryo+GZip",
        "Kryo+LZ4",
        "Kryo+Snappy",
        "Kryo+Zstd",
        "Fory",
        "Fory+BZip2",
        "Fory+Deflate",
        "Fory+GZip",
        "Fory+LZ4",
        "Fory+Snappy",
        "Fory+Zstd",
    )
    var combinationName: String = "Fory"

    @Param("SMALL", "MEDIUM", "LARGE")
    var payloadScale: String = "MEDIUM"

    private lateinit var serializer: BinarySerializer
    private lateinit var payload: BenchmarkPayload

    @Setup
    fun prepare() {
        serializer = SerializerCompressorRegistry.byName(combinationName)
        payload = BenchmarkFixtures.samplePayload(PayloadScale.valueOf(payloadScale))
    }

    @Benchmark
    fun roundTrip(): BenchmarkPayload? =
        serializer.deserialize(serializer.serialize(payload))
}
