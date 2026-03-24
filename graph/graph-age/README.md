# graph-age

Apache AGE (PostgreSQL 그래프 확장)를 기반으로 한 `GraphOperations` 구현체. PostgreSQL 위에서 Cypher 쿼리를 SQL로 변환하여 실행하며, JetBrains Exposed ORM과 HikariCP 연결 풀을 활용한다.

## 모듈 설명

- **Apache AGE 기반**: PostgreSQL 내장 Cypher 엔진으로 SQL 쿼리로 그래프 연산 수행
- **Exposed + JDBC**: JetBrains Exposed 트랜잭션과 PostgreSQL JDBC 드라이버로 데이터 접근
- **SQL 빌더**: `AgeSql` 객체로 Cypher-over-SQL 쿼리 문자열 생성
- **agtype 파싱**: PostgreSQL의 `agtype` 결과를 Graph 모델로 변환
- **코루틴 기반**: 모든 메서드가 `suspend` 함수이며 `Dispatchers.IO`에서 실행

## 아키텍처

### 모듈 레이어 구조

```mermaid
graph TD
    A["graph-core<br/>(GraphOperations 인터페이스)"] --> B["graph-age<br/>(AgeGraphOperations 구현)"]
    B --> C["AgeSql<br/>(Cypher→SQL 변환)"]
    C --> D["PostgreSQL AGE<br/>(ag_catalog.cypher)"]
    E["AgePropertySerializer<br/>(값 직렬화)"] --> C
    F["AgeTypeParser<br/>(agtype 파싱)"] --> B
    G["Exposed Database<br/>(JDBC 트랜잭션)"] --> D
    H["HikariCP<br/>(연결 풀)"] --> G
    I["PostgreSQL Driver<br/>(TCP)"] --> D

    style A fill:#e1f5ff
    style B fill:#fff3e0
    style C fill:#f3e5f5
    style D fill:#e8f5e9
    style E fill:#fce4ec
    style F fill:#fce4ec
    style G fill:#ede7f6
    style H fill:#e0f2f1
    style I fill:#f1f8e9
```

### Apache AGE 동작 흐름

```mermaid
graph LR
    A["GraphOperations 메서드<br/>createVertex, shortestPath 등"] --> B["AgeGraphOperations<br/>withContext IO"]
    B --> C["Exposed transaction"]
    C --> D["AgeSql 빌더<br/>Cypher-over-SQL"]
    D --> E["SQL 쿼리 문자열<br/>SELECT * FROM ag_catalog.cypher"]
    E --> F["PostgreSQL<br/>AGE Extension"]
    F --> G["Cypher 엔진<br/>MATCH RETURN CREATE"]
    G --> H["결과 agtype<br/>JSON 유사 포맷"]
    H --> I["AgeTypeParser<br/>agtype → GraphVertex/Edge"]
    I --> J["GraphVertex<br/>GraphEdge<br/>GraphPath"]

    style A fill:#fff3e0
    style B fill:#f3e5f5
    style C fill:#ede7f6
    style D fill:#fce4ec
    style E fill:#e0f2f1
    style F fill:#e8f5e9
    style G fill:#c8e6c9
    style H fill:#f0f4c3
    style I fill:#fce4ec
    style J fill:#c8e6c9
```

## 주요 클래스

### AgeGraphOperations 클래스 다이어그램

