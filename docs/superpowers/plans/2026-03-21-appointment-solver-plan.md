# Appointment Solver 구현 계획

> **Date**: 2026-03-21
> **Spec**: [appointment-solver-design.md](../specs/2026-03-21-appointment-solver-design.md)
> **Module**: `scheduling/appointment-solver`
> **Approach**: 접근법 A (Lightweight Constraint Streams)

---

## 현재 상태

| 항목 | 상태 |
|------|------|
| `scheduling/appointment-solver/build.gradle.kts` | 스텁 (`api(project(":appointment-core"))` 만 존재) |
| `scheduling/appointment-solver/README.md` | 플레이스홀더 (Phase 3+ 예정 문구) |
| `scheduling/appointment-solver/src/` | 소스 없음 |
| `buildSrc/src/main/kotlin/Libs.kt` | Timefold 의존성 이미 정의됨 (`timefold_solver_core`, `timefold_solver_test` 등, v1.31.0) |
| `appointment-core` Record 클래스 | 모두 존재 (AppointmentRecord, ClinicRecord, DoctorRecord, TreatmentTypeRecord 등) |
| `AppointmentStatus` | REQUESTED, CONFIRMED, CHECKED_IN, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW, PENDING_RESCHEDULE, RESCHEDULED |
| Test resources 템플릿 | `junit-platform.properties` 참조 가능 (infra/cache-lettuce 등) |

---

## 의존성 그래프

```
appointment-core (Record, Repository, Service)
    |
    v
appointment-solver
    ├── domain/        (Problem Fact, Planning Entity, Planning Solution)
    ├── constraint/    (ConstraintProvider)
    ├── converter/     (core Record <-> solver domain)
    └── service/       (SolverConfig, SolverService)
```

내부 의존 관계:

```
T1 (build.gradle.kts)
 |
 v
T4 (Problem Facts) ──> T5 (TimeSlot)
 |                        |
 v                        v
T2 (AppointmentPlanning) ─> T3 (ScheduleSolution)
                              |
                              v
                           T6 (SolutionConverter)
                              |
                              v
T7,T8,T9 (Hard Constraints) ──> T10,T11 (Soft Constraints)
 |                                  |
 v                                  v
T12 (Hard Constraint Tests)     T13 (Soft Constraint Tests)
                              |
                              v
                    T14 (SolverConfig) ──> T15,T16 (SolverService)
                                              |
                                              v
                                          T17 (통합 테스트)
```

---

## 태스크 목록

### Phase 3-1: 프로젝트 설정 & 도메인 모델

| # | 태스크 | Complexity | 산출물 | 선행 태스크 |
|---|--------|-----------|--------|-----------|
| T1 | `build.gradle.kts` 업데이트 — Timefold 의존성, test resources 추가 | **low** | `build.gradle.kts`, `src/test/resources/junit-platform.properties`, `src/test/resources/logback-test.xml` | - |
| T2 | `AppointmentPlanning` Planning Entity 구현 | **medium** | `domain/AppointmentPlanning.kt` | T1, T4, T5 |
| T3 | `ScheduleSolution` Planning Solution 구현 | **medium** | `domain/ScheduleSolution.kt` | T2, T4 |
| T4 | Problem Fact 클래스 구현 (`ClinicFact`, `DoctorFact`, `TreatmentFact`, `EquipmentFact`) | **low** | `domain/ClinicFact.kt`, `domain/DoctorFact.kt`, `domain/TreatmentFact.kt`, `domain/EquipmentFact.kt` | T1 |
| T5 | `TimeSlot` 이산 시간 슬롯 생성 유틸리티 | **low** | `domain/TimeSlot.kt` | T1 |
| T6 | `SolutionConverter` — core Record <-> solver domain 변환 | **medium** | `converter/SolutionConverter.kt` | T2, T3, T4 |

### Phase 3-2: Constraint Provider

| # | 태스크 | Complexity | 산출물 | 선행 태스크 |
|---|--------|-----------|--------|-----------|
| T7 | Hard Constraints H1~H6 (시간/날짜 관련) — withinOperatingHours, withinDoctorSchedule, noDoctorAbsenceConflict, noBreakTimeConflict, noClinicClosureConflict, noHolidayConflict | **high** | `constraint/HardConstraints.kt` (부분) | T3 |
| T8 | Hard Constraints H7~H8 (동시성/장비) — maxConcurrentPatientsPerDoctor, equipmentAvailability | **high** | `constraint/HardConstraints.kt` (부분) | T3 |
| T9 | Hard Constraints H9~H10 (매칭) — providerTypeMatch, doctorBelongsToClinic | **low** | `constraint/HardConstraints.kt` (부분) | T3 |
| T10 | Soft Constraints S1~S2 (부하 분산, 간격 최소화) — doctorLoadBalance, minimizeGaps | **medium** | `constraint/SoftConstraints.kt` (부분) | T3 |
| T11 | Soft Constraints S3~S6 (선호도) — preferOriginalDoctor, preferEarlySlot, equipmentUtilization, preferRequestedDate | **medium** | `constraint/SoftConstraints.kt` (부분) | T3 |
| T12 | `AppointmentConstraintProvider` — 모든 Constraint 조합 | **low** | `constraint/AppointmentConstraintProvider.kt` | T7, T8, T9, T10, T11 |
| T13 | Hard Constraint 단위 테스트 (`ConstraintVerifier` 기반) | **medium** | `constraint/HardConstraintTest.kt` | T12 |
| T14 | Soft Constraint 단위 테스트 (`ConstraintVerifier` 기반) | **medium** | `constraint/SoftConstraintTest.kt` | T12 |

