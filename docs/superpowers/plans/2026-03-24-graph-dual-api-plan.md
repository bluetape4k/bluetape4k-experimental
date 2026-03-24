# Graph Repository Dual API (Sync + Suspend) 구현 계획

> Spec: `docs/superpowers/specs/2026-03-24-graph-repository-dual-api-spec.md`
> 작성일: 2026-03-24
> 접근법: **B** (기존 인터페이스를 Sync로 전환 + Suspend 인터페이스 신규 추가)

---

## 설계 요약

| 구분     | Sync (blocking)            | Suspend + Flow                    |
|--------|----------------------------|-----------------------------------|
| 세션     | `GraphSession`             | `GraphSuspendSession`             |
| 정점     | `GraphVertexRepository`    | `GraphVertexSuspendRepository`    |
| 간선     | `GraphEdgeRepository`      | `GraphEdgeSuspendRepository`      |
| 순회     | `GraphTraversalRepository` | `GraphTraversalSuspendRepository` |
| Facade | `GraphOperations`          | `GraphSuspendOperations`          |

### Flow 반환 규칙

| 메서드 유형           | Sync 반환        | Suspend 반환     | suspend 키워드 (Suspend) |
|------------------|----------------|----------------|-----------------------|
| 단일 생성/수정         | `GraphVertex`  | `GraphVertex`  | `suspend fun`         |
| 단일 조회 (nullable) | `GraphVertex?` | `GraphVertex?` | `suspend fun`         |
| 컬렉션 조회           | `List<T>`      | `Flow<T>`      | `fun` (cold Flow)     |
| 삭제/존재 확인         | `Boolean`      | `Boolean`      | `suspend fun`         |
| 카운트              | `Long`         | `Long`         | `suspend fun`         |

---

## Phase 1: graph-core 인터페이스 변환

### Task 1-1: `GraphSession.kt` Sync 변환

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSession.kt`
- 변경: 모든 메서드에서 `suspend` 키워드 제거
- KDoc에 "동기 방식" 명시 추가
- 변경 전: `suspend fun createGraph(name: String)` / `suspend fun dropGraph(name: String)` /
  `suspend fun graphExists(name: String): Boolean`
- 변경 후: `fun createGraph(name: String)` / `fun dropGraph(name: String)` / `fun graphExists(name: String): Boolean`

### Task 1-2: `GraphVertexRepository.kt` Sync 변환

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVertexRepository.kt`
- 변경: 6개 메서드 모두 `suspend` 제거
- `findVerticesByLabel` 반환 타입은 `List<GraphVertex>` 유지 (Sync이므로)

### Task 1-3: `GraphEdgeRepository.kt` Sync 변환

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphEdgeRepository.kt`
- 변경: 3개 메서드 모두 `suspend` 제거

### Task 1-4: `GraphTraversalRepository.kt` Sync 변환

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTraversalRepository.kt`
- 변경: 3개 메서드 모두 `suspend` 제거

### Task 1-5: `GraphOperations.kt` KDoc 업데이트

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt`
- 변경: KDoc에 "동기 방식" 명시

### Task 1-6: `GraphSuspendSession.kt` 신규 생성

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendSession.kt` (신규)
- 내용: 기존 `GraphSession`의 suspend 버전 복원
- `suspend fun createGraph(name: String)` / `suspend fun dropGraph(name: String)` /
  `suspend fun graphExists(name: String): Boolean`
- `AutoCloseable` 상속

### Task 1-7: `GraphVertexSuspendRepository.kt` 신규 생성

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVertexSuspendRepository.kt` (신규)
- 핵심: `findVerticesByLabel` 반환을 `Flow<GraphVertex>`로 변경, `fun` (not `suspend fun`)
- 나머지 단일값 메서드는 `suspend fun` 유지
- import: `kotlinx.coroutines.flow.Flow`

### Task 1-8: `GraphEdgeSuspendRepository.kt` 신규 생성

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphEdgeSuspendRepository.kt` (신규)
- 핵심: `findEdgesByLabel` 반환을 `Flow<GraphEdge>`로 변경, `fun` (not `suspend fun`)
- 나머지 단일값 메서드는 `suspend fun` 유지

