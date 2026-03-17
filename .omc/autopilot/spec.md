# Technical Specification: Exposed vs JPA Benchmark Application

## Analyst Review: exposed-jpa-benchmark

### 1. Overview

Spring Boot 4 기반 벤치마크 애플리케이션으로, JetBrains Exposed와 JPA(Hibernate 7) 두 ORM의 1:N 관계 CRUD 성능을 비교 측정한다. Gatling 3.x를 사용하여 HTTP 부하 테스트를 수행하고, HTML 리포트로 결과를 시각화한다.

---

### 2. Module Structure

**단일 모듈** 방식을 채택한다. 두 ORM이 동일 Spring Boot 앱 내에서 별도 엔드포인트로 동작하여 JVM/DB 조건을 동일하게 유지한다.

```
examples/exposed-jpa-benchmark/
  build.gradle.kts
  src/
    main/kotlin/io/bluetape4k/examples/benchmark/
      BenchmarkApplication.kt
      config/
        ExposedConfig.kt           # Exposed DataSource + transaction 설정
        JpaConfig.kt               # JPA EntityManager 설정 (Spring Boot auto-config)
      domain/
        exposed/                    # Exposed Table + Entity 정의
          Tables.kt                 # Authors, Books (LongIdTable)
          Entities.kt               # AuthorEntity, BookEntity (DAO)
        jpa/                        # JPA Entity 정의
          AuthorJpa.kt              # @Entity Author
          BookJpa.kt                # @Entity Book
      dto/
        AuthorDto.kt
        BookDto.kt
      repository/
        exposed/
          AuthorExposedRepository.kt
        jpa/
          AuthorJpaRepository.kt    # Spring Data JPA
      service/
        ExposedAuthorService.kt
        JpaAuthorService.kt
      controller/
        ExposedController.kt        # /api/exposed/authors/**
        JpaController.kt            # /api/jpa/authors/**
    test/kotlin/io/bluetape4k/examples/benchmark/
      ExposedCrudTest.kt             # JUnit 5 통합 테스트
      JpaCrudTest.kt                 # JUnit 5 통합 테스트
      BenchmarkApplicationTest.kt    # 컨텍스트 로딩 테스트
  src/gatling/kotlin/io/bluetape4k/examples/benchmark/
    ExposedSimulation.kt             # Exposed 엔드포인트 부하 시나리오
    JpaSimulation.kt                 # JPA 엔드포인트 부하 시나리오
    ComparisonSimulation.kt          # 두 ORM 동시 비교 시나리오
  README.md
```

**모듈명**: `exposed-jpa-benchmark` (settings.gradle.kts의 `includeModules("examples", false, false)` 규칙에 따라 `:exposed-jpa-benchmark`로 자동 등록)

---

### 3. Domain Model Design

**Author (1) : Book (N)** 관계를 사용한다.

#### 3.1 Exposed (DAO Mode)

DAO 모드를 선택한다. 이유: 1:N 관계 매핑이 DSL보다 직관적이며, JPA Entity와 1:1 비교가 용이하다.

```kotlin
// Tables.kt
object Authors : LongIdTable("authors") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
}

object Books : LongIdTable("books") {
    val title = varchar("title", 500)
    val isbn = varchar("isbn", 13).uniqueIndex()
    val price = decimal("price", 10, 2)
    val authorId = reference("author_id", Authors, onDelete = ReferenceOption.CASCADE)
}

// Entities.kt
@ExposedEntity
class AuthorEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AuthorEntity>(Authors)
    var name by Authors.name
    var email by Authors.email
    var createdAt by Authors.createdAt
    val books by BookEntity referrersOn Books.authorId
}

@ExposedEntity
class BookEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<BookEntity>(Books)
    var title by Books.title
    var isbn by Books.isbn
    var price by Books.price
    var author by AuthorEntity referencedOn Books.authorId
}
```

#### 3.2 JPA / Hibernate 7