```mermaid
classDiagram
    class GraphOperations {
        <<interface>>
    }

    class GraphSession {
        <<interface>>
        +createGraph(String)*
        +dropGraph(String)*
        +graphExists(String) Boolean*
        +close()*
    }

    class GraphVertexRepository {
        <<interface>>
        +createVertex(String, Map) GraphVertex*
        +findVertexById(String, GraphElementId) GraphVertex?*
        +findVerticesByLabel(String, Map) List~GraphVertex~*
        +updateVertex(String, GraphElementId, Map) GraphVertex?*
        +deleteVertex(String, GraphElementId) Boolean*
        +countVertices(String) Long*
    }

    class GraphEdgeRepository {
        <<interface>>
        +createEdge(GraphElementId, GraphElementId, String, Map) GraphEdge*
        +findEdgesByLabel(String, Map) List~GraphEdge~*
        +deleteEdge(String, GraphElementId) Boolean*
    }

    class GraphTraversalRepository {
        <<interface>>
        +neighbors(GraphElementId, String, Direction, Int) List~GraphVertex~*
        +shortestPath(GraphElementId, GraphElementId, String?, Int) GraphPath?*
        +allPaths(GraphElementId, GraphElementId, String?, Int) List~GraphPath~*
    }

    class AgeGraphOperations {
        -database: Database
        -graphName: String
        +createGraph(String) Unit
        +dropGraph(String) Unit
        +graphExists(String) Boolean
        +createVertex(String, Map) GraphVertex
        +findVertexById(String, GraphElementId) GraphVertex?
        +findVerticesByLabel(String, Map) List~GraphVertex~
        +updateVertex(String, GraphElementId, Map) GraphVertex?
        +deleteVertex(String, GraphElementId) Boolean
        +countVertices(String) Long
        +createEdge(GraphElementId, GraphElementId, String, Map) GraphEdge
        +findEdgesByLabel(String, Map) List~GraphEdge~
        +deleteEdge(String, GraphElementId) Boolean
        +neighbors(GraphElementId, String, Direction, Int) List~GraphVertex~
        +shortestPath(GraphElementId, GraphElementId, String?, Int) GraphPath?
        +allPaths(GraphElementId, GraphElementId, String?, Int) List~GraphPath~
        +close() Unit
    }

    GraphOperations --|> GraphSession
    GraphOperations --|> GraphVertexRepository
    GraphOperations --|> GraphEdgeRepository
    GraphOperations --|> GraphTraversalRepository
    AgeGraphOperations ..|> GraphOperations
```

### AgeSql 클래스 다이어그램

```mermaid
classDiagram
    class AgeSql {
        <<object>>
        +loadAge() String$
        +setSearchPath() String$
        +createExtension() String$
        +createGraph(String) String$
        +dropGraph(String, Boolean) String$
        +graphExists(String) String$
        +cypher(String, String, List) String$
        +createVertex(String, String, Map) String$
        +matchVertices(String, String, Map) String$
        +matchVertexById(String, String, Long) String$
        +updateVertex(String, String, Long, Map) String$
        +deleteVertex(String, String, Long) String$
        +countVertices(String, String) String$
        +createEdge(String, Long, Long, String, Map) String$
        +matchEdgesByLabel(String, String, Map) String$
        +deleteEdge(String, String, Long) String$
        +neighbors(String, Long, String, String, Int) String$
        +shortestPath(String, Long, Long, String?, Int) String$
    }

    class AgePropertySerializer {
        <<object>>
        +toCypherProps(Map) String$
        +toCypherValue(Any?) String$
    }

    AgeSql --> AgePropertySerializer: uses
```

### AgeTypeParser 클래스 다이어그램

```mermaid
classDiagram
    class AgeTypeParser {
        <<object>>
        +parseVertex(String) GraphVertex$
        +parseEdge(String) GraphEdge$
        +parsePath(String) GraphPath$
        +isVertex(String) Boolean$
        +isEdge(String) Boolean$
        +isPath(String) Boolean$
        +parseJsonObject(String) Map$
        +parseJsonArray(String) List$
    }

    class GraphVertex {
        +GraphElementId id
        +String label
        +Map~String, Any?~ properties
    }

    class GraphEdge {
        +GraphElementId id
        +String label
        +GraphElementId startId
        +GraphElementId endId
        +Map~String, Any?~ properties
    }

    class GraphPath {
        +List~PathStep~ steps
        +List~GraphVertex~ vertices
        +List~GraphEdge~ edges
        +Int length
        +Boolean isEmpty
    }

    AgeTypeParser --> GraphVertex: creates
    AgeTypeParser --> GraphEdge: creates
    AgeTypeParser --> GraphPath: creates
```

## 시퀀스 다이어그램

### createVertex 호출 흐름

