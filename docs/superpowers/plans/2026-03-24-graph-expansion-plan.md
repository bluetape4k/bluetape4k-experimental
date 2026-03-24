# Graph Expansion Work Plan - 2026-03-24

## Context

spec: `docs/superpowers/specs/2026-03-24-graph-expansion-spec.md`

bluetape4k-experimental의 graph 모듈 확장. 기존 graph-core / graph-age / graph-neo4j 구조에
**graph-memgraph**, **graph-tinkerpop** 백엔드를 추가하고, Neo4j 백엔드 예제 모듈 2개를 생성하며,
기존 README의 Mermaid 다이어그램을 dark theme 호환으로 수정한다.

### 추가 결정사항 (spec 대비 변경)
- **Memgraph 추가**: Neo4j Bolt 프로토콜 + Cypher 완전 호환. `neo4j-java-driver` 재사용.
- **TinkerPop 유지**: Gremlin 패러다임 차별화 목적 그대로 유지.

---

## Work Objectives

1. Memgraph 백엔드 모듈 (`graph-memgraph`) 구현
2. TinkerPop/Gremlin 백엔드 모듈 (`graph-tinkerpop`) 구현
3. Neo4j 예제 모듈 2개 (code-graph-neo4j, linkedin-graph-neo4j) 생성
4. graph/ 하위 README Mermaid 다이어그램 dark theme 호환 수정

---

## Guardrails

### Must Have
- 모든 신규 모듈은 `./gradlew :<module>:test` 통과
- 기존 graph-core / graph-neo4j / graph-age 코드 변경 없음 (Libs.kt 의존성 추가는 허용)
- 각 모듈에 README.md 포함
- test resources (junit-platform.properties, logback-test.xml) 기존 모듈에서 복사

### Must NOT Have
- `./gradlew build` 전체 빌드 실행 금지
- 기존 모듈 테스트 깨뜨리기
- settings.gradle.kts 수정 (자동 감지)
- 불필요한 아키텍처 변경

---

## Task Flow

```
Task 1 (Libs.kt 의존성 추가) ─┬─> Task 2 (graph-memgraph)     ─┐
                               ├─> Task 3 (graph-tinkerpop)     ├─> Task 6 (README dark theme + 아키텍처 갱신)
                               ├─> Task 4 (code-graph-neo4j)    │
                               └─> Task 5 (linkedin-graph-neo4j)┘

병렬 그룹:
  Group A: Task 2, 3, 4, 5 (모두 Task 1 이후, 서로 독립)
  Group B: Task 6 (모든 모듈 완료 후)
```

---

## Detailed TODOs

### Task 1: Libs.kt 의존성 추가
- **Complexity**: LOW
- **Dependencies**: 없음
- **Files**:
  - MODIFY: `buildSrc/src/main/kotlin/Libs.kt`
- **Work**:
  - TinkerPop 의존성 추가: `tinkerpop_gremlin_core` ("org.apache.tinkerpop:gremlin-core:3.7.3"), `tinkergraph_gremlin` ("org.apache.tinkerpop:tinkergraph-gremlin:3.7.3")
  - Memgraph Testcontainers 의존성 추가: `testcontainers_memgraph` (GenericContainer 기반이므로 별도 아티팩트 불필요할 수 있음 -- 확인 후 결정)
- **Acceptance**:
  - `./gradlew buildSrc:build` 에러 없음
  - 새 상수가 다른 build.gradle.kts에서 참조 가능

---

### Task 2: graph-memgraph 모듈 생성
- **Complexity**: MEDIUM
- **Dependencies**: Task 1
- **Files**:
  - CREATE: `graph/graph-memgraph/build.gradle.kts`
  - CREATE: `graph/graph-memgraph/README.md`
  - CREATE: `graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt`
  - CREATE: `graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSuspendOperations.kt`
  - CREATE: `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphServer.kt`
  - CREATE: `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt`
  - CREATE: `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSuspendOperationsTest.kt`
  - COPY: `src/test/resources/junit-platform.properties`, `logback-test.xml`
- **Design Notes**:
  - Memgraph는 Bolt 프로토콜 + openCypher 호환. `neo4j-java-driver` 그대로 사용.
  - `Neo4jGraphOperations`를 **composition으로 위임** 또는 **얇은 래퍼**로 구현.
    - 핵심 차이점: Memgraph는 `elementId()` 대신 내부 ID 사용 가능성 확인. Memgraph 5.x는 `elementId()` 지원하므로 Neo4j 코드 그대로 동작할 가능성 높음.
    - database 파라미터: Memgraph는 단일 DB이므로 database 파라미터 무시 또는 기본값 사용.
  - `build.gradle.kts`: `api(project(":graph-core"))`, `api(Libs.neo4j_java_driver)`, `runtimeOnly(Libs.neo4j_bolt_connection_netty)` + Testcontainers (GenericContainer로 `memgraph/memgraph` 이미지)
  - MemgraphServer: `GenericContainer("memgraph/memgraph:latest")` + Bolt 포트(7687) expose + `withoutAuthentication` 대응