### Phase 3-3: Solver Service

| # | 태스크 | Complexity | 산출물 | 선행 태스크 |
|---|--------|-----------|--------|-----------|
| T15 | `SolverConfig` — SolverFactory 설정 | **low** | `service/SolverConfig.kt` | T3, T12 |
| T16 | `SolverService.optimize()` 구현 | **medium** | `service/SolverService.kt` | T6, T15 |
| T17 | `SolverService.optimizeReschedule()` 구현 | **medium** | `service/SolverService.kt` (확장) | T16 |
| T18 | `SolverResult` 결과 모델 | **low** | `service/SolverResult.kt` | T1 |
| T19 | SolverService 통합 테스트 (H2 DB) | **medium** | `service/SolverServiceTest.kt` | T16, T17 |

### Phase 3-4: 고도화 (선택)

| # | 태스크 | Complexity | 산출물 | 선행 태스크 |
|---|--------|-----------|--------|-----------|
| T20 | Move 전략 튜닝 (Custom MoveFilter) | **high** | `config/CustomMoveFilter.kt` | T19 |
| T21 | Incremental Score 계산 최적화 | **high** | 성능 프로파일링 & 튜닝 | T19 |
| T22 | Benchmark 설정 및 성능 측정 | **medium** | benchmark 설정 파일 | T19 |
| T23 | README.md 업데이트 | **low** | `README.md` | T19 |

---

## 복잡도 요약

| Complexity | 태스크 수 | 태스크 번호 |
|-----------|----------|-----------|
| **high** | 4 | T7, T8, T20, T21 |
| **medium** | 10 | T2, T3, T6, T10, T11, T13, T14, T16, T17, T19, T22 |
| **low** | 8 | T1, T4, T5, T9, T12, T15, T18, T23 |

---

## 병렬 실행 그룹

### Group 1 — 프로젝트 기반 설정 (선행 없음)
| 태스크 | 설명 |
|--------|------|
| **T1** | build.gradle.kts + test resources |

### Group 2 — 도메인 모델 (T1 완료 후, 병렬 실행 가능)
| 태스크 | 설명 |
|--------|------|
| **T4** | Problem Fact 클래스 4개 |
| **T5** | TimeSlot 유틸 |
| **T18** | SolverResult 결과 모델 |

### Group 3 — Planning Entity/Solution (T4, T5 완료 후)
| 태스크 | 설명 |
|--------|------|
| **T2** | AppointmentPlanning |
| **T3** | ScheduleSolution (T2 필요) |

### Group 4 — Constraints & Converter (T3 완료 후, 병렬 실행 가능)
| 태스크 | 설명 |
|--------|------|
| **T6** | SolutionConverter |
| **T7** | Hard Constraints H1~H6 |
| **T8** | Hard Constraints H7~H8 |
| **T9** | Hard Constraints H9~H10 |
| **T10** | Soft Constraints S1~S2 |
| **T11** | Soft Constraints S3~S6 |

### Group 5 — Constraint 조합 & 테스트 (T7~T11 완료 후)
| 태스크 | 설명 |
|--------|------|
| **T12** | AppointmentConstraintProvider |
| **T13** | Hard Constraint 테스트 (T12 이후) |
| **T14** | Soft Constraint 테스트 (T12 이후) |

### Group 6 — Solver Service (T6, T12 완료 후)
| 태스크 | 설명 |
|--------|------|
| **T15** | SolverConfig |
| **T16** | SolverService.optimize() (T15 이후) |
| **T17** | SolverService.optimizeReschedule() (T16 이후) |

### Group 7 — 통합 테스트 (T17 완료 후)
| 태스크 | 설명 |
|--------|------|
| **T19** | SolverService 통합 테스트 |
| **T23** | README.md 업데이트 |

### Group 8 — 고도화 (T19 완료 후, 선택적)
| 태스크 | 설명 |
|--------|------|
| **T20** | Custom MoveFilter |
| **T21** | Incremental Score 최적화 |
| **T22** | Benchmark |

---

## 실행 흐름 다이어그램

