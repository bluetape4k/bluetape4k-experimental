# linkedin-graph-tinkerpop

LinkedIn 인맥 그래프의 Apache TinkerPop TinkerGraph 백엔드 예제.

`linkedin-graph-neo4j`와 동일한 시나리오를 TinkerGraph(in-memory)로 구현한다.
Testcontainers 없이 즉시 실행 가능하다.

## 테스트 실행

```bash
./gradlew :linkedin-graph-tinkerpop:test
```

## 구조

- `schema/LinkedInSchema.kt` — 정점/간선 라벨 정의
- `service/LinkedInGraphService.kt` — 소셜 네트워크 서비스 (GraphOperations 추상화 사용)
- `TinkerGraphLinkedInGraphTest.kt` — 5개 시나리오 테스트
