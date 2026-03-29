# appointment-solver

## 개요

Timefold Solver 기반의 병원 예약 스케줄링 최적화 엔진입니다. 복수의 예약을 동시에 고려하여 전역 최적 배치를 수행합니다.

## 주요 기능

- **배치 최적화**: 특정 날짜 범위의 예약을 전역 최적으로 재배치
- **임시휴진 재배정**: 휴진 영향 예약의 전역 최적 재스케줄링
- **2단계 최적화**: FIRST_FIT_DECREASING 구축 → LATE_ACCEPTANCE 지역 탐색
- **Custom MoveFilter**: 무효한 이동(휴진, 부재, 공휴일, providerType 불일치) 사전 필터링
- **Hard Constraints 11개**: 영업시간, 의사스케줄, 부재, 휴식, 휴진, 공휴일, 시간겹침, 동시환자수, 장비, providerType, 클리닉소속
- **Soft Constraints 6개**: 부하분산, 간격최소화, 원래의사유지, 빠른슬롯, 장비효율, 요청일근접

## 아키텍처

- **Planning Entity**: `AppointmentPlanning` (doctorId, appointmentDate, startTime)
  - `@PlanningPin` — CONFIRMED 예약은 이동하지 않음
  - `AppointmentDifficultyComparator` — FFD용 난이도 비교기 (장비필요 > 시간 > 요청일)
- **Planning Solution**: `ScheduleSolution` (Problem Facts + Planning Entities)
- **Constraint Streams API**: 선언적 제약 정의 (`HardConstraints`, `SoftConstraints`)
- **SolutionConverter**: core Record ↔ solver domain 변환
- **AppointmentMoveFilter**: 무효 이동 사전 필터링

### Planning 도메인 클래스 구조

```mermaid
classDiagram
    class ScheduleSolution {
        <<PlanningSolution>>
        +ClinicFact clinic
        +List~DoctorFact~ doctors
        +List~TreatmentFact~ treatments
        +List~EquipmentFact~ equipments
        +List~OperatingHoursRecord~ operatingHours
        +List~DoctorScheduleRecord~ doctorSchedules
        +List~DoctorAbsenceRecord~ doctorAbsences
        +List~BreakTimeRecord~ breakTimes
        +List~ClinicClosureRecord~ closures
        +List~HolidayRecord~ holidays
        +List~Long~ doctorIds
        +List~LocalDate~ dateRange
        +List~LocalTime~ timeSlots
        +List~AppointmentPlanning~ appointments
        +HardSoftScore score
    }
    class AppointmentPlanning {
        <<PlanningEntity>>
        +Long id
        +Long clinicId
        +Boolean pinned «PlanningPin»
        +Long originalDoctorId
        +LocalDate requestedDate
        +Long doctorId «PlanningVariable»
        +LocalDate appointmentDate «PlanningVariable»
        +LocalTime startTime «PlanningVariable»
        +LocalTime endTime
    }
    class ClinicFact {
        +Long id
        +Int slotDurationMinutes
        +Int maxConcurrentPatients
        +Boolean openOnHolidays
    }
    class DoctorFact {
        +Long id
        +Long clinicId
        +String providerType
        +Int maxConcurrentPatients
    }
    class EquipmentFact {
        +Long id
        +Int usageDurationMinutes
        +Int quantity
    }
    class TreatmentFact {
        +Long id
        +Int defaultDurationMinutes
        +String requiredProviderType
        +Boolean requiresEquipment
    }

    ScheduleSolution "1" *-- "many" AppointmentPlanning : planningEntities
    ScheduleSolution "1" *-- "1" ClinicFact : problemFact
    ScheduleSolution "1" *-- "many" DoctorFact : problemFacts
    ScheduleSolution "1" *-- "many" EquipmentFact : problemFacts
    ScheduleSolution "1" *-- "many" TreatmentFact : problemFacts
```

## 2단계 최적화 전략

```mermaid
flowchart LR
    A([예약 목록\n + 제약 조건]) --> B

    subgraph Phase1["Phase 1: Construction Heuristic"]
        B[FIRST_FIT_DECREASING\n난이도 높은 예약부터 배치]
        B --> C{Hard Constraint\n모두 충족?}
        C -- Yes --> D([초기 해])
        C -- No --> B
    end

    subgraph Phase2["Phase 2: Local Search"]
        D --> E[LATE_ACCEPTANCE\n이웃 해 탐색]
        E --> F{개선됨?}
        F -- Yes --> E
        F -- No / TimeLimit --> G([최종 해\nHardSoftScore])
    end
```

## 최적화 실행 시퀀스

### optimize() — 기간 전체 배치 최적화

