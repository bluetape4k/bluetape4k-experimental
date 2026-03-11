# exposed-lettuce 캐시 전략 설계

**날짜**: 2026-03-12
**모듈**: `data/exposed-lettuce`
**참조**: `bluetape4k-projects/data/exposed-jdbc-redisson`

---

## 개요

JetBrains Exposed JDBC DAO를 백엔드로 사용하는 Lettuce 기반 캐시 전략 구현.
Redisson의 `MapLoader`/`MapWriter` 패턴을 Lettuce로 포팅하여 Read-through, Write-through, Write-behind를 지원한다.

---

## 설계 목표

- Redisson(`exposed-jdbc-redisson`) 구현과 동일한 인터페이스 이름 및 패턴 유지
- 동기(blocking) API만 제공 (코루틴은 향후 `exposed-r2dbc-lettuce`에서)
- 재시도 로직은 `bluetape4k-resilience4j` 활용
- 사용자는 `AbstractJdbcLettuceRepository`의 추상 메서드 4개만 구현하면 완성

---

## 아키텍처

### 모듈 위치

```
bluetape4k-experimental/
└── data/
    └── exposed-lettuce/
        ├── build.gradle.kts
        └── src/
            ├── main/kotlin/io/bluetape4k/exposed/lettuce/
            │   ├── map/
            │   │   ├── MapLoader.kt
            │   │   ├── MapWriter.kt
            │   │   ├── WriteMode.kt
            │   │   ├── LettuceCacheConfig.kt
            │   │   ├── LettuceLoadedMap.kt
            │   │   ├── EntityMapLoader.kt
            │   │   ├── ExposedEntityMapLoader.kt
            │   │   ├── EntityMapWriter.kt
            │   │   └── ExposedEntityMapWriter.kt
            │   └── repository/
            │       └── AbstractJdbcLettuceRepository.kt
            └── test/kotlin/io/bluetape4k/exposed/lettuce/
                ├── map/
                │   ├── ExposedEntityMapLoaderTest.kt
                │   └── ExposedEntityMapWriterTest.kt
                └── repository/
                    └── AbstractJdbcLettuceRepositoryTest.kt
```

### 클래스 계층

```
MapLoader<K,V> (interface)
  └── EntityMapLoader<ID,E>          (abstract — transaction 래핑)
        └── ExposedEntityMapLoader<ID,E>  (IdTable + batchSize 기반)

MapWriter<K,V> (interface)
  └── EntityMapWriter<ID,E>          (abstract — transaction 래핑 + Retry)
        └── ExposedEntityMapWriter<ID,E>  (WriteMode 분기)

LettuceLoadedMap<K,V>
  ├── StatefulRedisConnection<String, V>  (Lettuce — LettuceBinaryCodec<V> 사용)
  ├── RedisCodec<String, V>               (직렬화 코덱)
  ├── keyPrefix: String                   (Redis 키 prefix, 기본 "cache")
  ├── keySerializer: (K) -> String        (키 → Redis String 변환)
  ├── MapLoader<K,V>?
  ├── MapWriter<K,V>?
  └── LettuceCacheConfig

AbstractJdbcLettuceRepository<ID,E>
  ├── ExposedEntityMapLoader<ID,E>
  ├── ExposedEntityMapWriter<ID,E>
  └── LettuceLoadedMap<ID,E>
```

---

## Redis 자료구조

엔티티는 **String key** (`SET`/`GET`) 방식으로 저장한다.
- 키: `"$keyPrefix:${id}"` (String)
- 값: 직렬화된 ByteArray

Hash 방식(`HSET`)은 사용하지 않는다. 이유:
- TTL을 필드별로 지정할 수 없음
- `cache-lettuce-near`와 일관성 유지
- 단순한 Lettuce `StringCodec` + ByteArray 코덱 사용 가능

---

## 직렬화 / 코덱

### 기본 코덱

```kotlin
// CLAUDE.md infra 주의사항: LettuceBinaryCodecs 사용 금지 (NoClassDefFoundError)
// 대신 직접 LettuceBinaryCodec 사용
val codec: RedisCodec<String, V> = LettuceBinaryCodec(BinarySerializers.LZ4Fory)
```

`LettuceCacheConfig`에 코덱을 직접 받지 않고, `LettuceLoadedMap` 생성자에서 주입:

