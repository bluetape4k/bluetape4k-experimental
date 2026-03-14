# Lettuce Near Suspend Cache Benchmark

Measured on 2026-03-14.

## Setup

- Input: `/Users/debop/work/bluetape4k/bluetape4k-experimental/io/benchmarks/benchmark-results/near-suspend-path-check.json`
- JMH: `1.37`
- JDK: `25.0.2`
- Mode: `avgt`
- Warmup: `3` x `1 s`
- Measurement: `5` x `1 s`
- Forks: `1`

## LettuceNearSuspendCacheBenchmark

| Benchmark | Params | Score |
|---|---|---:|
| `getLocalHit` | `ttlEnabled=false` | `8.412 us/op` |
| `getLocalHit` | `ttlEnabled=true` | `8.326 us/op` |
| `putIfAbsentNewKey` | `ttlEnabled=false` | `479.182 us/op` |
| `putIfAbsentNewKey` | `ttlEnabled=true` | `513.265 us/op` |
| `replaceWithExpectedValue` | `ttlEnabled=false` | `477.654 us/op` |
| `replaceWithExpectedValue` | `ttlEnabled=true` | `465.992 us/op` |