```mermaid
sequenceDiagram
    actor Caller
    participant SS as SolverService
    participant DB as Repositories
    participant SC as SolutionConverter
    participant TF as Timefold Solver

    Caller->>SS: optimize(clinicId, dateRange, timeLimit)
    SS->>DB: loadSolution() — transaction
    DB-->>DB: clinic / doctors / appointments / treatments\noperatingHours / schedules / absences\nbreakTimes / closures / holidays
    DB-->>SC: 모든 Record 전달
    SC->>SC: buildSolution()\nRecord → Planning 도메인 변환\npinned 설정 (CONFIRMED 이상)
    SC-->>SS: ScheduleSolution

    SS->>TF: solver.solve(solution)
    Note over TF: Phase1: FIRST_FIT_DECREASING\nPhase2: LATE_ACCEPTANCE\n(timeLimit 내 최적해 탐색)
    TF-->>SS: ScheduleSolution (score 포함)

    SS->>SC: extractResults(solution, originalMap)
    SC-->>SS: List~AppointmentRecord~ (pinned 제외)
    SS-->>Caller: SolverResult\n(score, appointments, isFeasible, solveTimeMs)
```

### optimizeReschedule() — 임시휴진 재배정

```mermaid
sequenceDiagram
    actor CS as ClosureRescheduleService
    participant SS as SolverService
    participant DB as Repositories
    participant TF as Timefold Solver

    CS->>SS: optimizeReschedule(clinicId, closureDate, searchDays=7)
    Note over SS: dateRange = closureDate..closureDate+searchDays
    SS->>SS: optimize(clinicId, dateRange, timeLimit)
    SS->>DB: 휴진 포함 dateRange 전체 데이터 로딩
    DB-->>SS: ScheduleSolution
    SS->>TF: solver.solve(solution)
    Note over TF: PENDING_RESCHEDULE 예약 재배정\nCONFIRMED 예약은 pinned — 이동 없음
    TF-->>SS: 최적 배치 결과
    SS-->>CS: SolverResult
    CS->>DB: 변경된 예약만 상태 업데이트\n(PENDING_RESCHEDULE → RESCHEDULED)
```

## Constraint 평가

```mermaid
flowchart TD
    A([AppointmentPlanning]) --> H & S

    subgraph H["Hard Constraints (위반 시 infeasible)"]
        H1[H1: 병원 운영시간 내]
        H2[H2: 의사 스케줄 내]
        H3[H3: 의사 부재 없음]
        H4a[H4a: 요일별 휴시간 없음]
        H4b[H4b: 기본 휴시간 없음]
        H5[H5: 임시휴진 없음]
        H6[H6: 공휴일 없음]
        H7[H7: 동시 환자 수 제한]
        H8[H8: 장비 사용량 제한]
        H9[H9: providerType 일치]
        H10[H10: 병원 소속 의사]
    end

    subgraph S["Soft Constraints (점수 최대화)"]
        S1["S1: 의사 부하 분산 ×100"]
        S2["S2: 스케줄 간격 최소화 ×10/분"]
        S3["S3: 원래 의사 유지 ×1000"]
        S4["S4: 빠른 슬롯 선호 ×10/일"]
        S5["S5: 장비 연속 배치 ×5/분"]
        S6["S6: 요청 날짜 근접 ×500/일"]
    end

    H -- "위반 수 × -1 HARD" --> Score[HardSoftScore]
    S -- "위반 합산 × -weight SOFT" --> Score
    Score -- "hard==0 → isFeasible" --> R([SolverResult])
```

## SlotCalculationService와의 공존

| 시나리오 | 서비스 |
|---------|--------|
| 환자가 빈 슬롯 조회 | SlotCalculationService (실시간, 단건) |
| 관리자 배치 최적화 | SolverService.optimize (전역 최적) |
| 임시휴진 재배정 | SolverService.optimizeReschedule |

## 사용 예제

```kotlin
val solverService = SolverService()
val result = solverService.optimize(
    clinicId = 1L,
    dateRange = LocalDate.of(2026, 3, 23)..LocalDate.of(2026, 3, 27),
    timeLimit = Duration.ofSeconds(30),
)
if (result.isFeasible) {
    result.appointments.forEach { println(it) }
}
```

## 테스트

```bash
# 전체 테스트
./gradlew :appointment-solver:test

# ConstraintVerifier 단위 테스트 (20개)
./gradlew :appointment-solver:test --tests "*.ConstraintVerifierTest"

# SolverService 통합 테스트 (4개)
./gradlew :appointment-solver:test --tests "*.SolverServiceTest"

# Benchmark 성능 테스트 (3개, "benchmark" 태그)
./gradlew :appointment-solver:test --tests "*.BenchmarkTest"
```

2026-03-28 기준 모듈 테스트 27건 통과.

### Benchmark 규모

| 규모 | 의사 | 예약 | 시간 제한 |
|------|------|------|-----------|
| 소규모 | 2명 | 10건 | 10초 |
| 중규모 | 5명 | 30건 | 15초 |
| 대규모 | 10명 | 100건 | 30초 |

## 의존성

```kotlin
api(project(":appointment-core"))
api(Libs.timefold_solver_core)
implementation(Libs.timefold_solver_benchmark)
```
