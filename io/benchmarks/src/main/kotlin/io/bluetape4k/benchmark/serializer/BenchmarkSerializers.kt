package io.bluetape4k.benchmark.serializer

import io.bluetape4k.io.serializer.BinarySerializer
import io.bluetape4k.io.serializer.BinarySerializers

data class NamedBinarySerializer(
    val name: String,
    val serializer: BinarySerializer,
)

object BenchmarkSerializers {
    val kryo: BinarySerializer = BinarySerializers.Kryo
    val fory: BinarySerializer = BinarySerializers.Fory
    val chronicleWire: BinarySerializer = ChronicleWireBinarySerializer(BenchmarkPayload::class.java)
    val messagePack: BinarySerializer = MessagePackBinarySerializer(BenchmarkPayload::class.java)

    fun all(): List<NamedBinarySerializer> = listOf(
        NamedBinarySerializer("Kryo", kryo),
        NamedBinarySerializer("Fory", fory),
        NamedBinarySerializer("ChronicleWire", chronicleWire),
        NamedBinarySerializer("MessagePack", messagePack),
    )

    fun byName(name: String): BinarySerializer =
        all().first { it.name == name }.serializer
}
