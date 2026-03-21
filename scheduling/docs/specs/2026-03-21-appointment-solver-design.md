# Appointment Solver 설계 — Timefold Solver 기반 자동 스케줄링 최적화

> **Date**: 2026-03-21
> **Module**: `scheduling/appointment-solver`
> **Status**: Draft
> **Depends on**: `appointment-core` (모델, 리포지토리, 서비스)

---

## 1. 프로젝트 컨텍스트 요약

### 1.1 현재 도메인 모델

| Record | 핵심 필드 | Solver 관련성 |
|--------|----------|--------------|
| `AppointmentRecord` | clinicId, doctorId, treatmentTypeId, equipmentId, appointmentDate, startTime, endTime, status | **Planning Entity** — 날짜/시간/의사를 결정변수로 최적화 |
| `ClinicRecord` | slotDurationMinutes, maxConcurrentPatients, openOnHolidays | Problem Fact — 전역 제약 |
| `DoctorRecord` | clinicId, providerType, maxConcurrentPatients | Problem Fact — 의사별 제약 |
| `TreatmentTypeRecord` | defaultDurationMinutes, requiredProviderType, requiresEquipment, maxConcurrentPatients | Problem Fact — 진료 유형 제약 |
| `OperatingHoursRecord` | dayOfWeek, openTime, closeTime | Problem Fact — 영업시간 |
| `DoctorScheduleRecord` | dayOfWeek, startTime, endTime | Problem Fact — 의사 근무시간 |
| `DoctorAbsenceRecord` | absenceDate, startTime, endTime | Problem Fact — 의사 부재 |
| `BreakTimeRecord` | dayOfWeek, startTime, endTime | Problem Fact — 요일별 휴식 |
| `ClinicDefaultBreakTimeRecord` | startTime, endTime | Problem Fact — 기본 휴식 |
| `ClinicClosureRecord` | closureDate, isFullDay, startTime, endTime | Problem Fact — 임시휴진 |
| `EquipmentRecord` | usageDurationMinutes, quantity | Problem Fact — 장비 가용성 |
| `TreatmentEquipmentRecord` | treatmentTypeId, equipmentId | Problem Fact — 진료-장비 매핑 |
| `HolidayRecord` | holidayDate, recurring | Problem Fact — 공휴일 |

### 1.2 기존 서비스와의 관계

- **`SlotCalculationService`**: 단일 (doctor, date, treatment) 조합에 대해 가용 슬롯을 순차 계산. Greedy 방식이라 전역 최적화 불가.
- **`ClosureRescheduleService`**: 임시휴진 시 영향받는 예약을 재배정. 현재는 첫 번째 가용 슬롯을 우선순위로 사용하며, 전역 최적 재배치는 하지 않음.
- **Solver의 역할**: 복수의 예약을 동시에 고려하여 전역 최적 배치를 수행. 기존 서비스는 "단건 조회"용으로 유지하고, Solver는 "배치 최적화"용으로 분리.

---

## 2. 설계 질문 & 결정사항

### Q1. Planning Variable 범위를 어떻게 정의할 것인가?

**결정**: `doctorId`, `appointmentDate`, `startTime` 세 가지를 Planning Variable로 사용.

- `doctorId`: 같은 clinicId 내의 의사 중 requiredProviderType이 일치하는 의사만 후보
- `appointmentDate`: 지정된 날짜 범위 (예: 향후 7~30일)
- `startTime`: clinic.slotDurationMinutes 간격으로 이산화된 시간 목록

`endTime`은 `startTime + treatmentDuration`으로 자동 계산 (Shadow Variable).

### Q2. Pinned(고정) 예약은 어떻게 처리할 것인가?

**결정**: `@PlanningPin` 어노테이션 사용.
- `status == CONFIRMED` 또는 `CHECKED_IN` 또는 `IN_PROGRESS` 또는 `COMPLETED`인 예약은 고정
- `status == REQUESTED` 또는 `PENDING_RESCHEDULE`인 예약만 Solver가 이동 가능

### Q3. 슬롯 이산화 vs 연속 시간?

