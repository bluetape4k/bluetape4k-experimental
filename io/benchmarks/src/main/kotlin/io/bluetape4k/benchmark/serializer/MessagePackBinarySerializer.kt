package io.bluetape4k.benchmark.serializer

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import io.bluetape4k.io.serializer.AbstractBinarySerializer
import org.msgpack.jackson.dataformat.MessagePackFactory

class MessagePackBinarySerializer(
    private val rootType: Class<out Any>,
) : AbstractBinarySerializer() {

    private val mapper = JsonMapper.builder(MessagePackFactory())
        .addModule(kotlinModule())
        .build()

    override fun doSerialize(graph: Any): ByteArray =
        mapper.writeValueAsBytes(graph)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> doDeserialize(bytes: ByteArray): T? =
        mapper.readValue(bytes, rootType) as? T
}
