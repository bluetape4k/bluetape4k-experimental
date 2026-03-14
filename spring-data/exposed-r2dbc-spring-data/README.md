# spring-data-exposed-r2dbc-spring-data

JetBrains Exposed R2DBC DSL을 Spring Data Repository 패턴으로 사용할 수 있게 해주는 코루틴 모듈입니다.

## 개요

- `SuspendExposedCrudRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any>` 기반
- DAO `Entity` 없이 **Table + ResultRow + Domain DTO** 중심으로 동작
- Spring Data 4.x Repository 인프라와 WebFlux/Coroutine 조합 지원
- 저장/조회 매핑을 Repository 인터페이스에서 명시적으로 정의 (`toDomain`, `toPersistValues`)

## 최근 변경

- 제네릭을 `Entity` 중심에서 `IdTable + Domain` 중심으로 개편
- `HasIdentifier<ID>` 계약 도입으로 ID 매핑 단순화
- `SimpleExposedR2dbcRepository`를 `org.jetbrains.exposed.v1.r2dbc.*` DSL로 통일
- 기본 CRUD/paging 메서드는 Repository 내부에서 `suspendTransaction`을 열어 일관된 호출 계약 제공
- 대량 스트리밍은 `streamAll(database)` 에서 호출자가 사용할 `R2dbcDatabase`를 명시

## 핵심 API

```kotlin
interface SuspendExposedCrudRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any> : Repository<R, ID> {
    suspend fun <S : R> save(entity: S): S
    suspend fun findByIdOrNull(id: ID): R?
    fun findAll(): Flow<R>
    suspend fun count(): Long
    suspend fun deleteById(id: ID)

    fun toDomain(row: ResultRow): R
    fun toPersistValues(domain: R): Map<Column<*>, Any?>
}

interface SuspendExposedPagingRepository<T : IdTable<ID>, R : HasIdentifier<ID>, ID : Any> :
    SuspendExposedCrudRepository<T, R, ID> {
    suspend fun findAll(pageable: Pageable): Page<R>
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

interface UserRepository : SuspendExposedCrudRepository<Users, UserDto, Long> {
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

기본 CRUD/paging 메서드는 Repository 내부에서 트랜잭션을 엽니다. 여러 연산을 하나의 트랜잭션으로 묶거나
호출 계층에서 명시적인 경계를 제어하고 싶다면 직접 `suspendTransaction`으로 감싸면 됩니다.

```kotlin
suspend fun create(dto: UserDto): UserDto =
    userRepository.save(dto)
```

## 테스트

```bash
./gradlew :exposed-r2dbc-spring-data:test
```
