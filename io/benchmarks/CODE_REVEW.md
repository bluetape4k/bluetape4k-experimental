# Code Review

- Review date: 2026-03-08
- Module: `io/benchmarks`
- Test status: `./gradlew :benchmarks:test` passed
- Recommendation: REQUEST CHANGES

## Findings

- [MEDIUM] snapshot CLI가 파일명만 넘겨지면 `NullPointerException`으로 종료될 수 있습니다. [SerializerMetricsSnapshot.kt](src/main/kotlin/io/bluetape4k/benchmark/serializer/SerializerMetricsSnapshot.kt) 37-39행과 [SerializerCompressorMetricsSnapshot.kt](src/main/kotlin/io/bluetape4k/benchmark/serializer/SerializerCompressorMetricsSnapshot.kt) 37-39행은 `outputFile.parentFile.mkdirs()`를 무조건 호출합니다. `serialized-size.json`처럼 부모 디렉터리 없는 경로를 넘기면 `parentFile`이 null 입니다. `parentFile?.mkdirs()`로 바꾸고 CLI 회귀 테스트를 추가해야 합니다.
- [LOW] 공개 benchmark/support 타입의 한글 KDoc이 비어 있습니다. [BenchmarkFixtures.kt](src/main/kotlin/io/bluetape4k/benchmark/serializer/BenchmarkFixtures.kt) 3-80행, [BenchmarkSerializers.kt](src/main/kotlin/io/bluetape4k/benchmark/serializer/BenchmarkSerializers.kt) 6-25행, [ChronicleWireBinarySerializer.kt](src/main/kotlin/io/bluetape4k/benchmark/serializer/ChronicleWireBinarySerializer.kt) 8-38행, [MessagePackBinarySerializer.kt](src/main/kotlin/io/bluetape4k/benchmark/serializer/MessagePackBinarySerializer.kt) 8-21행은 외부에서 직접 사용할 수 있지만 계약 문서가 없습니다.

## Notes

- 현재 테스트는 serializer round-trip은 검증하지만 CLI entrypoint와 리포트 파일 생성 경로는 다루지 않습니다.
