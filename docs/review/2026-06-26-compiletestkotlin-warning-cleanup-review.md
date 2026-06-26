# compileTestKotlin warning cleanup review

## Scope

Reviewed the `chore/compiletestkotlin-warning-cleanup` diff for
`bluetape4k-experimental` before PR creation. The change is limited to benchmark
source warning cleanup and supporting evidence docs.

## Findings

- P0: 0
- P1: 0
- P2/P3: 0

## Review Notes

- `BenchmarkUser` no longer depends on deprecated `HasIdentifier`; the local
  benchmark repository still receives the same `idExtractor = { it.id }`.
- `SerializerCompressorRegistry` and `BinarySerializerCompressorBenchmark`
  remove the deprecated JDK serializer axis consistently, so the registry,
  benchmark params, size snapshot, and round-trip test enumerate the same
  supported combinations.
- The change intentionally avoids suppressing `BinarySerializers.Jdk` because
  the upstream API deprecates it for deserialization RCE risk.

## Validation

- `./gradlew compileTestKotlin --warning-mode all --rerun-tasks`: PASS, 18
  tasks executed.
- `./gradlew :benchmarks:test --warning-mode all --rerun-tasks`: PASS, 8 tasks
  executed, 6 passing.
- `git diff --check`: PASS.

## Residual Risk

`--warning-mode all` still reports Gradle 10 build-logic deprecations
(`ReportingExtension.file`, Kotlin DSL delegate syntax). `:benchmarks:test`
also prints a runtime `sun.misc.Unsafe` terminal deprecation warning from a
library path. These are outside this source warning cleanup.
