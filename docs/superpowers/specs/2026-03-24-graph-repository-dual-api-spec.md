# Graph Repository Dual API (Sync + Suspend) 설계 스펙

> 작성일: 2026-03-24
> 모듈: `graph/graph-core`, `graph/graph-age`, `graph/graph-neo4j`

---

## 1. 현재 상태 분석

### 기존 인터페이스 구조

| 인터페이스                      | suspend | 반환 타입                                  |
|----------------------------|---------|----------------------------------------|
| `GraphSession`             | O       | 단일값                                    |
| `GraphVertexRepository`    | O       | `List<GraphVertex>`                    |
| `GraphEdgeRepository`      | O       | `List<GraphEdge>`                      |
| `GraphTraversalRepository` | O       | `List<GraphVertex>`, `List<GraphPath>` |
| `GraphOperations` (Facade) | O (상속)  | 상속                                     |

### 구현체

- **`AgeGraphOperations`**: `withContext(Dispatchers.IO) { transaction(database) { ... } }` 패턴. 본질적으로 blocking JDBC.
- **`Neo4jGraphOperations`**: `ReactiveSession` + `awaitSingle()` / `asFlow().toList()` 패턴. 본질적으로 reactive.

### 문제점

1. AGE 구현은 JDBC blocking 호출을 `withContext(Dispatchers.IO)`로 감싸서 suspend로 제공 -- blocking 사용자에게 불필요한 coroutine 오버헤드
2. 모든 컬렉션 반환이 `List` -- 대량 데이터 시 메모리 부담, reactive/streaming 불가
3. Sync 전용 사용 케이스 (Spring MVC, 배치 작업)에서 `runBlocking`을 강제

---

## 2. 설계 트레이드오프 분석

### 접근법 A: 기존 인터페이스를 Sync로 변경 + Suspend 변형 신규 추가

| 항목              | 평가                                                                                       |
|-----------------|------------------------------------------------------------------------------------------|
| **장점**          | 기존 인터페이스명(`GraphVertexRepository`) 유지 → import 변경 없음 (Sync가 기본)                          |
| **단점**          | 기존 구현체(AGE/Neo4j) 모두 `suspend` 제거 필요. Neo4j는 ReactiveSession 기반이라 blocking 래퍼 신규 작성 필요   |
| **구현체 영향**      | AGE: `suspend` + `withContext` 제거 (자연스러움). Neo4j: `runBlocking` 래퍼 또는 별도 동기 Driver 사용 필요 |
| **examples 영향** | `LinkedInGraphService`, `CodeGraphService` 모두 suspend 제거 또는 Suspend 변형으로 전환              |

### 접근법 B: 기존 인터페이스를 Suspend로 리네임 + Sync 인터페이스 신규 추가

| 항목              | 평가                                                                    |
|-----------------|-----------------------------------------------------------------------|
| **장점**          | 기존 구현체 로직 변경 최소. `List` → `Flow` 변환만 추가. Sync는 깨끗한 새 인터페이스            |
| **단점**          | 기존 인터페이스 리네임 → import 경로 변경. examples 코드도 리네임 따라감                     |
| **구현체 영향**      | AGE/Neo4j 구현체 클래스명 변경 + `List` → `Flow` 반환 리팩터링. Sync 구현은 AGE에서 자연스러움 |
| **examples 영향** | import 변경 + 인터페이스명 변경                                                 |

### 접근법 C: 완전 독립 두 세트 (공통 베이스 없음)

| 항목         | 평가                                       |
|------------|------------------------------------------|
| **장점**     | 각 세트가 완전 독립 → 한쪽 변경이 다른 쪽에 영향 없음         |
| **단점**     | 메서드 시그니처 중복. 새 메서드 추가 시 두 군데 수정. 코드 양 2배 |
| **구현체 영향** | 구현체도 완전 별도 → 코드 중복 극심                    |

---

## 3. 최종 설계 결정: 접근법 B

### 결정 사유

