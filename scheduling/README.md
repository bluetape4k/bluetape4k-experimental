# Appointment Scheduling System

병원 진료 예약 스케줄링 시스템의 백엔드 구현체입니다.
멀티 병원(Clinic) 환경을 지원하며, 의사/전문상담사 스케줄 / 장비 가용성 / 동시 수용 인원 / 공휴일을 고려한 **가용 슬롯 조회** 기능을 제공합니다.

---

## System Architecture

```mermaid
graph TB
    subgraph "scheduling/"
        subgraph core["appointment-core"]
            direction TB
            Tables["Exposed Tables<br/>(17 tables)"]
            Records["Record Data Classes<br/>(17 records)"]
            Repos["Aggregate Repositories<br/>(6 repos)"]
            SM["AppointmentStateMachine"]
            TR["TimeRange Utilities"]
            CR["ConcurrencyResolver"]
            SCS["SlotCalculationService"]
            CRS["ClosureRescheduleService"]

            Tables --> Records
            Tables --> Repos
            Records --> Repos
            TR --> SCS
            CR --> SCS
            Repos --> SCS
            SCS --> CRS
        end

        subgraph event["appointment-event"]
            direction TB
            DE["AppointmentDomainEvent"]
            EL["AppointmentEventLogs"]
            Logger["AppointmentEventLogger"]

            DE --> Logger
            EL --> Logger
        end

        subgraph solver["appointment-solver (Phase 3+)"]
            TS["Timefold Solver<br/>(stub)"]
        end

        subgraph api["appointment-api (Phase 3+)"]
            WF["WebFlux REST<br/>(stub)"]
        end
    end

    core --> event
    core --> solver
    core --> api
    event --> api

    DB[(H2 / PostgreSQL)]
    Tables --> DB
    EL --> DB

    style core fill:#e1f5fe,stroke:#0288d1
    style event fill:#fff3e0,stroke:#f57c00
    style solver fill:#f3e5f5,stroke:#9c27b0
    style api fill:#e8f5e9,stroke:#388e3c
```

### Module Dependencies

| Module | Gradle ID | 역할 | 상태 |
|--------|-----------|------|------|
| `appointment-core` | `:appointment-core` | 도메인 모델 + 상태 머신 + 슬롯 계산 + 재배정 | **Phase 1-2 완료** |
| `appointment-event` | `:appointment-event` | 이벤트 정의 + 이벤트 로그 DB 저장 | **Phase 1 완료** |
| `appointment-solver` | `:appointment-solver` | Timefold 기반 최적화 | Phase 3+ stub |
| `appointment-api` | `:appointment-api` | WebFlux REST API | Phase 3+ stub |

---

## 주요 기능

### 의사 / 전문상담사 구분

진료 제공자를 유형별로 구분하여 적절한 진료 유형에만 배정합니다.

| Provider Type | 수행 가능 | 설명 |
|---------------|-----------|------|
| `DOCTOR` | 진료(TREATMENT), 시술(PROCEDURE) | 의사 |
| `CONSULTANT` | 상담(CONSULTATION) | 전문상담사 |

### 진료 카테고리 & 상담 방식

| 카테고리 | 필요 Provider | 설명 |
|----------|---------------|------|
| `TREATMENT` | DOCTOR | 일반 진료 |
| `PROCEDURE` | DOCTOR | 시술 |
| `CONSULTATION` | CONSULTANT | 상담 (시술안내, 진료비 안내 등) |

상담 방식 (`ConsultationMethod`):
- `IN_PERSON` — 오프라인 대면 상담
- `PHONE` — 전화 상담
- `VIDEO` — 영상통화 상담

### 국가 공휴일 관리

- `Holidays` 테이블에 공휴일 등록
- 기본적으로 모든 병원 휴진 (`openOnHolidays = false`)
- 예외적으로 영업하는 병원은 `openOnHolidays = true` 설정

### 임시휴진 시 예약 재배정

