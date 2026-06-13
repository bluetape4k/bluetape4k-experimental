# AGENTS.md - bluetape4k-experimental

This repository inherits the workspace guidance from `../AGENTS.md`.
Read and follow the workspace root guide first. This file only adds
repo-specific layout, commands, domain rules, and local exceptions.

Experimental Kotlin library project for prototyping Spring Boot 4, Java 25,
and Kotlin 2.3+ ideas before they are stabilized in the main bluetape4k line.

## Repository Constraints

- Do not run the full `./gradlew build` unless the user explicitly asks. Prefer
  target-module checks such as `./gradlew :<module>:test` or
  `./gradlew :<module>:build`.
- Do not edit files outside the requested module without a concrete dependency
  or registration reason.
- Do not finish implementation-only work without tests or equivalent validation
  evidence.
- For Kotlin implementation or review, load the workspace-selected
  `bluetape4k-workflow` lane and relevant Kotlin/domain skills before editing.

## Build And Layout

- Dependency versions: `buildSrc/src/main/kotlin/Libs.kt`.
- Plugin versions: `buildSrc/src/main/kotlin/Plugins.kt`.
- Module registration: `settings.gradle.kts` uses `includeModules()` auto-detection.
- Common build configuration: root `build.gradle.kts`.
- Prefer `repo-status`, `repo-diff`, and
  `repo-test-summary -- ./gradlew :<module>:test` when available.

## Module Notes

- New modules should add `{baseDir}/{moduleName}/build.gradle.kts`, source/test
  package trees, and a module README. Trust `includeModules()` before changing
  `settings.gradle.kts`.
- Most categories use `withBaseDir=false`; folder names usually become Gradle
  module names, such as `infra/cache-lettuce-near` -> `:cache-lettuce-near`.
- Spring Boot 4 Hibernate customizers use
  `org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer`.
- Actuator class-loading guards such as `@ConditionalOnClass(Endpoint::class)`
  belong at class level.
- Exposed DAO work must run inside `transaction {}` or an explicit
  `withContext(Dispatchers.IO) { transaction { ... } }` boundary.

## Testing

- Use JUnit 5 and `runTest` for coroutine tests.
- Prefer bluetape4k Testcontainers singleton launchers over `@Testcontainers`.
- Production code should not use `runBlocking`; wrap blocking calls with an
  appropriate dispatcher boundary.
- Check cancellation propagation, dispatcher boundaries, and blocking behavior
  whenever coroutine code changes.