**결정**: 이산 슬롯 방식 채택.
- `startTime`의 ValueRange를 `clinic.slotDurationMinutes` 간격의 `LocalTime` 리스트로 제한
- 연속 시간 모델은 탐색 공간이 너무 넓고, 실제 병원 운영도 슬롯 단위

### Q4. 멀티 클리닉 지원?

**결정**: SolverConfig 단위는 단일 클리닉. 멀티 클리닉은 클리닉별 독립 Solver 인스턴스.
- 의사가 복수 클리닉에 소속된 케이스는 현재 모델에서 미지원 (doctor.clinicId가 단일)

### Q5. SlotCalculationService와의 역할 분리?

**결정**: 공존 전략.
- `SlotCalculationService`: 환자 대면 API에서 "이 의사, 이 날짜에 빈 슬롯 보여줘" 용도 (실시간, 단건)
- `AppointmentSolver`: 관리자 배치 최적화 (대량 예약 재배치, 휴진 재스케줄, 일일 최적화)
- Solver 내부에서 SlotCalculationService의 로직을 재사용하지 않음 — Constraint Stream이 동일 로직을 선언적으로 표현

---

## 3. 접근법 비교

### 접근법 A: Lightweight Constraint Streams (권장)

Timefold Solver의 Constraint Streams API를 사용하여 순수 Kotlin으로 제약을 선언.

```
AppointmentPlanning (@PlanningEntity)
  ├── doctorId      (@PlanningVariable)
  ├── appointmentDate (@PlanningVariable)
  ├── startTime     (@PlanningVariable)
  └── endTime       (Shadow Variable)
```

**장점**:
- Kotlin DSL과 자연스럽게 통합
- 제약 조건 추가/수정이 용이 (Constraint Stream은 함수 합성)
- 단위 테스트가 `ConstraintVerifier`로 간편
- Spring Boot 없이도 동작 (현재 appointment-core가 Spring Bean이 아닌 일반 클래스)

**단점**:
- Planning Variable 3개 → 탐색 공간이 큼 (Move 전략 튜닝 필요)
- Shadow Variable 계산이 매 Move마다 실행

**적합 시나리오**: 현재 프로젝트 구조와 가장 일치. Spring 의존성 없이 순수 Timefold Core만 사용.

### 접근법 B: Chained Planning (시간축 체인)

의사별로 시간 순서의 Chained Planning Variable을 사용. 각 의사의 하루 스케줄을 linked list로 모델링.

```
DoctorTimeSlot (Anchor)
  └── Appointment₁ → Appointment₂ → Appointment₃ (chain)
```

**장점**:
- 연속 예약 간 간격 최소화를 자연스럽게 표현
- Vehicle Routing Problem (VRP) 패턴과 유사하여 Timefold의 최적화된 Move가 적용됨

**단점**:
- 모델 복잡도 증가 (Anchor, Shadow Variable for cumulative time 필요)
- 다중 날짜 최적화 시 복잡해짐
- 현재 도메인 모델과의 괴리가 큼

**적합 시나리오**: 단일 의사의 하루 스케줄 최적화에 특화. 멀티 데이, 멀티 닥터 시 과도한 복잡성.

### 접근법 C: Two-Phase 접근 (할당 + 시간 최적화)

Phase 1에서 의사 할당만 결정하고, Phase 2에서 시간 슬롯을 최적화.

```
Phase 1: doctorId 할당 (Construction Heuristic)
Phase 2: appointmentDate + startTime 최적화 (Local Search)
```

**장점**:
- 탐색 공간 분리로 각 Phase의 수렴 속도 향상
- 의사 배정과 시간 배정의 관심사 분리

**단점**:
- Phase 1의 결과가 Phase 2를 제약 → 전역 최적 해에 도달하기 어려움
- Timefold의 Phase 분리 설정이 복잡

**적합 시나리오**: 의사 수가 많고 예약 수가 매우 많은 대형 병원.

### 권장: 접근법 A (Lightweight Constraint Streams)

현재 프로젝트의 규모(단일 클리닉, 수십~수백 예약)와 모듈 구조(Spring 비의존 일반 클래스)에 가장 적합.

---

## 4. 추천 설계안

### 4.1 패키지 구조

