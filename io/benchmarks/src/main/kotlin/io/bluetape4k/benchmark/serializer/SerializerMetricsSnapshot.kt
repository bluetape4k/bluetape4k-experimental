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

/**
 * 직렬화기별 payload 크기 스냅샷을 JSON 파일로 기록한다.
 *
 * `outputPath`에 디렉터리 구성이 없더라도 현재 작업 디렉터리에 안전하게 파일을 생성한다.
 */
fun writeSerializerMetricsSnapshot(outputPath: String) {
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
    val outputFile = prepareOutputFile(outputPath)
    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, metrics)
}

fun main(args: Array<String>) {
    val outputPath = args.firstOrNull()
        ?: error("output path argument is required")
    writeSerializerMetricsSnapshot(outputPath)
}

internal fun prepareOutputFile(outputPath: String): File =
    File(outputPath).also { file ->
        file.parentFile?.mkdirs()
    }
