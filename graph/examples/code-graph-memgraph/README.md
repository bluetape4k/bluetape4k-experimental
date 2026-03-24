# code-graph-memgraph

소스 코드 의존성 그래프 예제 — Memgraph 백엔드.

`code-graph-neo4j` 예제와 동일한 시나리오를 `MemgraphGraphOperations`로 구현합니다.

## 시나리오

- 모듈 추가 및 의존성 관계 구성
- 의존성 경로 탐색 (shortest path)
- 영향 범위 분석 (역방향 탐색)
- 클래스 상속 계층 탐색
- 함수 호출 체인 분석
- 의존성 없는 경우 경로 null 반환

## 실행

```bash
./gradlew :code-graph-memgraph:test
```
