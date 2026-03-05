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
│   └── hibernate-redis-near/      # Hibernate Near Cache Spring Boot Auto-Configuration (:hibernate-redis-near)
├── spring-data/              # Spring Data 실험
│   ├── exposed-spring-data/       # JetBrains Exposed DAO → Spring Data JDBC Repository (:exposed-spring-data)
│   └── exposed-r2dbc-spring-data/ # JetBrains Exposed DAO → Spring Data Coroutine/Reactive Repository (:exposed-r2dbc-spring-data)
├── coroutines/               # Coroutines 실험
├── ai/                       # AI/LLM 통합 실험
├── data/                     # 데이터 계층 실험
├── io/                       # I/O, 직렬화 실험
├── infra/                    # 인프라(Redis, Kafka 등) 실험
│   ├── cache-lettuce-near/        # Lettuce Near Cache (Caffeine L1 + Redis L2 + CLIENT TRACKING) (:cache-lettuce-near)
│   └── hibernate-cache-lettuce-near/  # Hibernate 7 2nd Level Cache (Near Cache 기반) (:hibernate-cache-lettuce-near)
└── examples/                 # 예제 애플리케이션
    ├── hibernate-cache-lettuce-near-demo/    # Near Cache Spring Boot 데모 앱 (:hibernate-cache-lettuce-near-demo)
    ├── exposed-spring-data-mvc-demo/         # Exposed Spring Data + MVC 예제 (:exposed-spring-data-mvc-demo)
    └── exposed-r2dbc-spring-data-webflux-demo/ # Exposed Spring Data + WebFlux 예제 (:exposed-r2dbc-spring-data-webflux-demo)
```

### 모듈 명명 규칙

`settings.gradle.kts`의 `includeModules(baseDir, withProjectName=false, withBaseDir=false)` 패턴:
- 모든 카테고리에 `withBaseDir=false` 적용 → 폴더명이 그대로 모듈명이 됨
- `kotlin/context-parameters/` → `:context-parameters`
- `infra/cache-lettuce-near/` → `:cache-lettuce-near`
- `spring-data/exposed-spring-data/` → `:exposed-spring-data`
- 새 모듈의 `build.gradle.kts`에서 `Libs.*` 로 의존성 참조

### Key Conventions

- **Kotlin** 2.3 (context parameters 등 실험적 기능 활성화)
- **JUnit 5** 기본 테스트 프레임워크 (Kotest도 사용 가능)
- 의존성은 `buildSrc/src/main/kotlin/Libs.kt`에서 관리 (version catalog 아님)
- `./gradlew :<module>:test` 로 특정 모듈만 테스트

### infra 모듈 주의사항

- **`LettuceBinaryCodecs` 사용 금지**: protobuf optional 의존성으로 인해 `NoClassDefFoundError` 발생.
  대신 `LettuceBinaryCodec(BinarySerializers.LZ4Fory)` 직접 사용.
- **Fory/LZ4 의존성**: `bluetape4k-io`의 Fory, LZ4 의존성은 optional이므로
  `Libs.fory_kotlin`, `Libs.lz4_java`를 명시적으로 추가해야 함.
- **Hibernate 7 패키지**: `DomainDataRegionConfig`, `DomainDataRegionBuildingContext`는
  `org.hibernate.cache.cfg.spi` 패키지 (support 아님).
- **H2 버전**: Hibernate 7은 `Libs.h2_v2` (2.x) 사용 필요.

### spring-boot 모듈 주의사항

- **`HibernatePropertiesCustomizer` 패키지**: Spring Boot 4에서 `org.springframework.boot.hibernate.autoconfigure`로 이동.
  `compileOnly(Libs.springBoot("hibernate"))` 의존성 추가 필요.
- **Actuator 클래스로딩 안전성**: `@ConditionalOnClass(Endpoint::class)`는 반드시 **클래스 레벨**에 선언.
  메서드 레벨만으로는 actuate 미포함 환경에서 `NoClassDefFoundError` 발생 가능.
- **Map 키 바인딩**: `@ConfigurationProperties`에서 점(`.`)이 포함된 Map 키는 대괄호 표기법 사용.
  예: `redis-ttl.regions[io.example.Product]=300s`

### spring-data 모듈 주의사항

- **`@ExposedEntity`**: Exposed `Entity<ID>` 서브클래스에 필수. Spring Data 스캐닝 대상 지정.
- **`@EnableExposedRepositories`**: Spring MVC 앱의 `@SpringBootApplication` 클래스에 선언.
- **`@EnableCoroutineExposedRepositories`**: WebFlux/Coroutine 앱에서 사용.
- **트랜잭션 필수**: 모든 DAO 연산은 `transaction {}` / `withContext(Dispatchers.IO) { transaction {} }` 내에서 실행.
- **DB 초기화**: 테스트 `@BeforeEach`에서 `SchemaUtils.createMissingTablesAndColumns(Table)` + `Table.deleteAll()` 사용.
  - `deleteAll()`은 `import org.jetbrains.exposed.v1.jdbc.deleteAll` 필요.

### examples 모듈 주의사항

- `settings.gradle.kts`에서 `includeModules("examples", false, false)` 로 등록.
- Near Cache 예제 실행: `docker-compose up -d && ./gradlew :hibernate-cache-lettuce-near-demo:bootRun`
- MVC 예제 실행: `./gradlew :exposed-spring-data-mvc-demo:bootRun`
- WebFlux 예제 실행: `./gradlew :exposed-r2dbc-spring-data-webflux-demo:bootRun`

## Testing

JUnit 5 기본. Coroutine 테스트는 `runTest` 사용.

```bash
# Run tests with detailed output
./gradlew test --info

# Run tests continuously (watch mode)
./gradlew test --continuous
```