임시휴진 선언 시 기존 예약을 자동으로 처리합니다.

```mermaid
sequenceDiagram
    participant Admin as 관리자
    participant CRS as ClosureRescheduleService
    participant SCS as SlotCalculationService
    participant DB as Database

    Admin->>CRS: processClosureReschedule(clinicId, date)
    CRS->>DB: 해당 날짜 활성 예약 조회
    DB-->>CRS: 영향받는 예약 목록

    CRS->>DB: 예약 상태 → PENDING_RESCHEDULE

    loop 각 영향받는 예약
        loop searchDays (기본 7일)
            CRS->>SCS: findAvailableSlots(다음 날짜)
            SCS-->>CRS: 가용 슬롯
            CRS->>DB: RescheduleCandidates 저장
        end
    end

    CRS-->>Admin: 예약별 재배정 후보 목록

    alt 관리자 수동 선택
        Admin->>CRS: confirmReschedule(candidateId)
    else 자동 재배정
        Admin->>CRS: autoReschedule(appointmentId)
    end

    CRS->>DB: 새 예약 생성 (CONFIRMED)
    CRS->>DB: 원래 예약 → RESCHEDULED
```

---

## Data Model (ER Diagram)

```mermaid
erDiagram
    HOLIDAYS {
        long id PK
        date holiday_date UK
        string name
        boolean recurring "default false"
    }

    CLINICS ||--o{ OPERATING_HOURS : has
    CLINICS ||--o{ CLINIC_DEFAULT_BREAK_TIMES : has
    CLINICS ||--o{ BREAK_TIMES : has
    CLINICS ||--o{ CLINIC_CLOSURES : has
    CLINICS ||--o{ DOCTORS : employs
    CLINICS ||--o{ EQUIPMENTS : owns
    CLINICS ||--o{ TREATMENT_TYPES : offers
    CLINICS ||--o{ CONSULTATION_TOPICS : defines
    CLINICS ||--o{ APPOINTMENTS : schedules

    DOCTORS ||--o{ DOCTOR_SCHEDULES : has
    DOCTORS ||--o{ DOCTOR_ABSENCES : has
    DOCTORS ||--o{ APPOINTMENTS : assigned

    TREATMENT_TYPES ||--o{ TREATMENT_EQUIPMENTS : requires
    TREATMENT_TYPES ||--o{ APPOINTMENTS : type_of
    EQUIPMENTS ||--o{ TREATMENT_EQUIPMENTS : used_in
    EQUIPMENTS ||--o{ APPOINTMENTS : allocated

    APPOINTMENTS ||--o{ APPOINTMENT_NOTES : has
    APPOINTMENTS ||--o{ RESCHEDULE_CANDIDATES : has

    CLINICS {
        long id PK
        string name
        int slot_duration_minutes "default 30"
        string timezone "default Asia/Seoul"
        int max_concurrent_patients "default 1"
        boolean open_on_holidays "default false"
    }

    CLINIC_DEFAULT_BREAK_TIMES {
        long id PK
        long clinic_id FK
        string name
        time start_time
        time end_time
    }

    OPERATING_HOURS {
        long id PK
        long clinic_id FK
        enum day_of_week "MONDAY..SUNDAY"
        time open_time
        time close_time
        boolean is_active "default true"
    }

    BREAK_TIMES {
        long id PK
        long clinic_id FK
        enum day_of_week
        time start_time
        time end_time
    }

    CLINIC_CLOSURES {
        long id PK
        long clinic_id FK
        date closure_date
        string reason "nullable"
        boolean is_full_day "default true"
        time start_time "nullable"
        time end_time "nullable"
    }

    DOCTORS {
        long id PK
        long clinic_id FK
        string name
        string specialty "nullable"
        string provider_type "DOCTOR or CONSULTANT"
        int max_concurrent_patients "nullable"
    }

    DOCTOR_SCHEDULES {
        long id PK
        long doctor_id FK
        enum day_of_week
        time start_time
        time end_time
    }

    DOCTOR_ABSENCES {
        long id PK
        long doctor_id FK
        date absence_date
        time start_time "nullable (null=종일)"
        time end_time "nullable"
        string reason "nullable"
    }

    EQUIPMENTS {
        long id PK
        long clinic_id FK
        string name
        int usage_duration_minutes
        int quantity "default 1"
    }

    TREATMENT_TYPES {
        long id PK
        long clinic_id FK
        string name
        string category "TREATMENT PROCEDURE CONSULTATION"
        int default_duration_minutes
        string required_provider_type "DOCTOR or CONSULTANT"
        string consultation_method "nullable IN_PERSON PHONE VIDEO"
        boolean requires_equipment "default false"
        int max_concurrent_patients "nullable"
    }

    TREATMENT_EQUIPMENTS {
        long id PK
        long treatment_type_id FK
        long equipment_id FK
    }

    CONSULTATION_TOPICS {
        long id PK
        long clinic_id FK
        string name
        string description "nullable"
        int default_duration_minutes "default 30"
    }

    APPOINTMENTS {
        long id PK
        long clinic_id FK
        long doctor_id FK
        long treatment_type_id FK
        long equipment_id "FK nullable"
        long consultation_topic_id "FK nullable"
        string consultation_method "nullable"
        long reschedule_from_id "nullable"
        string patient_name
        string patient_phone "nullable"
        string patient_external_id "nullable"
        date appointment_date
        time start_time
        time end_time
        string status "default REQUESTED"
        timestamp created_at
        timestamp updated_at
    }

    APPOINTMENT_NOTES {
        long id PK
        long appointment_id FK
        string note_type
        string content
        string created_by "nullable"
        timestamp created_at
    }

    RESCHEDULE_CANDIDATES {
        long id PK
        long original_appointment_id FK
        date candidate_date
        time start_time
        time end_time
        long doctor_id FK
        int priority "default 0"
        boolean selected "default false"
        timestamp created_at
    }
```

