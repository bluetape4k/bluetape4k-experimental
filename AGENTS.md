# AGENTS.md

This file provides repository-specific guidance for coding agents working in `bluetape4k-experimental`.

## Project Overview

`bluetape4k-experimental` is an experimental Kotlin library project under the bluetape4k organization.
Use this repository for prototyping and validation work before ideas are stabilized in the main bluetape4k libraries.

## Build System

- Gradle with Kotlin DSL: `build.gradle.kts`, `settings.gradle.kts`
- Kotlin `2.3`
- Java toolchain `25`
- Spring Boot `4`
- Dependencies are managed in `buildSrc/src/main/kotlin/Libs.kt`

Common commands:

```bash
./gradlew build
./gradlew test
./gradlew check
./gradlew :<module>:test
./gradlew :<module>:test --tests "fully.qualified.ClassName"
./gradlew :<module>:test --tests "fully.qualified.ClassName.methodName"
./gradlew clean build
```

## Repository Structure

Key directories:

- `shared/` - common utility module
- `kotlin/` - Kotlin language feature experiments
- `spring-boot/` - Spring Boot 4 experiments
- `spring-data/` - Spring Data experiments
- `coroutines/` - coroutine experiments
- `ai/` - AI/LLM integration experiments
- `data/` - data layer experiments
- `io/` - I/O and serialization experiments
- `infra/` - infra experiments such as Redis/Kafka
- `examples/` - runnable sample applications

Important build files:

- `settings.gradle.kts` - module auto-registration via `includeModules(...)`
- `build.gradle.kts` - root build conventions
- `gradle.properties` - Gradle and JVM tuning
- `buildSrc/src/main/kotlin/Libs.kt` - dependency versions and coordinates

## Module Naming

`settings.gradle.kts` uses `includeModules(baseDir, false, false)` for the main categories.
That means folder names become Gradle project names directly.

Examples:

- `infra/cache-lettuce-near/` -> `:cache-lettuce-near`
- `spring-data/exposed-spring-data/` -> `:exposed-spring-data`
- `kotlin/context-parameters/` -> `:context-parameters`

When adding a module:

1. Put it under the correct category directory.
2. Name the directory exactly as the intended Gradle project name.
3. Use `Libs.*` from `buildSrc` for dependencies.

## Working Conventions

- Prefer targeted module commands over root-wide builds when iterating.
- Match existing Kotlin DSL and source layout patterns in the touched module.
- Keep experimental scope tight; do not introduce publishing-related setup unless explicitly asked.
- If you add dependencies, update `buildSrc/src/main/kotlin/Libs.kt` instead of introducing a version catalog.

## Project-Specific Gotchas

### Infra modules

- Do not use `LettuceBinaryCodecs`.
  Use `LettuceBinaryCodec(BinarySerializers.LZ4Fory)` directly because protobuf optional dependencies can cause `NoClassDefFoundError`.
- `bluetape4k-io` optional serializer dependencies must be added explicitly when needed:
  `Libs.fory_kotlin`, `Libs.lz4_java`
- For Hibernate 7 cache work, `DomainDataRegionConfig` and `DomainDataRegionBuildingContext` are in `org.hibernate.cache.cfg.spi`
- Use `Libs.h2_v2` for Hibernate 7 tests

### Spring Boot modules

- `HibernatePropertiesCustomizer` moved in Spring Boot 4 to `org.springframework.boot.hibernate.autoconfigure`
- Add `compileOnly(Libs.springBoot("hibernate"))` when needed for Hibernate auto-config classes
- Put `@ConditionalOnClass(Endpoint::class)` on the class, not only on bean methods, to avoid Actuator classloading failures
- For `@ConfigurationProperties` map binding, keys containing `.` must use bracket notation
  Example: `redis-ttl.regions[io.example.Product]=300s`

### Spring Data / Exposed modules

- `@ExposedEntity` is required for Exposed `Entity<ID>` subclasses that should be scanned
- Use `@EnableExposedRepositories` for MVC/JDBC style apps
- Use `@EnableCoroutineExposedRepositories` for WebFlux/coroutine apps
- All DAO operations must run inside `transaction {}` or the coroutine equivalent
- In tests, initialize and clean tables explicitly with `SchemaUtils.createMissingTablesAndColumns(...)` and `Table.deleteAll()`
- `deleteAll()` requires `import org.jetbrains.exposed.v1.jdbc.deleteAll`

### Example modules

- Example projects are registered through `includeModules("examples", false, false)`
- Common run commands:
  - `./gradlew :hibernate-cache-lettuce-near-demo:bootRun`
  - `./gradlew :exposed-spring-data-mvc-demo:bootRun`
  - `./gradlew :exposed-r2dbc-spring-data-webflux-demo:bootRun`
- Near-cache demos may also require Docker services first:
  `docker-compose up -d`

## Testing Guidance

- JUnit 5 is the default test framework
- Coroutine tests should prefer `runTest`
- Use `./gradlew test --info` when you need detailed logs
- Prefer validating only the affected module before escalating to a wider build

## Agent Guidance

- Read this file before making changes anywhere in the repository
- Prefer evidence from the touched module over generic framework assumptions
- When updating build logic or dependencies, inspect `Libs.kt` and existing module `build.gradle.kts` files first
- When adding new files, place them in the category/module structure that `settings.gradle.kts` actually includes