### Task 1-9: `GraphTraversalSuspendRepository.kt` 신규 생성

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTraversalSuspendRepository.kt` (신규)
- 핵심:
    - `neighbors` → `fun neighbors(...): Flow<GraphVertex>` (cold Flow)
    - `shortestPath` → `suspend fun shortestPath(...): GraphPath?` (단일값)
    - `allPaths` → `fun allPaths(...): Flow<GraphPath>` (cold Flow)

### Task 1-10: `GraphSuspendOperations.kt` 신규 생성

- **complexity: low**
- 파일: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendOperations.kt` (신규)
- 내용: `GraphSuspendSession`, `GraphVertexSuspendRepository`, `GraphEdgeSuspendRepository`,
  `GraphTraversalSuspendRepository` 통합 Facade

---

## Phase 2: graph-age 구현체 (Sync 전환)

### Task 2-1: `AgeGraphOperations` Sync 전환

- **complexity: medium**
- 파일: `graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt`
- 변경 범위: 15개 메서드 전부
- 패턴:
  ```
  // Before
  override suspend fun xxx(): T = withContext(Dispatchers.IO) { transaction(database) { ... } }

  // After
  override fun xxx(): T = transaction(database) { ... }
  ```
- import 제거: `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.withContext`
- KDoc 업데이트: "Dispatcher 경계" 섹션 제거, Sync 전환 명시
- 구현 인터페이스: `GraphOperations` (변경 없음, 이미 구현 중)
- 주의: `transaction(database) { ... }!!` 패턴과 `?: false`, `?: emptyList()` 패턴은 그대로 유지

### Task 2-2: `AgeGraphOperationsTest` 일반 테스트 전환

- **complexity: medium**
- 파일: `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt`
- 변경:
    - 모든 `= runSuspendIO { ... }` 블록 제거 → 일반 함수 body로 변환
    - `@BeforeEach fun resetGraph() = runSuspendIO { ... }` → `fun resetGraph() { ... }`
    - import 제거: `io.bluetape4k.junit5.coroutines.runSuspendIO`
- 테스트 수: 약 18개 테스트 메서드 변환

---

## Phase 3: graph-neo4j 구현체 (Suspend + Flow 전환)

### Task 3-1: `Neo4jGraphOperations` → `GraphSuspendOperations` 구현 변경

- **complexity: high**
- 파일: `graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt`
- 핵심 변경:
    1. 구현 인터페이스: `GraphOperations` → `GraphSuspendOperations`
    2. 컬렉션 반환 메서드 4개의 시그니처 변경:
        - `findVerticesByLabel`: `suspend fun ... : List<GraphVertex>` → `fun ... : Flow<GraphVertex>`
        - `findEdgesByLabel`: `suspend fun ... : List<GraphEdge>` → `fun ... : Flow<GraphEdge>`
        - `neighbors`: `suspend fun ... : List<GraphVertex>` → `fun ... : Flow<GraphVertex>`
        - `allPaths`: `suspend fun ... : List<GraphPath>` → `fun ... : Flow<GraphPath>`
    3. 단일값 반환 메서드는
       `suspend fun` 유지 (createVertex, findVertexById, updateVertex, deleteVertex, countVertices, createEdge, deleteEdge, shortestPath, createGraph, dropGraph, graphExists)
- 내부 구현: 컬렉션 메서드에서 `runQuery` 대신 `flowQuery` 사용

### Task 3-2: `flowQuery` 헬퍼 메서드 추가

- **complexity: medium**
- 파일: `graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt` (같은 파일)
- 기존 `runQuery` (suspend, List 반환)에 대응하는 `flowQuery` 추가:
  ```kotlin
  private fun <T> flowQuery(
      cypher: String,
      params: Map<String, Any?> = emptyMap(),
      mapper: (Record) -> T,
  ): Flow<T> = flow {
      val s = session()
      try {
          val result = s.run(Query(cypher, params)).awaitSingle()
          emitAll(result.records().asFlow().map(mapper))
      } finally {
          s.close<Void>().awaitFirstOrNull()
      }
  }
  ```
- import 추가: `kotlinx.coroutines.flow.flow`, `kotlinx.coroutines.flow.emitAll`, `kotlinx.coroutines.flow.map`

