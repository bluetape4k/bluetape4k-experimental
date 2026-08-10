# bluetape4k-experimental

[![CI](https://github.com/bluetape4k/bluetape4k-experimental/actions/workflows/ci.yml/badge.svg)](https://github.com/bluetape4k/bluetape4k-experimental/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![JVM](https://img.shields.io/badge/JVM-25-ED8B00?logo=openjdk)](https://openjdk.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[English](./README.md) | 한국어

![bluetape4k experimental 작업대 일러스트](./docs/assets/experimental-workbench.png)

안정 라이브러리로 옮기기 전 새로운 bluetape4k 아이디어를 검증하는 Kotlin/JVM 실험 모듈 모음입니다.

## 프로젝트 목적

`bluetape4k-experimental`은 Kotlin 2.4, Java 25, Spring Boot 4, Exposed, cache,
coroutine, data, I/O, benchmark 아이디어를 검증하는 공간입니다. 이 저장소의 모듈은 안정
artifact로 배포하지 않으며, 계약·빌드 동작·마이그레이션 경로를 확인한 뒤 승격합니다.

## 제공 기능

- **Prototype module** — Kotlin, coroutine, AI, data, I/O, infra 아이디어 빠른 검증
- **Spring Boot 4 실험** — 최신 Boot 라인의 auto-configuration/integration 확인
- **Exposed DB 실험** — CockroachDB, Ignite 계열 호환성 작업의 안정화 전 검증
- **Benchmark lane** — serializer/compressor와 infra 성능 근거 수집
- **Promotion staging** — stable repo로 옮기기 전 동작을 증명하는 안전한 공간

## 아키텍처

![experimental Architecture diagram](docs/assets/readme-diagrams/bluetape4k-experimental-architecture-01.png)

<!-- README_VISUAL_OVERVIEW:START -->
## Overview Diagram

![Bluetape4k Experimental overview diagram](docs/assets/readme-diagrams/root-readme-overview-01.png)

## Module Composition Chart

![Bluetape4k Experimental module composition chart](docs/assets/readme-charts/root-readme-module-chart-01.png)
<!-- README_VISUAL_OVERVIEW:END -->

## 모듈 그룹

| 디렉토리 | 목적 |
|---|---|
| `shared/` | 공통 유틸리티 |
| `kotlin/` | Kotlin 언어 기능 실험 |
| `coroutines/` | Coroutine/Flow 실험 |
| `ai/` | AI/LLM 통합 실험 |
| `data/` | Exposed CockroachDB/Ignite 등 data-layer 실험 |
| `io/` | I/O, 직렬화, benchmark 실험 |
| `infra/` | 인프라/cache 실험 |
| `examples/` | 실행 가능한 예제 애플리케이션 |

## 요구사항

- Kotlin 2.4+
- Java 25
- 필요한 경우 Spring Boot 4.x
- Gradle 9.x

## 빌드

기본적으로 root 전체 빌드를 실행하지 말고, 영향을 받는 모듈만 검증합니다.

```bash
./gradlew :<module>:build
./gradlew :<module>:test
./gradlew :<module>:check
```

## 개발자 로컬 Testcontainers 재사용

`io/benchmarks`의 Redis benchmark와 PostgreSQL 기반
`examples/exposed-jpa-benchmark` 애플리케이션은 기본적으로 컨테이너를 재사용하지
않습니다. 개발자가 로컬 애플리케이션 또는 benchmark 실행에서만 재사용하려면
`~/.testcontainers.properties`에서 reusable container를 활성화하고 다음 system
property 하나를 전달합니다.

```bash
JAVA_TOOL_OPTIONS='-Dbluetape4k.testcontainers.reuse=true' \
  ./gradlew :benchmarks:benchmarkCustom
```

`CI` 또는 `GITHUB_ACTIONS`가 존재하면 이 opt-in은 무시됩니다. 일반 module test는
계속 test JVM마다 재사용하지 않는 `Launcher` 컨테이너 하나를 사용합니다. 이 정책은
root test 동시성이나 workflow worker 제한을 변경하지 않습니다.

## 주요 실험

- `io/benchmarks`: serializer/compressor 성능과 크기 비교
- `data/exposed-cockroachdb`: CockroachDB JDBC dialect 실험
- `data/exposed-ignite3`: Ignite 계열 data integration 실험

## 모듈 등록

카테고리 디렉토리는 `settings.gradle.kts`에서 자동 감지합니다.

예: `data/exposed-cockroachdb/` -> `:exposed-cockroachdb`
