# appointment-solver

## 개요

Timefold Solver 기반의 병원 예약 스케줄링 최적화 엔진입니다. 복수의 예약을 동시에 고려하여 전역 최적 배치를 수행합니다.

## 주요 기능

- **배치 최적화**: 특정 날짜 범위의 예약을 전역 최적으로 재배치
- **임시휴진 재배정**: 휴진 영향 예약의 전역 최적 재스케줄링
- **Hard Constraints 11개**: 영업시간, 의사스케줄, 부재, 휴식, 휴진, 공휴일, 동시환자수, 장비, providerType, 클리닉소속
- **Soft Constraints 6개**: 부하분산, 간격최소화, 원래의사유지, 빠른슬롯, 장비효율, 요청일근접

## 아키텍처

- **Planning Entity**: `AppointmentPlanning` (doctorId, appointmentDate, startTime)
- **Planning Solution**: `ScheduleSolution` (Problem Facts + Planning Entities)
- **Constraint Streams API**: 선언적 제약 정의
- **SolutionConverter**: core Record ↔ solver domain 변환

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

## 의존성

```kotlin
api(project(":appointment-core"))
api(Libs.timefold_solver_core)
```
