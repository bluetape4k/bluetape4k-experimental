# 병원 예약 스케줄링 시스템 - Backend 구현 계획

## 개요

병원 진료 예약 시스템의 백엔드를 구현합니다.
- **Tech Stack**: Spring Boot 4 WebFlux + Exposed R2DBC + PostgreSQL + Timefold Solver
- **모듈 위치**: `scheduling/` 카테고리 하위에 모듈 생성

---

## Phase 1: 도메인 모델 & 기반 모듈 (`scheduling/appointment-core`)

**모듈**: `:appointment-core` (순수 도메인 모델, 의존성 최소화)

### 1-1. 핵심 도메인 엔티티 (Exposed Table + Entity)

```
io.bluetape4k.scheduling.appointment.domain/
├── clinic/
│   ├── ClinicTable, ClinicEntity           # 병원 기본 정보
│   ├── OperatingHoursTable, OperatingHoursEntity  # 병원 운영 시간 (요일별)
│   ├── BreakTimeTable, BreakTimeEntity     # 점심시간 등 휴식 시간
│   └── ClinicClosureTable, ClinicClosureEntity    # 휴진일 (공휴일, 병원사정)
├── doctor/
│   ├── DoctorTable, DoctorEntity           # 의사 기본 정보
│   ├── DoctorScheduleTable, DoctorScheduleEntity  # 의사 정규 스케줄 (요일별)
│   └── DoctorAbsenceTable, DoctorAbsenceEntity    # 의사 부재 (휴가, 학회 등)
├── equipment/
│   ├── EquipmentTable, EquipmentEntity     # 장비 정보 (이름, 사용시간)
│   └── TreatmentEquipmentTable             # 진료종류-장비 매핑 (N:M)
├── treatment/
│   └── TreatmentTypeTable, TreatmentTypeEntity  # 진료 종류 (이름, 기본소요시간, 필요장비)
└── appointment/
    ├── AppointmentTable, AppointmentEntity  # 예약 핵심
    ├── AppointmentStatus (enum)             # PROVISIONAL(가예약), CONFIRMED(확정), CANCELLED_PATIENT, CANCELLED_CLINIC, COMPLETED(진료완료)
    └── AppointmentNoteTable, AppointmentNoteEntity  # 예약 메모/취소 사유
```

### 1-2. 핵심 테이블 설계

**Clinic (병원)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| name | VARCHAR | 병원명 |
| slot_duration_minutes | INT | 예약 시간 단위 (15, 30, 60) |
| timezone | VARCHAR | 타임존 (Asia/Seoul) |

**OperatingHours (운영시간)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| clinic_id | UUID | FK → Clinic |
| day_of_week | INT | 요일 (1=MON ~ 7=SUN) |
| open_time | TIME | 오픈 시간 |
| close_time | TIME | 마감 시간 |
| is_active | BOOLEAN | 운영 여부 |

**BreakTime (휴식시간)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| clinic_id | UUID | FK → Clinic |
| day_of_week | INT | 요일 |
| start_time | TIME | 시작 |
| end_time | TIME | 종료 |

**ClinicClosure (휴진일)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| clinic_id | UUID | FK → Clinic |
| closure_date | DATE | 휴진 날짜 |
| reason | VARCHAR | 사유 |
| is_full_day | BOOLEAN | 종일 여부 |
| start_time | TIME? | 부분 휴진 시작 (nullable) |
| end_time | TIME? | 부분 휴진 종료 (nullable) |

**Doctor (의사)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| clinic_id | UUID | FK → Clinic |
| name | VARCHAR | 이름 |
| specialty | VARCHAR | 전문과목 |

**DoctorSchedule (의사 정규 스케줄)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| doctor_id | UUID | FK → Doctor |
| day_of_week | INT | 요일 |
| start_time | TIME | 진료 시작 |
| end_time | TIME | 진료 종료 |

**DoctorAbsence (의사 부재)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| doctor_id | UUID | FK → Doctor |
| absence_date | DATE | 부재 날짜 |
| start_time | TIME? | 부분 부재 시작 (null = 종일) |
| end_time | TIME? | 부분 부재 종료 |
| reason | VARCHAR | 사유 |

**Equipment (장비)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| clinic_id | UUID | FK → Clinic |
| name | VARCHAR | 장비명 |
| usage_duration_minutes | INT | 사용 소요시간 (분) |
| quantity | INT | 보유 수량 (동시사용 가능 수) |

**TreatmentType (진료 종류)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| clinic_id | UUID | FK → Clinic |
| name | VARCHAR | 진료명 |
| default_duration_minutes | INT | 기본 소요시간 (분) |
| requires_equipment | BOOLEAN | 장비 필요 여부 |

**TreatmentEquipment (진료-장비 매핑)**
| Column | Type | Description |
|--------|------|-------------|
| treatment_type_id | UUID | FK → TreatmentType |
| equipment_id | UUID | FK → Equipment |