```kotlin
// LettuceBinaryCodec<V>는 RedisCodec<String, V>를 구현 — 값 타입 V를 투명하게 직렬화
class LettuceLoadedMap<K, V>(
    private val client: RedisClient,
    private val codec: RedisCodec<String, V>,          // LettuceBinaryCodec<V> 권장
    private val keyPrefix: String = "cache",            // Redis 키 prefix
    private val keySerializer: (K) -> String,           // K → Redis String 키 ("$keyPrefix:${keySerializer(k)}")
    private val loader: MapLoader<K, V>? = null,
    private val writer: MapWriter<K, V>? = null,
    private val config: LettuceCacheConfig = LettuceCacheConfig.READ_WRITE_THROUGH,
) : Closeable {
    private val connection: StatefulRedisConnection<String, V> =
        client.connect(codec)
    // ...
}
```

---

## 핵심 인터페이스

### MapLoader / MapWriter / WriteMode

```kotlin
interface MapLoader<K, V> {
    fun load(key: K): V?
    fun loadAllKeys(): Iterable<K>
}

interface MapWriter<K, V> {
    fun write(map: Map<K, V>)
    fun delete(keys: Collection<K>)
}

enum class WriteMode {
    NONE,           // Redis 전용 (write-back 없음)
    WRITE_THROUGH,  // Redis + DB 동시 쓰기
    WRITE_BEHIND,   // Redis 즉시 + DB 비동기
}
```

### LettuceCacheConfig

```kotlin
data class LettuceCacheConfig(
    val writeMode: WriteMode = WriteMode.WRITE_THROUGH,
    val writeBehindBatchSize: Int = 50,
    val writeBehindDelay: Duration = Duration.ofMillis(1000),
    val writeBehindQueueCapacity: Int = 10_000,       // 큐 한계 초과 시 예외
    val writeBehindShutdownTimeout: Duration = Duration.ofSeconds(10), // 종료 시 drain 대기
    val writeRetryAttempts: Int = 3,
    val writeRetryInterval: Duration = Duration.ofMillis(100),
    val ttl: Duration = Duration.ofMinutes(30),
) {
    companion object {
        // READ_ONLY: writer 주입 없이 loader만 사용 (WriteMode.NONE)
        val READ_ONLY = LettuceCacheConfig(writeMode = WriteMode.NONE)
        // READ_WRITE_THROUGH: loader + writer 모두 사용
        val READ_WRITE_THROUGH = LettuceCacheConfig(writeMode = WriteMode.WRITE_THROUGH)
        // WRITE_BEHIND: 비동기 flush
        val WRITE_BEHIND = LettuceCacheConfig(writeMode = WriteMode.WRITE_BEHIND)
    }
}
```

---

## Exposed 연동 계층

### ExposedEntityMapLoader

```kotlin
abstract class EntityMapLoader<ID, E> : MapLoader<ID, E> {
    override fun load(key: ID): E? = transaction { loadById(key) }
    override fun loadAllKeys(): Iterable<ID> = transaction { loadAllIds() }

    abstract fun loadById(id: ID): E?
    abstract fun loadAllIds(): Iterable<ID>
}

class ExposedEntityMapLoader<ID, E>(
    private val table: IdTable<ID>,
    private val toEntity: (ResultRow) -> E,
    private val batchSize: Int = 1000,
) : EntityMapLoader<ID, E>() {

    override fun loadById(id: ID): E? =
        table.selectAll().where { table.id eq id }.singleOrNull()?.let(toEntity)

    // limit/offset 반복 (성능 주의: 대용량 테이블에서 O(N) 스캔)
    override fun loadAllIds(): Iterable<ID> = buildList {
        var offset = 0L
        while (true) {
            val batch = table.select(table.id)
                .limit(batchSize).offset(offset)
                .map { it[table.id].value }
            addAll(batch)
            if (batch.size < batchSize) break
            offset += batchSize
        }
    }
}
```

### ExposedEntityMapWriter