```
appointment-solver/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/kotlin/io/bluetape4k/scheduling/appointment/solver/
    │   ├── domain/
    │   │   ├── AppointmentPlanning.kt          # @PlanningEntity
    │   │   ├── ScheduleSolution.kt             # @PlanningSolution
    │   │   ├── DoctorFact.kt                   # Problem Fact (의사)
    │   │   ├── ClinicFact.kt                   # Problem Fact (병원)
    │   │   ├── TreatmentFact.kt                # Problem Fact (진료 유형)
    │   │   ├── EquipmentFact.kt                # Problem Fact (장비)
    │   │   ├── TimeSlot.kt                     # 이산 시간 슬롯
    │   │   └── PlanningDateRange.kt            # 스케줄링 대상 날짜 범위
    │   ├── constraint/
    │   │   ├── AppointmentConstraintProvider.kt # ConstraintProvider 구현
    │   │   ├── HardConstraints.kt              # Hard Constraint 정의
    │   │   └── SoftConstraints.kt              # Soft Constraint 정의
    │   ├── converter/
    │   │   └── SolutionConverter.kt            # core Record ↔ solver domain 변환
    │   └── service/
    │       ├── SolverService.kt                # Solver 실행 진입점
    │       └── SolverConfig.kt                 # SolverFactory 설정
    └── test/kotlin/io/bluetape4k/scheduling/appointment/solver/
        ├── constraint/
        │   ├── HardConstraintTest.kt
        │   └── SoftConstraintTest.kt
        ├── domain/
        │   └── SolutionTest.kt
        └── service/
            └── SolverServiceTest.kt
```

### 4.2 Planning Entity: `AppointmentPlanning`

```kotlin
@PlanningEntity
class AppointmentPlanning(
    val id: Long,
    val clinicId: Long,
    val treatmentTypeId: Long,
    val equipmentId: Long?,
    val patientName: String,
    val durationMinutes: Int,
    val requiredProviderType: String,
    val requiresEquipment: Boolean,

    // --- Planning Variables ---
    @PlanningVariable(valueRangeProviderRefs = ["doctorRange"])
    var doctorId: Long? = null,

    @PlanningVariable(valueRangeProviderRefs = ["dateRange"])
    var appointmentDate: LocalDate? = null,

    @PlanningVariable(valueRangeProviderRefs = ["timeSlotRange"])
    var startTime: LocalTime? = null,

    // --- Derived ---
    val pinned: Boolean = false,  // @PlanningPin
) {
    val endTime: LocalTime?
        get() = startTime?.plusMinutes(durationMinutes.toLong())

    // Timefold requires no-arg constructor
    constructor() : this(0, 0, 0, null, "", 0, "", false)
}
```

> **Note**: `endTime`을 `@ShadowVariable`이 아닌 단순 getter로 구현. durationMinutes는 고정이므로 Shadow Variable의 리스너 오버헤드가 불필요.

### 4.3 Planning Solution: `ScheduleSolution`

```kotlin
@PlanningSolution
class ScheduleSolution(
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "doctorRange")
    val doctors: List<DoctorFact> = emptyList(),

    @ValueRangeProvider(id = "dateRange")
    val dateRange: CountableValueRange<LocalDate>,

    @ValueRangeProvider(id = "timeSlotRange")
    val timeSlots: List<LocalTime> = emptyList(),

    @PlanningEntityCollectionProperty
    val appointments: List<AppointmentPlanning> = emptyList(),

    // Problem Facts (제약 평가에 사용)
    @ProblemFactProperty
    val clinic: ClinicFact,

    @ProblemFactCollectionProperty
    val treatments: List<TreatmentFact> = emptyList(),

    @ProblemFactCollectionProperty
    val equipments: List<EquipmentFact> = emptyList(),

    @ProblemFactCollectionProperty
    val operatingHours: List<OperatingHoursRecord> = emptyList(),

    @ProblemFactCollectionProperty
    val doctorSchedules: List<DoctorScheduleRecord> = emptyList(),

    @ProblemFactCollectionProperty
    val doctorAbsences: List<DoctorAbsenceRecord> = emptyList(),

    @ProblemFactCollectionProperty
    val breakTimes: List<BreakTimeRecord> = emptyList(),

    @ProblemFactCollectionProperty
    val defaultBreakTimes: List<ClinicDefaultBreakTimeRecord> = emptyList(),

    @ProblemFactCollectionProperty
    val closures: List<ClinicClosureRecord> = emptyList(),

    @ProblemFactCollectionProperty
    val holidays: List<HolidayRecord> = emptyList(),

    @PlanningScore
    var score: HardSoftScore? = null,
)
```