```mermaid
sequenceDiagram
    participant User as User
    participant AgeOps as AgeGraphOperations
    participant IO as Dispatchers.IO
    participant Tx as Exposed Transaction
    participant SQL as PostgreSQL AGE
    participant Parser as AgeTypeParser
    participant Model as GraphVertex

    User->>AgeOps: createVertex("Person", props)
    AgeOps->>IO: withContext(Dispatchers.IO)
    IO->>Tx: transaction(database)
    Tx->>SQL: exec(LOAD 'age')
    SQL-->>Tx: OK
    Tx->>SQL: exec(SET search_path)
    SQL-->>Tx: OK
    Tx->>SQL: exec(AgeSql.createVertex(...))
    Note over SQL: SELECT * FROM ag_catalog.cypher<br/>('graph', $$ CREATE (v:Person {props}) RETURN v $$)<br/>AS (v agtype)
    SQL-->>Tx: agtype result: {"id": 123, "label": "Person", ...}::vertex
    Tx->>Parser: parseVertex(agtypeStr)
    Parser->>Parser: Remove "::vertex" suffix
    Parser->>Parser: Parse JSON
    Parser->>Model: GraphVertex(id, label, properties)
    Model-->>Parser: GraphVertex
    Parser-->>AgeOps: GraphVertex
    AgeOps-->>User: GraphVertex
```

### createEdge 호출 흐름

```mermaid
sequenceDiagram
    participant User as User
    participant AgeOps as AgeGraphOperations
    participant IO as Dispatchers.IO
    participant Tx as Exposed Transaction
    participant SQL as PostgreSQL AGE
    participant Parser as AgeTypeParser
    participant Model as GraphEdge

    User->>AgeOps: createEdge(fromId, toId, "KNOWS", props)
    AgeOps->>AgeOps: Convert IDs to Long
    AgeOps->>IO: withContext(Dispatchers.IO)
    IO->>Tx: transaction(database)
    Tx->>SQL: exec(LOAD 'age')
    SQL-->>Tx: OK
    Tx->>SQL: exec(SET search_path)
    SQL-->>Tx: OK
    Note over SQL: MATCH (a), (b)<br/>WHERE id(a) = fromId AND id(b) = toId<br/>CREATE (a)-[e:KNOWS {props}]->(b)<br/>RETURN e
    Tx->>SQL: exec(AgeSql.createEdge(...))
    SQL-->>Tx: agtype result: {"id": 456, "label": "KNOWS", ...}::edge
    Tx->>Parser: parseEdge(agtypeStr)
    Parser->>Parser: Remove "::edge" suffix
    Parser->>Parser: Parse JSON
    Parser->>Model: GraphEdge(id, label, startId, endId, properties)
    Model-->>Parser: GraphEdge
    Parser-->>AgeOps: GraphEdge
    AgeOps-->>User: GraphEdge
```

### shortestPath 호출 흐름

```mermaid
sequenceDiagram
    participant User as User
    participant AgeOps as AgeGraphOperations
    participant IO as Dispatchers.IO
    participant Tx as Exposed Transaction
    participant SQL as PostgreSQL AGE
    participant Parser as AgeTypeParser
    participant Model as GraphPath

    User->>AgeOps: shortestPath(fromId, toId, "KNOWS", maxDepth=10)
    AgeOps->>AgeOps: Validate & convert IDs to Long
    AgeOps->>IO: withContext(Dispatchers.IO)
    IO->>Tx: transaction(database)
    Tx->>SQL: exec(LOAD 'age')
    SQL-->>Tx: OK
    Tx->>SQL: exec(SET search_path)
    SQL-->>Tx: OK
    Note over SQL: MATCH p = shortestPath<br/>((a)-[:KNOWS*1..10]-(b))<br/>WHERE id(a) = fromId<br/>AND id(b) = toId<br/>RETURN p
    Tx->>SQL: exec(AgeSql.shortestPath(...))
    SQL-->>Tx: agtype path: [vertex, edge, vertex, ...]::path
    Tx->>Parser: parsePath(agtypeStr)
    Parser->>Parser: Remove "::path" suffix
    Parser->>Parser: Parse JSON array of steps
    loop For each element
        alt Element is vertex
            Parser->>Parser: parseVertex(element)
            Parser->>Model: Add VertexStep
        else Element is edge
            Parser->>Parser: parseEdge(element)
            Parser->>Model: Add EdgeStep
        end
    end
    Parser->>Model: GraphPath(steps)
    Model-->>Parser: GraphPath
    Parser-->>AgeOps: GraphPath or null
    AgeOps-->>User: GraphPath?
```

