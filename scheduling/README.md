# Appointment Scheduling System

병원 진료 예약 스케줄링 시스템의 백엔드 구현체입니다.
멀티 병원(Clinic) 환경을 지원하며, 의사 스케줄 / 장비 가용성 / 동시 수용 인원을 고려한 **가용 슬롯 조회** 기능을 제공합니다.

---

## System Architecture

```mermaid
graph TB
    subgraph "scheduling/"
        subgraph core["appointment-core"]
            direction TB
            Tables["Exposed Tables<br/>(12 tables)"]
            DTOs["DTO Data Classes"]
            SM["AppointmentStateMachine"]
            TR["TimeRange Utilities"]
            CR["ConcurrencyResolver"]
            SCS["SlotCalculationService"]

            Tables --> DTOs
            TR --> SCS
            CR --> SCS
            Tables --> SCS
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
| `appointment-core` | `:appointment-core` | 도메인 모델 + 상태 머신 + 슬롯 계산 | **Phase 1-2 완료** |
| `appointment-event` | `:appointment-event` | 이벤트 정의 + 이벤트 로그 DB 저장 | **Phase 1 완료** |
| `appointment-solver` | `:appointment-solver` | Timefold 기반 최적화 | Phase 3+ stub |
| `appointment-api` | `:appointment-api` | WebFlux REST API | Phase 3+ stub |

---

## Data Model (ER Diagram)

```mermaid
erDiagram
    CLINICS ||--o{ OPERATING_HOURS : has
    CLINICS ||--o{ BREAK_TIMES : has
    CLINICS ||--o{ CLINIC_CLOSURES : has
    CLINICS ||--o{ DOCTORS : employs
    CLINICS ||--o{ EQUIPMENTS : owns
    CLINICS ||--o{ TREATMENT_TYPES : offers
    CLINICS ||--o{ APPOINTMENTS : schedules

    DOCTORS ||--o{ DOCTOR_SCHEDULES : has
    DOCTORS ||--o{ DOCTOR_ABSENCES : has
    DOCTORS ||--o{ APPOINTMENTS : assigned

    TREATMENT_TYPES ||--o{ TREATMENT_EQUIPMENTS : requires
    TREATMENT_TYPES ||--o{ APPOINTMENTS : type_of
    EQUIPMENTS ||--o{ TREATMENT_EQUIPMENTS : used_in
    EQUIPMENTS ||--o{ APPOINTMENTS : allocated

    APPOINTMENTS ||--o{ APPOINTMENT_NOTES : has

    CLINICS {
        long id PK
        string name
        int slot_duration_minutes "default 30"
        string timezone "default Asia/Seoul"
        int max_concurrent_patients "default 1"
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
        int default_duration_minutes
        boolean requires_equipment "default false"
        int max_concurrent_patients "nullable"
    }

    TREATMENT_EQUIPMENTS {
        long id PK
        long treatment_type_id FK
        long equipment_id FK
    }

    APPOINTMENTS {
        long id PK
        long clinic_id FK
        long doctor_id FK
        long treatment_type_id FK
        long equipment_id "FK nullable"
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
```

---

## Appointment State Transition Diagram

```mermaid
stateDiagram-v2
    [*] --> PENDING : 예약 생성

    PENDING --> REQUESTED : Request
    PENDING --> CANCELLED : Cancel

    REQUESTED --> CONFIRMED : Confirm
    REQUESTED --> CANCELLED : Cancel

    CONFIRMED --> CHECKED_IN : CheckIn
    CONFIRMED --> NO_SHOW : MarkNoShow
    CONFIRMED --> CANCELLED : Cancel
    CONFIRMED --> PENDING : Reschedule

    CHECKED_IN --> IN_PROGRESS : StartTreatment
    CHECKED_IN --> CANCELLED : Cancel

    IN_PROGRESS --> COMPLETED : Complete

    COMPLETED --> [*]
    NO_SHOW --> [*]
    CANCELLED --> [*]

    note right of PENDING
        가예약 / 미확정
    end note

    note right of CONFIRMED
        Reschedule 시
        PENDING으로 복귀
    end note

    note right of CANCELLED
        PENDING, REQUESTED,
        CONFIRMED, CHECKED_IN
        에서 취소 가능
    end note
```

### State & Event 매핑

| 현재 상태 | 이벤트 | 다음 상태 |
|-----------|--------|-----------|
| `PENDING` | `Request` | `REQUESTED` |
| `PENDING` | `Cancel(reason)` | `CANCELLED` |
| `REQUESTED` | `Confirm` | `CONFIRMED` |
| `REQUESTED` | `Cancel(reason)` | `CANCELLED` |
| `CONFIRMED` | `CheckIn` | `CHECKED_IN` |
| `CONFIRMED` | `MarkNoShow` | `NO_SHOW` |
| `CONFIRMED` | `Cancel(reason)` | `CANCELLED` |
| `CONFIRMED` | `Reschedule` | `PENDING` |
| `CHECKED_IN` | `StartTreatment` | `IN_PROGRESS` |
| `CHECKED_IN` | `Cancel(reason)` | `CANCELLED` |
| `IN_PROGRESS` | `Complete` | `COMPLETED` |

---

## Slot Calculation Service

### Class Diagram

```mermaid
classDiagram
    class SlotQuery {
        +Long clinicId
        +Long doctorId
        +Long treatmentTypeId
        +LocalDate date
        +Int? requestedDurationMinutes
    }

    class AvailableSlot {
        +LocalDate date
        +LocalTime startTime
        +LocalTime endTime
        +Long doctorId
        +List~Long~ equipmentIds
        +Int remainingCapacity
    }

    class TimeRange {
        +LocalTime start
        +LocalTime end
        +contains(LocalTime) Boolean
        +overlaps(TimeRange) Boolean
        +duration() Duration
    }

    class SlotCalculationService {
        +findAvailableSlots(SlotQuery) List~AvailableSlot~
        -countOverlappingAppointments(Long, LocalDate, LocalTime, LocalTime) Int
        -countEquipmentUsage(Long, LocalDate, LocalTime, LocalTime) Int
    }

    class ConcurrencyResolver {
        +resolveMaxConcurrent(Int, Int?, Int?) Int
    }

    SlotCalculationService ..> SlotQuery : input
    SlotCalculationService ..> AvailableSlot : output
    SlotCalculationService ..> TimeRange : uses
    SlotCalculationService ..> ConcurrencyResolver : uses

    class AppointmentStateMachine {
        -Map transitions
        -onTransition suspend callback
        +transition(AppointmentState, AppointmentEvent) AppointmentState
        +canTransition(AppointmentState, AppointmentEvent) Boolean
        +allowedEvents(AppointmentState) Set~Class~
    }

    class AppointmentState {
        <<sealed>>
        +String name
    }
    AppointmentState <|-- PENDING
    AppointmentState <|-- REQUESTED
    AppointmentState <|-- CONFIRMED
    AppointmentState <|-- CHECKED_IN
    AppointmentState <|-- IN_PROGRESS
    AppointmentState <|-- COMPLETED
    AppointmentState <|-- NO_SHOW
    AppointmentState <|-- CANCELLED

    class AppointmentEvent {
        <<sealed>>
    }
    AppointmentEvent <|-- Request
    AppointmentEvent <|-- Confirm
    AppointmentEvent <|-- CheckIn
    AppointmentEvent <|-- StartTreatment
    AppointmentEvent <|-- Complete
    AppointmentEvent <|-- Cancel
    AppointmentEvent <|-- MarkNoShow
    AppointmentEvent <|-- Reschedule

    AppointmentStateMachine ..> AppointmentState : manages
    AppointmentStateMachine ..> AppointmentEvent : processes
```

### Slot Calculation Sequence Diagram

```mermaid
sequenceDiagram
    participant Client
    participant SCS as SlotCalculationService
    participant DB as Database
    participant TR as TimeRange Utils
    participant CR as ConcurrencyResolver

    Client->>SCS: findAvailableSlots(SlotQuery)
    activate SCS

    SCS->>DB: Load Clinic (slotDuration, maxConcurrent)
    DB-->>SCS: clinicRow

    SCS->>DB: Check ClinicClosures (date)
    DB-->>SCS: closures
    Note over SCS: 종일 휴진이면 빈 리스트 반환

    SCS->>DB: Get OperatingHours (dayOfWeek)
    DB-->>SCS: openTime, closeTime

    SCS->>DB: Get BreakTimes (dayOfWeek)
    DB-->>SCS: breakTimeRanges

    SCS->>DB: Get DoctorSchedules (dayOfWeek)
    DB-->>SCS: doctorStart, doctorEnd
    Note over SCS: 스케줄 없으면 빈 리스트 반환

    SCS->>DB: Get DoctorAbsences (date)
    DB-->>SCS: absences
    Note over SCS: 종일 부재면 빈 리스트 반환

    SCS->>TR: computeEffectiveRanges(clinic, doctor, breaks, closures, absences)
    TR-->>SCS: effectiveRanges

    SCS->>DB: Get TreatmentType (duration, requiresEquipment)
    DB-->>SCS: treatmentRow

    SCS->>DB: Get Doctor (maxConcurrentPatients)
    DB-->>SCS: doctorRow

    SCS->>CR: resolveMaxConcurrent(clinic, doctor, treatment)
    CR-->>SCS: maxConcurrent

    Note over SCS: effectiveRanges에서<br/>slotDuration 간격으로<br/>슬롯 후보 생성

    loop 각 슬롯 후보
        SCS->>DB: countOverlappingAppointments(doctor, date, slot)
        DB-->>SCS: overlappingCount

        alt overlappingCount >= maxConcurrent
            Note over SCS: 슬롯 제외 (용량 초과)
        else requiresEquipment
            SCS->>DB: countEquipmentUsage(equipment, date, slot)
            DB-->>SCS: usedCount
            alt usedCount >= quantity
                Note over SCS: 슬롯 제외 (장비 부족)
            else
                Note over SCS: 슬롯 추가 (잔여 용량 계산)
            end
        else
            Note over SCS: 슬롯 추가 (잔여 용량 계산)
        end
    end

    SCS-->>Client: List<AvailableSlot>
    deactivate SCS
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

### Domain Event Class Diagram

```mermaid
classDiagram
    class ApplicationEvent {
        <<Spring Framework>>
    }

    class AppointmentDomainEvent {
        <<sealed>>
        +Instant occurredAt
    }

    ApplicationEvent <|-- AppointmentDomainEvent

    class Created {
        +Long appointmentId
        +Long clinicId
    }

    class StatusChanged {
        +Long appointmentId
        +Long clinicId
        +String fromState
        +String toState
        +String? reason
    }

    class Cancelled {
        +Long appointmentId
        +Long clinicId
        +String reason
    }

    AppointmentDomainEvent <|-- Created
    AppointmentDomainEvent <|-- StatusChanged
    AppointmentDomainEvent <|-- Cancelled

    class AppointmentEventLogger {
        <<@Component>>
        +onCreated(Created) void
        +onStatusChanged(StatusChanged) void
        +onCancelled(Cancelled) void
        -saveEventLog(String, Long, Long, String) void
    }

    class AppointmentEventLogs {
        <<LongIdTable>>
        +Column eventType
        +Column entityType
        +Column entityId
        +Column clinicId
        +Column payloadJson
        +Column createdAt
    }

    AppointmentEventLogger ..> AppointmentDomainEvent : listens
    AppointmentEventLogger ..> AppointmentEventLogs : writes
```

---

## Project Structure

```
scheduling/
├── README.md
├── appointment-core/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/io/bluetape4k/scheduling/appointment/
│       │   ├── model/
│       │   │   ├── tables/          # 12 Exposed Table objects
│       │   │   │   ├── Clinics.kt
│       │   │   │   ├── OperatingHoursTable.kt
│       │   │   │   ├── BreakTimes.kt
│       │   │   │   ├── ClinicClosures.kt
│       │   │   │   ├── Doctors.kt
│       │   │   │   ├── DoctorSchedules.kt
│       │   │   │   ├── DoctorAbsences.kt
│       │   │   │   ├── Equipments.kt
│       │   │   │   ├── TreatmentTypes.kt
│       │   │   │   ├── TreatmentEquipments.kt
│       │   │   │   ├── Appointments.kt
│       │   │   │   └── AppointmentNotes.kt
│       │   │   └── dto/             # 12 DTO data classes
│       │   │       ├── ClinicDto.kt
│       │   │       ├── OperatingHoursDto.kt
│       │   │       ├── BreakTimeDto.kt
│       │   │       ├── ClinicClosureDto.kt
│       │   │       ├── DoctorDto.kt
│       │   │       ├── DoctorScheduleDto.kt
│       │   │       ├── DoctorAbsenceDto.kt
│       │   │       ├── EquipmentDto.kt
│       │   │       ├── TreatmentTypeDto.kt
│       │   │       ├── TreatmentEquipmentDto.kt
│       │   │       ├── AppointmentDto.kt
│       │   │       └── AppointmentNoteDto.kt
│       │   ├── statemachine/        # 상태 머신
│       │   │   ├── AppointmentState.kt
│       │   │   ├── AppointmentEvent.kt
│       │   │   └── AppointmentStateMachine.kt
│       │   └── service/             # 슬롯 계산 서비스
│       │       ├── SlotCalculationService.kt
│       │       ├── ConcurrencyResolver.kt
│       │       └── model/
│       │           ├── TimeRange.kt
│       │           ├── SlotQuery.kt
│       │           └── AvailableSlot.kt
│       └── test/kotlin/io/bluetape4k/scheduling/appointment/
│           ├── model/tables/TableSchemaTest.kt
│           ├── statemachine/AppointmentStateMachineTest.kt
│           └── service/
│               ├── SlotCalculationServiceTest.kt
│               ├── ResolveMaxConcurrentTest.kt
│               └── model/TimeRangeTest.kt
├── appointment-event/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/io/bluetape4k/scheduling/appointment/event/
│       │   ├── AppointmentDomainEvent.kt
│       │   ├── AppointmentEventLogs.kt
│       │   ├── AppointmentEventLogDto.kt
│       │   └── AppointmentEventLogger.kt
│       └── test/kotlin/io/bluetape4k/scheduling/appointment/event/
│           └── EventLogTest.kt
├── appointment-solver/               # Phase 3+ stub
│   └── build.gradle.kts
└── appointment-api/                   # Phase 3+ stub
    └── build.gradle.kts
```

---

## Getting Started

### Prerequisites

- JDK 25+
- Gradle 9.4+

### Build & Test

```bash
# appointment-core 테스트 (63 tests)
./gradlew :appointment-core:test

# appointment-event 테스트 (5 tests)
./gradlew :appointment-event:test

# 개별 테스트 실행
./gradlew :appointment-core:test --tests "*.TableSchemaTest"
./gradlew :appointment-core:test --tests "*.AppointmentStateMachineTest"
./gradlew :appointment-core:test --tests "*.TimeRangeTest"
./gradlew :appointment-core:test --tests "*.ResolveMaxConcurrentTest"
./gradlew :appointment-core:test --tests "*.SlotCalculationServiceTest"
./gradlew :appointment-event:test --tests "*.EventLogTest"
```

### Usage Example

```kotlin
import io.bluetape4k.scheduling.appointment.service.SlotCalculationService
import io.bluetape4k.scheduling.appointment.service.model.SlotQuery
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.LocalDate

// 1. DB 연결
Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

// 2. 슬롯 조회
val service = SlotCalculationService()
val slots = service.findAvailableSlots(
    SlotQuery(
        clinicId = 1L,
        doctorId = 1L,
        treatmentTypeId = 1L,
        date = LocalDate.of(2026, 3, 20)
    )
)

// 3. 결과 활용
slots.forEach { slot ->
    println("${slot.startTime}-${slot.endTime} (잔여: ${slot.remainingCapacity}명)")
}
```

### State Machine Usage

```kotlin
import io.bluetape4k.scheduling.appointment.statemachine.*

val sm = AppointmentStateMachine { from, event, to ->
    println("$from --[$event]--> $to")
}

// 예약 흐름
var state: AppointmentState = AppointmentState.PENDING
state = sm.transition(state, AppointmentEvent.Request)      // → REQUESTED
state = sm.transition(state, AppointmentEvent.Confirm)       // → CONFIRMED
state = sm.transition(state, AppointmentEvent.CheckIn)       // → CHECKED_IN
state = sm.transition(state, AppointmentEvent.StartTreatment) // → IN_PROGRESS
state = sm.transition(state, AppointmentEvent.Complete)       // → COMPLETED

// 허용된 이벤트 확인
val allowed = sm.allowedEvents(AppointmentState.CONFIRMED)
// → {CheckIn, MarkNoShow, Cancel, Reschedule}

// 전이 가능 여부 확인
sm.canTransition(AppointmentState.COMPLETED, AppointmentEvent.Cancel("test"))
// → false (완료 후 취소 불가)
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
| 데이터 접근 | Exposed JDBC (서비스) + R2DBC (API 준비) | 서비스 계층은 동기, API는 비동기 지원 |
| 테이블 접두사 | `scheduling_` | 다른 모듈과 네임스페이스 충돌 방지 |

---

## Roadmap

- [x] **Phase 1**: 도메인 모델 (Tables, DTOs, State Machine, Events)
- [x] **Phase 2**: 가용 슬롯 조회 서비스
- [ ] **Phase 3**: Timefold Solver 기반 자동 스케줄 최적화
- [ ] **Phase 4**: WebFlux REST API + 예약 CRUD
- [ ] **Phase 5**: Spring Boot Auto-Configuration + 알림 연동
