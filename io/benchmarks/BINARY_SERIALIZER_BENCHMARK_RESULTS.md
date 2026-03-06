# Binary Serializer Benchmark Results

Measured on 2026-03-06.

Commands used:

- `./gradlew :benchmarks:test`
- `./gradlew :benchmarks:serializerSizeSnapshot`
- `./gradlew :benchmarks:jmhJson`
- `./gradlew :benchmarks:jmhGc`

## Setup

- Module: `:benchmarks`
- Kotlin: `2.3.0`
- Benchmark JDK: `OpenJDK 25.0.2+10-LTS`
- Benchmark plugin: `org.jetbrains.kotlinx.benchmark:0.4.15`
- Throughput mode: `avgt`
- Perf run: `3` warmups, `5` measurements, `1s` each, `fork=1`
- GC run: `2` warmups, `3` measurements, `1s` each, `fork=1`, `-prof gc`
- Allocation metric: `gc.alloc.rate.norm` from JMH GC profiler

Compared serializers:

- `BinarySerializers.Fory`
- `BinarySerializers.Kryo`
- Custom `MessagePackBinarySerializer`
- Custom `ChronicleWireBinarySerializer`

## Small Payload

Payload shape:
- `8` line items
- `3` tags
- `2` attributes per line item

| Serializer | Serialize | Deserialize | Round Trip | Serialized Size | Round Trip Alloc |
|---|---:|---:|---:|---:|---:|
| `Fory` | `0.274 ± 0.027 us/op` | `0.519 ± 0.013 us/op` | `0.849 ± 0.145 us/op` | `938 B` | `4280 B/op` |
| `Kryo` | `1.519 ± 0.010 us/op` | `1.744 ± 0.273 us/op` | `3.676 ± 0.264 us/op` | `918 B` | `22736 B/op` |
| `MessagePack` | `1.678 ± 0.058 us/op` | `2.449 ± 0.094 us/op` | `4.245 ± 0.113 us/op` | `1044 B` | `15672 B/op` |
| `Chronicle Wire` | `2.639 ± 0.074 us/op` | `3.454 ± 0.232 us/op` | `6.755 ± 1.106 us/op` | `1103 B` | `9736 B/op` |

## Medium Payload

Payload shape:
- `24` line items
- `5` tags
- `3` attributes per line item

| Serializer | Serialize | Deserialize | Round Trip | Serialized Size | Round Trip Alloc |
|---|---:|---:|---:|---:|---:|
| `Fory` | `1.056 ± 0.045 us/op` | `1.639 ± 0.161 us/op` | `1.991 ± 0.179 us/op` | `2327 B` | `12096 B/op` |
| `Kryo` | `3.508 ± 0.137 us/op` | `3.299 ± 0.134 us/op` | `6.926 ± 0.558 us/op` | `2249 B` | `30768 B/op` |
| `MessagePack` | `4.955 ± 0.187 us/op` | `6.873 ± 0.375 us/op` | `11.369 ± 0.351 us/op` | `3059 B` | `44784 B/op` |
| `Chronicle Wire` | `7.296 ± 0.262 us/op` | `9.669 ± 0.962 us/op` | `17.062 ± 0.429 us/op` | `3161 B` | `24392 B/op` |

## Large Payload

Payload shape:
- `96` line items
- `10` tags
- `5` attributes per line item

| Serializer | Serialize | Deserialize | Round Trip | Serialized Size | Round Trip Alloc |
|---|---:|---:|---:|---:|---:|
| `Fory` | `3.325 ± 0.109 us/op` | `6.415 ± 0.449 us/op` | `8.754 ± 0.245 us/op` | `11247 B` | `59272 B/op` |
| `Kryo` | `15.579 ± 0.638 us/op` | `14.082 ± 2.689 us/op` | `28.841 ± 1.426 us/op` | `10758 B` | `78888 B/op` |
| `MessagePack` | `24.175 ± 0.984 us/op` | `26.980 ± 2.504 us/op` | `50.096 ± 0.281 us/op` | `14787 B` | `187398 B/op` |
| `Chronicle Wire` | `33.908 ± 1.326 us/op` | `47.558 ± 4.677 us/op` | `79.481 ± 1.750 us/op` | `15136 B` | `93752 B/op` |

## Key Findings

- `Fory` was fastest at every payload size and every operation.
- `Kryo` consistently produced the smallest serialized payloads, but still trailed `Fory` on CPU cost.
- `MessagePack` stayed ahead of `Chronicle Wire` on CPU time, but its allocation burden became the worst on medium and large round-trip runs.
- `Chronicle Wire` stayed slower on CPU, but for `deserialize` and `roundTrip` it often allocated less than `MessagePack`, and on small/medium round-trip it also allocated less than `Kryo`.
- As payload size grew, the CPU gap between `Fory` and the rest widened rather than shrinking.

## Recommendation

If the priority is overall application performance under realistic object round-trips, `Fory` is still the strongest default choice.

If wire size matters more than CPU time, `Kryo` is the next candidate because it produced the smallest payloads on all three sizes.

`MessagePack` and `Chronicle Wire` are harder to justify for this object graph unless you need their interoperability or format characteristics for reasons outside raw JVM performance.

## FST Attempt

`FST` was evaluated for the same Java 21/25 environment and excluded.

- Added `de.ruedigermoeller:fst:2.56` and a benchmark-local `FstBinarySerializer`
- Initialization failed under modern Java module encapsulation
- Access failures required opening internal packages such as `java.lang`, `java.math`, and `java.util`
- Because it was not usable in the repository's default JDK setup, it was not benchmarked further

## Raw Artifacts

- Perf JSON: [`jmh-results.json`](/Users/debop/work/bluetape4k/bluetape4k-experimental/io/benchmarks/build/reports/benchmarks/jmh-results.json)
- GC JSON: [`jmh-gc-results.json`](/Users/debop/work/bluetape4k/bluetape4k-experimental/io/benchmarks/build/reports/benchmarks/jmh-gc-results.json)
- Serialized size JSON: [`serialized-size.json`](/Users/debop/work/bluetape4k/bluetape4k-experimental/io/benchmarks/build/reports/benchmarks/serialized-size.json)