### neighbors 탐색 (방향별)

```mermaid
sequenceDiagram
    participant User as User
    participant AgeOps as AgeGraphOperations
    participant AgeSql as AgeSql
    participant SQL as PostgreSQL AGE

    User->>AgeOps: neighbors(startId, "KNOWS", OUTGOING, depth=2)
    AgeOps->>AgeSql: neighbors(graphName, startId, "KNOWS", "OUTGOING", 2)
    alt OUTGOING
        AgeSql->>AgeSql: Pattern = (start)-[:KNOWS*1..2]->(neighbor)
        Note over AgeSql: 출발 정점에서 나가는 간선만
    else INCOMING
        AgeSql->>AgeSql: Pattern = (start)<-[:KNOWS*1..2]-(neighbor)
        Note over AgeSql: 들어오는 간선만
    else BOTH
        AgeSql->>AgeSql: Pattern = (start)-[:KNOWS*1..2]-(neighbor)
        Note over AgeSql: 양방향 모두
    end
    AgeSql->>AgeSql: Build Cypher: MATCH pattern<br/>WHERE id(start) = startId<br/>RETURN DISTINCT neighbor
    AgeSql-->>AgeOps: SQL 쿼리 문자열
    AgeOps->>SQL: exec(SQL 쿼리)
    SQL-->>AgeOps: List of agtype vertices
    AgeOps->>AgeOps: parseVertex for each result
    AgeOps-->>User: List~GraphVertex~
```

## agtype 파싱 플로우

```mermaid
flowchart TD
    A["agtype 결과 문자열<br/>from PostgreSQL"] --> B{"Suffix 확인"}
    B -->|"::vertex"| C["removePrefix<br/>Remove ::vertex"]
    B -->|"::edge"| D["removeSuffix<br/>Remove ::edge"]
    B -->|"::path"| E["removeSuffix<br/>Remove ::path"]

    C --> F["parseJsonObject"]
    F --> G["Extract<br/>id, label, properties"]
    G --> H["Create GraphVertex<br/>GraphElementId id,<br/>String label,<br/>Map properties"]

    D --> I["parseJsonObject"]
    I --> J["Extract<br/>id, label, start_id, end_id, properties"]
    J --> K["Create GraphEdge<br/>id, label,<br/>startId, endId, properties"]

    E --> L["parseJsonArray"]
    L --> M["Iterate elements"]
    M --> N{"Element type?"}
    N -->|Contains ::vertex| O["parseVertex"]
    N -->|Contains ::edge| P["parseEdge"]
    O --> Q["Add VertexStep"]
    P --> Q
    Q --> R{"More elements?"}
    R -->|Yes| M
    R -->|No| S["Create GraphPath<br/>List of steps"]

    H --> T["Return GraphVertex"]
    K --> U["Return GraphEdge"]
    S --> V["Return GraphPath"]

    style A fill:#f0f4c3
    style B fill:#fff9c4
    style C fill:#fce4ec
    style D fill:#fce4ec
    style E fill:#fce4ec
    style T fill:#c8e6c9
    style U fill:#c8e6c9
    style V fill:#c8e6c9
```

## HikariCP 연결 초기화

```mermaid
sequenceDiagram
    participant Init as 초기화 코드
    participant HikariCP as HikariCP Pool
    participant PgSQL as PostgreSQL
    participant AGE as AGE Extension

    Init->>HikariCP: 커넥션 풀 생성<br/>connectionInitSql 설정
    HikariCP->>PgSQL: TCP 연결
    PgSQL-->>HikariCP: Connected
    HikariCP->>PgSQL: LOAD 'age'
    PgSQL->>AGE: Extension 로드
    AGE-->>PgSQL: OK
    PgSQL-->>HikariCP: Query OK
    HikariCP->>PgSQL: SET search_path = ag_catalog, "$user", public
    PgSQL-->>HikariCP: Query OK
    Note over HikariCP: 커넥션 풀 준비 완료<br/>이후 모든 커넥션은<br/>AGE 사용 가능 상태

    Init->>HikariCP: getConnection()
    HikariCP-->>Init: Connection (AGE ready)
```

