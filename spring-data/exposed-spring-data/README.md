# spring-data-exposed-spring-data

JetBrains Exposed DAO 모드를 Spring Data Commons Repository 패턴으로 사용할 수 있게 해주는 JDBC 기반 모듈입니다.

## 개요

- **Exposed 1.x DAO** (`Entity<ID>` + `EntityClass<ID, E>`) 기반
- **Spring Data 4.x** Repository 인프라 완전 지원
- Spring Boot 4 + Exposed Spring Boot 4 Starter와 통합

## 주요 기능

| 기능 | 설명 |
|------|------|
| CRUD | `ListCrudRepository` 전체 구현 |
| Paging/Sorting | `ListPagingAndSortingRepository` 지원 |
| PartTree 쿼리 파생 | 메서드명으로 쿼리 자동 생성 (`findByName`, `findByAgeGreaterThan` 등) |
| `@Query` 어노테이션 | Raw SQL 직접 실행 |
| QueryByExample | `Example<E>` 기반 조회 |
| DSL Op 직접 사용 | `findAll { table.col eq value }` |

## 사용 방법

### 1. 엔티티 정의

```kotlin
object Users : LongIdTable("users") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
    val age = integer("age")
}

@ExposedEntity
class UserEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<UserEntity>(Users)
    var name by Users.name
    var email by Users.email
    var age by Users.age
}
```

### 2. Repository 정의

```kotlin
interface UserRepository : ExposedRepository<UserEntity, Long> {
    fun findByName(name: String): List<UserEntity>
    fun findByAgeGreaterThan(age: Int): List<UserEntity>
    fun findByEmailContaining(keyword: String): List<UserEntity>
    fun countByAge(age: Int): Long
    fun existsByEmail(email: String): Boolean
    fun deleteByName(name: String): Long

    @Query("SELECT * FROM users WHERE email = ?1")
    fun findByEmailNative(email: String): List<UserEntity>
}
```

### 3. 활성화

```kotlin
@SpringBootApplication
@EnableExposedRepositories(basePackages = ["com.example.repository"])
class Application
```

### 4. application.yml

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
  exposed:
    generate-ddl: true
```

## 지원하는 PartTree 키워드

| 키워드 | 예시 | Exposed Op |
|--------|------|-----------|
| `findBy` | `findByName` | `column eq value` |
| `GreaterThan` | `findByAgeGreaterThan` | `column greater value` |
| `LessThan` | `findByAgeLessThan` | `column less value` |
| `Between` | `findByAgeBetween` | `column.between(a, b)` |
| `Containing` | `findByEmailContaining` | `column like "%value%"` |
| `StartingWith` | `findByNameStartingWith` | `column like "value%"` |
| `EndingWith` | `findByNameEndingWith` | `column like "%value"` |
| `IsNull` | `findByEmailIsNull` | `column.isNull()` |
| `In` | `findByIdIn` | `column inList values` |
| `Not` | `findByNameNot` | `column neq value` |
| `And` / `Or` | `findByNameAndAge` | `op1 and op2` |
| `countBy` | `countByAge` | count 실행 |
| `existsBy` | `existsByEmail` | exists 확인 |
| `deleteBy` | `deleteByName` | delete 실행 |
| `Top` / `First` | `findTop3ByOrderByAgeDesc` | limit 적용 |

## 테스트

```bash
./gradlew :spring-data-exposed-spring-data:test
```
