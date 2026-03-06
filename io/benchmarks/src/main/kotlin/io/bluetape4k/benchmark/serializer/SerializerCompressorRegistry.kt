package io.bluetape4k.benchmark.serializer

import io.bluetape4k.io.compressor.Compressor
import io.bluetape4k.io.compressor.Compressors
import io.bluetape4k.io.serializer.BinarySerializer
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.io.serializer.CompressableBinarySerializer

data class NamedSerializerCompressor(
    val name: String,
    val serializer: BinarySerializer,
)

object SerializerCompressorRegistry {

    private data class BaseSerializer(
        val name: String,
        val serializer: BinarySerializer,
    )

    private data class NamedCompressor(
        val name: String,
        val compressor: Compressor,
    )

    private val baseSerializers = listOf(
        BaseSerializer("Jdk", BinarySerializers.Jdk),
        BaseSerializer("Kryo", BinarySerializers.Kryo),
        BaseSerializer("Fory", BinarySerializers.Fory),
    )

    private val compressors = listOf(
        NamedCompressor("BZip2", Compressors.BZip2),
        NamedCompressor("Deflate", Compressors.Deflate),
        NamedCompressor("GZip", Compressors.GZip),
        NamedCompressor("LZ4", Compressors.LZ4),
        NamedCompressor("Snappy", Compressors.Snappy),
        NamedCompressor("Zstd", Compressors.Zstd),
    )

    val combinations: List<NamedSerializerCompressor> =
        buildList {
            baseSerializers.forEach { base ->
                add(NamedSerializerCompressor(base.name, base.serializer))
                compressors.forEach { compressor ->
                    add(
                        NamedSerializerCompressor(
                            "${base.name}+${compressor.name}",
                            CompressableBinarySerializer(base.serializer, compressor.compressor),
                        )
                    )
                }
            }
        }

    fun names(): List<String> = combinations.map { it.name }

    fun byName(name: String): BinarySerializer =
        combinations.first { it.name == name }.serializer
}
