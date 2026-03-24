# graph-tinkerpop

Apache TinkerPop Gremlin 기반 `GraphOperations` / `GraphSuspendOperations` 구현 모듈.

## 개요

TinkerGraph(in-memory JVM 그래프 DB)를 사용하여 `graph-core` 인터페이스를 구현한다.
외부 서버 없이 단독 실행 가능하므로 테스트, 프로토타이핑에 적합하다.

## 주요 클래스

| 클래스 | 설명 |
|--------|------|
| `TinkerGraphOperations` | 동기(blocking) 방식 구현 |
| `TinkerGraphSuspendOperations` | 코루틴(suspend + Flow) 방식 구현 |
| `GremlinRecordMapper` | TinkerPop Vertex/Edge/Path -> GraphVertex/GraphEdge/GraphPath 변환 |

## 의존성

```kotlin
dependencies {
    api(project(":graph-core"))
    api(Libs.tinkerpop_gremlin_core)
    api(Libs.tinkergraph_gremlin)
}
```

## 사용 예시

```kotlin
val ops = TinkerGraphOperations()

// Vertex 생성
val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
val bob = ops.createVertex("Person", mapOf("name" to "Bob"))

// Edge 생성
ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024L))

// 이웃 탐색
val neighbors = ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS"))

ops.close()
```
