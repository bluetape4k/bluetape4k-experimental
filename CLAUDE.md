# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`bluetape4k-experimental` is an experimental Kotlin library project under the bluetape4k organization. This repository is used for prototyping and exploring new ideas before they are stabilized into the main bluetape4k library.

## Build System

This project uses **Gradle with Kotlin DSL** (`build.gradle.kts`, `settings.gradle.kts`).

### Common Commands

```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :<module-name>:test

# Run a single test class
./gradlew :<module-name>:test --tests "fully.qualified.ClassName"

# Run a single test method
./gradlew :<module-name>:test --tests "fully.qualified.ClassName.methodName"

# Clean build
./gradlew clean build

# Check (build + test + lint)
./gradlew check
```

## Architecture

**Kotlin 2.3 + Java 25 + Spring Boot 4** 기반의 멀티모듈 Gradle 프로젝트입니다. publishing 없이 실험 전용으로 구성되어 있습니다.

### 실제 모듈 구조

```
bluetape4k-experimental/
├── settings.gradle.kts       # includeModules() 함수로 카테고리별 자동 모듈 등록
├── build.gradle.kts          # 루트 빌드 설정 (publishing 없음)
├── gradle.properties         # Gradle 데몬, JVM 설정
├── buildSrc/
│   ├── build.gradle.kts
│   └── src/main/kotlin/Libs.kt  # 모든 의존성 버전 및 좌표 정의
├── shared/                   # 공통 유틸리티 모듈
├── templates/                # logback, junit-platform 설정 템플릿
├── kotlin/                   # Kotlin 언어 기능 실험
├── spring-boot/              # Spring Boot 4 실험
├── spring-data/              # Spring Data 실험
├── coroutines/               # Coroutines 실험
├── ai/                       # AI/LLM 통합 실험
├── data/                     # 데이터 계층 실험
├── io/                       # I/O, 직렬화 실험
└── infra/                    # 인프라(Redis, Kafka 등) 실험
```

### 모듈 추가 방법

카테고리 디렉토리 아래에 폴더를 만들면 자동으로 모듈로 인식됩니다:
- `kotlin/context-parameters/` → `:context-parameters` 모듈
- 새 모듈의 `build.gradle.kts`에서 `Libs.*` 로 의존성 참조

### Key Conventions

- **Kotlin** 2.3 (context parameters 등 실험적 기능 활성화)
- **JUnit 5** 기본 테스트 프레임워크 (Kotest도 사용 가능)
- 의존성은 `buildSrc/src/main/kotlin/Libs.kt`에서 관리 (version catalog 아님)
- `./gradlew :<module>:test` 로 특정 모듈만 테스트

## Testing

JUnit 5 기본. Coroutine 테스트는 `runTest` 사용.

```bash
# Run tests with detailed output
./gradlew test --info

# Run tests continuously (watch mode)
./gradlew test --continuous
```