- **Acceptance**:
  - `./gradlew :graph-memgraph:test` 통과
  - Vertex CRUD, Edge CRUD, neighbors, shortestPath, allPaths 모두 테스트 통과
  - Memgraph Testcontainer 정상 기동 확인

---

### Task 3: graph-tinkerpop 모듈 생성
- **Complexity**: HIGH
- **Dependencies**: Task 1
- **Files**:
  - CREATE: `graph/graph-tinkerpop/build.gradle.kts`
  - CREATE: `graph/graph-tinkerpop/README.md`
  - CREATE: `graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt`
  - CREATE: `graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphSuspendOperations.kt`
  - CREATE: `graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/GremlinRecordMapper.kt`
  - CREATE: `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt`
  - CREATE: `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphSuspendOperationsTest.kt`
  - COPY: `src/test/resources/junit-platform.properties`, `logback-test.xml`
- **Design Notes**:
  - Gremlin traversal API로 GraphOperations 인터페이스 매핑 (spec 섹션 2 참조)
  - `GraphTraversalSource` 기반. `TinkerGraph.open()` 으로 in-memory 그래프 생성.
  - **GraphElementId 매핑**: TinkerPop은 Long ID 자동 생성. `GraphElementId.of(id.toString())` 사용.
  - **SuspendOperations**: TinkerGraph는 in-process이므로 `withContext(Dispatchers.IO)` 래핑으로 구현.
  - Testcontainers 불필요 (in-memory).
  - `GremlinRecordMapper`: TinkerPop `Vertex` -> `GraphVertex`, `Edge` -> `GraphEdge`, `Path` -> `GraphPath` 변환.
- **Acceptance**:
  - `./gradlew :graph-tinkerpop:test` 통과
  - Neo4jGraphOperationsTest와 동일한 수준의 테스트 시나리오 (Vertex CRUD, Edge CRUD, neighbors 방향별, depth, shortestPath, allPaths)
  - Gremlin traversal이 올바르게 GraphOperations 시맨틱에 매핑됨

---

### Task 4: code-graph-neo4j 예제 모듈 생성
- **Complexity**: MEDIUM
- **Dependencies**: Task 1 (graph-neo4j, code-graph-age는 이미 존재)
- **Files**:
  - CREATE: `graph/examples/code-graph-neo4j/build.gradle.kts`
  - CREATE: `graph/examples/code-graph-neo4j/README.md`
  - CREATE: `graph/examples/code-graph-neo4j/src/main/kotlin/io/bluetape4k/graph/examples/code/service/CodeGraphService.kt` (code-graph-age에서 복사 -- GraphOperations 인터페이스 의존이므로 동일)
  - CREATE: `graph/examples/code-graph-neo4j/src/main/kotlin/io/bluetape4k/graph/examples/code/service/CodeGraphSuspendService.kt` (복사)
  - CREATE: `graph/examples/code-graph-neo4j/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt` (복사)
  - CREATE: `graph/examples/code-graph-neo4j/src/test/kotlin/io/bluetape4k/graph/examples/code/Neo4jCodeGraphTest.kt`
  - CREATE: `graph/examples/code-graph-neo4j/src/test/kotlin/io/bluetape4k/graph/examples/code/Neo4jServer.kt`
  - COPY: `src/test/resources/junit-platform.properties`, `logback-test.xml`
- **Design Notes**:
  - Service/Schema 클래스를 **소스 복사** 방식 사용 (code-graph-age 모듈을 의존하면 AGE 전이 의존성 발생하므로).
  - `build.gradle.kts`: `implementation(project(":graph-core"))`, `implementation(project(":graph-neo4j"))`, `implementation(Libs.kotlinx_coroutines_core)` + test deps
  - `Neo4jServer.kt`: graph-neo4j의 `Neo4jServer.kt`와 동일 패턴 (Testcontainers 싱글턴)
  - `Neo4jCodeGraphTest.kt`: CodeGraphTest의 6개 시나리오를 Neo4jGraphOperations로 수행. Driver 셋업만 변경.
- **Acceptance**:
  - `./gradlew :code-graph-neo4j:test` 통과
  - 6개 테스트 시나리오: 모듈 의존성, 경로 탐색, 영향 범위, 상속 계층, 호출 체인, null 경로

---