## 테스트 환경 구성

```mermaid
graph TD
    A["테스트 시작"] --> B["PostgreSQLAgeServer<br/>Testcontainers"]
    B --> C["Docker 이미지<br/>apache/age:PG16_latest"]
    C --> D["PostgreSQL 16<br/>+ AGE Extension"]
    D --> E["CREATE EXTENSION<br/>IF NOT EXISTS age<br/>자동 실행"]

    E --> F["HikariCP DataSource"]
    F --> G["connectionInitSql<br/>LOAD 'age'<br/>SET search_path"]
    G --> H["Exposed Database"]
    H --> I["AgeGraphOperations"]
    I --> J["테스트 메서드<br/>runTest"]

    J --> K["suspend 메서드 호출<br/>createVertex 등"]
    K --> L["withContext<br/>Dispatchers.IO"]
    L --> M["transaction"]
    M --> N["PostgreSQL 쿼리"]
    N --> O["agtype 결과"]
    O --> P["AgeTypeParser"]
    P --> Q["GraphVertex<br/>GraphEdge<br/>GraphPath"]
    Q --> R["테스트 검증"]
    R --> S["테스트 완료"]
    S --> T["컨테이너 정리"]

    style A fill:#c8e6c9
    style B fill:#e8f5e9
    style C fill:#c8e6c9
    style D fill:#a5d6a7
    style F fill:#ede7f6
    style H fill:#f3e5f5
    style I fill:#fff3e0
    style J fill:#c8e6c9
    style T fill:#ffcdd2
```

## 코드 예시

### 의존성

```kotlin
// build.gradle.kts
dependencies {
    api(project(":graph-core"))
    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.postgresql_driver)
    api(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.hikaricp)
    testImplementation(Libs.kotlinx_coroutines_test)
}
```

### HikariCP + PostgreSQL AGE 설정

```kotlin
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database

// PostgreSQL AGE 서버 시작 (Testcontainers)
val pgServer = PostgreSQLAgeServer().also { it.start() }

// HikariCP 설정: AGE LOAD + search_path 자동 실행
val hikariConfig = HikariConfig().apply {
    jdbcUrl = pgServer.jdbcUrl
    username = pgServer.username
    password = pgServer.password
    driverClassName = "org.postgresql.Driver"

    // 각 커넥션 획득 시 AGE 초기화
    connectionInitSql = """
        LOAD 'age';
        SET search_path = ag_catalog, "${'$'}user", public
    """.trimIndent()

    maximumPoolSize = 5
    minimumIdle = 2
}
val datasource = HikariDataSource(hikariConfig)

// Exposed Database 생성
val database = Database.connect(datasource)

// AgeGraphOperations 인스턴스
val graphOps = AgeGraphOperations(
    database = database,
    graphName = "my_graph"
)
```

### 그래프 초기화

```kotlin
// 그래프 생성 (첫 사용 시)
graphOps.createGraph("my_graph")

// 또는 존재 여부 확인 후 생성
if (!graphOps.graphExists("my_graph")) {
    graphOps.createGraph("my_graph")
}
```

### 정점 생성

```kotlin
runTest {
    // 단순 정점 생성
    val alice = graphOps.createVertex(
        label = "Person",
        properties = mapOf(
            "name" to "Alice",
            "age" to 30,
            "email" to "alice@example.com"
        )
    )
    // GraphVertex(id=GraphElementId("12345"), label="Person", properties={...})

    val bob = graphOps.createVertex(
        label = "Person",
        properties = mapOf(
            "name" to "Bob",
            "age" to 28,
            "email" to "bob@example.com"
        )
    )
}
```

### 간선 생성

```kotlin
runTest {
    val knows = graphOps.createEdge(
        fromId = alice.id,
        toId = bob.id,
        label = "KNOWS",
        properties = mapOf(
            "since" to LocalDate.of(2020, 1, 15),
            "strength" to 5
        )
    )
    // GraphEdge(
    //   id=GraphElementId("456"),
    //   label="KNOWS",
    //   startId=alice.id,
    //   endId=bob.id,
    //   properties={since: LocalDate, strength: 5}
    // )
}
```

