# bluetape4k Kotlin Dev Agent

Multi-module Gradle project — Kotlin 2.3 + Java 25 + Spring Boot 4. Experimental only, no publishing.

## Role

- Apply `bluetape4k-patterns` skill on all Kotlin code. No exceptions.
- New features/modules: use `design` skill (brainstorm→spec→plan→execute).
- Communicate in Korean. Keep code identifiers and technical terms in English.

## Principles

- **Think Before Coding** — ask when uncertain, never guess
- **Simplicity First** — implement only what was asked
- **Surgical Changes** — every changed line must trace back to a request
- **Goal-Driven** — "write a failing test then fix it" not "fix the bug"

---

## Build

```bash
# ❌ Never run full build
# ./gradlew build

# ✅ Per-module only
./gradlew :<module>:build
./gradlew :<module>:test
./gradlew :<module>:test --tests "fully.qualified.ClassName.methodName"
./gradlew :<module>:check
```

## Key Files

| Purpose             | Path                                                                       |
|---------------------|----------------------------------------------------------------------------|
| Dependency versions | `buildSrc/src/main/kotlin/Libs.kt`                                         |
| Plugin versions     | `buildSrc/src/main/kotlin/Plugins.kt`                                      |
| Build tuning        | `gradle.properties` (cache, parallelism, atomicfu, JVM args)               |
| Module registration | `settings.gradle.kts` (auto-detected via `includeModules()` — do not edit) |

## Module Layout

```
bluetape4k-experimental/
├── shared/               # common utilities
├── kotlin/               # language feature experiments
├── spring-boot/
│   └── hibernate-redis-near/          # :hibernate-redis-near
├── spring-data/
│   ├── exposed-spring-data/           # :exposed-spring-data
│   └── exposed-r2dbc-spring-data/     # :exposed-r2dbc-spring-data
├── infra/
│   ├── cache-lettuce-near/            # :cache-lettuce-near
│   └── hibernate-cache-lettuce-near/  # :hibernate-cache-lettuce-near
└── examples/
```

Module naming: `infra/cache-lettuce-near/` → `:cache-lettuce-near` (strip base dir prefix)

---

## Module Gotchas

### infra

- **`LettuceBinaryCodecs` forbidden** — `NoClassDefFoundError` (optional protobuf dep). Use `LettuceBinaryCodec(BinarySerializers.LZ4Fory)` directly.
- **Fory/LZ4 explicit deps** — `Libs.fory_kotlin`, `Libs.lz4_java` are optional; declare explicitly.
- **Hibernate 7 package** — `DomainDataRegionConfig`, `DomainDataRegionBuildingContext` → `org.hibernate.cache.cfg.spi` (not `support`).
- **H2 version** — Hibernate 7 requires `Libs.h2_v2` (2.x).

### spring-boot

- **`HibernatePropertiesCustomizer`** — moved to `org.springframework.boot.hibernate.autoconfigure` in Spring Boot 4. Add `compileOnly(Libs.springBoot("hibernate"))`.
- **`@ConditionalOnClass`** — must be on **class level**. Method-level only → `NoClassDefFoundError`.
- **Map key binding** — keys containing `.` in `@ConfigurationProperties` require bracket notation: `redis-ttl.regions[io.example.Product]=300s`

### spring-data

- **`@ExposedEntity`** — required on all `Entity<ID>` subclasses for Spring Data scanning.
- **`@EnableExposedRepositories`** — declare on `@SpringBootApplication` for MVC apps.
- **`@EnableCoroutineExposedRepositories`** — use for WebFlux/Coroutine apps.
- **Transactions required** — all DAO ops must run inside `transaction {}` or `withContext(Dispatchers.IO) { transaction {} }`.
- **DB init in tests** — `@BeforeEach`: `SchemaUtils.createMissingTablesAndColumns(Table)` + `Table.deleteAll()`. Import: `org.jetbrains.exposed.v1.jdbc.deleteAll`.
- **`CoroutineCrudRepository` forbidden** — activates Spring Data reactive transaction proxy → `ClassCastException`. `ExposedSuspendRepository` must extend `Repository<R, ID>` only.
- **R2DBC suspend proxy** — override `ExposedSuspendRepositoryFactory.getRepository()` to bypass Spring Data interceptor chain (MonoUsingWhen wrapping). Use `RepositoryFactoryBeanSupport` (not `TransactionalRepositoryFactoryBeanSupport`; abstract method is `createRepositoryFactory()`).
- **`table.selectAll().toList()` forbidden (R2DBC)** — calls stdlib `Iterable.toList()`, may miss rows. Use `.collect { rows.add(it) }`.

## Testcontainers

No `@Testcontainers` annotation needed. Use bluetape4k singleton pattern:

```kotlin
companion object {
    @JvmStatic val redisServer = RedisServer.Standalone.start()
}
```

---

## Skills Reference

| Skill | When to use |
|-------|-------------|
| `bluetape4k-patterns` | Writing or reviewing any Kotlin code |
| `design` | New feature, new module, significant refactor |
| `coroutines-kotlin` | Coroutines, Flow, Channel, structured concurrency |
| `kotlin-spring` | Spring Boot + Kotlin integration |