**Appointment (예약)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| clinic_id | UUID | FK → Clinic |
| doctor_id | UUID | FK → Doctor |
| treatment_type_id | UUID | FK → TreatmentType |
| equipment_id | UUID? | FK → Equipment (nullable) |
| patient_name | VARCHAR | 환자명 |
| patient_phone | VARCHAR | 연락처 |
| appointment_date | DATE | 예약 날짜 |
| start_time | TIME | 시작 시간 |
| end_time | TIME | 종료 시간 (= start + 진료시간 + 장비시간) |
| status | VARCHAR | PROVISIONAL / CONFIRMED / CANCELLED_PATIENT / CANCELLED_CLINIC / COMPLETED |
| created_at | TIMESTAMP | 생성일시 |
| updated_at | TIMESTAMP | 수정일시 |

**AppointmentNote (예약 메모)**
| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| appointment_id | UUID | FK → Appointment |
| note_type | VARCHAR | BOOKING / CANCELLATION / RESCHEDULE / GENERAL |
| content | TEXT | 내용 |
| created_by | VARCHAR | 작성자 |
| created_at | TIMESTAMP | 작성일시 |

---

## Phase 2: 가용 슬롯 조회 서비스 (`scheduling/appointment-core` 내)

### 2-1. AvailableSlotService

예약 가능한 시간 슬롯을 계산하는 핵심 비즈니스 로직.

```
io.bluetape4k.scheduling.appointment.service/
├── slot/
│   ├── TimeSlot (data class)                    # 시작~종료 시간 값 객체
│   ├── AvailableSlotQuery (data class)          # 조회 조건 (날짜범위, 의사, 진료종류)
│   ├── AvailableSlotService                     # 가용 슬롯 계산 서비스
│   └── SlotCalculator                           # 순수 계산 로직 (테스트 용이)
├── appointment/
│   ├── AppointmentService                       # 예약 CRUD + 상태 변경
│   └── AppointmentRescheduleService             # 예약 변경 처리
└── constraint/
    └── AppointmentConstraintChecker             # 예약 제약조건 검증
```

**슬롯 계산 알고리즘**:
1. 해당 날짜의 병원 운영시간 조회
2. 휴진일 여부 확인 → 휴진이면 빈 슬롯
3. 점심시간 등 휴식시간 제외
4. 해당 의사의 스케줄 교차 (의사 근무시간 ∩ 병원 운영시간)
5. 의사 부재 제외
6. 이미 예약된 시간 제외
7. 진료에 필요한 장비의 가용 시간 확인 (장비 수량 고려)
8. slot_duration_minutes 단위로 분할하여 반환

---

## Phase 3: 예약 상태 관리 (Spring StateMachine 활용)

### 3-1. 예약 상태 전이

```
PROVISIONAL (가예약)
  ├──→ CONFIRMED (확정)          # 환자 확인 후
  ├──→ CANCELLED_PATIENT (환자취소)
  └──→ CANCELLED_CLINIC (병원취소)

CONFIRMED (확정)
  ├──→ COMPLETED (진료완료)
  ├──→ CANCELLED_PATIENT
  ├──→ CANCELLED_CLINIC
  └──→ PROVISIONAL (재조정 필요 시 가예약으로 복귀)

CANCELLED_* → (최종 상태, 전이 불가)
COMPLETED → (최종 상태, 전이 불가)
```

### 3-2. 대량 가예약 재배치 (병원 전체 휴진 시)

- 휴진일 등록 시 → 해당 날짜의 CONFIRMED/PROVISIONAL 예약 조회
- 상태를 PROVISIONAL로 변경
- Timefold Solver로 최적 재배치 후보 계산
- 예약 담당자에게 재배치 후보 제시 (자동 확정 X)

---

## Phase 4: Timefold Solver 통합 (`scheduling/appointment-solver`)

**모듈**: `:appointment-solver`

### 4-1. 최적화 모델

```
io.bluetape4k.scheduling.appointment.solver/
├── domain/
│   ├── AppointmentSolution (@PlanningSolution)   # 최적화 솔루션
│   ├── AppointmentAssignment (@PlanningEntity)   # 계획 엔티티 (날짜+시간 변경 가능)
│   └── AvailableSlotValue (@PlanningVariable)    # 가능한 슬롯 값
├── constraint/
│   └── AppointmentConstraintProvider             # 제약 조건 정의
└── service/
    └── SolverService                             # Timefold Solver 호출 서비스
```

### 4-2. 제약 조건 (ConstraintProvider)

**Hard Constraints (필수)**:
- 같은 의사가 같은 시간에 2개 이상 예약 불가
- 같은 장비가 같은 시간에 수량 초과 사용 불가
- 병원 운영시간 내에서만 예약
- 의사 근무시간 내에서만 예약
- 휴진일/의사부재 시간에 예약 불가

**Soft Constraints (선호)**:
- 원래 시간과 가까운 시간 우선 (시간 차이 최소화)
- 원래 날짜와 가까운 날짜 우선 (날짜 차이 최소화, 시간보다 낮은 우선순위)
- 연속 예약 사이에 버퍼 시간 선호

---