---

## Appointment State Transition Diagram

```mermaid
stateDiagram-v2
    [*] --> PENDING : 예약 생성

    PENDING --> REQUESTED : Request
    PENDING --> CANCELLED : Cancel

    REQUESTED --> CONFIRMED : Confirm
    REQUESTED --> PENDING_RESCHEDULE : RequestReschedule (임시휴진)
    REQUESTED --> CANCELLED : Cancel

    CONFIRMED --> CHECKED_IN : CheckIn
    CONFIRMED --> NO_SHOW : MarkNoShow
    CONFIRMED --> PENDING : Reschedule
    CONFIRMED --> PENDING_RESCHEDULE : RequestReschedule (임시휴진)
    CONFIRMED --> CANCELLED : Cancel

    CHECKED_IN --> IN_PROGRESS : StartTreatment
    CHECKED_IN --> CANCELLED : Cancel

    IN_PROGRESS --> COMPLETED : Complete

    PENDING_RESCHEDULE --> RESCHEDULED : ConfirmReschedule
    PENDING_RESCHEDULE --> CANCELLED : Cancel

    COMPLETED --> [*]
    NO_SHOW --> [*]
    CANCELLED --> [*]
    RESCHEDULED --> [*]

    note right of PENDING_RESCHEDULE
        임시휴진으로 인한
        재배정 대기 상태
    end note

    note right of RESCHEDULED
        새 예약이 생성되고
        원래 예약은 종료
    end note
```

### State & Event 매핑

