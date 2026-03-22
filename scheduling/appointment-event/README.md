# appointment-event

예약 도메인 이벤트 정의 및 이벤트 로그 DB 저장 모듈입니다.

---

## 패키지 구조

```
io.bluetape4k.scheduling.appointment.event
├── AppointmentDomainEvent.kt       # 도메인 이벤트 sealed class
├── AppointmentEventLogRecord.kt    # 이벤트 로그 Record
├── AppointmentEventLogs.kt         # Exposed Table
└── AppointmentEventLogger.kt       # Spring @EventListener 기반 로거
```

---

## 도메인 이벤트

```kotlin
sealed class AppointmentDomainEvent {
    data class StatusChanged(
        val appointmentId: Long,
        val fromStatus: String,
        val toStatus: String,
        val timestamp: Instant
    ) : AppointmentDomainEvent()

    data class Created(
        val appointmentId: Long,
        val clinicId: Long,
        val doctorId: Long,
        val treatmentTypeId: Long,
        val appointmentDate: LocalDate,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val timestamp: Instant
    ) : AppointmentDomainEvent()

    data class Cancelled(
        val appointmentId: Long,
        val reason: String?,
        val timestamp: Instant
    ) : AppointmentDomainEvent()

    data class Rescheduled(
        val originalAppointmentId: Long,
        val newAppointmentId: Long,
        val newDate: LocalDate,
        val newStartTime: LocalTime,
        val newEndTime: LocalTime,
        val timestamp: Instant
    ) : AppointmentDomainEvent()
}
```

---

## 이벤트 로그 테이블

`scheduling_appointment_event_logs` — 모든 도메인 이벤트를 JSON payload와 함께 DB에 기록합니다.

| 칼럼 | 타입 | 설명 |
|------|------|------|
| `id` | Long | Primary Key |
| `event_type` | String | 이벤트 타입 (StatusChanged, Created, Cancelled, Rescheduled) |
| `entity_type` | String | 엔티티 타입 (항상 "Appointment") |
| `entity_id` | Long | 예약 ID |
| `payload_json` | Text | 이벤트 전체 JSON |
| `created_at` | Timestamp | 생성 시각 |

---

## 이벤트 로거

`AppointmentEventLogger` — Spring의 `@EventListener`를 사용하여 도메인 이벤트를 DB에 기록합니다.

```kotlin
@Component
class AppointmentEventLogger {
    @EventListener
    suspend fun onStatusChanged(event: AppointmentDomainEvent.StatusChanged) {
        // DB에 이벤트 로그 저장
    }

    @EventListener
    suspend fun onCreated(event: AppointmentDomainEvent.Created) {
        // DB에 이벤트 로그 저장
    }

    // ... onCancelled, onRescheduled
}
```

---

## 이벤트 흐름

1. Application에서 예약 상태 변경
2. `AppointmentStateMachine`이 상태 전이 수행
3. Domain Event (`StatusChanged` 등) 발행
4. Spring `ApplicationEventPublisher`가 리스너 호출
5. `AppointmentEventLogger`가 DB에 이벤트 로그 저장

---

## 테스트

```bash
./gradlew :appointment-event:test
```

---

## 의존성

- `appointment-core` 모듈의 테이블 정의 참조
- Spring Framework (ApplicationEventPublisher)
- JetBrains Exposed (v1 API)

---

## 주요 특징

- **감사 추적**: 모든 예약 상태 변경을 DB에 영구 기록
- **JSON Payload**: 이벤트 상세 정보를 구조화된 JSON으로 저장
- **Spring 통합**: ApplicationEventPublisher와 @EventListener 활용
- **Record 기반**: 불변 데이터 클래스로 안전성 확보
