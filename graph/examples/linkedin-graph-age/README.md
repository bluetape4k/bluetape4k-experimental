# linkedin-graph-age

LinkedIn 스타일 인맥 관리 시스템을 Apache AGE + graph-age 라이브러리로 구현한 예제 모듈입니다.

소셜 네트워크의 핵심 기능(인맥 연결, 최단 경로 탐색, N촌 인맥, 재직 정보)을 그래프 데이터베이스로 구현합니다.

---

## 아키텍처

```mermaid
graph TD
    subgraph "Application Layer"
        TEST["LinkedInGraphTest"]
    end

    subgraph "Service Layer"
        SVC["LinkedInGraphService"]
    end

    subgraph "Graph Abstraction"
        OPS["GraphOperations\n(interface)"]
    end

    subgraph "AGE Backend"
        AGE_OPS["AgeGraphOperations"]
        SQL["AgeSql / AgeTypeParser"]
    end

    subgraph "Infrastructure"
        HIKARI["HikariCP\nConnection Pool"]
        EXPOSED["Exposed (JDBC)"]
        PG["PostgreSQL + AGE Extension"]
    end

    TEST --> SVC
    SVC --> OPS
    OPS --> AGE_OPS
    AGE_OPS --> SQL
    AGE_OPS --> EXPOSED
    EXPOSED --> HIKARI
    HIKARI --> PG
```

---

## 소셜 네트워크 데이터 모델

```mermaid
graph LR
    P1(("Person\n- name\n- title\n- company\n- location"))
    P2(("Person"))
    P3(("Person"))
    C(("Company\n- name\n- industry\n- size\n- location"))
    S(("Skill\n- name\n- category"))

    P1 -->|"KNOWS\n(since, strength)"| P2
    P2 -->|"KNOWS\n(since, strength)"| P3
    P1 -->|"FOLLOWS"| P2
    P1 -->|"WORKS_AT\n(role, isCurrent)"| C
    P2 -->|"WORKS_AT\n(role, isCurrent)"| C
    P1 -->|"HAS_SKILL\n(level)"| S
    P1 -->|"ENDORSES\n(skillName)"| P2
```

---

## 스키마 클래스 다이어그램

```mermaid
classDiagram
    class VertexLabel {
        <<abstract>>
        +label: String
        +properties: List~PropertyDef~
        +string(name) PropertyDef~String~
        +integer(name) PropertyDef~Int~
        +boolean(name) PropertyDef~Boolean~
        +stringList(name) PropertyDef~List~String~~
    }

    class EdgeLabel {
        <<abstract>>
        +label: String
        +from: VertexLabel
        +to: VertexLabel
        +properties: List~PropertyDef~
    }

    class PersonLabel {
        <<object>>
        +name: PropertyDef~String~
        +title: PropertyDef~String~
        +company: PropertyDef~String~
        +location: PropertyDef~String~
        +skills: PropertyDef~List~String~~
        +connectionCount: PropertyDef~Int~
    }

    class CompanyLabel {
        <<object>>
        +name: PropertyDef~String~
        +industry: PropertyDef~String~
        +size: PropertyDef~String~
        +location: PropertyDef~String~
    }

    class SkillLabel {
        <<object>>
        +name: PropertyDef~String~
        +category: PropertyDef~String~
    }

    class KnowsLabel {
        <<object>>
        +since: PropertyDef~String~
        +strength: PropertyDef~Int~
    }

    class WorksAtLabel {
        <<object>>
        +role: PropertyDef~String~
        +startDate: PropertyDef~String~
        +isCurrent: PropertyDef~Boolean~
    }

    class FollowsLabel {
        <<object>>
    }

    class HasSkillLabel {
        <<object>>
        +level: PropertyDef~String~
    }

    class EndorsesLabel {
        <<object>>
        +skillName: PropertyDef~String~
    }

    VertexLabel <|-- PersonLabel
    VertexLabel <|-- CompanyLabel
    VertexLabel <|-- SkillLabel
    EdgeLabel <|-- KnowsLabel
    EdgeLabel <|-- WorksAtLabel
    EdgeLabel <|-- FollowsLabel
    EdgeLabel <|-- HasSkillLabel
    EdgeLabel <|-- EndorsesLabel

    KnowsLabel --> PersonLabel : from
    KnowsLabel --> PersonLabel : to
    WorksAtLabel --> PersonLabel : from
    WorksAtLabel --> CompanyLabel : to
    FollowsLabel --> PersonLabel : from
    FollowsLabel --> PersonLabel : to
    HasSkillLabel --> PersonLabel : from
    HasSkillLabel --> SkillLabel : to
    EndorsesLabel --> PersonLabel : from
    EndorsesLabel --> PersonLabel : to
```

