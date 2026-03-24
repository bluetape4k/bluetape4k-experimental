# graph-memgraph

Memgraph 그래프 데이터베이스를 위한 `GraphOperations` / `GraphSuspendOperations` 구현 모듈.

## 개요

[Memgraph](https://memgraph.com/)는 Neo4j Bolt 프로토콜과 openCypher를 완전 호환하는 인메모리 그래프 DB다.
`neo4j-java-driver`를 그대로 사용해 연결할 수 있다.

## 주요 클래스

| 클래스 | 설명 |
|--------|------|
| `MemgraphGraphOperations` | 동기(blocking) 방식 그래프 연산 |
| `MemgraphGraphSuspendOperations` | 코루틴(suspend/Flow) 방식 그래프 연산 |

## 사용법

```kotlin
val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())

// 동기 방식
val ops = MemgraphGraphOperations(driver)
val vertex = ops.createVertex("Person", mapOf("name" to "Alice"))

// 코루틴 방식
val suspendOps = MemgraphGraphSuspendOperations(driver)
val vertex = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
```

## Neo4j와의 차이점

| 항목 | Neo4j | Memgraph |
|------|-------|----------|
| 기본 database 파라미터 | `"neo4j"` | `"memgraph"` |
| `elementId()` 지원 | O (5.x) | O (2.x+) |
| `shortestPath` | O | O |
| 인증 | basic auth | 기본 없음 (AuthTokens.none()) |

## 테스트

Testcontainers를 통해 `memgraph/memgraph:latest` 이미지를 자동으로 실행한다.

```bash
./gradlew :graph-memgraph:test
```