### Task 3-3: `Neo4jGraphOperationsTest` Suspend + Flow 검증 업데이트

- **complexity: high**
- 파일: `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt`
- 변경:
    1. `runSuspendIO` 블록은 유지 (suspend 메서드 호출 필요)
    2. 컬렉션 반환 테스트에서 `Flow` 수집 추가:
        - `ops.findVerticesByLabel("Person")` → `ops.findVerticesByLabel("Person").toList()`
        - `ops.findEdgesByLabel("KNOWS")` → `ops.findEdgesByLabel("KNOWS").toList()`
        - `ops.neighbors(...)` → `ops.neighbors(...).toList()`
        - `ops.allPaths(...)` → `ops.allPaths(...).toList()`
    3. import 추가: `kotlinx.coroutines.flow.toList`
- 테스트 수: 약 18개 테스트 메서드 중 ~8개에서 `.toList()` 추가

---

## Phase 4: examples 업데이트 (AGE 기반 → Sync 전환)

### Task 4-1: `LinkedInGraphService` Sync 전환

- **complexity: medium**
- 파일:
  `graph/examples/linkedin-graph-age/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/service/LinkedInGraphService.kt`
- 변경: 12개 메서드 전부 `suspend` 키워드 제거
- `ops`는 `GraphOperations` (Sync) 타입이므로 호환 유지

### Task 4-2: `LinkedInGraphTest` 일반 테스트 전환

- **complexity: medium**
- 파일: `graph/examples/linkedin-graph-age/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/LinkedInGraphTest.kt`
- 변경:
    - `@BeforeEach fun setupGraph() = runSuspendIO { ... }` → `fun setupGraph() { ... }`
    - 5개 테스트: `= runSuspendIO { ... }` → 일반 body `{ ... }`
    - import 제거: `io.bluetape4k.junit5.coroutines.runSuspendIO`

### Task 4-3: `CodeGraphService` Sync 전환

- **complexity: medium**
- 파일: `graph/examples/code-graph-age/src/main/kotlin/io/bluetape4k/graph/examples/code/service/CodeGraphService.kt`
- 변경: 17개 메서드 전부 `suspend` 키워드 제거
- `ops`는 `GraphOperations` (Sync) 타입이므로 호환 유지

### Task 4-4: `CodeGraphTest` 일반 테스트 전환

- **complexity: medium**
- 파일: `graph/examples/code-graph-age/src/test/kotlin/io/bluetape4k/graph/examples/code/CodeGraphTest.kt`
- 변경:
    - `@BeforeEach fun setupGraph() = runSuspendIO { ... }` → `fun setupGraph() { ... }`
    - 6개 테스트: `= runSuspendIO { ... }` → 일반 body `{ ... }`
    - import 제거: `io.bluetape4k.junit5.coroutines.runSuspendIO`

---

## Phase 5: 빌드 검증

### Task 5-1: graph-core 컴파일 확인

- **complexity: low**
- 명령: `./gradlew :graph-core:build`
- 확인: Sync 인터페이스 5개 + Suspend 인터페이스 5개 모두 컴파일 통과

### Task 5-2: graph-age 테스트 통과

- **complexity: low**
- 명령: `./gradlew :graph-age:test`
- 확인: 18개 테스트 모두 `suspend` 없이 통과

### Task 5-3: graph-neo4j 테스트 통과

- **complexity: low**
- 명령: `./gradlew :graph-neo4j:test`
- 확인: 18개 테스트 모두 `Flow.toList()` 기반으로 통과

### Task 5-4: examples 테스트 통과

- **complexity: low**
- 명령: `./gradlew :linkedin-graph-age:test :code-graph-age:test`
- 확인: 모든 example 테스트 Sync 전환 후 통과

---

## 태스크 요약

