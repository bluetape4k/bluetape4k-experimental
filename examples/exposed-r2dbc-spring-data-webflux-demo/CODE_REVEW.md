# Code Review

- Review date: 2026-03-08
- Module: `examples/exposed-r2dbc-spring-data-webflux-demo`
- Test status: `./gradlew :exposed-r2dbc-spring-data-webflux-demo:test` passed
- Recommendation: COMMENT

## Findings

- [LOW] startup initializer가 재기동 안전하지 않습니다. [DataInitializer.kt](src/main/kotlin/io/bluetape4k/examples/exposed/webflux/config/DataInitializer.kt) 20-24행은 `SchemaUtils.create(Products)`를 매번 수행합니다. 현재 테스트는 매번 새 H2 인메모리 DB를 쓰기 때문에 통과하지만, 파일/H2 TCP 등 지속성 있는 DB로 데모를 재시작하면 기존 테이블 생성에서 실패할 수 있습니다. `createMissingTablesAndColumns` 또는 존재 여부 확인 후 생성으로 바꾸는 편이 안전합니다.
- [LOW] 테스트가 happy-path CRUD에 치우쳐 있습니다. [ProductControllerTest.kt](src/test/kotlin/io/bluetape4k/examples/exposed/webflux/ProductControllerTest.kt) 33-107행은 404, 재기동, invalid payload를 다루지 않습니다. 데모 문서화 목적이라도 not-found/update-miss 정도는 추가하는 편이 좋습니다.
- [LOW] 공개 controller/repository/domain 타입의 한글 KDoc이 부족합니다. [ProductController.kt](src/main/kotlin/io/bluetape4k/examples/exposed/webflux/controller/ProductController.kt) 20-64행, [ProductEntity.kt](src/main/kotlin/io/bluetape4k/examples/exposed/webflux/domain/ProductEntity.kt) 10-32행, [ProductCoroutineRepository.kt](src/main/kotlin/io/bluetape4k/examples/exposed/webflux/repository/ProductCoroutineRepository.kt) 8-24행에 사용 계약 설명이 없습니다.

## Notes

- 현재 코드에서 즉시 깨지는 기능 버그는 보지 못했습니다.
- 운영성보다는 데모 단순성을 우선한 흔적이 뚜렷합니다.
