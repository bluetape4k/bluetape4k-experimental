# exposed-r2dbc-spring-data-webflux-demo

`spring-data-exposed-r2dbc-spring-data` 모듈을 사용한 Spring WebFlux + Coroutine REST API 예제입니다.

## 기술 스택

- **Spring Boot 4** + **Spring WebFlux**
- **JetBrains Exposed 1.x** DAO 모드
- **`CoroutineExposedRepository`** — Coroutine 기반 CRUD
- **Kotlin Coroutines** (`suspend` 함수, `Flow`)
- **H2** 인메모리 데이터베이스

## 프로젝트 구조

```
src/main/kotlin/.../
├── WebfluxDemoApplication.kt   # @SpringBootApplication + @EnableCoroutineExposedRepositories
├── domain/
│   └── ProductEntity.kt        # Products 테이블 + ProductEntity + ProductDto
├── repository/
│   └── ProductCoroutineRepository.kt  # CoroutineExposedRepository<ProductEntity, Long>
├── controller/
│   └── ProductController.kt    # suspend fun + Flow 기반 REST 엔드포인트
└── config/
    └── DataInitializer.kt      # 초기 샘플 데이터 삽입
```

## API 엔드포인트

| Method | Path | 반환 타입 | 설명 |
|--------|------|----------|------|
| `GET` | `/products` | `Flow<ProductDto>` | 전체 목록 조회 |
| `GET` | `/products/{id}` | `ProductDto` | ID로 단건 조회 |
| `POST` | `/products` | `ProductDto` | 새 상품 생성 |
| `PUT` | `/products/{id}` | `ProductDto` | 상품 수정 |
| `DELETE` | `/products/{id}` | `204 No Content` | 상품 삭제 |

## Controller 예시

```kotlin
@RestController
@RequestMapping("/products")
class ProductController(
    private val productRepository: ProductCoroutineRepository,
) {
    @GetMapping
    fun findAll(): Flow<ProductDto> =
        productRepository.findAll().map { transaction { it.toDto() } }

    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: Long): ProductDto =
        productRepository.findByIdOrNull(id)?.let { transaction { it.toDto() } }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: $id")

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun create(@RequestBody dto: ProductDto): ProductDto {
        val entity = transaction {
            ProductEntity.new { name = dto.name; price = dto.price; stock = dto.stock }
        }
        return transaction { entity.toDto() }
    }
}
```

## 실행

```bash
./gradlew :exposed-r2dbc-spring-data-webflux-demo:bootRun
```

## 테스트

```bash
./gradlew :exposed-r2dbc-spring-data-webflux-demo:test
```

테스트는 `@SpringBootTest(RANDOM_PORT)` + `WebTestClient`로 실제 HTTP 요청을 검증합니다.
`@TestMethodOrder` + `@Order(1)`로 초기 데이터 검증 테스트(`hasSize(3)`)가 가장 먼저 실행됩니다.
