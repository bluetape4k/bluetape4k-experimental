# CLAUDE.md — bluetape4k-experimental

실험적 모듈 모음 — Kotlin 2.3 + Java 25 + Spring Boot 4. 미출판.

- `bluetape4k-patterns` 스킬: 모든 Kotlin 코드에 예외 없이 적용
- 신규 기능/모듈: `design` 스킬 사용 (brainstorm→spec→plan→execute)

## Build

```bash
# 전체 빌드 금지
# ./gradlew build   ❌

# 모듈 단위만
./gradlew :<module>:build
./gradlew :<module>:test
./gradlew :<module>:test --tests "fully.qualified.ClassName.methodName"
./gradlew :<module>:check
```

## Key Files

| Purpose | Path |
|---------|------|
| Dependency versions | `buildSrc/src/main/kotlin/Libs.kt` |
| Plugin versions | `buildSrc/src/main/kotlin/Plugins.kt` |
| Build tuning | `gradle.properties` |
| Module registration | `settings.gradle.kts` (auto-detected — do not edit) |

## Module Layout

```
bluetape4k-experimental/
├── shared/               # common utilities
├── kotlin/               # language feature experiments
├── spring-boot/
│   └── hibernate-redis-near/
├── spring-data/
│   ├── exposed-spring-data/
│   └── exposed-r2dbc-spring-data/
├── infra/
│   ├── cache-lettuce-near/
│   └── hibernate-cache-lettuce-near/
└── examples/
```

모듈 네이밍: `infra/cache-lettuce-near/` → `:cache-lettuce-near` (base dir prefix 제거)

## Module Gotchas

### infra

- **`LettuceBinaryCodecs` 사용 금지** — `NoClassDefFoundError` (optional protobuf dep). `LettuceBinaryCodec(BinarySerializers.LZ4Fory)` 직접 사용
- **Fory/LZ4 명시적 의존성** — `Libs.fory_kotlin`, `Libs.lz4_java` optional → 반드시 명시적 선언
- **Hibernate 7 패키지** — `DomainDataRegionConfig`, `DomainDataRegionBuildingContext` → `org.hibernate.cache.cfg.spi` (`support` 아님)
- **H2 버전** — Hibernate 7 은 `Libs.h2_v2` (2.x) 필요

### spring-boot

- **`HibernatePropertiesCustomizer`** — Spring Boot 4 에서 `org.springframework.boot.hibernate.autoconfigure` 로 이동. `compileOnly(Libs.springBoot("hibernate"))` 추가 필요
- **`@ConditionalOnClass`** — 반드시 **클래스 레벨**에 선언. 메서드 레벨 only → `NoClassDefFoundError`
- **Map 키 바인딩** — `@ConfigurationProperties` 에서 `.` 포함 키는 bracket 표기: `redis-ttl.regions[io.example.Product]=300s`

### spring-data

- **`@ExposedEntity`** — Spring Data 스캔용. 모든 `Entity<ID>` 서브클래스에 필수
- **`@EnableExposedRepositories`** — MVC 앱의 `@SpringBootApplication` 에 선언
- **`@EnableCoroutineExposedRepositories`** — WebFlux/Coroutine 앱에 사용
- **트랜잭션 필수** — 모든 DAO 작업은 `transaction {}` 또는 `withContext(Dispatchers.IO) { transaction {} }` 안에서
- **DB 초기화 (테스트)** — `@BeforeEach`: `SchemaUtils.createMissingTablesAndColumns(Table)` + `Table.deleteAll()`. Import: `org.jetbrains.exposed.v1.jdbc.deleteAll`
- **`CoroutineCrudRepository` 사용 금지** — Spring Data reactive transaction proxy 활성화 → `ClassCastException`. `ExposedSuspendRepository` 는 `Repository<R, ID>` 만 extends
- **R2DBC suspend proxy** — `ExposedSuspendRepositoryFactory.getRepository()` 오버라이드로 Spring Data interceptor chain(MonoUsingWhen) 우회. `RepositoryFactoryBeanSupport` 사용 (`TransactionalRepositoryFactoryBeanSupport` 아님; abstract method: `createRepositoryFactory()`)
- **`table.selectAll().toList()` 사용 금지 (R2DBC)** — stdlib `Iterable.toList()` 호출 → 행 누락 가능. `.collect { rows.add(it) }` 사용

## Skills Reference

| Skill | When to use |
|-------|-------------|
| `bluetape4k-patterns` | Writing or reviewing any Kotlin code |
| `design` | New feature, new module, significant refactor |
| `coroutines-kotlin` | Coroutines, Flow, Channel, structured concurrency |
| `kotlin-spring` | Spring Boot + Kotlin integration |
