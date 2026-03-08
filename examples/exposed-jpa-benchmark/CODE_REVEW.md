# Code Review

- Review date: 2026-03-08
- Module: `examples/exposed-jpa-benchmark`
- Test status: `./gradlew :exposed-jpa-benchmark:test` passed
- Recommendation: REQUEST CHANGES

## Findings

- [MEDIUM] PUT 업데이트가 요청 본문의 `books`를 무시해 생성(create)과 수정(update) 계약이 달라집니다. JPA 경로는 [JpaAuthorService.kt](src/main/kotlin/io/bluetape4k/examples/benchmark/service/JpaAuthorService.kt) 43-47행에서 이름/이메일만 갱신하고, Exposed 경로는 [AuthorExposedRepository.kt](src/main/kotlin/io/bluetape4k/examples/benchmark/repository/exposed/AuthorExposedRepository.kt) 42-46행에서 동일하게 책 컬렉션을 건드리지 않습니다. 클라이언트가 `CreateAuthorRequest.books`를 수정 요청에 포함해도 서버는 조용히 무시하므로 API 일관성이 깨집니다. books를 replace/merge 중 하나로 명시적으로 처리하고 회귀 테스트를 추가해야 합니다.
- [LOW] 테스트는 update 시 책 정보 보존/교체 여부를 검증하지 않습니다. [ExposedCrudTest.kt](src/test/kotlin/io/bluetape4k/examples/benchmark/ExposedCrudTest.kt) 100-120행과 [JpaCrudTest.kt](src/test/kotlin/io/bluetape4k/examples/benchmark/JpaCrudTest.kt) 100-120행은 author name만 확인합니다. books 포함 업데이트 시나리오를 추가하는 편이 좋습니다.
- [LOW] 공개 controller/entity 타입에 한글 KDoc이 거의 없습니다. [ExposedController.kt](src/main/kotlin/io/bluetape4k/examples/benchmark/controller/ExposedController.kt) 18-56행, [JpaController.kt](src/main/kotlin/io/bluetape4k/examples/benchmark/controller/JpaController.kt) 18-56행, [AuthorJpa.kt](src/main/kotlin/io/bluetape4k/examples/benchmark/domain/jpa/AuthorJpa.kt) 14-41행, [Entities.kt](src/main/kotlin/io/bluetape4k/examples/benchmark/domain/exposed/Entities.kt) 7-22행은 데모 코드라도 공개 API 설명이 부족합니다.

## Notes

- CRUD happy-path 자체는 테스트가 안정적으로 커버합니다.
- 다만 update 요청 스키마의 의미가 create와 일치하지 않는 점은 실제 벤치마크 비교에서도 혼선을 만듭니다.
