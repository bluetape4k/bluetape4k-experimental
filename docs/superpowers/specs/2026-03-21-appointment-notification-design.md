# Phase 6: Appointment Notification Design

## Overview

병원 예약 알림 모듈. 예약 생성/확정/취소/재배정 시 알림 발송 + 리마인더 기능. 외부 알림 서비스는 Feign 호출 예정이므로, 인터페이스 설계 + 더미 구현체(로그+이력 저장).

## Module: appointment-notification

### 1. NotificationChannel Interface

```kotlin
interface NotificationChannel {
    fun sendCreated(appointment: AppointmentRecord)
    fun sendConfirmed(appointment: AppointmentRecord)
    fun sendCancelled(appointment: AppointmentRecord, reason: String?)
    fun sendRescheduled(original: AppointmentRecord, new: AppointmentRecord)
    fun sendReminder(appointment: AppointmentRecord, reminderType: ReminderType)
}

enum class ReminderType {
    DAY_BEFORE,
    SAME_DAY
}
```

### 2. NotificationHistory Table

```
scheduling_notification_history
- id: Long PK
- appointment_id: Long FK
- channel_type: String (DUMMY, EMAIL, SMS, PUSH)
- event_type: String (CREATED, CONFIRMED, CANCELLED, RESCHEDULED, REMINDER_DAY_BEFORE, REMINDER_SAME_DAY)
- recipient: String (nullable, 환자 연락처)
- payload_json: Text
- status: String (SUCCESS, FAILED)
- error_message: String (nullable)
- created_at: Timestamp
```

### 3. DummyNotificationChannel

- 로그 출력 (`log.info`)
- NotificationHistory 테이블에 이력 저장
- 항상 SUCCESS 반환

### 4. NotificationEventListener

- Spring `@EventListener`로 기존 `AppointmentDomainEvent` 구독
- `NotificationProperties` 설정에 따라 알림 on/off
- `NotificationChannel` 호출

### 5. AppointmentReminderScheduler

- `@Scheduled(fixedRate = 1시간)` 주기 실행
- 내일/오늘 예약 중 CONFIRMED 상태인 예약 대상
- 이미 발송한 리마인더는 중복 방지 (NotificationHistory 조회)

### 6. Configuration

```yaml
scheduling:
    notification:
        enabled: true
        events:
            created: true
            confirmed: true
            cancelled: true
            rescheduled: true
        reminder:
            enabled: true
            day-before: true
            same-day: true
            same-day-hours-before: 2
```

## Dependencies

```kotlin
api(project(":appointment-core"))
implementation(Libs.springBootStarter("web"))
```

## Test Plan

- NotificationEventListener 단위 테스트 (MockK)
- DummyNotificationChannel 이력 저장 테스트
- ReminderScheduler 중복 방지 테스트
- ConfigurationProperties on/off 테스트