1. **AGE의 본질**: JDBC blocking → Sync가 자연스러움. `withContext(Dispatchers.IO)` 제거하면 더 깔끔
2. **Neo4j의 본질**: ReactiveSession → Suspend + Flow가 자연스러움. 기존 코드의 `asFlow().toList()`를 `asFlow()`로 바꾸면 됨
3. **네이밍 일관성**: Spring Data의 `CrudRepository` / `CoroutineCrudRepository` 패턴과 동일. Sync가 기본, Suspend가 접미사
4. **기존 코드 영향 최소화**: 기존 suspend 구현체는 리네임 + List→Flow만 변경. Sync 구현은 AGE에서 `withContext` 제거로 단순화

### 네이밍 규칙

| Sync (blocking)            | Suspend + Flow                    |
|----------------------------|-----------------------------------|
| `GraphSession`             | `GraphSuspendSession`             |
| `GraphVertexRepository`    | `GraphVertexSuspendRepository`    |
| `GraphEdgeRepository`      | `GraphEdgeSuspendRepository`      |
| `GraphTraversalRepository` | `GraphTraversalSuspendRepository` |
| `GraphOperations`          | `GraphSuspendOperations`          |

---

## 4. 인터페이스 설계

### 4.1 Sync 인터페이스 (신규)

```kotlin
// GraphSession.kt
package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스 세션 관리 (동기 방식).
 *
 * 소유권: 외부에서 주입된 Database/Driver를 [close]에서 닫지 않는다.
 * 연결 풀/드라이버 생명주기는 Spring 컨테이너 또는 호출자가 관리한다.
 */
interface GraphSession: AutoCloseable {
    fun createGraph(name: String)
    fun dropGraph(name: String)
    fun graphExists(name: String): Boolean
}
```

```kotlin
// GraphVertexRepository.kt
package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex

/**
 * 그래프 정점(Vertex) CRUD 저장소 (동기 방식).
 */
interface GraphVertexRepository {
    fun createVertex(label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex
    fun findVertexById(label: String, id: GraphElementId): GraphVertex?
    fun findVerticesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): List<GraphVertex>
    fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex?
    fun deleteVertex(label: String, id: GraphElementId): Boolean
    fun countVertices(label: String): Long
}
```

```kotlin
// GraphEdgeRepository.kt
package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId

/**
 * 그래프 간선(Edge) CRUD 저장소 (동기 방식).
 */
interface GraphEdgeRepository {
    fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): GraphEdge

    fun findEdgesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): List<GraphEdge>
    fun deleteEdge(label: String, id: GraphElementId): Boolean
}
```

```kotlin
// GraphTraversalRepository.kt
package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex

/**
 * 그래프 순회(Traversal) 저장소 (동기 방식).
 */
interface GraphTraversalRepository {
    fun neighbors(
        startId: GraphElementId,
        edgeLabel: String,
        direction: Direction = Direction.OUTGOING,
        depth: Int = 1,
    ): List<GraphVertex>

    fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String? = null,
        maxDepth: Int = 10,
    ): GraphPath?

    fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String? = null,
        maxDepth: Int = 5,
    ): List<GraphPath>
}
```

```kotlin
// GraphOperations.kt
package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스의 통합 Facade (동기 방식).
 * AGE, Neo4j 등 각 백엔드가 이 인터페이스를 구현한다.
 */
interface GraphOperations:
    GraphSession,
    GraphVertexRepository,
    GraphEdgeRepository,
    GraphTraversalRepository
```

### 4.2 Suspend 인터페이스 (기존 리네임 + Flow 전환)

```kotlin
// GraphSuspendSession.kt
package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스 세션 관리 (코루틴 방식).
 *
 * 소유권: 외부에서 주입된 Database/Driver를 [close]에서 닫지 않는다.
 * 연결 풀/드라이버 생명주기는 Spring 컨테이너 또는 호출자가 관리한다.
 */
interface GraphSuspendSession: AutoCloseable {
    suspend fun createGraph(name: String)
    suspend fun dropGraph(name: String)
    suspend fun graphExists(name: String): Boolean
}
```

