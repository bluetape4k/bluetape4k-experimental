# redis-lettuce

## 개요

Lettuce 클라이언트 기반으로 Redis 확률적 자료구조(HyperLogLog, BloomFilter, CuckooFilter)를
순수 Kotlin으로 구현한 모듈입니다. Redisson Pro가 제공하는 고급 필터 기능을 의존성 없이 사용할 수 있습니다.

## 주요 기능

### HyperLogLog

대용량 집합의 **근사 카디널리티(중복 제거 개수)** 를 O(1) 메모리로 추정합니다 (오차율 ~0.81%).

| 클래스 | 설명 |
|--------|------|
| `LettuceHyperLogLog<V>` | 동기 API |
| `LettuceSuspendHyperLogLog<V>` | Coroutines 비동기 API |

```kotlin
val hll = LettuceHyperLogLog(connection, "unique-visitors")
hll.add("user:1", "user:2", "user:3")
val count = hll.count()             // ~3

// 여러 HLL 합산 카디널리티
val count2 = hll.countWith(otherHll)
```

### BloomFilter

**존재 여부만 확인**하는 확률적 필터. 삭제 불가, false positive 발생 가능.

| 클래스 | 설명 |
|--------|------|
| `LettuceBloomFilter` | 동기 API |
| `LettuceSuspendBloomFilter` | Coroutines 비동기 API |

```kotlin
val bf = LettuceBloomFilter(
    connection = connection,
    filterName = "email-blacklist",
    options = BloomFilterOptions(expectedInsertions = 1_000_000L, falseProbability = 0.01),
)
bf.tryInit()              // Redis에 필터 초기화 (이미 존재하면 false)

bf.add("spam@evil.com")
bf.contains("spam@evil.com")  // true
bf.contains("good@user.com")  // false (with ~1% chance of false positive)
```

### CuckooFilter

**삭제를 지원**하는 확률적 필터. BloomFilter 대비 삭제 가능, 삽입 실패 시 undo-log 롤백으로 기존 원소 보호.

| 클래스 | 설명 |
|--------|------|
| `LettuceCuckooFilter` | 동기 API |
| `LettuceSuspendCuckooFilter` | Coroutines 비동기 API |

```kotlin
val cf = LettuceCuckooFilter(
    connection = connection,
    filterName = "dedup-ids",
    options = CuckooFilterOptions(capacity = 100_000L, bucketSize = 4),
)
cf.tryInit()

cf.insert("order:123")          // true
cf.contains("order:123")        // true
cf.delete("order:123")          // true
cf.contains("order:123")        // false
```

## 내부 구조

```
filter/
├── Murmur3.kt                  # 순수 Kotlin MurmurHash3 x64 128-bit (외부 의존성 없음)
├── BloomFilterOptions.kt       # expectedInsertions, falseProbability 설정
├── CuckooFilterOptions.kt      # capacity, bucketSize(1~8), maxIterations 설정
├── CuckooFilterScripts.kt      # Lua 스크립트 공유 (INSERT/CONTAINS/DELETE)
├── LettuceBloomFilter.kt
├── LettuceSuspendBloomFilter.kt
├── LettuceCuckooFilter.kt
└── LettuceSuspendCuckooFilter.kt

hll/
├── LettuceHyperLogLog.kt
└── LettuceSuspendHyperLogLog.kt
```

### 설계 포인트

- **Murmur3**: Guava 등 외부 라이브러리 불필요. 순수 Kotlin x64 128-bit 구현.
- **BloomFilter `tryInit()`**: 동일 키로 다른 파라미터(m/k) 재초기화 시 `IllegalStateException` 발생.
- **CuckooFilter undo-log**: 삽입 중 kick-out 실패 시 변경된 버킷을 원상 복구하여 기존 원소 유실 방지.
- **CuckooFilterScripts**: Sync/Suspend 클래스가 Lua 스크립트를 공유하는 internal object.

## 의존성

```kotlin
// build.gradle.kts
dependencies {
    testImplementation(project(":redis-lettuce"))
}
```

## 테스트 실행

Testcontainers를 사용하므로 Docker가 필요합니다.

```bash
./gradlew :redis-lettuce:test
```
