# code-graph-neo4j

소스 코드 의존성 그래프 예제 - Neo4j 백엔드.

`code-graph-age`와 동일한 시나리오를 Neo4j(`graph-neo4j`) 백엔드로 실행한다.

## 모듈 구성

```
code-graph-neo4j/
├── src/main/kotlin/
│   └── io/bluetape4k/graph/examples/code/
│       ├── schema/CodeGraphSchema.kt     # 정점/간선 라벨 정의
│       └── service/CodeGraphService.kt   # 그래프 서비스 (GraphOperations 의존)
└── src/test/kotlin/
    └── io/bluetape4k/graph/examples/code/
        ├── Neo4jServer.kt                # Testcontainers 싱글턴
        └── Neo4jCodeGraphTest.kt         # 6개 테스트 시나리오
```

## 테스트 시나리오

1. 모듈 간 의존성 그래프 생성 및 조회
2. 의존 경로 탐색 (shortestPath)
3. 영향 범위 분석 (역방향 neighbors)
4. 클래스 상속 계층 탐색
5. 함수 호출 체인 분석
6. 연결되지 않은 경우 null 반환

## 실행

```bash
./gradlew :code-graph-neo4j:test
```

Neo4j 컨테이너는 Testcontainers로 자동 시작된다 (Docker 필요).