### 4.4 Constraint Provider

#### Hard Constraints (위반 시 해가 무효)

| ID | 제약 | 설명 |
|----|------|------|
| H1 | `withinOperatingHours` | 예약 시간이 해당 요일의 영업시간 내에 있어야 함 |
| H2 | `withinDoctorSchedule` | 예약 시간이 의사의 근무 스케줄 내에 있어야 함 |
| H3 | `noDoctorAbsenceConflict` | 의사 부재 기간과 예약이 겹치지 않아야 함 |
| H4 | `noBreakTimeConflict` | 휴식시간과 예약이 겹치지 않아야 함 |
| H5 | `noClinicClosureConflict` | 임시휴진 기간과 예약이 겹치지 않아야 함 |
| H6 | `noHolidayConflict` | 공휴일에 예약 불가 (openOnHolidays=false인 경우) |
| H7 | `maxConcurrentPatientsPerDoctor` | 같은 의사의 동시 환자 수가 maxConcurrent 이하 |
| H8 | `equipmentAvailability` | 같은 시간에 장비 사용 수가 quantity 이하 |
| H9 | `providerTypeMatch` | 의사의 providerType이 진료의 requiredProviderType과 일치 |
| H10 | `doctorBelongsToClinic` | 할당된 의사가 해당 클리닉 소속 |

#### Soft Constraints (최적화 목표)

| ID | 제약 | Weight | 설명 |
|----|------|--------|------|
| S1 | `doctorLoadBalance` | MEDIUM | 의사 간 예약 수 분산 (표준편차 최소화) |
| S2 | `minimizeGaps` | LOW | 의사별 하루 스케줄의 빈 시간 간격 최소화 |
| S3 | `preferOriginalDoctor` | HIGH | 재스케줄 시 원래 의사 유지 선호 |
| S4 | `preferEarlySlot` | LOW | PENDING_RESCHEDULE 예약은 가능한 빠른 날짜/시간 선호 |
| S5 | `equipmentUtilization` | LOW | 장비 사용 시간대를 연속적으로 배치하여 효율성 향상 |
| S6 | `preferRequestedDate` | HIGH | 환자가 요청한 원래 날짜에 가까울수록 선호 |

#### Constraint Streams 예시 (H7 — 동시 환자 수 제한)

```kotlin
fun maxConcurrentPatientsPerDoctor(factory: ConstraintFactory): Constraint =
    factory.forEach(AppointmentPlanning::class.java)
        .join(
            AppointmentPlanning::class.java,
            Joiners.equal(AppointmentPlanning::doctorId),
            Joiners.equal(AppointmentPlanning::appointmentDate),
            Joiners.lessThan(AppointmentPlanning::id),  // 중복 쌍 방지
            Joiners.overlapping(
                AppointmentPlanning::startTime,
                AppointmentPlanning::endTime
            )
        )
        .groupBy(
            { a, _ -> a.doctorId },
            { a, _ -> a.appointmentDate },
            { a, _ -> a.startTime },
            ConstraintCollectors.count()
        )
        .filter { _, _, _, count -> count >= resolveMaxConcurrent(...) }
        .penalize(HardSoftScore.ONE_HARD)
        .asConstraint("H7: maxConcurrentPatientsPerDoctor")
```

### 4.5 SolutionConverter

`appointment-core`의 Record 클래스와 Solver의 Planning 도메인 간 변환을 담당.

