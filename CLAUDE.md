# CLAUDE.md - bluetape4k-experimental

Experimental bluetape4k modules for Kotlin 2.3, Java 25, and Spring Boot 4.
These modules are not published as stable artifacts.

- Use `bluetape4k-patterns` for Kotlin implementation and review work.
- Use `design` for new features, new modules, and significant refactors.

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
| Dependency versions | `gradle/libs.versions.toml` |
| Build tuning | `gradle.properties` |
| Module registration | `settings.gradle.kts` auto-detection |

## Layout

```text
shared/
kotlin/
coroutines/
ai/
data/
io/
infra/
examples/
```

Module names drop the base directory prefix. Example:
`data/exposed-cockroachdb/` becomes `:exposed-cockroachdb`.

## Module Gotchas

- Do not use `LettuceBinaryCodecs`; it can fail with optional protobuf missing.
  Use `LettuceBinaryCodec(BinarySerializers.LZ4Fory)` directly.
- Add explicit Fory/LZ4 dependencies when needed.
- Hibernate 7 cache config types live under `org.hibernate.cache.cfg.spi`.
- Spring Boot 4 `HibernatePropertiesCustomizer` lives under
  `org.springframework.boot.hibernate.autoconfigure`.
- `@ConditionalOnClass` must be class-level when guarded types appear in method
  signatures.
- For `@ConfigurationProperties` map keys containing `.`, use bracket syntax
  such as `redis-ttl.regions[io.example.Product]=300s`.

## Documentation Rules

- Keep `README.md` and `README.ko.md` structurally aligned.
- Store shared README images under `docs/assets/` and reference them with the
  same relative path from both locales.
- Keep this file and other agent-facing guidance in English.
