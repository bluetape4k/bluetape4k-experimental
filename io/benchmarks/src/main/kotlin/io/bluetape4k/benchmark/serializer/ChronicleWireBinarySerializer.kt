package io.bluetape4k.benchmark.serializer

import io.bluetape4k.io.serializer.AbstractBinarySerializer
import net.openhft.chronicle.bytes.Bytes
import net.openhft.chronicle.core.pool.ClassAliasPool
import net.openhft.chronicle.wire.WireType

class ChronicleWireBinarySerializer(
    private val rootType: Class<out Any>,
) : AbstractBinarySerializer() {

    init {
        ClassAliasPool.CLASS_ALIASES.addAlias(BenchmarkPayload::class.java)
        ClassAliasPool.CLASS_ALIASES.addAlias(BenchmarkAddress::class.java)
        ClassAliasPool.CLASS_ALIASES.addAlias(BenchmarkLineItem::class.java)
    }

    override fun doSerialize(graph: Any): ByteArray =
        Bytes.allocateElasticOnHeap().let { bytes ->
            try {
                val wire = WireType.BINARY.apply(bytes)
                wire.write("payload").`object`(graph)
                bytes.toByteArray()
            } finally {
                bytes.releaseLast()
            }
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> doDeserialize(bytes: ByteArray): T? =
        Bytes.wrapForRead(bytes).let { source ->
            try {
                val wire = WireType.BINARY.apply(source)
                wire.read("payload").`object`(rootType) as? T
            } finally {
                source.releaseLast()
            }
        }
}