```kotlin
object SolutionConverter {
    fun buildSolution(
        clinic: ClinicRecord,
        doctors: List<DoctorRecord>,
        appointments: List<AppointmentRecord>,      // REQUESTED, PENDING_RESCHEDULE만
        confirmedAppointments: List<AppointmentRecord>, // CONFIRMED 등 (pinned)
        treatments: List<TreatmentTypeRecord>,
        equipments: List<EquipmentRecord>,
        operatingHours: List<OperatingHoursRecord>,
        doctorSchedules: List<DoctorScheduleRecord>,
        doctorAbsences: List<DoctorAbsenceRecord>,
        breakTimes: List<BreakTimeRecord>,
        defaultBreakTimes: List<ClinicDefaultBreakTimeRecord>,
        closures: List<ClinicClosureRecord>,
        holidays: List<HolidayRecord>,
        dateRange: ClosedRange<LocalDate>,
    ): ScheduleSolution

    fun extractResults(solution: ScheduleSolution): List<AppointmentRecord>
}
```

### 4.6 SolverService

```kotlin
class SolverService(
    private val clinicRepository: ClinicRepository,
    private val doctorRepository: DoctorRepository,
    private val appointmentRepository: AppointmentRepository,
    private val treatmentTypeRepository: TreatmentTypeRepository,
    private val holidayRepository: HolidayRepository,
) {
    /**
     * 특정 클리닉의 주어진 날짜 범위에 대해 배치 최적화를 실행합니다.
     *
     * @param clinicId 대상 클리닉
     * @param dateRange 최적화 대상 날짜 범위
     * @param timeLimit 최대 Solver 실행 시간 (기본 30초)
     * @return 최적화된 예약 배치 결과
     */
    fun optimize(
        clinicId: Long,
        dateRange: ClosedRange<LocalDate>,
        timeLimit: Duration = Duration.ofSeconds(30),
    ): SolverResult

    /**
     * 임시휴진에 의한 재스케줄을 전역 최적화로 수행합니다.
     * ClosureRescheduleService.autoReschedule의 전역 최적 대안.
     */
    fun optimizeReschedule(
        clinicId: Long,
        closureDate: LocalDate,
        searchDays: Int = 7,
        timeLimit: Duration = Duration.ofSeconds(30),
    ): SolverResult
}

data class SolverResult(
    val score: HardSoftScore,
    val appointments: List<AppointmentRecord>,
    val isFeasible: Boolean,
)
```

### 4.7 SolverConfig

```kotlin
object SolverConfig {
    fun create(timeLimit: Duration = Duration.ofSeconds(30)): SolverFactory<ScheduleSolution> =
        SolverFactory.create(
            ai.timefold.solver.config.SolverConfig()
                .withSolutionClass(ScheduleSolution::class.java)
                .withEntityClasses(AppointmentPlanning::class.java)
                .withConstraintProviderClass(AppointmentConstraintProvider::class.java)
                .withTerminationSpentLimit(timeLimit)
        )
}
```

### 4.8 build.gradle.kts

```kotlin
dependencies {
    api(project(":appointment-core"))

    // Timefold Solver
    api(Libs.timefold_solver_core)

    testImplementation(Libs.timefold_solver_test)
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.h2_v2)
}
```

---

## 5. 테스트 전략

### 5.1 Constraint 단위 테스트

Timefold의 `ConstraintVerifier`를 사용하여 각 제약을 독립적으로 검증.

```kotlin
class HardConstraintTest {
    private val constraintVerifier = ConstraintVerifier.build(
        AppointmentConstraintProvider(),
        ScheduleSolution::class.java,
        AppointmentPlanning::class.java,
    )

    @Test
    fun `H1 - 영업시간 외 예약은 Hard 위반`() {
        // Given: 영업시간 09:00-18:00인 clinic, 20:00 시작 예약
        // When: constraintVerifier.verifyThat(...)
        // Then: penalizedBy(1)
    }
}
```

### 5.2 통합 테스트

실제 H2 DB에 데이터를 넣고 SolverService.optimize()를 실행하여 Feasible 해를 검증.

### 5.3 벤치마크 (선택)

`timefold-solver-benchmark`를 사용하여 다양한 크기의 문제에 대한 성능 측정.

---

## 6. SlotCalculationService와의 공존 시나리오

