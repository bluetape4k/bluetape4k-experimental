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
open class BinarySerializerBenchmark {

    @Param("Kryo", "Fory", "ChronicleWire", "MessagePack")
    var serializerName: String = "Kryo"

    @Param("SMALL", "MEDIUM", "LARGE")
    var payloadScale: String = "MEDIUM"

    private lateinit var payload: BenchmarkPayload

    private lateinit var serializer: BinarySerializer
    private lateinit var serializedBytes: ByteArray

    @Setup
    fun prepare() {
        val scale = PayloadScale.valueOf(payloadScale)
        payload = BenchmarkFixtures.samplePayload(scale)
        serializer = BenchmarkSerializers.byName(serializerName)
        serializedBytes = serializer.serialize(payload)
    }

    @Benchmark
    fun serialize(): ByteArray = serializer.serialize(payload)

    @Benchmark
    fun deserialize(): BenchmarkPayload? = serializer.deserialize(serializedBytes)

    @Benchmark
    fun roundTrip(): BenchmarkPayload? = serializer.deserialize(serializer.serialize(payload))
}
