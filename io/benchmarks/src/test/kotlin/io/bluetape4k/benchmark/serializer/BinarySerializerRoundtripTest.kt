package io.bluetape4k.benchmark.serializer

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import kotlin.test.Test

class BinarySerializerRoundtripTest {

    @Test
    fun `all serializers round-trip every payload scale`() {
        PayloadScale.entries.forEach { scale ->
            val payload = BenchmarkFixtures.samplePayload(scale)
            val expected = BenchmarkFixtures.fingerprint(payload)

            BenchmarkSerializers.all().forEach { candidate ->
                val restored =
                    candidate.serializer.deserialize<BenchmarkPayload>(candidate.serializer.serialize(payload))
                        .shouldNotBeNull()

                BenchmarkFixtures.fingerprint(restored) shouldBeEqualTo expected
            }
        }
    }
}