```kotlin
// AuthorJpa.kt
@Entity
@Table(name = "authors_jpa")
class AuthorJpa(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var name: String = "",

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "author", cascade = [CascadeType.ALL],
               orphanRemoval = true, fetch = FetchType.LAZY)
    var books: MutableList<BookJpa> = mutableListOf(),
)

// BookJpa.kt
@Entity
@Table(name = "books_jpa")
class BookJpa(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false, unique = true, length = 13)
    var isbn: String = "",

    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: AuthorJpa? = null,
)
```

**테이블 분리 전략**: Exposed는 `authors`/`books`, JPA는 `authors_jpa`/`books_jpa` 테이블을 사용하여 동일 DB 내에서 충돌 없이 공존한다.

---

### 4. DTO Design

```kotlin
data class AuthorDto(
    val id: Long? = null,
    val name: String,
    val email: String,
    val books: List<BookDto> = emptyList(),
)

data class BookDto(
    val id: Long? = null,
    val title: String,
    val isbn: String,
    val price: BigDecimal,
)

// Request DTO (생성/수정용, 중첩 Book 포함)
data class CreateAuthorRequest(
    val name: String,
    val email: String,
    val books: List<BookDto> = emptyList(),
)
```

---

### 5. REST API Design

두 ORM이 동일한 API 구조를 공유하되, prefix로 구분한다.

| Operation | Exposed Endpoint | JPA Endpoint | Method |
|-----------|-----------------|-------------|--------|
| Create Author + Books | `POST /api/exposed/authors` | `POST /api/jpa/authors` | POST |
| Get Author + Books | `GET /api/exposed/authors/{id}` | `GET /api/jpa/authors/{id}` | GET |
| List Authors (paged) | `GET /api/exposed/authors?page=0&size=20` | `GET /api/jpa/authors?page=0&size=20` | GET |
| Update Author | `PUT /api/exposed/authors/{id}` | `PUT /api/jpa/authors/{id}` | PUT |
| Delete Author (cascade) | `DELETE /api/exposed/authors/{id}` | `DELETE /api/jpa/authors/{id}` | DELETE |
| Bulk Create | `POST /api/exposed/authors/bulk` | `POST /api/jpa/authors/bulk` | POST |

---

### 6. Build Configuration

```kotlin
// build.gradle.kts
plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
    id(Plugins.spring_boot)
    id(Plugins.gatling)
}

dependencies {
    // Exposed
    implementation(Libs.exposed_spring_boot4_starter)
    implementation(Libs.exposed_jdbc)
    implementation(Libs.exposed_dao)
    implementation(Libs.exposed_java_time)

    // JPA / Hibernate 7
    implementation(Libs.springBootStarter("data-jpa"))

    // Web
    implementation(Libs.springBootStarter("web"))

    // Actuator (optional, for metrics)
    implementation(Libs.springBootStarter("actuator"))
    implementation(Libs.micrometer_core)

    // Database
    runtimeOnly(Libs.h2_v2)
    // PostgreSQL (optional, Testcontainers)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.postgresql_driver)

    // Test
    testImplementation(Libs.springBootStarter("test"))

    // Gatling
    gatling(Libs.gatling_charts_highcharts)
    gatling(Libs.gatling_http_java)
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.bootJar {
    enabled = true
}

tasks.jar {
    enabled = false
}
```

**주의사항**:
- `Plugins.gatling` = `"io.gatling.gradle"`, 버전 `3.15.0` (root `build.gradle.kts`에서 `apply false`로 추가 필요)
- Gatling Gradle 플러그인은 `src/gatling/kotlin/` 소스셋을 자동 인식
- `kotlin("plugin.jpa")`로 JPA 엔티티 no-arg constructor 자동 생성
- `allOpen`으로 JPA 엔티티 클래스 open 처리 (Hibernate 프록시 필요)

---