| 현재 상태 | 이벤트 | 다음 상태 |
|-----------|--------|-----------|
| `PENDING` | `Request` | `REQUESTED` |
| `PENDING` | `Cancel(reason)` | `CANCELLED` |
| `REQUESTED` | `Confirm` | `CONFIRMED` |
| `REQUESTED` | `RequestReschedule(reason)` | `PENDING_RESCHEDULE` |
| `REQUESTED` | `Cancel(reason)` | `CANCELLED` |
| `CONFIRMED` | `CheckIn` | `CHECKED_IN` |
| `CONFIRMED` | `MarkNoShow` | `NO_SHOW` |
| `CONFIRMED` | `Reschedule` | `PENDING` |
| `CONFIRMED` | `RequestReschedule(reason)` | `PENDING_RESCHEDULE` |
| `CONFIRMED` | `Cancel(reason)` | `CANCELLED` |
| `CHECKED_IN` | `StartTreatment` | `IN_PROGRESS` |
| `CHECKED_IN` | `Cancel(reason)` | `CANCELLED` |
| `IN_PROGRESS` | `Complete` | `COMPLETED` |
| `PENDING_RESCHEDULE` | `ConfirmReschedule` | `RESCHEDULED` |
| `PENDING_RESCHEDULE` | `Cancel(reason)` | `CANCELLED` |

---

## Slot Calculation Service

### 슬롯 계산 흐름

```mermaid
sequenceDiagram
    participant Client
    participant SCS as SlotCalculationService
    participant DB as Database
    participant TR as TimeRange Utils
    participant CR as ConcurrencyResolver

    Client->>SCS: findAvailableSlots(SlotQuery)
    activate SCS

    SCS->>DB: Load Clinic (slotDuration, maxConcurrent, openOnHolidays)
    DB-->>SCS: clinicRow

    SCS->>DB: Check Holidays (date)
    Note over SCS: 공휴일 + !openOnHolidays → 빈 리스트

    SCS->>DB: Check ClinicClosures (date)
    DB-->>SCS: closures
    Note over SCS: 종일 휴진이면 빈 리스트 반환

    SCS->>DB: Get OperatingHours (dayOfWeek)
    DB-->>SCS: openTime, closeTime

    SCS->>DB: Get BreakTimes (dayOfWeek)
    DB-->>SCS: breakTimeRanges

    SCS->>DB: Get DoctorSchedules (dayOfWeek)
    DB-->>SCS: doctorStart, doctorEnd

    SCS->>DB: Get DoctorAbsences (date)
    DB-->>SCS: absences

    SCS->>TR: computeEffectiveRanges(clinic, doctor, breaks, closures, absences)
    TR-->>SCS: effectiveRanges

    SCS->>DB: Get TreatmentType (duration, requiredProviderType)
    DB-->>SCS: treatmentRow

    SCS->>DB: Get Doctor (providerType, maxConcurrentPatients)
    DB-->>SCS: doctorRow
    Note over SCS: providerType 불일치 → 빈 리스트

    SCS->>CR: resolveMaxConcurrent(clinic, doctor, treatment)
    CR-->>SCS: maxConcurrent

    loop 각 슬롯 후보
        SCS->>DB: countOverlappingAppointments
        alt 용량 초과
            Note over SCS: 슬롯 제외
        else 장비 필요
            SCS->>DB: countEquipmentUsage
        end
    end

    SCS-->>Client: List<AvailableSlot>
    deactivate SCS
```

### Provider Type 검증

```mermaid
graph TD
    Q{TreatmentType<br/>requiredProviderType}
    D{Doctor<br/>providerType}
    Q --> C{일치?}
    D --> C
    C -->|Yes| SLOTS[슬롯 계산 진행]
    C -->|No| EMPTY[빈 리스트 반환]

    style SLOTS fill:#c8e6c9,stroke:#2e7d32
    style EMPTY fill:#ffccbc,stroke:#bf360c
```

### Max Concurrent Patients Resolution (3-Level Cascade)

```mermaid
graph TD
    Q{treatmentType<br/>maxConcurrentPatients?}
    Q -->|not null| T[Treatment 값 사용]
    Q -->|null| D{doctor<br/>maxConcurrentPatients?}
    D -->|not null| DR[Doctor 값 사용]
    D -->|null| C[Clinic 기본값 사용]

    style T fill:#c8e6c9,stroke:#2e7d32
    style DR fill:#fff9c4,stroke:#f9a825
    style C fill:#ffccbc,stroke:#bf360c
```

