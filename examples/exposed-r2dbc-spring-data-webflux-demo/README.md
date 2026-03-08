# exposed-r2dbc-spring-data-webflux-demo

`spring-data-exposed-r2dbc-spring-data`를 사용한 Spring WebFlux + Coroutine REST API 예제입니다.

## 기술 스택

- Spring Boot 4 + Spring WebFlux
- Exposed R2DBC (`suspendTransaction`)
- SuspendExposedCrudRepository (`IdTable + Domain DTO`)
- H2 (JDBC 초기화 + R2DBC 트랜잭션 경로)

## 최근 변경

- 컨트롤러에서 `suspendTransaction(r2dbcDatabase)`로 트랜잭션 경계 명시
- `R2dbcDatabase` 빈(`ExposedR2dbcConfig`) 추가
- `DataInitializer`를 R2DBC DSL 기반 초기화로 전환
- `spring.r2dbc.*` 설정 및 `r2dbc-h2` 런타임 의존성 보강

## 프로젝트 구조

```text
src/main/kotlin/.../
├── WebfluxDemoApplication.kt
├── config/
│   ├── DataInitializer.kt
│   └── ExposedR2dbcConfig.kt
├── domain/
│   └── ProductEntity.kt
├── repository/
│   └── ProductCoroutineRepository.kt
└── controller/
    └── ProductController.kt
```

## API

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/products` | 상품 목록 조회 |
| `GET` | `/products/{id}` | 상품 단건 조회 |
| `POST` | `/products` | 상품 생성 |
| `PUT` | `/products/{id}` | 상품 수정 |
| `DELETE` | `/products/{id}` | 상품 삭제 |

## 실행

```bash
./gradlew :exposed-r2dbc-spring-data-webflux-demo:bootRun
```

## 테스트

```bash
./gradlew :exposed-r2dbc-spring-data-webflux-demo:test
```
