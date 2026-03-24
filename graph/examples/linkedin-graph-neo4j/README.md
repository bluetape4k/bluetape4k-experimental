# linkedin-graph-neo4j

LinkedIn 인맥 그래프 예제 — Neo4j 백엔드.

`linkedin-graph-age`와 동일한 Service/Schema를 사용하며, 백엔드만 `Neo4jGraphOperations`로 교체한 버전입니다.

## 모듈 구조

```
linkedin-graph-neo4j/
├── src/main/kotlin/
│   └── io/bluetape4k/graph/examples/linkedin/
│       ├── schema/LinkedInSchema.kt      # 정점/간선 라벨 정의
│       └── service/LinkedInGraphService.kt  # 인맥 그래프 서비스
└── src/test/kotlin/
    └── io/bluetape4k/graph/examples/linkedin/
        ├── Neo4jServer.kt                # 테스트용 Neo4j 싱글턴 컨테이너
        └── Neo4jLinkedInGraphTest.kt     # 통합 테스트 (5개 시나리오)
```

## 테스트 시나리오

1. 사람 추가 및 인맥 연결 (1촌 조회)
2. 최단 인맥 경로 탐색 (shortestPath)
3. 2촌 인맥 탐색 (depth=2 neighbors)
4. 회사 재직자 조회 (WORKS_AT 역방향)
5. 팔로우 관계 생성 및 조회

## 실행

```bash
./gradlew :linkedin-graph-neo4j:test
```