```
T1 ─────────────────────────────────────────────────────────> (build.gradle.kts)
  |
  ├── T4 (Problem Facts)  ──┐
  ├── T5 (TimeSlot)  ───────┤
  └── T18 (SolverResult)    │
                             v
                      T2 (AppointmentPlanning)
                             |
                             v
                      T3 (ScheduleSolution)
                             |
              ┌──────────────┼──────────────────────┐
              v              v                      v
        T6 (Converter)  T7,T8,T9 (Hard)    T10,T11 (Soft)
              |              |                      |
              |              v                      v
              |         T12 (ConstraintProvider) <──┘
              |              |
              |         ┌────┴────┐
              |         v         v
              |    T13 (H-Test)  T14 (S-Test)
              |         |         |
              v         v         v
        T15 (SolverConfig) <──────┘
              |
              v
        T16 (optimize)
              |
              v
        T17 (optimizeReschedule)
              |
              v
        T19 (통합 테스트) + T23 (README)
              |
              v
        T20, T21, T22 (고도화, 선택)
```

---

## 구현 세부 지침

### T1: build.gradle.kts

```kotlin
dependencies {
    api(project(":appointment-core"))

    // Timefold Solver
    api(Libs.timefold_solver_core)

    testImplementation(Libs.timefold_solver_test)
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kluent)
    testImplementation(Libs.h2_v2)
}
```

- `src/test/resources/junit-platform.properties` 복사 (infra/cache-lettuce 참조)
- `src/test/resources/logback-test.xml` 복사 (templates/ 참조)

### T2: AppointmentPlanning

- `@PlanningEntity`, `@PlanningPin` (pinned 필드)
- Planning Variables: `doctorId`, `appointmentDate`, `startTime`
- `endTime`은 getter로 계산 (`startTime + durationMinutes`)
- Timefold는 no-arg constructor 필요 -- 주의
- `KLogging` companion object 불필요 (순수 도메인 객체)

### T4: Problem Fact 클래스

- core Record의 필드를 그대로 매핑하되, Solver에 필요한 필드만 포함
- `DoctorFact`: id, clinicId, providerType, maxConcurrentPatients
- `ClinicFact`: id, slotDurationMinutes, maxConcurrentPatients, openOnHolidays
- `TreatmentFact`: id, defaultDurationMinutes, requiredProviderType, requiresEquipment, maxConcurrentPatients
- `EquipmentFact`: id, usageDurationMinutes, quantity

### T5: TimeSlot

- `generateTimeSlots(openTime, closeTime, slotDurationMinutes): List<LocalTime>`
- 영업시간 내에서 slotDurationMinutes 간격으로 이산 시간 생성

### T7-T8: Hard Constraints (핵심)

- Constraint Streams API 사용
- `Joiners.overlapping()` 으로 시간 겹침 검사
- H7 (maxConcurrentPatients)이 가장 복잡 -- 3-level cascade (clinic, doctor, treatment)
- 각 Constraint에 명확한 ID 문자열 부여 (`"H1: withinOperatingHours"` 등)

### T6: SolutionConverter

- `buildSolution()`: core Record -> solver domain 변환, pinned 상태 결정
- `extractResults()`: solver 결과 -> AppointmentRecord 목록 변환
- REQUESTED, PENDING_RESCHEDULE 상태만 Planning Entity로 변환 (pinned=false)
- CONFIRMED, CHECKED_IN, IN_PROGRESS, COMPLETED 상태는 pinned=true

### T16: SolverService

- Repository에서 데이터 로딩 -> SolutionConverter.buildSolution() -> SolverFactory.solve()
- 트랜잭션 내에서 데이터 로딩 완료 후 Solver 전달 (Exposed JDBC transaction{} 경계)
- `KLogging` companion object 사용

---

## 리스크 관리

| 리스크 | 영향 | 완화 | 관련 태스크 |
|--------|------|------|-----------|
| Planning Variable 3개로 탐색 공간 폭발 | Solver 수렴 속도 저하 | 날짜 범위 7일 제한, Custom MoveFilter (T20) | T2, T20 |
| LocalTime ValueRange 이산화 | 구현 복잡도 | TimeSlot 유틸로 List<LocalTime> 생성 (T5) | T5 |
| H7 동시성 제약 Constraint Stream 복잡도 | 버그 가능성 | ConstraintVerifier로 격리 테스트 (T13) | T8, T13 |
| Exposed JDBC 트랜잭션 경계 | 데이터 불일치 | SolutionConverter에서 로딩 완료 후 Solver 전달 | T6, T16 |
| Timefold no-arg constructor 요구 | Kotlin data class와 충돌 | 일반 class + constructor() 오버로드 사용 | T2, T3 |

---

## 예상 작업량

| Phase | 태스크 수 | 예상 세션 |
|-------|----------|----------|
| 3-1 (도메인 모델) | 6 | 1 세션 |
| 3-2 (Constraints) | 8 | 1.5~2 세션 |
| 3-3 (Service) | 5 | 1 세션 |
| 3-4 (고도화) | 4 | 선택적 |
| **합계** | **23** | **3.5~4 세션** |
