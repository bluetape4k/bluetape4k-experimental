package io.bluetape4k.benchmark.serializer

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

data class SerializerCompressorSizeMetric(
    val combinationName: String,
    val payloadScale: String,
    val serializedBytes: Int,
    val fingerprint: String,
)

/**
 * 직렬화기+압축기 조합별 payload 크기 스냅샷을 JSON 파일로 기록한다.
 *
 * `outputPath`가 단순 파일명이어도 부모 디렉터리 접근에서 예외가 나지 않아야 한다.
 */
fun writeSerializerCompressorMetricsSnapshot(outputPath: String) {
    val metrics = buildList {
        PayloadScale.entries.forEach { scale ->
            val payload = BenchmarkFixtures.samplePayload(scale)
            val fingerprint = BenchmarkFixtures.fingerprint(payload)

            SerializerCompressorRegistry.combinations.forEach { combination ->
                add(
                    SerializerCompressorSizeMetric(
                        combinationName = combination.name,
                        payloadScale = scale.name,
                        serializedBytes = combination.serializer.serialize(payload).size,
                        fingerprint = fingerprint,
                    )
                )
            }
        }
    }

    val mapper = jacksonObjectMapper().registerKotlinModule()
    val outputFile = prepareOutputFile(outputPath)
    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, metrics)
}

fun main(args: Array<String>) {
    val outputPath = args.firstOrNull()
        ?: error("output path argument is required")
    writeSerializerCompressorMetricsSnapshot(outputPath)
}