### 7. Application Configuration

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:h2:mem:benchmark;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    hibernate:
      ddl-auto: create-drop
    properties:
      hibernate:
        format_sql: true
        generate_statistics: true
        jdbc.batch_size: 50
        order_inserts: true
        order_updates: true
    open-in-view: false

  exposed:
    generate-ddl: true

server:
  port: 8080

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.stat: DEBUG
    org.jetbrains.exposed: INFO
```

**PostgreSQL 프로필** (Testcontainers):
```yaml
# application-postgres.yml
spring:
  datasource:
    url: jdbc:tc:postgresql:16:///benchmark
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
```

---

### 8. Gatling Simulation Design

#### 8.1 시나리오 구성

각 CRUD 연산을 독립 시나리오로 분리하여 개별 측정 가능하게 한다.

```kotlin
// ComparisonSimulation.kt (Java DSL 기반)
class ComparisonSimulation : Simulation() {

    val httpProtocol = http.baseUrl("http://localhost:8080")

    // Feeders - UUID 기반 고유값으로 unique 제약 위반 방지
    val authorFeeder = generateSequence(1) { it + 1 }
        .map { mapOf(
            "name" to "Author$it",
            "email" to "author$it-${UUID.randomUUID()}@test.com",
            "isbn" to UUID.randomUUID().toString().take(13)
        )}
        .iterator()

    // Exposed Scenarios
    val exposedCreate = scenario("Exposed Create")
        .feed(authorFeeder)
        .exec(
            http("Create Author (Exposed)")
                .post("/api/exposed/authors")
                .body(StringBody("""..."""))
                .header("Content-Type", "application/json")
                .check(status().is(201))
                .check(jsonPath("$.id").saveAs("authorId"))
        )

    // JPA Scenarios (동일 구조, 경로만 /api/jpa/)

    init {
        setUp(
            exposedCreate.injectOpen(rampUsers(100).during(30)),
            jpaCreate.injectOpen(rampUsers(100).during(30)),
            exposedRead.injectOpen(rampUsers(200).during(30)),
            jpaRead.injectOpen(rampUsers(200).during(30)),
        ).protocols(httpProtocol)
    }
}
```

#### 8.2 부하 프로필

| Scenario | Users | Duration | Ramp |
|----------|-------|----------|------|
| Create (단건) | 100 | 30s | linear |
| Read (단건 + 관계) | 200 | 30s | linear |
| Update | 100 | 30s | linear |
| Delete (cascade) | 50 | 30s | linear |
| Bulk Create (10건) | 50 | 30s | linear |
| Mixed (CRUD 혼합) | 300 | 60s | linear |

#### 8.3 실행 방법

```bash
# 1. 앱 시작
./gradlew :exposed-jpa-benchmark:bootRun &

# 2. Gatling 실행
./gradlew :exposed-jpa-benchmark:gatlingRun

# 3. 리포트 확인
open examples/exposed-jpa-benchmark/build/reports/gatling/*/index.html
```

---

### 9. Result Comparison

#### 9.1 Gatling HTML Report (기본 제공)

Gatling 자체가 시나리오별 HTML 리포트를 생성하며 다음을 포함한다:
- 응답 시간 분포 (p50, p75, p95, p99)
- 초당 처리량 (req/s)
- 에러율
- 응답 시간 추이 그래프

#### 9.2 추가 비교 방안

Actuator + Micrometer 메트릭을 통해 내부 지표도 수집:
- `http.server.requests` (Spring Boot 기본)
- JPA: `hibernate.sessions.open`, `hibernate.query.executions`
- Exposed: 커스텀 타이머 (Micrometer 연동)

---

### 10. JUnit 5 Integration Tests

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExposedCrudTest(@Autowired val client: TestRestTemplate) {

    @Test
    fun `create author with books via Exposed`() {
        val request = CreateAuthorRequest(
            name = "Test Author",
            email = "test-${UUID.randomUUID()}@example.com",
            books = listOf(BookDto(
                title = "Book 1",
                isbn = "1234567890123",
                price = BigDecimal("19.99")
            ))
        )
        val response = client.postForEntity(
            "/api/exposed/authors", request, AuthorDto::class.java
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body!!.books).hasSize(1)
    }

    @Test
    fun `delete author cascades to books via Exposed`() { /* ... */ }
}