---

## LinkedInGraphService 클래스 다이어그램

```mermaid
classDiagram
    class GraphOperations {
        <<interface>>
        +createGraph(name)
        +dropGraph(name)
        +graphExists(name) Boolean
        +createVertex(label, properties) GraphVertex
        +findVerticesByLabel(label, filter) List~GraphVertex~
        +createEdge(fromId, toId, label, properties) GraphEdge
        +neighbors(startId, edgeLabel, direction, depth) List~GraphVertex~
        +shortestPath(fromId, toId, edgeLabel, maxDepth) GraphPath?
        +allPaths(fromId, toId, edgeLabel, maxDepth) List~GraphPath~
    }

    class LinkedInGraphService {
        -ops: GraphOperations
        -graphName: String
        +initialize()
        +addPerson(name, title, company, location) GraphVertex
        +addCompany(name, industry, location) GraphVertex
        +connect(personId1, personId2, since, strength)
        +addWorkExperience(personId, companyId, role, isCurrent)
        +follow(followerId, targetId)
        +getDirectConnections(personId) List~GraphVertex~
        +getConnectionsWithinDegree(personId, degree) List~GraphVertex~
        +findConnectionPath(fromId, toId) GraphPath?
        +findAllConnectionPaths(fromId, toId) List~GraphPath~
        +findEmployees(companyId) List~GraphVertex~
        +findPersonByName(name) List~GraphVertex~
    }

    class AgeGraphOperations {
        -database: Database
        -graphName: String
    }

    LinkedInGraphService --> GraphOperations : uses
    AgeGraphOperations ..|> GraphOperations : implements
```

---

## 인맥 연결 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant Client
    participant Service as LinkedInGraphService
    participant Ops as GraphOperations
    participant AGE as Apache AGE

    Client->>Service: connect(aliceId, bobId, since, strength)
    activate Service

    Service->>Ops: createEdge(aliceId, bobId, "KNOWS", {since, strength})
    activate Ops
    Ops->>AGE: MATCH (a), (b) WHERE id(a)=aliceId AND id(b)=bobId\nCREATE (a)-[e:KNOWS {since, strength}]->(b)
    AGE-->>Ops: GraphEdge (A→B)
    deactivate Ops

    Service->>Ops: createEdge(bobId, aliceId, "KNOWS", {since, strength})
    activate Ops
    Ops->>AGE: MATCH (a), (b) WHERE id(a)=bobId AND id(b)=aliceId\nCREATE (a)-[e:KNOWS {since, strength}]->(b)
    AGE-->>Ops: GraphEdge (B→A)
    deactivate Ops

    Service-->>Client: 양방향 KNOWS 간선 생성 완료
    deactivate Service
```

---

## 최단 경로 탐색 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant Client
    participant Service as LinkedInGraphService
    participant Ops as GraphOperations
    participant AGE as Apache AGE

    Client->>Service: findConnectionPath(aliceId, daveId)
    activate Service

    Service->>Ops: shortestPath(aliceId, daveId, "KNOWS", maxDepth=6)
    activate Ops

    Ops->>AGE: MATCH p = shortestPath(\n  (a)-[:KNOWS*1..6]-(b)\n)\nWHERE id(a)=aliceId AND id(b)=daveId\nRETURN p
    AGE-->>Ops: agtype path 결과셋

    Ops->>Ops: AgeTypeParser.parsePath(rs)
    Note over Ops: GraphPath(steps=[VertexStep, EdgeStep, ...])

    Ops-->>Service: GraphPath?
    deactivate Ops

    Service-->>Client: GraphPath (length=단계수)
    deactivate Service
```

---

## 6단계 분리 이론 (Six Degrees of Separation)