| 시나리오 | 사용 서비스 | 이유 |
|---------|-----------|------|
| 환자가 예약 가능 슬롯 조회 | `SlotCalculationService` | 실시간 응답 필요, 단건 |
| 관리자가 하루 전체 스케줄 최적화 | `SolverService.optimize` | 전역 최적 배치 |
| 임시휴진 발생 시 대량 재배정 | `SolverService.optimizeReschedule` | 전역 최적 재배치 |
| 신규 예약 단건 배치 | `SlotCalculationService` | 실시간, 기존 예약 고정 상태에서 빈 슬롯 |
| 주간 스케줄 리밸런싱 | `SolverService.optimize` | 의사 부하 분산 |

---

## 7. 태스크 목록

### Phase 3-1: Solver 도메인 모델 (complexity: medium)

| # | 태스크 | 복잡도 |
|---|--------|--------|
| 1 | `build.gradle.kts` — Timefold 의존성 추가 | low |
| 2 | `AppointmentPlanning` Planning Entity 구현 | medium |
| 3 | `ScheduleSolution` Planning Solution 구현 | medium |
| 4 | Problem Fact 클래스들 (`DoctorFact`, `ClinicFact`, `TreatmentFact`, `EquipmentFact`) | low |
| 5 | `TimeSlot` 이산 시간 슬롯 생성 유틸 | low |
| 6 | `SolutionConverter` — core Record ↔ solver domain 변환 | medium |

### Phase 3-2: Constraint Provider (complexity: high)

| # | 태스크 | 복잡도 |
|---|--------|--------|
| 7 | Hard Constraints H1~H6 (시간/날짜 관련 제약) | high |
| 8 | Hard Constraints H7~H8 (동시성, 장비 제약) | high |
| 9 | Hard Constraints H9~H10 (providerType, clinic 소속) | low |
| 10 | Soft Constraints S1~S2 (부하 분산, 간격 최소화) | medium |
| 11 | Soft Constraints S3~S6 (선호도 기반) | medium |
| 12 | `ConstraintVerifier` 기반 Hard Constraint 단위 테스트 | medium |
| 13 | `ConstraintVerifier` 기반 Soft Constraint 단위 테스트 | medium |

### Phase 3-3: Solver Service (complexity: medium)

| # | 태스크 | 복잡도 |
|---|--------|--------|
| 14 | `SolverConfig` — SolverFactory 설정 | low |
| 15 | `SolverService.optimize()` 구현 | medium |
| 16 | `SolverService.optimizeReschedule()` 구현 | medium |
| 17 | SolverService 통합 테스트 (H2 DB) | medium |

### Phase 3-4: 고도화 (complexity: high, 선택)

| # | 태스크 | 복잡도 |
|---|--------|--------|
| 18 | Move 전략 튜닝 (Custom Move Filter) | high |
| 19 | Incremental Score 계산 최적화 | high |
| 20 | Benchmark 설정 및 성능 측정 | medium |
| 21 | `appointment-event` 통합 (Solver 완료 이벤트 발행) | low |

### 총 태스크: 21개

- **low**: 6개
- **medium**: 10개
- **high**: 5개
- **예상 Phase 3-1~3-3 소요**: 3~5 작업 세션
- **Phase 3-4는 선택적 고도화**

---

## 8. 리스크 및 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| 3개 Planning Variable로 탐색 공간 폭발 | Solver 수렴 속도 저하 | Custom MoveFilter로 불필요한 Move 제거; 날짜 범위를 7일로 제한 |
| Timefold의 `LocalTime` ValueRange 지원 | 구현 복잡도 | `List<LocalTime>`으로 이산화하여 ValueRangeProvider 등록 |
| H7 동시 환자 수 제약의 3-level cascade | Constraint Stream 복잡도 | `resolveMaxConcurrent` 함수를 Constraint 내에서 재사용 |
| 대규모 데이터(수백 예약) 시 성능 | Solver 시간 초과 | timeLimit을 사용자 설정으로 노출; 병렬 Solver 고려 |
| Exposed JDBC 트랜잭션과 Solver의 비동기 실행 | 트랜잭션 경계 불일치 | SolutionConverter에서 데이터 로딩을 트랜잭션 내에서 완료 후 Solver에 전달 |