### 최단 경로 찾기

```kotlin
runTest {
    val path = graphOps.shortestPath(
        fromId = alice.id,
        toId = bob.id,
        edgeLabel = "KNOWS",
        maxDepth = 10
    )

    if (path != null && !path.isEmpty) {
        println("경로 길이: ${path.length} (간선 수)")
        println("정점 수: ${path.vertices.size}")

        // 경로의 모든 단계 순회
        path.steps.forEachIndexed { idx, step ->
            when (step) {
                is PathStep.VertexStep -> {
                    println("[$idx] 정점: ${step.vertex.properties["name"]}")
                }
                is PathStep.EdgeStep -> {
                    println("[$idx] 간선: ${step.edge.label}")
                }
            }
        }
    }
}
```

### 인접 정점 탐색

```kotlin
runTest {
    // 나가는 간선 (OUTGOING)
    val friends = graphOps.neighbors(
        startId = alice.id,
        edgeLabel = "KNOWS",
        direction = Direction.OUTGOING,
        depth = 1
    )
    // [bob, charlie, dave] - alice가 KNOWS하는 사람들

    // 들어오는 간선 (INCOMING)
    val admirers = graphOps.neighbors(
        startId = alice.id,
        edgeLabel = "KNOWS",
        direction = Direction.INCOMING,
        depth = 1
    )
    // [eve, frank] - alice를 KNOWS하는 사람들

    // 양방향
    val allConnected = graphOps.neighbors(
        startId = alice.id,
        edgeLabel = "KNOWS",
        direction = Direction.BOTH,
        depth = 1
    )
    // [bob, charlie, dave, eve, frank] - 모든 연결된 사람들

    // 깊이 기반 탐색 (2촌)
    val secondDegree = graphOps.neighbors(
        startId = alice.id,
        edgeLabel = "KNOWS",
        direction = Direction.OUTGOING,
        depth = 2
    )
}
```

### 모든 경로 탐색

```kotlin
runTest {
    val allPaths = graphOps.allPaths(
        fromId = alice.id,
        toId = charlie.id,
        edgeLabel = "KNOWS",
        maxDepth = 5
    )

    println("총 경로 수: ${allPaths.size}")

    for ((idx, path) in allPaths.withIndex()) {
        println("경로 $idx: ${path.length}개 간선, ${path.vertices.size}개 정점")
        val names = path.vertices.map { it.properties["name"] }
        println("  $names")
    }
}
```

### 정점 수정

```kotlin
runTest {
    val updated = graphOps.updateVertex(
        label = "Person",
        id = alice.id,
        properties = mapOf(
            "age" to 31,
            "email" to "alice.updated@example.com"
        )
    )
    // 수정된 GraphVertex 반환
    println("Updated: ${updated?.properties}")
}
```

### 정점/간선 조회 및 삭제

```kotlin
runTest {
    // ID로 조회
    val vertex = graphOps.findVertexById("Person", alice.id)
    println("Found: $vertex")

    // 레이블로 전체 조회
    val allPersons = graphOps.findVerticesByLabel("Person")
    println("All persons: ${allPersons.size}")

    // 필터 조건으로 조회
    val engineers = graphOps.findVerticesByLabel(
        "Person",
        filter = mapOf("role" to "Engineer")
    )

    // 정점 개수
    val count = graphOps.countVertices("Person")
    println("Total persons: $count")

    // 간선 조회
    val knowsEdges = graphOps.findEdgesByLabel("KNOWS")

    // 간선 필터
    val strongRelations = graphOps.findEdgesByLabel(
        "KNOWS",
        filter = mapOf("strength" to 5)
    )

    // 정점 삭제
    val deleted = graphOps.deleteVertex("Person", alice.id)
    println("Deleted: $deleted")

    // 간선 삭제
    val edgeDeleted = graphOps.deleteEdge("KNOWS", knows.id)
}
```

## 내부 구현 상세

