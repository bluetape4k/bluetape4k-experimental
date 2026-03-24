# linkedin-graph-memgraph

LinkedIn 인맥 그래프 예제 - Memgraph 백엔드.

`linkedin-graph-neo4j` 예제와 동일한 5개 시나리오를 Memgraph(`MemgraphGraphOperations`)로 구현합니다.

## 시나리오

1. 사람 추가 및 인맥 연결
2. 최단 인맥 경로 탐색 (6단계 분리)
3. 2촌 인맥 탐색
4. 회사 추가 및 재직자 조회
5. 팔로우 관계 생성

## 테스트 실행

```bash
./gradlew :linkedin-graph-memgraph:test
```

> Testcontainers를 통해 `memgraph/memgraph:latest` 컨테이너가 자동으로 실행됩니다.
