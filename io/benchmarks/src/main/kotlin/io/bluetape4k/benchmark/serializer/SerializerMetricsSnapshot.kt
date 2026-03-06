package io.bluetape4k.benchmark.serializer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

data class SerializerSizeMetric(
    val serializerName: String,
    val payloadScale: String,
    val serializedBytes: Int,
    val fingerprint: String,
)

fun main(args: Array<String>) {
    val outputPath = args.firstOrNull()
        ?: error("output path argument is required")

    val metrics = buildList {
        PayloadScale.entries.forEach { scale ->
            val payload = BenchmarkFixtures.samplePayload(scale)
            val fingerprint = BenchmarkFixtures.fingerprint(payload)

            BenchmarkSerializers.all().forEach { serializer ->
                add(
                    SerializerSizeMetric(
                        serializerName = serializer.name,
                        payloadScale = scale.name,
                        serializedBytes = serializer.serializer.serialize(payload).size,
                        fingerprint = fingerprint,
                    )
                )
            }
        }
    }

    val mapper = jacksonObjectMapper().registerKotlinModule()
    val outputFile = File(outputPath)
    outputFile.parentFile.mkdirs()
    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, metrics)
}