| Phase | Task | 설명                                            | Complexity | 파일 변경 유형 |
|-------|------|-----------------------------------------------|------------|----------|
| 1     | 1-1  | GraphSession Sync 변환                          | low        | 수정       |
| 1     | 1-2  | GraphVertexRepository Sync 변환                 | low        | 수정       |
| 1     | 1-3  | GraphEdgeRepository Sync 변환                   | low        | 수정       |
| 1     | 1-4  | GraphTraversalRepository Sync 변환              | low        | 수정       |
| 1     | 1-5  | GraphOperations KDoc 업데이트                     | low        | 수정       |
| 1     | 1-6  | GraphSuspendSession 신규 생성                     | low        | 신규       |
| 1     | 1-7  | GraphVertexSuspendRepository 신규 (Flow)        | low        | 신규       |
| 1     | 1-8  | GraphEdgeSuspendRepository 신규 (Flow)          | low        | 신규       |
| 1     | 1-9  | GraphTraversalSuspendRepository 신규 (Flow)     | low        | 신규       |
| 1     | 1-10 | GraphSuspendOperations 신규 Facade              | low        | 신규       |
| 2     | 2-1  | AgeGraphOperations Sync 전환                    | medium     | 수정       |
| 2     | 2-2  | AgeGraphOperationsTest 일반 테스트 전환              | medium     | 수정       |
| 3     | 3-1  | Neo4jGraphOperations → GraphSuspendOperations | **high**   | 수정       |
| 3     | 3-2  | flowQuery 헬퍼 메서드 추가                           | medium     | 수정       |
| 3     | 3-3  | Neo4jGraphOperationsTest Flow 검증              | **high**   | 수정       |
| 4     | 4-1  | LinkedInGraphService Sync 전환                  | medium     | 수정       |
| 4     | 4-2  | LinkedInGraphTest 일반 테스트 전환                   | medium     | 수정       |
| 4     | 4-3  | CodeGraphService Sync 전환                      | medium     | 수정       |
| 4     | 4-4  | CodeGraphTest 일반 테스트 전환                       | medium     | 수정       |
| 5     | 5-1  | graph-core 컴파일 확인                             | low        | 검증       |
| 5     | 5-2  | graph-age 테스트 통과                              | low        | 검증       |
| 5     | 5-3  | graph-neo4j 테스트 통과                            | low        | 검증       |
| 5     | 5-4  | examples 테스트 통과                               | low        | 검증       |

### Complexity 분포

- **high**: 2개 (Neo4j 구현체 변경, Neo4j 테스트 Flow 전환)
- **medium**: 7개 (AGE 구현체/테스트, Neo4j flowQuery, 4개 examples)
- **low**: 14개 (core 인터페이스 10개, 빌드 검증 4개)

### 실행 순서 제약

```
Phase 1 (1-1 ~ 1-10) → Phase 2 (2-1, 2-2) ─┐
                      → Phase 3 (3-1 ~ 3-3) ─┤→ Phase 5 (5-1 ~ 5-4)
                      → Phase 4 (4-1 ~ 4-4) ─┘
```

- Phase 1 완료 후 Phase 2/3/4는 병렬 실행 가능
- Phase 5는 모든 Phase 완료 후 실행

---

## 마이그레이션 체크리스트

- [ ] Sync 인터페이스에 `suspend` 키워드가 없는지 확인
- [ ] Suspend 인터페이스의 컬렉션 반환이 모두 `Flow<T>`인지 확인
- [ ] Suspend 인터페이스의 `Flow` 반환 메서드가 `fun` (not `suspend fun`)인지 확인
- [ ] AGE 구현체에서 `withContext(Dispatchers.IO)` 완전 제거 확인
- [ ] Neo4j 구현체에서 `asFlow().toList()` → `asFlow()` 변환 확인
- [ ] Neo4j 구현체의 구현 인터페이스가 `GraphSuspendOperations`인지 확인
- [ ] examples 서비스에서 `suspend` 완전 제거 확인
- [ ] 모든 모듈 컴파일 통과
- [ ] 모든 테스트 통과

---

## 향후 확장 (현재 스코프 밖)

- `AgeSuspendGraphOperations`: AGE의 Suspend 구현 (blocking을 `withContext(Dispatchers.IO)` + `flow {}` 로 래핑)
- `Neo4jSyncGraphOperations`: Neo4j의 Sync 구현 (`org.neo4j.driver.Session` 동기 세션 사용)
- `Neo4jCoroutineSession` Flow 반환 메서드 추가 (현재 `List<Record>` 반환 → `Flow<Record>`)
