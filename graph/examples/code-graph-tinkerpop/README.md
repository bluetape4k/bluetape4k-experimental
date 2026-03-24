# code-graph-tinkerpop

Apache TinkerPop TinkerGraph 백엔드를 사용하는 코드 의존성 그래프 예제 모듈.

`code-graph-neo4j` 예제와 동일한 시나리오를 TinkerGraph(in-memory)로 구현한다.
Testcontainers 불필요 — TinkerGraph는 JVM 내부 메모리 그래프이다.

## 주요 컴포넌트

- `CodeGraphSchema` — 코드 요소(Module, Class, Function) 정점/간선 라벨 정의
- `CodeGraphService` — 모듈/클래스/함수 CRUD 및 그래프 탐색 서비스
- `TinkerGraphCodeGraphTest` — 6개 시나리오 통합 테스트

## 테스트 시나리오

1. 모듈 추가 및 의존성 관계 구성
2. 의존성 경로 탐색
3. 영향 범위 분석 (역방향 탐색)
4. 클래스 상속 계층 탐색
5. 함수 호출 체인 분석
6. 의존성 없는 경우 경로 null

## 실행

```bash
./gradlew :code-graph-tinkerpop:test
```