// JpaCrudTest: 동일 구조, /api/jpa/ 경로
```

---

### 11. Data Initialization

`ApplicationRunner`로 시드 데이터 삽입:
- Exposed: `transaction { SchemaUtils.createMissingTablesAndColumns(Authors, Books) }` + 시드
- JPA: `spring.jpa.hibernate.ddl-auto=create-drop` + `CommandLineRunner`로 시드
- 시드 규모: Author 100명, 각 Author당 Book 5권 = 총 600 레코드

---

## Analyst Findings

### Missing Questions
1. **Gatling Kotlin DSL 지원 여부** - Gatling 3.15.0에서 Kotlin DSL이 공식 지원되는지 확인 필요. 미지원 시 Java DSL을 `src/gatling/java/` 또는 Scala DSL로 작성해야 한다.
2. **Exposed + JPA 동일 DataSource 공유 가능 여부** - 단일 DataSource에서 두 ORM이 트랜잭션 충돌 없이 동작하는지 검증 필요. 별도 DataSource 구성이 필요할 수 있다.
3. **Gatling 플러그인 root build.gradle.kts 등록** - `id(Plugins.gatling) version Plugins.Versions.gatling apply false`가 root에 추가되어야 하는데, 기존 root에 없으므로 추가 필요.
4. **Exposed Spring Boot 4 Starter 호환성** - `exposed-spring-boot4-starter:1.1.1`이 Spring Boot 4.0.3과 정상 동작하는지 검증 필요.

### Undefined Guardrails
1. **벤치마크 워밍업** - JVM JIT 컴파일 영향을 제거하기 위한 웜업 요청 횟수 미정의. 제안: Gatling 시나리오 시작 전 각 엔드포인트 50회 웜업 요청.
2. **DB 커넥션 풀 크기** - Exposed와 JPA가 공정하게 경쟁하려면 동일 풀 크기 사용 필요. 제안: HikariCP `maximumPoolSize=20`.
3. **배치 크기 상한** - Bulk Create의 배치 크기 미정의. 제안: 10건으로 고정.
4. **응답 시간 SLA** - 벤치마크 pass/fail 기준 미정의. 제안: p99 < 500ms를 기준으로 설정.

### Scope Risks
1. **N+1 쿼리 최적화 범위** - Exposed eager loading vs JPA fetch join 등 최적화를 어디까지 적용할지. 제안: 기본 설정(lazy) + 최적화 버전(eager/fetch join) 두 시나리오로 한정.
2. **DB 엔진 확장** - H2 외 PostgreSQL 등으로 확장 시 scope 증가. 제안: H2를 기본으로, PostgreSQL은 별도 프로필로 제공하되 초기 scope에서 제외.
3. **Reactive/Coroutine 비교 추가** - 현재 scope는 JDBC 동기 방식만 포함. R2DBC/Coroutine 비교는 명시적으로 제외.

### Unvalidated Assumptions
1. **Gatling Gradle 플러그인이 Kotlin 소스셋을 지원한다** - `src/gatling/kotlin/` 경로 인식 여부 확인 필요. 검증: 플러그인 문서 확인 또는 `src/gatling/java/`로 대체.
2. **Exposed와 JPA가 동일 H2 인스턴스에서 독립적으로 DDL 생성 가능하다** - 테이블명 분리로 해결 가정. 검증: 앱 시작 시 두 ORM 모두 정상 테이블 생성 확인.
3. **`exposed-spring-boot4-starter`가 Spring Data JPA와 공존 가능하다** - auto-configuration 충돌 여부. 검증: 앱 컨텍스트 로딩 테스트.

### Missing Acceptance Criteria
1. **앱 시작 성공** - `./gradlew :exposed-jpa-benchmark:bootRun` 실행 후 30초 내 `Started BenchmarkApplication` 로그 출력.
2. **CRUD 기능 정상 동작** - 6개 엔드포인트(Create/Read/List/Update/Delete/Bulk) x 2 ORM = 12개 API 모두 2xx 응답.
3. **1:N 관계 정합성** - Author 생성 시 Book이 함께 저장되고, Author 조회 시 Book 목록이 포함되며, Author 삭제 시 Book이 cascade 삭제됨.
4. **Gatling 실행 완료** - `./gradlew :exposed-jpa-benchmark:gatlingRun` 실행 후 에러율 1% 미만.
5. **HTML 리포트 생성** - `build/reports/gatling/` 하위에 `index.html` 파일 존재.
6. **JUnit 테스트 통과** - `./gradlew :exposed-jpa-benchmark:test` 전체 통과.

### Edge Cases
1. **동시 생성 시 unique 제약 위반** - email/isbn unique 인덱스 충돌. Gatling feeder에서 UUID 기반 고유값 생성으로 회피.
2. **Exposed transaction 내 lazy loading** - `books` 컬렉션 접근이 transaction 밖에서 발생하면 예외. Controller에서 `transaction {}` 블록 내에서 DTO 변환 필수.
3. **JPA Open-in-View 비활성화** - `spring.jpa.open-in-view=false` 설정 시 Controller에서 lazy 컬렉션 접근 불가. Service 계층에서 `@Transactional` + fetch join 사용.
4. **H2 동시성 한계** - H2 인메모리 모드는 높은 동시성에서 lock contention 발생 가능. Gatling 동시 사용자 수를 300 이하로 제한.
5. **Cascade 삭제 중 외래키 순서** - Author 삭제 시 Book이 먼저 삭제되어야 함. JPA `orphanRemoval=true`와 Exposed `onDelete=CASCADE`로 처리.
6. **BigDecimal 직렬화 정밀도** - JSON 직렬화 시 소수점 이하 자릿수 불일치 가능. Jackson 설정으로 2자리 고정.

### Recommendations
1. **[Critical]** Gatling Gradle 플러그인(`io.gatling.gradle:3.15.0`)을 root `build.gradle.kts`에 `apply false`로 추가해야 한다.
2. **[Critical]** Exposed + Spring Data JPA 공존 테스트를 최우선으로 수행 (ApplicationContext 로딩 확인).
3. **[High]** Gatling 소스 언어를 확정한다 (Kotlin DSL 미지원 시 Java DSL로 전환).
4. **[High]** DataSource 공유 vs 분리 결정. 단일 DataSource가 권장되나, 트랜잭션 매니저 충돌 시 `@Primary` 지정 필요.
5. **[Medium]** N+1 문제 최적화 시나리오를 scope에 포함할지 결정.
6. **[Low]** PostgreSQL Testcontainers 프로필은 Phase 2로 연기 가능.

### Open Questions
- [ ] Gatling 3.15.0이 `src/gatling/kotlin/` Kotlin DSL을 공식 지원하는가? -- Simulation 코드 언어 선택에 영향
- [ ] `exposed-spring-boot4-starter:1.1.1`과 `spring-boot-starter-data-jpa` (Spring Boot 4.0.3)가 동일 앱에서 auto-configuration 충돌 없이 동작하는가? -- 아키텍처의 근본적 가능성에 영향
- [ ] 벤치마크 공정성을 위해 Exposed와 JPA가 별도 DataSource를 사용해야 하는가, 동일 DataSource를 공유해야 하는가? -- 커넥션 풀 경합이 결과에 영향
- [ ] N+1 최적화(eager loading / fetch join)를 별도 시나리오로 포함할 것인가? -- scope 크기와 구현 복잡도에 영향
- [ ] 벤치마크 pass/fail 판정 기준(SLA)을 정의할 것인가? -- CI 파이프라인 연동 여부에 영향
