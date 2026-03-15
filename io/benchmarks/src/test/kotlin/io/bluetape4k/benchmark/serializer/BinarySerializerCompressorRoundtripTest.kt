package io.bluetape4k.benchmark.serializer

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import kotlin.test.Test

class BinarySerializerCompressorRoundtripTest {

    @Test
    fun `all serializer-compressor combinations round-trip every payload scale`() {
        PayloadScale.entries.forEach { scale ->
            val payload = BenchmarkFixtures.samplePayload(scale)
            val expected = BenchmarkFixtures.fingerprint(payload)

            SerializerCompressorRegistry.combinations.forEach { candidate ->
                val restored =
                    candidate.serializer.deserialize<BenchmarkPayload>(candidate.serializer.serialize(payload))
                        .shouldNotBeNull()

                BenchmarkFixtures.fingerprint(restored) shouldBeEqualTo expected
            }
        }
    }
}
