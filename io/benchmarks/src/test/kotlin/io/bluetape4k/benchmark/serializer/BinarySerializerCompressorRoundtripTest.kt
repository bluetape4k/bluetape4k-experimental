package io.bluetape4k.benchmark.serializer

import kotlin.test.Test
import kotlin.test.assertEquals

class BinarySerializerCompressorRoundtripTest {

    @Test
    fun `all serializer-compressor combinations round-trip every payload scale`() {
        PayloadScale.entries.forEach { scale ->
            val payload = BenchmarkFixtures.samplePayload(scale)
            val expected = BenchmarkFixtures.fingerprint(payload)

            SerializerCompressorRegistry.combinations.forEach { candidate ->
                val restored = candidate.serializer.deserialize<BenchmarkPayload>(candidate.serializer.serialize(payload))
                assertEquals(expected, BenchmarkFixtures.fingerprint(restored!!), "${candidate.name}-$scale")
            }
        }
    }
}
