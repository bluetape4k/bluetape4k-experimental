# exposed-pgvector

PostgreSQL [pgvector](https://github.com/pgvector/pgvector) 확장을 Exposed ORM에서 사용할 수 있도록 지원하는 모듈.

## 기능

- `VECTOR(dimension)` 컬럼 타입 (`FloatArray` 매핑)
- 코사인 거리 (`<=>`) 연산자
- L2 유클리드 거리 (`<->`) 연산자
- 내적 (`<#>`) 연산자

## 사용법

### 테이블 정의

```kotlin
object Items : LongIdTable("items") {
    val name = varchar("name", 255)
    val embedding = vector("embedding", 1536)  // OpenAI ada-002 차원
}
```

### 데이터베이스 초기화

```kotlin
transaction {
    exec("CREATE EXTENSION IF NOT EXISTS vector")
    connection.connection.registerVectorType()
    SchemaUtils.create(Items)
}
```

### 벡터 저장

```kotlin
transaction {
    connection.connection.registerVectorType()
    Items.insert {
        it[name] = "document-1"
        it[embedding] = floatArrayOf(0.1f, 0.2f, ...) // 1536차원
    }
}
```

### 유사도 검색

```kotlin
transaction {
    connection.connection.registerVectorType()
    Items.selectAll()
        .orderBy(Items.embedding.cosineDistance(Items.embedding) to SortOrder.ASC)
        .limit(10)
        .map { it[Items.name] }
}
```

## 의존성

- `com.pgvector:pgvector` -- JDBC 어댑터
- `org.jetbrains.exposed:exposed-core` -- Exposed ORM
- PostgreSQL 16+ with pgvector extension

## 주의사항

- `PGvector.addVectorType(conn)` 은 각 Connection에서 한 번씩 호출 필요
- `transaction {}` 블록 내에서 `connection.connection.registerVectorType()` 호출
- PostgreSQL dialect 전용 (다른 DB에서는 거리 연산자 사용 불가)