```mermaid
graph LR
    A(("Alice"))
    B(("Bob\n1촌"))
    C(("Carol\n2촌"))
    D(("Dave\n3촌"))
    E(("Eve\n4촌"))
    F(("Frank\n5촌"))
    G(("Grace\n6촌"))

    A -->|"KNOWS"| B
    B -->|"KNOWS"| C
    C -->|"KNOWS"| D
    D -->|"KNOWS"| E
    E -->|"KNOWS"| F
    F -->|"KNOWS"| G

    style A fill:#0077B5,color:#fff
    style B fill:#00A0DC,color:#fff
    style C fill:#44B3E0,color:#fff
    style D fill:#78C6E4,color:#fff
    style E fill:#AADAE8,color:#000
    style F fill:#CCE8F0,color:#000
    style G fill:#EEF7FB,color:#000
```

> 세상의 모든 사람은 최대 6단계의 인맥으로 연결되어 있다는 이론입니다.
> `findConnectionPath()`는 AGE의 `shortestPath()` Cypher 함수로 이를 구현합니다.

---

## 테스트 환경 구성

```mermaid
graph TD
    subgraph "JUnit 5 Test (@TestInstance PER_CLASS)"
        BF["@BeforeAll\nstartServer()"]
        BE["@BeforeEach\nsetupGraph()"]
        T1["@Test 사람 추가 및 인맥 연결"]
        T2["@Test 최단 경로 탐색"]
        T3["@Test 2촌 인맥 탐색"]
        T4["@Test 회사 재직자 조회"]
        T5["@Test 팔로우 관계 생성"]
        AF["@AfterAll\nstopServer()"]
    end

    subgraph "PostgreSQLAgeServer (Testcontainers)"
        TC["PostgreSQLContainer\napache/age:PG16_latest"]
        EXT["CREATE EXTENSION IF NOT EXISTS age"]
    end

    subgraph "Connection Pool"
        HIKARI["HikariDataSource\nconnectionInitSql:\n  LOAD 'age';\n  SET search_path = ag_catalog"]
    end

    BF --> TC
    TC --> EXT
    BF --> HIKARI
    BE --> T1
    BE --> T2
    BE --> T3
    BE --> T4
    BE --> T5
    AF --> TC

    HIKARI -.-> T1
    HIKARI -.-> T2
    HIKARI -.-> T3
    HIKARI -.-> T4
    HIKARI -.-> T5
```

---

## 모듈 의존성

```
:linkedin-graph-age
├── implementation :graph-core       ← GraphOperations, GraphVertex, GraphPath 등 추상화
├── implementation :graph-age        ← AgeGraphOperations (AGE 백엔드 구현체)
├── implementation kotlinx-coroutines-core
├── testImplementation bluetape4k-junit5       ← runSuspendIO
├── testImplementation bluetape4k-testcontainers
├── testImplementation testcontainers-postgresql
├── testImplementation hikaricp
└── testImplementation kotlinx-coroutines-test
```

---

## 주요 기능

| 기능 | 메서드 | Cypher 패턴 |
|------|--------|-------------|
| 인맥 연결 (양방향) | `connect()` | `CREATE (a)-[:KNOWS]->(b)` × 2 |
| 1촌 인맥 조회 | `getDirectConnections()` | `MATCH (a)-[:KNOWS]->(n)` |
| N촌 인맥 조회 | `getConnectionsWithinDegree()` | `MATCH (a)-[:KNOWS*1..N]->(n)` |
| 최단 인맥 경로 | `findConnectionPath()` | `shortestPath((a)-[:KNOWS*1..6]-(b))` |
| 모든 연결 경로 | `findAllConnectionPaths()` | `MATCH p=(a)-[:KNOWS*1..3]-(b)` |
| 재직자 조회 | `findEmployees()` | `MATCH (p)-[:WORKS_AT]->(c)` |
| 팔로우 | `follow()` | `CREATE (a)-[:FOLLOWS]->(b)` |
| 이름으로 검색 | `findPersonByName()` | `MATCH (v:Person {name: $name})` |

---

## 실행 방법

```bash
# 테스트 실행 (Testcontainers가 자동으로 PostgreSQL+AGE 컨테이너 시작)
./gradlew :linkedin-graph-age:test

# 특정 테스트만 실행
./gradlew :linkedin-graph-age:test --tests "io.bluetape4k.graph.examples.linkedin.LinkedInGraphTest"
```

> Docker가 실행 중이어야 합니다. Testcontainers가 `apache/age:PG16_latest` 이미지를 자동으로 pull합니다.