```kotlin
// GraphVertexSuspendRepository.kt
package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 정점(Vertex) CRUD 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 */
interface GraphVertexSuspendRepository {
    suspend fun createVertex(label: String, properties: Map<String, Any?> = emptyMap()): GraphVertex
    suspend fun findVertexById(label: String, id: GraphElementId): GraphVertex?
    fun findVerticesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): Flow<GraphVertex>
    suspend fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex?
    suspend fun deleteVertex(label: String, id: GraphElementId): Boolean
    suspend fun countVertices(label: String): Long
}
```

```kotlin
// GraphEdgeSuspendRepository.kt
package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 간선(Edge) CRUD 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 */
interface GraphEdgeSuspendRepository {
    suspend fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?> = emptyMap(),
    ): GraphEdge

    fun findEdgesByLabel(label: String, filter: Map<String, Any?> = emptyMap()): Flow<GraphEdge>
    suspend fun deleteEdge(label: String, id: GraphElementId): Boolean
}
```

```kotlin
// GraphTraversalSuspendRepository.kt
package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import kotlinx.coroutines.flow.Flow

/**
 * 그래프 순회(Traversal) 저장소 (코루틴 방식).
 *
 * 컬렉션 반환은 [Flow]로 제공하여 대량 데이터 스트리밍을 지원한다.
 */
interface GraphTraversalSuspendRepository {
    fun neighbors(
        startId: GraphElementId,
        edgeLabel: String,
        direction: Direction = Direction.OUTGOING,
        depth: Int = 1,
    ): Flow<GraphVertex>

    suspend fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String? = null,
        maxDepth: Int = 10,
    ): GraphPath?

    fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeLabel: String? = null,
        maxDepth: Int = 5,
    ): Flow<GraphPath>
}
```

```kotlin
// GraphSuspendOperations.kt
package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스의 통합 Facade (코루틴 방식).
 * AGE, Neo4j 등 각 백엔드가 이 인터페이스를 구현한다.
 */
interface GraphSuspendOperations:
    GraphSuspendSession,
    GraphVertexSuspendRepository,
    GraphEdgeSuspendRepository,
    GraphTraversalSuspendRepository
```

### 4.3 Flow 반환 규칙

| 메서드 유형           | Sync 반환        | Suspend 반환     | suspend 키워드 (Suspend) |
|------------------|----------------|----------------|-----------------------|
| 단일 생성/수정         | `GraphVertex`  | `GraphVertex`  | `suspend fun`         |
| 단일 조회 (nullable) | `GraphVertex?` | `GraphVertex?` | `suspend fun`         |
| 컬렉션 조회           | `List<T>`      | `Flow<T>`      | `fun` (cold Flow)     |
| 삭제/존재 확인         | `Boolean`      | `Boolean`      | `suspend fun`         |
| 카운트              | `Long`         | `Long`         | `suspend fun`         |

> **핵심**: `Flow<T>`를 반환하는 메서드는 `suspend`가 아닌 일반 `fun`으로 선언한다.
> Flow는 cold stream이므로 선언 시점에 실행되지 않고, collect 시점에 실행된다.

---

## 5. 파일 변경 계획

### 5.1 수정할 파일