## Phase 5: WebFlux API 서버 (`scheduling/appointment-api`)

**모듈**: `:appointment-api` (Spring Boot App)

### 5-1. REST API 설계

```
io.bluetape4k.scheduling.appointment.api/
├── AppointmentApiApplication.kt
├── config/
│   ├── DatabaseConfig.kt                 # R2DBC + Exposed 설정
│   └── TimefoldConfig.kt                 # Timefold Solver 설정
├── controller/
│   ├── ClinicController.kt               # 병원 관리 API
│   ├── DoctorController.kt               # 의사 관리 API
│   ├── EquipmentController.kt            # 장비 관리 API
│   ├── TreatmentTypeController.kt        # 진료종류 관리 API
│   ├── AppointmentController.kt          # 예약 CRUD API
│   ├── SlotController.kt                 # 가용 슬롯 조회 API
│   └── OptimizationController.kt         # 가예약 최적 재배치 API
└── dto/
    ├── request/                           # 요청 DTO
    └── response/                          # 응답 DTO
```

### 5-2. 핵심 API 엔드포인트

**병원 운영 관리**
```
POST   /api/clinics                        # 병원 등록
PUT    /api/clinics/{id}/operating-hours    # 운영시간 설정
PUT    /api/clinics/{id}/break-times        # 휴식시간 설정
POST   /api/clinics/{id}/closures           # 휴진일 등록 → 가예약 재배치 트리거
```

**의사 관리**
```
POST   /api/doctors                         # 의사 등록
PUT    /api/doctors/{id}/schedule            # 정규 스케줄 설정
POST   /api/doctors/{id}/absences            # 부재 등록 → 가예약 재배치 트리거
```

**장비 관리**
```
POST   /api/equipment                        # 장비 등록
PUT    /api/equipment/{id}                   # 장비 수정 (사용시간, 수량)
```

**진료 종류**
```
POST   /api/treatment-types                  # 진료종류 등록
PUT    /api/treatment-types/{id}/equipment    # 필요 장비 매핑
```

**예약 (핵심)**
```
GET    /api/appointments/available-slots      # 가용 슬롯 조회
       ?clinicId=&doctorId=&treatmentTypeId=
       &dateFrom=&dateTo=
POST   /api/appointments                     # 예약 생성 (가예약)
PUT    /api/appointments/{id}/confirm         # 예약 확정
PUT    /api/appointments/{id}/cancel          # 예약 취소
PUT    /api/appointments/{id}/complete        # 진료 완료
PUT    /api/appointments/{id}/reschedule      # 예약 변경 (시간/날짜)
POST   /api/appointments/{id}/notes           # 메모 추가
```

**최적화**
```
POST   /api/optimization/reschedule           # 가예약 일괄 재배치 계산
       body: { clinicId, targetDate, ... }
       response: 재배치 후보 목록 (확정 아님)
PUT    /api/optimization/apply                 # 재배치 결과 적용 (가예약 상태 유지)
```

---

## Phase 6: 테스트

각 Phase에서 구현과 함께 작성:

- **Phase 1**: 도메인 엔티티 CRUD 테스트 (H2 R2DBC)
- **Phase 2**: SlotCalculator 단위 테스트 (순수 로직), AvailableSlotService 통합 테스트
- **Phase 3**: 상태 전이 테스트
- **Phase 4**: Timefold ConstraintProvider 단위 테스트
- **Phase 5**: WebFlux Controller 통합 테스트 (WebTestClient)

---

## 모듈 구조 요약

```
scheduling/
├── appointment-core/          # 도메인 모델 + 비즈니스 로직 + 상태 관리
│   └── build.gradle.kts
│       dependencies:
│         - exposed-r2dbc, exposed-java-time
│         - bluetape4k-exposed-r2dbc
│         - kotlinx-coroutines
│         - spring-statemachine-core (compileOnly)
│
├── appointment-solver/        # Timefold 최적화
│   └── build.gradle.kts
│       dependencies:
│         - project(":appointment-core")
│         - timefold-solver-core
│         - timefold-solver-spring-boot-starter
│         - timefold-solver-test (testOnly)
│
└── appointment-api/           # WebFlux API 서버 (Spring Boot App)
    └── build.gradle.kts
        dependencies:
          - project(":appointment-core")
          - project(":appointment-solver")
          - spring-boot-starter-webflux
          - r2dbc-postgresql (runtime)
          - r2dbc-h2 (test)
```

`settings.gradle.kts`에 `includeModules("scheduling", false, false)` 추가 필요.

---

## 구현 순서

1. **Phase 1**: `appointment-core` 모듈 생성 + 도메인 엔티티 + 테스트
2. **Phase 2**: 가용 슬롯 조회 서비스 + 테스트
3. **Phase 3**: 예약 상태 관리 + 테스트
4. **Phase 4**: `appointment-solver` 모듈 생성 + Timefold 통합 + 테스트
5. **Phase 5**: `appointment-api` 모듈 생성 + REST API + 통합 테스트
6. **Phase 6**: 전체 시나리오 테스트 (E2E)
