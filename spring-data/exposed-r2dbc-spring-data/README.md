# spring-data-exposed-r2dbc-spring-data

JetBrains Exposed R2DBC DSL을 Spring Data Repository 패턴으로 사용할 수 있게 해주는 코루틴 모듈입니다.

## 개요

- `CoroutineExposedRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any>` 기반
- DAO `Entity` 없이 **Table + ResultRow + Domain DTO** 중심으로 동작
- Spring Data 4.x Repository 인프라와 WebFlux/Coroutine 조합 지원
- 저장/조회 매핑을 Repository 인터페이스에서 명시적으로 정의 (`toDomain`, `toPersistValues`)

## 최근 변경

- 제네릭을 `Entity` 중심에서 `IdTable + Domain` 중심으로 개편
- `HasIdentifier<ID>` 계약 도입으로 ID 매핑 단순화
- `SimpleCoroutineExposedRepository`를 `org.jetbrains.exposed.v1.r2dbc.*` DSL로 통일
- Repository 내부 트랜잭션 경계를 제거하고, 호출 계층에서 `suspendTransaction`을 적용하도록 정리

## 핵심 API

```kotlin
interface CoroutineExposedRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any> : Repository<R, ID> {
    suspend fun <S : R> save(entity: S): S
    suspend fun findByIdOrNull(id: ID): R?
    fun findAll(): Flow<R>
    suspend fun findAll(pageable: Pageable): Page<R>
    suspend fun count(): Long
    suspend fun deleteById(id: ID)

    fun toDomain(row: ResultRow): R
    fun toPersistValues(domain: R): Map<Column<*>, Any?>
}
```

## 사용 예시

```kotlin
object Users : LongIdTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255)
}

data class UserDto(
    override val id: Long? = null,
    val name: String,
    val email: String,
) : HasIdentifier<Long>

interface UserCoroutineRepository : CoroutineExposedRepository<Users, UserDto, Long> {
    override fun toDomain(row: ResultRow): UserDto = UserDto(
        id = row[Users.id].value,
        name = row[Users.name],
        email = row[Users.email],
    )

    override fun toPersistValues(domain: UserDto): Map<Column<*>, Any?> = mapOf(
        Users.name to domain.name,
        Users.email to domain.email,
    )
}
```

## 트랜잭션 경계

Repository는 트랜잭션을 열지 않습니다. 서비스/컨트롤러 계층에서 `suspendTransaction`을 적용해야 합니다.

```kotlin
suspend fun create(dto: UserDto): UserDto =
    suspendTransaction(r2dbcDatabase) {
        userRepository.save(dto)
    }
```

## 테스트

```bash
./gradlew :exposed-r2dbc-spring-data:test
```