### Effective Time Range Calculation

```mermaid
graph LR
    subgraph Input
        CO[Clinic Open/Close<br/>09:00 - 18:00]
        DS[Doctor Schedule<br/>10:00 - 17:00]
        BT[Break Times<br/>12:00 - 13:00]
        PC[Partial Closure<br/>15:00 - 16:00]
        DA[Doctor Absence<br/>-]
    end

    subgraph Step1["1. Intersection"]
        IS[10:00 - 17:00]
    end

    subgraph Step2["2. Subtract Breaks"]
        S2A[10:00 - 12:00]
        S2B[13:00 - 17:00]
    end

    subgraph Step3["3. Subtract Closures"]
        S3A[10:00 - 12:00]
        S3B[13:00 - 15:00]
        S3C[16:00 - 17:00]
    end

    subgraph Output["Effective Ranges"]
        direction TB
        O1["10:00 - 12:00 (4 slots)"]
        O2["13:00 - 15:00 (4 slots)"]
        O3["16:00 - 17:00 (2 slots)"]
    end

    CO --> IS
    DS --> IS
    IS --> S2A
    IS --> S2B
    BT -.->|제외| IS
    S2A --> S3A
    S2B --> S3B
    S2B --> S3C
    PC -.->|제외| S2B
    S3A --> O1
    S3B --> O2
    S3C --> O3

    style Output fill:#e8f5e9,stroke:#388e3c
```

---

## Event Module

### Event Flow Diagram

```mermaid
sequenceDiagram
    participant App as Application
    participant SM as AppointmentStateMachine
    participant Pub as ApplicationEventPublisher
    participant Logger as AppointmentEventLogger
    participant DB as EventLogs Table

    App->>SM: transition(REQUESTED, Confirm)
    SM-->>App: CONFIRMED
    Note over SM: onTransition 콜백 호출

    App->>Pub: publishEvent(StatusChanged)

    Pub->>Logger: @EventListener onStatusChanged()
    Logger->>DB: INSERT INTO scheduling_appointment_event_logs
    Note over DB: eventType="StatusChanged"<br/>entityType="Appointment"<br/>payloadJson={...}
```

---

## Project Structure