```kotlin
abstract class EntityMapWriter<ID, E>(
    private val retryConfig: RetryConfig,   // bluetape4k-resilience4j
) : MapWriter<ID, E> {
    private val retry = Retry.of("exposed-lettuce-writer", retryConfig)

    override fun write(map: Map<ID, E>) =
        Retry.decorateRunnable(retry) { transaction { writeEntities(map) } }.run()

    override fun delete(keys: Collection<ID>) =
        Retry.decorateRunnable(retry) { transaction { deleteEntities(keys) } }.run()

    abstract fun writeEntities(map: Map<ID, E>)
    abstract fun deleteEntities(keys: Collection<ID>)
}

class ExposedEntityMapWriter<ID, E>(
    private val table: IdTable<ID>,
    private val writeMode: WriteMode,
    private val updateEntity: (UpdateStatement, E) -> Unit,
    private val insertEntity: (InsertStatement<*>, E) -> Unit,
    private val chunkSize: Int = 1000,
    retryConfig: RetryConfig,
) : EntityMapWriter<ID, E>(retryConfig) {

    override fun writeEntities(map: Map<ID, E>) {
        when (writeMode) {
            WriteMode.WRITE_THROUGH -> {
                // 기존 ID → UPDATE, 신규 ID → batchInsert
                val existingIds = table.select(table.id)
                    .where { table.id inList map.keys }.map { it[table.id].value }.toSet()
                existingIds.forEach { id ->
                    table.update({ table.id eq id }) { updateEntity(it, map[id]!!) }
                }
                (map.keys - existingIds).chunked(chunkSize) { chunk ->
                    table.batchInsert(chunk) { id -> insertEntity(this, map[id]!!) }
                }
            }
            WriteMode.WRITE_BEHIND -> {
                // WRITE_THROUGH와 동일하게 UPDATE/INSERT 분기 (duplicate key 방지)
                val existingIds = table.select(table.id)
                    .where { table.id inList map.keys }.map { it[table.id].value }.toSet()
                existingIds.forEach { id ->
                    table.update({ table.id eq id }) { updateEntity(it, map[id]!!) }
                }
                (map.keys - existingIds).chunked(chunkSize) { chunk ->
                    table.batchInsert(chunk) { id -> insertEntity(this, map[id]!!) }
                }
            }
            WriteMode.NONE -> { /* no-op */ }
        }
    }

    override fun deleteEntities(keys: Collection<ID>) {
        table.deleteWhere { table.id inList keys }
    }
}
```

---

## 데이터 흐름

### Read-through

```
findById(id)
  ├─ Redis GET "$prefix:$id" → HIT  → 역직렬화 → 반환
  └─ Redis MISS
       → MapLoader.load(id) → DB 조회
       → 결과 직렬화 → Redis SETEX (TTL 적용)
       → 반환 (null이면 Redis 저장 안 함)
```

### Write-through

```
save(id, entity)
  → 직렬화 → Redis SETEX
  → ExposedEntityMapWriter.write(mapOf(id to entity))
       → transaction { UPDATE or INSERT } (Resilience4j Retry 포함)
  ※ Redis 성공 + DB 실패(재시도 초과) 시: 예외 throw, Redis 보상 없음 (best-effort)
```

### Write-behind

```
save(id, entity)
  → 직렬화 → Redis SETEX
  → LinkedBlockingDeque(capacity=writeBehindQueueCapacity) 적재
       → 큐 포화 시: IllegalStateException (drop 안 함, caller 실패)
  → ScheduledExecutorService (단일 스레드, writeBehindDelay 주기)
       → 큐에서 writeBehindBatchSize 단위 poll
       → ExposedEntityMapWriter.write(batch) (Retry 포함)
       → 최종 실패 시: Dead Letter → Redis List("$prefix:dead-letter") 기록 + 로그
```

### Write-behind 종료 (Closeable)

```
close()
  → ScheduledExecutorService.shutdown()
  → 큐 잔여분 drain → ExposedEntityMapWriter.write() (writeBehindShutdownTimeout 내)
  → 타임아웃 초과 시: 잔여 항목 Dead Letter 기록 + 경고 로그
  → connection.close()
```

---

## Repository 기반 클래스