| 파일                                                              | 변경 내용                                                  |
|-----------------------------------------------------------------|--------------------------------------------------------|
| `graph-core/.../repository/GraphSession.kt`                     | `suspend` 제거 → Sync 인터페이스로 변환                          |
| `graph-core/.../repository/GraphVertexRepository.kt`            | `suspend` 제거 → Sync 인터페이스로 변환                          |
| `graph-core/.../repository/GraphEdgeRepository.kt`              | `suspend` 제거 → Sync 인터페이스로 변환                          |
| `graph-core/.../repository/GraphTraversalRepository.kt`         | `suspend` 제거 → Sync 인터페이스로 변환                          |
| `graph-core/.../repository/GraphOperations.kt`                  | KDoc 수정 ("동기 방식" 명시)                                   |
| `graph-age/.../AgeGraphOperations.kt`                           | `suspend` + `withContext` 제거 → `GraphOperations` 직접 구현 |
| `graph-neo4j/.../Neo4jGraphOperations.kt`                       | `GraphSuspendOperations` 구현으로 변경 + `List` → `Flow` 반환  |
| `graph-neo4j/.../Neo4jCoroutineSession.kt`                      | `Flow` 반환 지원 메서드 추가 (필요시)                              |
| `graph/examples/linkedin-graph-age/.../LinkedInGraphService.kt` | `suspend` 제거, `GraphOperations` (Sync) 사용              |
| `graph/examples/code-graph-age/.../CodeGraphService.kt`         | `suspend` 제거, `GraphOperations` (Sync) 사용              |
| `graph-age/...test.../AgeGraphOperationsTest.kt`                | `runTest` → 일반 테스트로 변경                                 |
| `graph-neo4j/...test.../Neo4jGraphOperationsTest.kt`            | `GraphSuspendOperations` 기반으로 업데이트                     |

### 5.2 신규 생성할 파일

| 파일                                                             | 역할                  |
|----------------------------------------------------------------|---------------------|
| `graph-core/.../repository/GraphSuspendSession.kt`             | 코루틴 세션 인터페이스        |
| `graph-core/.../repository/GraphVertexSuspendRepository.kt`    | 코루틴 정점 저장소 인터페이스    |
| `graph-core/.../repository/GraphEdgeSuspendRepository.kt`      | 코루틴 간선 저장소 인터페이스    |
| `graph-core/.../repository/GraphTraversalSuspendRepository.kt` | 코루틴 순회 저장소 인터페이스    |
| `graph-core/.../repository/GraphSuspendOperations.kt`          | 코루틴 통합 Facade 인터페이스 |

---

## 6. 구현체 변경 가이드라인

### 6.1 AgeGraphOperations (Sync 전환)

**변경 전:**

```kotlin
override suspend fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
    withContext(Dispatchers.IO) {
        transaction(database) { ... }!!
    }
```

**변경 후:**

```kotlin
override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex =
    transaction(database) { ... }!!
```

- 모든 메서드에서 `suspend` 키워드 제거
- `withContext(Dispatchers.IO)` 래퍼 제거
- `transaction(database) { ... }` 직접 호출 (본래 blocking이므로 자연스러움)
- 구현 인터페이스: `GraphOperations`

### 6.2 Neo4jGraphOperations (Suspend + Flow 전환)

**변경 전:**

```kotlin
override suspend fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
    ...
    return runQuery(cypher, filter) { Neo4jRecordMapper.recordToVertex(it) }
}
```

**변경 후:**

```kotlin
override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): Flow<GraphVertex> {
    ...
    return flowQuery(cypher, filter) { Neo4jRecordMapper.recordToVertex(it) }
}
```

- 컬렉션 반환 메서드: `suspend fun` → `fun`, `List<T>` → `Flow<T>`
- 단일값 반환 메서드: `suspend fun` 유지
- 내부 `runQuery` 외에 `flowQuery` 헬퍼 추가 (Flow 반환용)
- 구현 인터페이스: `GraphSuspendOperations`

### 6.3 AGE Suspend 구현 (향후 필요시)

AGE 백엔드의 Suspend 구현이 필요한 경우:

- `AgeSuspendGraphOperations` 신규 생성
- 기존 blocking 코드를 `withContext(Dispatchers.IO)` + `flow { }` 빌더로 래핑
- 현재 스코프에서는 생성하지 않음 (AGE는 JDBC 기반이므로 Sync가 자연스러움)

### 6.4 Neo4j Sync 구현 (향후 필요시)

Neo4j 백엔드의 Sync 구현이 필요한 경우:

- `Neo4jSyncGraphOperations` 신규 생성
- `org.neo4j.driver.Session` (동기 세션) 사용
- 현재 스코프에서는 생성하지 않음 (Neo4j는 reactive가 자연스러움)

