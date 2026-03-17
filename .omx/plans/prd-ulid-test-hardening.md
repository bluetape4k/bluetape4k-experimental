# PRD: utils/ulid Test Hardening

## Task Statement
- `utils/ulid` 모듈의 테스트를 보강해 생성된 ULID의 uniqueness 및 monotonic ordering을 검증한다.
- `~/work/bluetape4k/bluetape4k-projects/utils/idgenerators` 모듈의 동시성 테스트 패턴을 참고해 멀티스레드, Virtual Threads, Coroutines 환경에서 concurrency 문제가 없음을 확인한다.

## Current Facts
- `ULIDFactoryTest`는 유효성, 파싱, 바이트 변환 위주로만 검증하고 있으며, 다건 생성 시 uniqueness/ordering 검증은 없다.
  - 근거: `utils/ulid/src/test/kotlin/io/bluetape4k/ulid/ULIDFactoryTest.kt:29-229`
- `ULIDStatefulMonotonicTest`는 단일 호출 흐름에서 monotonic increment만 확인하고 있고, 공유 generator에 대한 동시성 검증은 없다.
  - 근거: `utils/ulid/src/test/kotlin/io/bluetape4k/ulid/ULIDStatefulMonotonicTest.kt:16-77`
- `ULIDStatefulMonotonic` 구현은 `atomic` 기반 CAS loop로 이전 값을 갱신하므로, 공유 인스턴스에 대한 concurrent access가 핵심 검증 지점이다.
  - 근거: `utils/ulid/src/main/kotlin/io/bluetape4k/ulid/internal/ULIDStatefulMonotonic.kt:14-32`
- 일반 factory는 timestamp + random bytes 기반 ULID를 생성하므로 다건 생성에서는 “중복 없음”이 핵심이고, 정렬 증가 보장은 stateful monotonic 계열에서만 기대하는 것이 안전하다.
  - 근거: `utils/ulid/src/main/kotlin/io/bluetape4k/ulid/internal/ULIDFactory.kt:12-30`
- 참고 패턴으로 `idgenerators`는
  - time-based UUID에서 다건 생성 후 `distinct()` 및 정렬 일치 여부를 본다.
    - 근거: `~/work/bluetape4k/bluetape4k-projects/utils/idgenerators/src/test/kotlin/io/bluetape4k/idgenerators/uuid/timebased/AbstractTimebasedUuidTest.kt:50-114`
  - flake/ksuid에서 `MultithreadingTester`, `StructuredTaskScopeTester`, `SuspendedJobTester`로 concurrent uniqueness를 검증한다.
    - 근거: `~/work/bluetape4k/bluetape4k-projects/utils/idgenerators/src/test/kotlin/io/bluetape4k/idgenerators/flake/FlakeTest.kt:87-142`
    - 근거: `~/work/bluetape4k/bluetape4k-projects/utils/idgenerators/src/test/kotlin/io/bluetape4k/idgenerators/ksuid/KsuidTest.kt:45-89`

## Desired Outcome
- 일반 ULID 생성 테스트는 다건 생성 결과가 모두 유효하고 중복되지 않음을 보여준다.
- stateful monotonic ULID 생성 테스트는 동일 timestamp와 concurrent access에서도 결과가 중복되지 않고 전체 ordering이 깨지지 않음을 보여준다.
- Java 21+/25 환경에서는 Virtual Threads 검증이 포함되고, 그 외 런타임에서는 조건부 skip 처리된다.

## Scope
- In scope:
  - `utils/ulid/src/test/kotlin/io/bluetape4k/ulid/ULIDFactoryTest.kt`
  - `utils/ulid/src/test/kotlin/io/bluetape4k/ulid/ULIDStatefulMonotonicTest.kt`
  - 필요 시 `utils/ulid/build.gradle.kts`에 test dependency 정리
- Out of scope:
  - `utils/ulid` production 코드 수정
  - 다른 모듈의 테스트 수정
  - 저장소 전체 빌드

