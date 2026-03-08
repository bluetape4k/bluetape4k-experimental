# Code Review

- Review date: 2026-03-08
- Module: `examples/exposed-spring-data-mvc-demo`
- Test status: `./gradlew :exposed-spring-data-mvc-demo:test` passed
- Recommendation: REQUEST CHANGES

## Findings

- [MEDIUM] controller가 Exposed DAO entity를 트랜잭션 경계 밖으로 들고 나가 다시 사용합니다. [ProductController.kt](src/main/kotlin/io/bluetape4k/examples/exposed/mvc/controller/ProductController.kt) 31-33행은 조회한 entity를 다른 `transaction {}`에서 DTO로 변환하고, 51-58행과 63-65행은 한 트랜잭션에서 찾은 entity를 별도 트랜잭션에서 수정/삭제합니다. Exposed entity는 transaction-bound 객체라서 요청 처리 스레드/트랜잭션 구성이 바뀌면 예외나 예기치 않은 지연 로딩 문제가 날 수 있습니다. 조회/수정/변환을 한 트랜잭션 안에서 끝내거나 repository/service 계층에서 DTO로 끊어내는 편이 안전합니다.
- [LOW] 공개 controller/domain 타입에 한글 KDoc이 없습니다. [ProductController.kt](src/main/kotlin/io/bluetape4k/examples/exposed/mvc/controller/ProductController.kt) 19-71행과 [ProductEntity.kt](src/main/kotlin/io/bluetape4k/examples/exposed/mvc/domain/ProductEntity.kt) 9-31행은 데모 사용법 설명이 부족합니다.

## Notes

- REST happy-path 테스트는 충분합니다.
- 트랜잭션 경계 문제는 동일 스레드 단위 테스트에서는 잘 드러나지 않아 별도 회귀 테스트가 필요합니다.