```kotlin
// AbstractJdbcRedissonRepository와 동일한 4개 추상 메서드 패턴
abstract class AbstractJdbcLettuceRepository<ID : Any, E : Any>(
    private val client: RedisClient,
    private val config: LettuceCacheConfig = LettuceCacheConfig.READ_WRITE_THROUGH,
) : Closeable {

    // 구현 필수 (4개)
    abstract val table: IdTable<ID>
    abstract fun ResultRow.toEntity(): E
    abstract fun UpdateStatement.updateEntity(entity: E)
    abstract fun InsertStatement<*>.insertEntity(entity: E)

    // 키 직렬화: 기본 toString(), 필요시 override
    open fun serializeKey(id: ID): String = id.toString()

    // 코덱: 기본 LZ4Fory, 필요시 override (LettuceBinaryCodec<E>는 RedisCodec<String, E> 구현)
    open val codec: RedisCodec<String, E> = LettuceBinaryCodec(BinarySerializers.LZ4Fory)

    private val retryConfig = RetryConfig.custom<Any>()
        .maxAttempts(config.writeRetryAttempts)
        .waitDuration(config.writeRetryInterval)
        .build()

    protected val cache: LettuceLoadedMap<ID, E> = LettuceLoadedMap(
        client = client,
        codec = codec,
        keySerializer = ::serializeKey,
        loader = ExposedEntityMapLoader(table) { it.toEntity() },
        writer = ExposedEntityMapWriter(
            table, config.writeMode,
            updateEntity = { stmt, e -> stmt.updateEntity(e) },
            insertEntity = { stmt, e -> stmt.insertEntity(e) },
            retryConfig = retryConfig,
        ),
        config = config,
    )

    // 공통 CRUD
    fun findById(id: ID): E? = cache[id]
    fun findAll(ids: Collection<ID>): Map<ID, E> = cache.getAll(ids.toSet())
    fun save(id: ID, entity: E) { cache[id] = entity }
    fun delete(id: ID) { cache.delete(id) }
    fun deleteAll(ids: Collection<ID>) { cache.deleteAll(ids) }

    override fun close() = cache.close()
}
```

### 사용 예시

```kotlin
class CustomerRepository(client: RedisClient)
    : AbstractJdbcLettuceRepository<Long, Customer>(client, LettuceCacheConfig.READ_WRITE_THROUGH) {

    override val table = CustomerTable

    override fun ResultRow.toEntity() =
        Customer(this[CustomerTable.id].value, this[CustomerTable.name])

    override fun UpdateStatement.updateEntity(e: Customer) {
        this[CustomerTable.name] = e.name
    }

    override fun InsertStatement<*>.insertEntity(e: Customer) {
        this[CustomerTable.name] = e.name
    }
}
```

---

## 에러 처리

| 상황 | 처리 |
|------|------|
| Redis 연결 실패 시 `get()` | loader fallback → DB 직접 조회 |
| loader null 반환 | Redis 저장 안 함, null 반환 |
| Write-through: DB 실패 (재시도 초과) | 예외 throw (Redis 보상 없음 — best-effort) |
| Write-behind: flush 실패 (재시도 초과) | Dead Letter List 기록 + 경고 로그 |
| Write-behind: 큐 포화 | `IllegalStateException` throw (drop 없음) |
| Write-behind: 종료 시 drain 타임아웃 | 잔여 항목 Dead Letter 기록 + 경고 로그 |

---

## 테스트 전략

- **단위**: MapLoader/MapWriter mock으로 LettuceLoadedMap 동작 검증
- **통합**: Testcontainers Redis + H2 (`Libs.h2_v2`)
  - Read-through: DB에만 데이터 → `findById()` → Redis 저장 확인
  - Write-through: `save()` → Redis + DB 동시 확인
  - Write-behind: `save()` → Redis 즉시 확인 → `writeBehindDelay` 대기 → DB 확인
  - Write-behind 종료: `close()` → 큐 drain 후 DB 반영 확인
  - Write-through 실패 보상: DB 오류 주입 → 예외 확인 → Redis 상태 확인

---

## 의존성

```kotlin
dependencies {
    api(Libs.lettuce_core)                 // cache-lettuce-near와 동일 패턴
    api(Libs.bluetape4k_exposed_jdbc)
    api(Libs.bluetape4k_resilience4j)
    api(Libs.bluetape4k_io)                // BinarySerializers (LZ4Fory 등)
    api(Libs.fory_kotlin)                  // 직렬화 (optional → 명시 필요)
    api(Libs.lz4_java)                     // LZ4 압축 (optional → 명시 필요)

    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.bluetape4k_redis)  // RedisServer.Standalone
    testRuntimeOnly(Libs.h2_v2)
}
```

---

## 향후 확장

- `data/exposed-r2dbc-lettuce/` — R2DBC + Coroutines 버전 (동일 패턴, `suspend` 함수)
  - `R2dbcExposedEntityMapLoader` / `R2dbcExposedEntityMapWriter`