### Task 5: linkedin-graph-neo4j 예제 모듈 생성
- **Complexity**: MEDIUM
- **Dependencies**: Task 1 (graph-neo4j, linkedin-graph-age는 이미 존재)
- **Files**:
  - CREATE: `graph/examples/linkedin-graph-neo4j/build.gradle.kts`
  - CREATE: `graph/examples/linkedin-graph-neo4j/README.md`
  - CREATE: `graph/examples/linkedin-graph-neo4j/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/service/LinkedInGraphService.kt` (복사)
  - CREATE: `graph/examples/linkedin-graph-neo4j/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/schema/LinkedInSchema.kt` (복사)
  - CREATE: `graph/examples/linkedin-graph-neo4j/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/Neo4jLinkedInGraphTest.kt`
  - CREATE: `graph/examples/linkedin-graph-neo4j/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/Neo4jServer.kt`
  - COPY: `src/test/resources/junit-platform.properties`, `logback-test.xml`
- **Design Notes**:
  - Task 4와 동일 패턴. LinkedInGraphService + Neo4jGraphOperations 조합.
  - `Neo4jServer.kt`: code-graph-neo4j와 동일 (또는 그쪽 모듈에서 복사).
  - 5개 테스트: 인맥 연결, 최단 경로, 2촌 탐색, 회사 재직자, 팔로우 관계.
- **Acceptance**:
  - `./gradlew :linkedin-graph-neo4j:test` 통과
  - LinkedInGraphTest와 동일 시나리오 Neo4j 백엔드로 통과

---

### Task 6: README dark theme 수정 + 아키텍처 갱신
- **Complexity**: LOW-MEDIUM
- **Dependencies**: Task 2, 3, 4, 5 (새 모듈이 확정된 후 아키텍처 다이어그램 갱신)
- **Files**:
  - MODIFY: `graph/graph-core/README.md` (아키텍처 다이어그램에 TinkerPop, Memgraph 추가 + dark theme)
  - MODIFY: `graph/graph-neo4j/README.md` (dark theme 수정)
  - MODIFY: `graph/graph-age/README.md` (dark theme 수정)
  - MODIFY: `graph/examples/code-graph-age/README.md` (dark theme 수정)
  - MODIFY: `graph/examples/linkedin-graph-age/README.md` (dark theme 수정)
- **Design Notes**:
  - 방법 B (절충안) 적용: 중간 채도 fill + #FFFFFF 텍스트 + 진한 stroke
  - 팔레트 (spec 섹션 4 참조):
    - Core: `fill:#2563EB,stroke:#1E40AF,color:#FFFFFF`
    - AGE: `fill:#059669,stroke:#047857,color:#FFFFFF`
    - Neo4j: `fill:#DC2626,stroke:#B91C1C,color:#FFFFFF`
    - TinkerPop: `fill:#D97706,stroke:#B45309,color:#FFFFFF`
    - Memgraph: `fill:#7C3AED,stroke:#6D28D9,color:#FFFFFF`
    - Neutral: `fill:#4B5563,stroke:#374151,color:#FFFFFF`
  - graph-core README에 Memgraph, TinkerPop 노드를 아키텍처 다이어그램에 추가
- **Acceptance**:
  - 모든 Mermaid style 지시문이 dark/light 호환 팔레트 사용
  - graph-core 아키텍처 다이어그램에 5개 백엔드 (AGE, Neo4j, Memgraph, TinkerPop, 미래확장) 표시

---

## Success Criteria

1. 모든 신규 모듈 개별 테스트 통과: `:graph-memgraph:test`, `:graph-tinkerpop:test`, `:code-graph-neo4j:test`, `:linkedin-graph-neo4j:test`
2. 기존 모듈 테스트 불변: `:graph-core:test`, `:graph-neo4j:test`, `:graph-age:test` 깨지지 않음
3. 각 모듈에 README.md 존재
4. graph/ 하위 README Mermaid 다이어그램이 dark theme에서 가독성 확보

## Execution Groups (병렬 실행 가이드)

| Phase | Tasks | 비고 |
|-------|-------|------|
| Phase 0 | Task 1 (Libs.kt) | 선행 필수 |
| Phase 1 | Task 2, 3, 4, 5 (병렬) | 모두 독립적, 동시 실행 가능 |
| Phase 2 | Task 6 (README) | 전체 모듈 확정 후 |

## Open Questions

- [ ] Memgraph `elementId()` 호환성: Memgraph 최신 버전이 Neo4j 5.x의 `elementId()` API를 완전히 지원하는지 런타임 확인 필요 -- 미지원 시 `id()` (Long) 기반으로 폴백 로직 추가
- [ ] TinkerPop 3.7.3 + Java 25 호환성: TinkerPop 3.7.x가 Java 25에서 동작하는지 빌드 시 확인 필요 -- 미호환 시 3.7.4+ 또는 4.0.x 검토
- [ ] Neo4j 예제에서 Service 클래스 소스 복사 vs 공통 모듈 분리: 현재는 소스 복사 방식. 향후 예제가 3개 이상이면 공통 모듈(`graph-examples-common`) 분리 검토
- [ ] Memgraph Testcontainers: `memgraph/memgraph` Docker 이미지 ARM64 지원 여부 (Apple Silicon) -- 미지원 시 `--platform linux/amd64` 옵션 필요
