# AGENTS.md - bluetape4k-experimental

Experimental bluetape4k modules for Kotlin 2.3, Java 25, and Spring Boot 4.
These modules are not published.

Use `bluetape4k-patterns` for all Kotlin implementation and review work. Use
`design` for new features, new modules, and significant refactors.

## Build

Do not run a full root build by default. Validate only the affected module.

```bash
./gradlew :<module>:build
./gradlew :<module>:test
./gradlew :<module>:test --tests "fully.qualified.ClassName.methodName"
./gradlew :<module>:check
```

## Key Files

| Purpose | Path |
|---|---|
| Dependency versions | `buildSrc/src/main/kotlin/Libs.kt` |
| Plugin versions | `buildSrc/src/main/kotlin/Plugins.kt` |
| Build tuning | `gradle.properties` |
| Module registration | `settings.gradle.kts` auto-detection |

## Layout

```text
shared/
kotlin/
spring-boot/hibernate-redis-near/
spring-data/exposed-spring-data/
spring-data/exposed-r2dbc-spring-data/
infra/cache-lettuce-near/
infra/hibernate-cache-lettuce-near/
examples/
```

Module names drop the base directory prefix. Example:
`infra/cache-lettuce-near/` becomes `:cache-lettuce-near`.

Root README visual assets live under `docs/assets/` and should be shared by
`README.md` and `README.ko.md` through the same relative path.

## Module Gotchas

### infra

- Do not use `LettuceBinaryCodecs`; it can fail with optional protobuf missing.
  Use `LettuceBinaryCodec(BinarySerializers.LZ4Fory)` directly.
- Add explicit Fory/LZ4 dependencies when needed: `Libs.fory_kotlin`,
  `Libs.lz4_java`.
- Hibernate 7 cache config types live under `org.hibernate.cache.cfg.spi`.
- Hibernate 7 requires H2 2.x (`Libs.h2_v2`).

### spring-boot

- `HibernatePropertiesCustomizer` moved to
  `org.springframework.boot.hibernate.autoconfigure` in Spring Boot 4.
- `@ConditionalOnClass` must be class-level when guarded types appear in method
  signatures.
- For `@ConfigurationProperties` map keys containing `.`, use bracket syntax
  such as `redis-ttl.regions[io.example.Product]=300s`.

### spring-data

- Add `@ExposedEntity` to every `Entity<ID>` subclass scanned by Spring Data.
- MVC apps use `@EnableExposedRepositories`; WebFlux/coroutine apps use
  `@EnableCoroutineExposedRepositories`.
- All DAO work requires `transaction {}` or
  `withContext(Dispatchers.IO) { transaction {} }`.
- Test DB setup: `SchemaUtils.createMissingTablesAndColumns(Table)` and
  `Table.deleteAll()` in `@BeforeEach`.
- Do not extend `CoroutineCrudRepository`; use the repository contracts already
  established in this repo.
- For R2DBC `selectAll()`, collect the Flow explicitly; do not accidentally call
  stdlib `Iterable.toList()`.
