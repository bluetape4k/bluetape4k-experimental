# compileTestKotlin 경고 정리 리뷰

## 범위

PR을 만들기 전에 `bluetape4k-experimental`의
`chore/compiletestkotlin-warning-cleanup` diff를 검토했다. 변경 범위는
벤치마크 소스의 경고 정리와 이를 뒷받침하는 증거 문서로 제한된다.

## 발견 사항

- P0: 0
- P1: 0
- P2/P3: 0

## 리뷰 메모

- `BenchmarkUser`는 더 이상 deprecated `HasIdentifier`에 의존하지 않는다.
  로컬 벤치마크 저장소에는 기존과 동일한 `idExtractor = { it.id }`가 전달된다.
- `SerializerCompressorRegistry`와 `BinarySerializerCompressorBenchmark`는
  deprecated JDK serializer 축을 일관되게 제거한다. 따라서 registry,
  benchmark params, size snapshot, round-trip test가 동일한 지원 조합을
  열거한다.
- upstream API가 역직렬화 RCE 위험을 이유로 `BinarySerializers.Jdk`를
  deprecated 처리하므로, 이 변경에서는 의도적으로 경고를 억제하지 않는다.

## 검증

- `./gradlew compileTestKotlin --warning-mode all --rerun-tasks`: PASS, 18개
  task 실행.
- `./gradlew :benchmarks:test --warning-mode all --rerun-tasks`: PASS, 8개 task
  실행, 6개 통과.
- `git diff --check`: PASS.

## 잔여 위험

`--warning-mode all`은 여전히 Gradle 10 build-logic deprecation
(`ReportingExtension.file`, Kotlin DSL delegate syntax)을 보고한다.
`:benchmarks:test`도 라이브러리 경로에서 발생한 런타임
`sun.misc.Unsafe` terminal deprecation warning을 출력한다. 이 항목들은 이번
소스 경고 정리 범위 밖이다.