### AgeSql 쿼리 생성 예

정점 생성 시 생성되는 SQL:

```sql
SELECT *
FROM ag_catalog.cypher(
    'my_graph',
    $$ CREATE (v:Person {name: 'Alice', age: 30}) RETURN v $$
) AS (v agtype)
```

간선 생성 시 생성되는 SQL:

```sql
SELECT *
FROM ag_catalog.cypher(
    'my_graph',
    $$ MATCH (a), (b)
       WHERE id(a) = 123 AND id(b) = 456
       CREATE (a)-[e:KNOWS {since: '2020-01-15', strength: 5}]->(b)
       RETURN e $$
) AS (e agtype)
```

최단 경로 쿼리:

```sql
SELECT *
FROM ag_catalog.cypher(
    'my_graph',
    $$ MATCH p = shortestPath((a)-[:KNOWS*1..10]-(b))
       WHERE id(a) = 123 AND id(b) = 456
       RETURN p $$
) AS (p agtype)
```

### AgePropertySerializer 변환

Kotlin 값을 Cypher 맵으로 변환:

```kotlin
// 입력
mapOf(
    "name" to "Alice",
    "age" to 30,
    "joinedAt" to LocalDate.of(2020, 1, 15),
    "tags" to listOf("engineer", "kotlin"),
    "metadata" to mapOf("level" to "senior")
)

// 출력 (Cypher 문자열)
{name: 'Alice', age: 30, joinedAt: '2020-01-15', tags: ['engineer', 'kotlin'], metadata: {level: 'senior'}}
```

### AgeTypeParser agtype 파싱

PostgreSQL AGE의 agtype 응답:

```json
{"id": 12345, "label": "Person", "properties": {"name": "Alice", "age": 30}}::vertex
```

파싱 결과:

```kotlin
GraphVertex(
    id = GraphElementId("12345"),
    label = "Person",
    properties = mapOf("name" to "Alice", "age" to 30)
)
```

## 주의사항

### ID 타입

- **AGE 내부 ID**: Long 형식 (64bit)
- **GraphElementId**: String 래퍼
- **변환**: `id.value.toLongOrNull()` 사용 후 전달

예외 발생 조건:
```kotlin
val longId = id.value.toLongOrNull()
    ?: throw GraphQueryException("AGE requires numeric ID, got: ${id.value}")
```

### agtype 파싱 한계

현재 `AgeTypeParser`는 Jackson/Gson 없이 단순 JSON 파싱을 수행:

- 깊게 중첩된 객체 파싱에 한계
- 특수 문자 이스케이핑 기본 처리만 지원
- 복잡한 구조는 Jackson 도입 고려

### HikariCP connectionInitSql

각 커넥션 획득 시 `LOAD 'age'` 실행:

```kotlin
connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"${'$'}user\", public"
```

주의: 스크립트 여러 줄은 세미콜론(`;`)으로 구분하되, 마지막 문장도 세미콜론 포함

### 트랜잭션 격리

모든 메서드는 `withContext(Dispatchers.IO) { transaction { ... } }` 내에서 실행:

- 각 메서드는 독립적인 트랜잭션
- 외부 트랜잭션 관리 불가
- 멀티 쿼리 원자성 필요 시 별도 API 확장 필요

## 의존성

```kotlin
// 필수
api(project(":graph-core"))
api(Libs.exposed_core)
api(Libs.exposed_jdbc)
api(Libs.postgresql_driver)
api(Libs.kotlinx_coroutines_core)

// 테스트
testImplementation(Libs.bluetape4k_junit5)
testImplementation(Libs.bluetape4k_testcontainers)
testImplementation(Libs.testcontainers_postgresql)
testImplementation(Libs.hikaricp)
testImplementation(Libs.kotlinx_coroutines_test)
```

## 참고

- **Apache AGE 공식 문서**: https://age.apache.org/
- **PostgreSQL Cypher 문법**: AGE는 openCypher 표준 준수
- **Exposed 문서**: https://github.com/JetBrains/Exposed
- **graph-core**: 백엔드 독립 모델 및 인터페이스
- **graph-neo4j**: Neo4j 기반 다른 GraphOperations 구현체
