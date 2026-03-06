# Binary Serializer + Compressor Combination Results

Measured on 2026-03-06.

Commands used:

- `./gradlew :benchmarks:test`
- `./gradlew :benchmarks:comboSizeSnapshot`
- `./gradlew :benchmarks:comboJmhJson`
- `./gradlew :benchmarks:comboJmhGc`

## Setup

- Module: `:benchmarks`
- Benchmark JDK: `OpenJDK 25.0.2+10-LTS`
- Workload: `roundTrip`
- Base serializers: `Jdk`, `Kryo`, `Fory`
- Compressors: `none`, `BZip2`, `Deflate`, `GZip`, `LZ4`, `Snappy`, `Zstd`
- Performance run: `2` warmups, `4` measurements, `1s`, `fork=1`
- GC run: `1` warmup, `2` measurements, `1s`, `fork=1`, `-prof gc`
- Allocation metric: `gc.alloc.rate.norm`

## Recommendation

### Best default

Use `Fory` without compression as the default.

- Fastest round-trip on all payload sizes
- Lowest allocation burden on all payload sizes
- Compression wrappers reduce size, but none of them beat plain `Fory` on overall latency

### Best compressed default

Use `Fory + Snappy` when you want meaningful compression without blowing up CPU cost.

- Small: `1.588 us/op`, `662 B`, `7064 B/op`
- Medium: `3.392 us/op`, `933 B`, `18160 B/op`
- Large: `15.944 us/op`, `2358 B`, `86088 B/op`

Why `Snappy`:

- It stays close to `Fory + LZ4` on CPU
- It is smaller than `Fory + LZ4` on `MEDIUM` and `LARGE`, while `LZ4` is slightly smaller on `SMALL`
- It avoids the very large CPU penalty of `Deflate`, `GZip`, `Zstd`, and especially `BZip2`

### Best size-first choice

Use `Kryo + Deflate` for small/medium payloads, and `Kryo + Zstd` for large payloads.

- Small minimum size: `Kryo+Deflate` at `492 B`
- Medium minimum size: `Kryo+Deflate` at `690 B`
- Large minimum size: `Kryo+Zstd` at `1396 B`

This is the right direction only when wire size matters more than latency.

### Avoid

- `BZip2` on every serializer family
- `Jdk` plus any compressor unless compatibility is the overriding goal

`BZip2` delivered the worst latency by a very large margin and also huge allocation numbers.

## Payload Summary

| Scale    | Shape                                                    |
|----------|----------------------------------------------------------|
| `SMALL`  | `8` line items, `3` tags, `2` attributes per line item   |
| `MEDIUM` | `24` line items, `5` tags, `3` attributes per line item  |
| `LARGE`  | `96` line items, `10` tags, `5` attributes per line item |

## Small Payload

Top round-trip latency:

| Rank | Combination   |    Round Trip |
|------|---------------|--------------:|
| 1    | `Fory`        | `0.734 us/op` |
| 2    | `Fory+Snappy` | `1.588 us/op` |
| 3    | `Fory+LZ4`    | `1.851 us/op` |
| 4    | `Kryo`        | `2.961 us/op` |
| 5    | `Kryo+Snappy` | `3.894 us/op` |

Top serialized size:

| Rank | Combination    |    Size |
|------|----------------|--------:|
| 1    | `Kryo+Deflate` | `492 B` |
| 2    | `Kryo+GZip`    | `504 B` |
| 3    | `Kryo+Zstd`    | `512 B` |
| 4    | `Kryo+LZ4`     | `586 B` |
| 5    | `Kryo+BZip2`   | `599 B` |

Top allocation efficiency:

| Rank | Combination    | Round Trip Alloc |
|------|----------------|-----------------:|
| 1    | `Fory`         |      `4296 B/op` |
| 2    | `Fory+Zstd`    |      `7000 B/op` |
| 3    | `Fory+LZ4`     |      `7056 B/op` |
| 4    | `Fory+Snappy`  |      `7064 B/op` |
| 5    | `Fory+Deflate` |      `7373 B/op` |

## Medium Payload

Top round-trip latency:

