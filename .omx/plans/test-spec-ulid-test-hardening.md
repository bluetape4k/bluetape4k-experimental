# Test Spec: utils/ulid Test Hardening

## Verification Rules
- 검증은 `utils/ulid` 모듈 범위로 제한한다.
- concurrency 검증은 기존 저장소 패턴과 동일하게 `bluetape4k-junit5` tester를 우선 사용한다.
- Virtual Threads 시나리오는 JRE 21/25에서만 실행하고, 나머지 런타임에서는 skip 되어야 한다.

## Planned Test Additions

### 1. Factory Bulk Uniqueness
- Target: `utils/ulid/src/test/kotlin/io/bluetape4k/ulid/ULIDFactoryTest.kt`
- Add:
  - `randomULID()` 다건 생성 후 모든 결과에 `assertValidParts()` 적용
  - `randomULID()` 다건 생성 결과 `distinct().size == count`
  - `nextULID(timestamp)` 또는 `nextULID()` 다건 생성 결과 `distinct().size == count`
- Purpose:
  - 현재 개별 생성 위주 테스트를 다건 uniqueness 검증으로 확장한다.

### 2. Stateful Monotonic Ordered Batch
- Target: `utils/ulid/src/test/kotlin/io/bluetape4k/ulid/ULIDStatefulMonotonicTest.kt`
- Add:
  - shared generator + `MockRandom(0)` + 고정 timestamp로 여러 건 생성
  - `ulids.sorted() shouldBeEqualTo ulids`
  - `ulids.distinct().size shouldBeEqualTo ulids.size`
- Purpose:
  - 현재 2건 또는 10건 수준의 단일 흐름 검증을 더 명시적인 uniqueness + monotonic ordering 검증으로 강화한다.

### 3. Multi-thread Concurrency
- Target: `utils/ulid/src/test/kotlin/io/bluetape4k/ulid/ULIDStatefulMonotonicTest.kt`
- Add:
  - `MultithreadingTester`
  - shared `ULID.statefulMonotonic(...)`
  - `ConcurrentHashMap<ULID, Int>`에 `putIfAbsent(...).shouldBeNull()`
- Purpose:
  - CAS loop 기반 상태 공유 구현이 thread contention에서도 duplicate 없이 동작하는지 확인한다.

### 4. Virtual Threads Concurrency
- Target: `utils/ulid/src/test/kotlin/io/bluetape4k/ulid/ULIDStatefulMonotonicTest.kt`
- Add:
  - `@EnabledOnJre(JRE.JAVA_21, JRE.JAVA_25)`
  - `StructuredTaskScopeTester`
  - shared generator + duplicate detection map
- Purpose:
  - virtual-thread scheduling 하에서도 shared monotonic generator가 안전한지 확인한다.

### 5. Coroutine Concurrency
- Target: `utils/ulid/src/test/kotlin/io/bluetape4k/ulid/ULIDStatefulMonotonicTest.kt`
- Add:
  - `runSuspendDefault` + `SuspendedJobTester` 또는 `async/awaitAll`
  - shared generator + duplicate detection
  - 필요 시 same timestamp를 강제해 monotonic generator 경로를 집중 검증
- Purpose:
  - coroutine dispatcher 경계에서 race 없이 monotonic ULID를 발급하는지 확인한다.

## Pass Criteria
- 새로 추가한 모든 테스트가 반복 실행에서도 안정적으로 통과한다.
- concurrency 테스트에서 duplicate가 한 건도 발생하지 않는다.
- monotonic ordered batch 테스트에서 정렬 결과와 생성 순서가 일치한다.
- `./gradlew :ulid:test`가 통과한다.

## Command Plan
- 우선 실행:
  - `./gradlew :ulid:test`
- 필요 시 국소 재실행:
  - `./gradlew :ulid:test --tests io.bluetape4k.ulid.ULIDFactoryTest`
  - `./gradlew :ulid:test --tests io.bluetape4k.ulid.ULIDStatefulMonotonicTest`