---

## 7. 구현 태스크 목록

### Phase 1: graph-core 인터페이스 (complexity: medium)

| #    | 태스크                                                  | complexity |
|------|------------------------------------------------------|------------|
| 1-1  | `GraphSession.kt` → Sync 변환 (`suspend` 제거)           | **low**    |
| 1-2  | `GraphVertexRepository.kt` → Sync 변환                 | **low**    |
| 1-3  | `GraphEdgeRepository.kt` → Sync 변환                   | **low**    |
| 1-4  | `GraphTraversalRepository.kt` → Sync 변환              | **low**    |
| 1-5  | `GraphOperations.kt` KDoc 업데이트                       | **low**    |
| 1-6  | `GraphSuspendSession.kt` 신규 생성                       | **low**    |
| 1-7  | `GraphVertexSuspendRepository.kt` 신규 생성 (Flow 반환)    | **low**    |
| 1-8  | `GraphEdgeSuspendRepository.kt` 신규 생성 (Flow 반환)      | **low**    |
| 1-9  | `GraphTraversalSuspendRepository.kt` 신규 생성 (Flow 반환) | **low**    |
| 1-10 | `GraphSuspendOperations.kt` 신규 생성                    | **low**    |

### Phase 2: graph-age 구현체 (complexity: medium)

| #   | 태스크                                                         | complexity |
|-----|-------------------------------------------------------------|------------|
| 2-1 | `AgeGraphOperations` → Sync 전환 (`suspend`/`withContext` 제거) | **medium** |
| 2-2 | `AgeGraphOperationsTest` → `runTest` 제거, 일반 테스트로 전환         | **medium** |

### Phase 3: graph-neo4j 구현체 (complexity: high)

| #   | 태스크                                                       | complexity |
|-----|-----------------------------------------------------------|------------|
| 3-1 | `Neo4jGraphOperations` → `GraphSuspendOperations` 구현으로 변경 | **high**   |
| 3-2 | `flowQuery` 헬퍼 메서드 추가 (Flow 반환용)                          | **medium** |
| 3-3 | `Neo4jGraphOperationsTest` → Suspend + Flow 기반 검증으로 업데이트  | **high**   |

### Phase 4: examples 업데이트 (complexity: medium)

| #   | 태스크                                             | complexity |
|-----|-------------------------------------------------|------------|
| 4-1 | `LinkedInGraphService` → Sync 전환 (`suspend` 제거) | **medium** |
| 4-2 | `LinkedInGraphTest` → 일반 테스트로 전환                | **medium** |
| 4-3 | `CodeGraphService` → Sync 전환 (`suspend` 제거)     | **medium** |
| 4-4 | `CodeGraphTest` → 일반 테스트로 전환                    | **medium** |

### Phase 5: 검증 (complexity: low)

| #   | 태스크                                     | complexity |
|-----|-----------------------------------------|------------|
| 5-1 | `./gradlew :graph-core:build` 컴파일 확인    | **low**    |
| 5-2 | `./gradlew :graph-age:test` 전체 테스트 통과   | **low**    |
| 5-3 | `./gradlew :graph-neo4j:test` 전체 테스트 통과 | **low**    |

---

## 8. 마이그레이션 체크리스트

- [ ] Sync 인터페이스에 `suspend` 키워드가 없는지 확인
- [ ] Suspend 인터페이스의 컬렉션 반환이 모두 `Flow<T>`인지 확인
- [ ] Suspend 인터페이스의 `Flow` 반환 메서드가 `fun` (not `suspend fun`)인지 확인
- [ ] AGE 구현체에서 `withContext(Dispatchers.IO)` 제거 확인
- [ ] Neo4j 구현체에서 `asFlow().toList()` → `asFlow()` 변환 확인
- [ ] examples 서비스에서 `suspend` 제거 확인
- [ ] 모든 모듈 컴파일 통과
- [ ] 모든 테스트 통과