```
scheduling/
├── README.md
├── appointment-core/
│   ├── README.md
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/io/bluetape4k/scheduling/appointment/
│       │   ├── model/
│       │   │   ├── tables/                # 17 Exposed Table objects
│       │   │   │   ├── Holidays.kt
│       │   │   │   ├── Clinics.kt
│       │   │   │   ├── ClinicDefaultBreakTimes.kt
│       │   │   │   ├── OperatingHoursTable.kt
│       │   │   │   ├── BreakTimes.kt
│       │   │   │   ├── ClinicClosures.kt
│       │   │   │   ├── Doctors.kt
│       │   │   │   ├── DoctorSchedules.kt
│       │   │   │   ├── DoctorAbsences.kt
│       │   │   │   ├── Equipments.kt
│       │   │   │   ├── TreatmentTypes.kt
│       │   │   │   ├── TreatmentEquipments.kt
│       │   │   │   ├── ConsultationTopics.kt
│       │   │   │   ├── Appointments.kt
│       │   │   │   ├── AppointmentNotes.kt
│       │   │   │   └── RescheduleCandidates.kt
│       │   │   └── dto/                   # 17 Record data classes
│       │   │       ├── HolidayRecord.kt
│       │   │       ├── ClinicRecord.kt
│       │   │       ├── ClinicDefaultBreakTimeRecord.kt
│       │   │       ├── OperatingHoursRecord.kt
│       │   │       ├── BreakTimeRecord.kt
│       │   │       ├── ClinicClosureRecord.kt
│       │   │       ├── DoctorRecord.kt
│       │   │       ├── DoctorScheduleRecord.kt
│       │   │       ├── DoctorAbsenceRecord.kt
│       │   │       ├── EquipmentRecord.kt
│       │   │       ├── TreatmentTypeRecord.kt
│       │   │       ├── TreatmentEquipmentRecord.kt
│       │   │       ├── ConsultationTopicRecord.kt
│       │   │       ├── AppointmentRecord.kt
│       │   │       ├── AppointmentNoteRecord.kt
│       │   │       └── RescheduleCandidateRecord.kt
│       │   ├── repository/               # Aggregate Root Repository (6개)
│       │   │   ├── RecordMappers.kt         # ResultRow → Record 변환
│       │   │   ├── ClinicRepository.kt
│       │   │   ├── DoctorRepository.kt
│       │   │   ├── TreatmentTypeRepository.kt
│       │   │   ├── AppointmentRepository.kt
│       │   │   ├── HolidayRepository.kt
│       │   │   └── RescheduleCandidateRepository.kt
│       │   ├── statemachine/              # 상태 머신 (10 states, 13 events)
│       │   │   ├── AppointmentState.kt
│       │   │   ├── AppointmentEvent.kt
│       │   │   └── AppointmentStateMachine.kt
│       │   └── service/                   # 슬롯 계산 + 재배정 서비스
│       │       ├── SlotCalculationService.kt
│       │       ├── ClosureRescheduleService.kt
│       │       ├── ConcurrencyResolver.kt
│       │       └── model/
│       │           ├── TimeRange.kt
│       │           ├── SlotQuery.kt
│       │           └── AvailableSlot.kt
│       └── test/kotlin/io/bluetape4k/scheduling/appointment/
│           ├── model/tables/TableSchemaTest.kt
│           ├── statemachine/AppointmentStateMachineTest.kt
│           └── service/
│               ├── SlotCalculationServiceTest.kt       # 21 tests
│               ├── ClosureRescheduleServiceTest.kt     # 6 tests
│               ├── ResolveMaxConcurrentTest.kt
│               └── model/TimeRangeTest.kt
├── appointment-event/
│   ├── README.md
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/io/bluetape4k/scheduling/appointment/event/
│       │   ├── AppointmentDomainEvent.kt
│       │   ├── AppointmentEventLogs.kt
│       │   ├── AppointmentEventLogRecord.kt
│       │   └── AppointmentEventLogger.kt
│       └── test/kotlin/io/bluetape4k/scheduling/appointment/event/
│           └── EventLogTest.kt
├── appointment-solver/
│   ├── README.md
│   └── build.gradle.kts
└── appointment-api/
    ├── README.md
    └── build.gradle.kts
```

---

## Getting Started

### Prerequisites

- JDK 25+
- Gradle 9.4+

### Build & Test

```bash
# appointment-core 테스트
./gradlew :appointment-core:test

# appointment-event 테스트
./gradlew :appointment-event:test

# 개별 테스트 실행
./gradlew :appointment-core:test --tests "*.SlotCalculationServiceTest"
./gradlew :appointment-core:test --tests "*.ClosureRescheduleServiceTest"
./gradlew :appointment-core:test --tests "*.AppointmentStateMachineTest"
./gradlew :appointment-core:test --tests "*.TimeRangeTest"
./gradlew :appointment-core:test --tests "*.ResolveMaxConcurrentTest"
./gradlew :appointment-core:test --tests "*.TableSchemaTest"
./gradlew :appointment-event:test --tests "*.EventLogTest"
```

### Usage Example