| Rank | Combination   |    Round Trip |
|------|---------------|--------------:|
| 1    | `Fory`        | `1.919 us/op` |
| 2    | `Fory+Snappy` | `3.392 us/op` |
| 3    | `Fory+LZ4`    | `3.813 us/op` |
| 4    | `Kryo`        | `6.025 us/op` |
| 5    | `Kryo+Snappy` | `7.918 us/op` |

Top serialized size:

| Rank | Combination    |    Size |
|------|----------------|--------:|
| 1    | `Kryo+Deflate` | `690 B` |
| 2    | `Kryo+Zstd`    | `700 B` |
| 3    | `Kryo+GZip`    | `702 B` |
| 4    | `Fory+Zstd`    | `796 B` |
| 5    | `Kryo+BZip2`   | `798 B` |

Top allocation efficiency:

| Rank | Combination    | Round Trip Alloc |
|------|----------------|-----------------:|
| 1    | `Fory`         |     `12080 B/op` |
| 2    | `Fory+Deflate` |     `16776 B/op` |
| 3    | `Fory+Zstd`    |     `17760 B/op` |
| 4    | `Fory+LZ4`     |     `17824 B/op` |
| 5    | `Fory+Snappy`  |     `18160 B/op` |

## Large Payload

Top round-trip latency:

| Rank | Combination   |     Round Trip |
|------|---------------|---------------:|
| 1    | `Fory`        |  `9.350 us/op` |
| 2    | `Fory+LZ4`    | `15.040 us/op` |
| 3    | `Fory+Snappy` | `15.944 us/op` |
| 4    | `Kryo`        | `24.972 us/op` |
| 5    | `Fory+Zstd`   | `30.619 us/op` |

Top serialized size:

| Rank | Combination    |     Size |
|------|----------------|---------:|
| 1    | `Kryo+Zstd`    | `1396 B` |
| 2    | `Kryo+BZip2`   | `1453 B` |
| 3    | `Kryo+Deflate` | `1523 B` |
| 4    | `Kryo+GZip`    | `1535 B` |
| 5    | `Fory+Zstd`    | `1556 B` |

Top allocation efficiency:

| Rank | Combination    | Round Trip Alloc |
|------|----------------|-----------------:|
| 1    | `Fory`         |     `59240 B/op` |
| 2    | `Fory+Deflate` |     `73809 B/op` |
| 3    | `Kryo`         |     `78888 B/op` |
| 4    | `Fory+Zstd`    |     `83659 B/op` |
| 5    | `Fory+LZ4`     |     `84468 B/op` |

## Interpretation

- Compression is not free. For this object graph, `Deflate`, `GZip`, `Zstd`, and especially
  `BZip2` buy smaller payloads at very large latency cost.
-
`Fory` dominates the CPU and allocation side so strongly that adding heavy compression usually makes the overall result worse unless transport size is the main concern.
-
`Kryo` becomes interesting when compressed size is the first priority. Its compressed outputs are consistently the smallest.
- `LZ4` and `Snappy` are the only compressors that stayed in a reasonable performance band for a practical default.

## Final Answer

If you want one default for application use, choose `BinarySerializers.Fory`.

If you need compression too, choose `Fory + Snappy` first, and consider
`Fory + LZ4` when you want a slightly faster large-payload path.

If the main objective is minimizing bytes on the wire, use `Kryo + Deflate` for small/medium graphs and
`Kryo + Zstd` for large graphs.

## Raw Artifacts

- Perf JSON: [
  `combo-jmh-results.json`](/Users/debop/work/bluetape4k/bluetape4k-experimental/io/benchmarks/build/reports/benchmarks/combo-jmh-results.json)
- GC JSON: [
  `combo-jmh-gc-results.json`](/Users/debop/work/bluetape4k/bluetape4k-experimental/io/benchmarks/build/reports/benchmarks/combo-jmh-gc-results.json)
- Size JSON: [
  `combo-serialized-size.json`](/Users/debop/work/bluetape4k/bluetape4k-experimental/io/benchmarks/build/reports/benchmarks/combo-serialized-size.json)