## Acceptance Criteria
- `ULIDFactoryTest`에 다음 검증이 추가된다.
  - 다건 `randomULID()` 생성 결과가 모두 26자 ULID 형식을 만족한다.
  - 다건 `randomULID()` 또는 `nextULID()` 생성 결과가 중복 없이 생성된다.
  - deterministic timestamp를 주는 `nextULID(timestamp)` 다건 생성은 정렬 가능한 ULID 문자열/값으로 검증된다.
- `ULIDStatefulMonotonicTest`에 다음 검증이 추가된다.
  - 동일 generator를 공유하는 다건 단일 스레드 생성 결과가 `sorted() == original` 및 `distinct().size == size`를 만족한다.
  - `MultithreadingTester` 기반 concurrent generation에서 duplicate가 발생하지 않는다.
  - `StructuredTaskScopeTester` 기반 Virtual Threads concurrent generation에서 duplicate가 발생하지 않는다.
  - `SuspendedJobTester` 또는 coroutine `async/awaitAll` 기반 concurrent generation에서 duplicate가 발생하지 않는다.
  - 동일 timestamp를 강제한 concurrent generation 결과가 전체적으로 monotonic ordering을 만족한다.
- 최종 검증은 모듈 단위 `./gradlew :ulid:test` 또는 동등한 대상 모듈 테스트 명령으로 수행한다.

## Implementation Steps
1. `ULIDFactoryTest`에 대량 생성 헬퍼 또는 반복 테스트를 추가한다.
   - `List(count) { ... }` 패턴으로 ULID 문자열/값을 모으고 `distinct().size` 및 필요 시 `sorted()` 비교를 수행한다.
   - 기존 `assertValidParts()`를 재사용해 포맷 검증을 함께 유지한다.
2. `ULIDStatefulMonotonicTest`에 shared generator 기반 ordered batch 테스트를 추가한다.
   - `MockRandom(0)` + 고정 timestamp를 사용해 랜덤 요인을 줄이고 monotonic 증가 검증을 안정화한다.
3. `ULIDStatefulMonotonicTest`에 concurrency 테스트 3종을 추가한다.
   - 멀티스레드: `MultithreadingTester`
   - Virtual Threads: `StructuredTaskScopeTester` + `@EnabledOnJre(JRE.JAVA_21, JRE.JAVA_25)`
   - 코루틴: `SuspendedJobTester` 또는 `runSuspendDefault` 내부 `async/awaitAll`
4. concurrent 검증에서 결과 수집은 `ConcurrentHashMap<ULID, Int>` 또는 thread-safe list를 사용한다.
   - duplicate 검증은 `putIfAbsent(...).shouldBeNull()` 패턴을 우선 적용한다.
5. 필요 시 `utils/ulid/build.gradle.kts`에서 `bluetape4k_junit5`가 concurrency tester를 이미 제공하는지 확인하고, 추가 dependency 없이는 사용 가능하도록 유지한다.

## Risks And Mitigations
- Risk: 일반 factory 생성 결과에 strict monotonic ordering을 기대하면 flaky test가 될 수 있다.
  - Mitigation: 일반 factory는 uniqueness/format 중심으로만 검증하고 ordering은 stateful monotonic에 한정한다.
- Risk: 동시성 테스트가 wall-clock timestamp에 의존하면 희소하게 순서가 섞일 수 있다.
  - Mitigation: monotonic ordering 검증은 고정 timestamp + `MockRandom(0)` 조합에서 수행한다.
- Risk: Virtual Threads 테스트는 JDK 21 미만에서 실패할 수 있다.
  - Mitigation: `@EnabledOnJre(JRE.JAVA_21, JRE.JAVA_25)`로 guard 한다.
- Risk: concurrent ordering을 리스트 append 순서로 검증하면 스케줄링 차이 때문에 false negative가 날 수 있다.
  - Mitigation: concurrent path에서는 우선 uniqueness를 검증하고, ordering은 동기 수집 가능한 controlled batch 테스트로 분리한다.

## Verification Steps
- 대상 테스트 파일 정적 점검
  - import 누락, JRE guard, assertion API 일관성 확인
- 모듈 테스트 실행
  - `./gradlew :ulid:test`
- 실패 시 우선 확인
  - ordering 기대치가 일반 factory에 잘못 적용되었는지
  - fixed timestamp + `MockRandom` 설정이 빠졌는지
  - concurrent collection이 thread-safe 한지
