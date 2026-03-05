# spring-data-exposed-r2dbc-spring-data

JetBrains Exposed DAO 모드를 Kotlin Coroutine 기반으로 사용할 수 있게 해주는 R2DBC 지원 모듈입니다.

## 개요

- **Exposed 1.x DAO** + **Kotlin Coroutines** (`suspend` 함수 + `Flow`)
- `spring-data-exposed-spring-data` 모듈 기반, Coroutine 레이어 추가
- `withContext(Dispatchers.IO) + transaction {}` 패턴으로 논블로킹 인터페이스 제공
- Spring Boot 4 + Spring WebFlux와 통합

## 주요 기능

| 기능 | 설명 |
|------|------|
| Coroutine CRUD | 모든 메서드 `suspend` 지원 |
| `Flow<E>` | `findAll()` → `Flow<E>` 반환 |
| Paging | `suspend fun findAll(pageable: Pageable): Page<E>` |
| DSL Op | `suspend fun findAll(op: () -> Op<Boolean>): List<E>` |

## 사용 방법

### 1. 엔티티 정의

```kotlin
object Users : LongIdTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255)
}

@ExposedEntity
class UserEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserEntity>(Users)
    var name by Users.name
    var email by Users.email
}
```

### 2. Coroutine Repository 정의

```kotlin
interface UserCoroutineRepository : CoroutineExposedRepository<UserEntity, Long>
```

### 3. 활성화

```kotlin
@SpringBootApplication
@EnableCoroutineExposedRepositories(basePackages = ["com.example.repository"])
class Application
```

### 4. Controller (WebFlux)

```kotlin
@RestController
@RequestMapping("/users")
class UserController(
    private val userRepository: UserCoroutineRepository,
) {
    @GetMapping
    fun findAll(): Flow<UserDto> =
        userRepository.findAll().map { transaction { it.toDto() } }

    @GetMapping("/{id}")
    suspend fun findById(@PathVariable id: Long): UserDto =
        userRepository.findByIdOrNull(id)?.let { transaction { it.toDto() } }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
}
```

## CoroutineExposedRepository API

```kotlin
interface CoroutineExposedRepository<E : Entity<ID>, ID : Any> : Repository<E, ID> {
    suspend fun <S : E> save(entity: S): S
    suspend fun findByIdOrNull(id: ID): E?
    fun findAll(): Flow<E>
    suspend fun findAllList(): List<E>
    suspend fun findAll(sort: Sort): List<E>
    suspend fun findAll(pageable: Pageable): Page<E>
    suspend fun count(): Long
    suspend fun existsById(id: ID): Boolean
    suspend fun deleteById(id: ID)
    suspend fun delete(entity: E)
    suspend fun deleteAll()
    suspend fun findAll(op: () -> Op<Boolean>): List<E>
    suspend fun count(op: () -> Op<Boolean>): Long
    suspend fun exists(op: () -> Op<Boolean>): Boolean
}
```

## 내부 구현

모든 Exposed DAO 연산은 `withContext(Dispatchers.IO)` + `transaction {}` 내에서 실행됩니다:

```kotlin
private suspend fun <T> io(block: () -> T): T =
    withContext(Dispatchers.IO) { transaction { block() } }
```

## 테스트

```bash
./gradlew :spring-data-exposed-r2dbc-spring-data:test
```