```kotlin
import io.bluetape4k.scheduling.appointment.service.SlotCalculationService
import io.bluetape4k.scheduling.appointment.service.ClosureRescheduleService
import io.bluetape4k.scheduling.appointment.service.model.SlotQuery
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.LocalDate

// 1. DB 연결
Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

// 2. 슬롯 조회
val slotService = SlotCalculationService()
val slots = slotService.findAvailableSlots(
    SlotQuery(
        clinicId = 1L,
        doctorId = 1L,          // 의사 또는 상담사 ID
        treatmentTypeId = 1L,   // provider type 자동 검증
        date = LocalDate.of(2026, 3, 20)
    )
)

slots.forEach { slot ->
    println("${slot.startTime}-${slot.endTime} (잔여: ${slot.remainingCapacity}명)")
}

// 3. 임시휴진 재배정
val rescheduleService = ClosureRescheduleService(slotService)

// 후보 생성
val candidates = rescheduleService.processClosureReschedule(
    clinicId = 1L,
    closureDate = LocalDate.of(2026, 3, 23),
    searchDays = 7
)

// 관리자 수동 선택
rescheduleService.confirmReschedule(candidateId = 42L)

// 또는 자동 재배정 (최우선순위 후보 선택)
rescheduleService.autoReschedule(originalAppointmentId = 1L)
```

### State Machine Usage

```kotlin
import io.bluetape4k.scheduling.appointment.statemachine.*

val sm = AppointmentStateMachine { from, event, to ->
    println("$from --[$event]--> $to")
}

// 일반 예약 흐름
var state: AppointmentState = AppointmentState.PENDING
state = sm.transition(state, AppointmentEvent.Request)        // → REQUESTED
state = sm.transition(state, AppointmentEvent.Confirm)         // → CONFIRMED
state = sm.transition(state, AppointmentEvent.CheckIn)         // → CHECKED_IN
state = sm.transition(state, AppointmentEvent.StartTreatment)  // → IN_PROGRESS
state = sm.transition(state, AppointmentEvent.Complete)         // → COMPLETED

// 임시휴진 재배정 흐름
var state2: AppointmentState = AppointmentState.CONFIRMED
state2 = sm.transition(state2, AppointmentEvent.RequestReschedule("임시휴진"))
// → PENDING_RESCHEDULE
state2 = sm.transition(state2, AppointmentEvent.ConfirmReschedule)
// → RESCHEDULED
```

---

## Design Decisions

| 항목 | 결정 | 이유 |
|------|------|------|
| 환자 모델 | 인라인 (patient_name, patient_phone 등) | 별도 테이블 불필요, 예약에 직접 포함 |
| 멀티 병원 | 공유 DB + clinic_id FK | 단순하면서 병원 간 데이터 격리 |
| 상태 관리 | Kotlin sealed class | 컴파일 타임 안전성, StateMachine 마이그레이션 가능 |
| 동시 예약 | 3단계 cascade (Clinic > Doctor > TreatmentType) | 세밀한 제어 가능 |
| 이벤트 | Spring ApplicationEvent + DB 로그 | 느슨한 결합 + 감사 추적 |
| 데이터 접근 패턴 | Aggregate Root Repository (LongJdbcRepository) | DDD 원칙 준수, 서비스에서 직접 쿼리 제거 |
| 테이블 접두사 | `scheduling_` | 다른 모듈과 네임스페이스 충돌 방지 |
| Provider 구분 | Doctors 테이블에 providerType 컬럼 | 테이블 리네이밍 없이 유형 구분 |
| 공휴일 | 별도 Holidays 테이블 + 병원별 opt-in | 전체 적용 + 예외 허용 |
| 병원 휴식시간 | ClinicDefaultBreakTimes 별도 테이블 | 하루 여러 번 휴식 지원, 요일 무관 적용 |
| 재배정 | 후보군 생성 + 관리자 선택/자동 모드 | 유연한 운영 정책 지원 |

---

## Roadmap

- [x] **Phase 1**: 도메인 모델 (Tables, DTOs, State Machine, Events)
- [x] **Phase 2**: 가용 슬롯 조회 서비스
- [x] **Phase 2.5**: 의사/상담사 구분, 상담 방식/주제, 공휴일, 임시휴진 재배정
- [ ] **Phase 3**: Timefold Solver 기반 자동 스케줄 최적화
- [ ] **Phase 4**: WebFlux REST API + 예약 CRUD
- [ ] **Phase 5**: Spring Boot Auto-Configuration + 알림 연동
