# appointment-notification

## 개요

예약 시스템의 알림(Notification) 모듈입니다. 도메인 이벤트 기반으로 예약 생성/확정/취소/재배정 알림을 발송하고, 스케줄러를 통해 리마인더를 전송합니다.

## 주요 기능

- **이벤트 기반 알림**: Spring `@EventListener`로 예약 도메인 이벤트(Created, Confirmed, Cancelled, Rescheduled) 수신 → 알림 발송
- **리마인더 스케줄러**: 매시간 실행되어 내일/당일 예약에 리마인더 발송 (중복 방지)
- **플러그인 채널**: `NotificationChannel` 인터페이스로 알림 채널 추상화, 운영 환경에서 Feign 기반 구현체로 교체 가능
- **이벤트별 on/off**: `NotificationProperties`로 이벤트별, 리마인더별 활성화/비활성화 설정
- **발송 이력**: `NotificationHistoryTable`에 모든 알림 발송 이력 저장
- **HA 리더 선출**: `LettuceLeaderGroupElection`으로 분산 환경에서 스케줄러 1개 인스턴스만 실행
- **장애 격리**: Resilience4j CircuitBreaker + Retry + Bulkhead로 외부 알림 서비스 장애 격리
- **중복 방지**: 성공 이력이 있는 리마인더는 재발송하지 않음

## 아키텍처

```
AppointmentDomainEvent
    ↓ (Spring Event)
NotificationEventListener
    ↓
ResilientNotificationChannel (CircuitBreaker + Retry + Bulkhead)
    ↓
NotificationChannel (interface)
    ├── DummyNotificationChannel (기본 - 로그 + 이력 저장)
    └── FeignNotificationChannel (운영 - 외부 서비스 호출)
    ↓
NotificationHistoryRepository → NotificationHistoryTable

AppointmentReminderScheduler
    ├── LettuceLeaderGroupElection (HA 리더 선출)
    └── ResilientNotificationChannel → NotificationChannel
```

### 알림 발송 플로우

```mermaid
sequenceDiagram
    participant SVC as AppointmentService\n/ ReminderScheduler
    participant EP as ApplicationEventPublisher
    participant EL as NotificationEventListener
    participant RC as ResilientNotificationChannel
    participant BH as Bulkhead
    participant RT as Retry
    participant CB as CircuitBreaker
    participant CH as NotificationChannel\n(Dummy / Feign)
    participant HR as NotificationHistoryRepository
    participant DB as notification_history

    SVC->>EP: publish(AppointmentDomainEvent)
    EP->>EL: @EventListener 호출
    EL->>EL: properties.enabled && events.X 확인
    alt 이벤트 비활성화
        EL-->>EP: (skip)
    else 이벤트 활성화
        EL->>RC: sendCreated / sendConfirmed / ...
        RC->>BH: 동시 호출 제한 (max 10, wait 1s)
        BH->>RT: 재시도 래핑 (max 3, 500ms)
        RT->>CB: 회로 차단기 확인
        alt CircuitBreaker OPEN
            CB-->>RC: CallNotPermittedException
            RC-->>EL: 예외 전파
        else CircuitBreaker CLOSED / HALF_OPEN
            CB->>CH: 실제 알림 발송
            CH-->>CB: 성공 / 실패
            CB-->>RT: 결과 반환
            RT-->>BH: 결과 반환
            BH-->>RC: 결과 반환
            RC->>HR: save(NotificationHistoryRecord)
            HR->>DB: INSERT notification_history
        end
    end
```

### Resilience4j 데코레이터 체인

```mermaid
flowchart LR
    IN([알림 요청]) --> BH

    subgraph Decorator["ResilientNotificationChannel 데코레이터 체인 (외→내)"]
        BH["Bulkhead\n동시 10건 / 대기 1s\n초과 시 BulkheadFullException"]
        RT["Retry\n최대 3회 / 500ms 간격\nIOException 등 재시도"]
        CB["CircuitBreaker\n실패율 50% / 슬로우콜 80%\n대기 30s / 슬라이딩 10건"]
        ACT["delegate.send*()\n실제 알림 채널 호출"]
    end

    BH --> RT --> CB --> ACT
    ACT -- 성공 --> CB
    CB -- 실패 --> RT
    RT -- 재시도 소진 --> BH
    BH -- 허용 초과 --> OUT_FAIL([BulkheadFullException])
    ACT -- 성공 반환 --> OUT_OK([완료])
```

### CircuitBreaker 상태 전이

```mermaid
stateDiagram-v2
    [*] --> CLOSED : 초기 상태

    CLOSED --> OPEN : 실패율 ≥ 50%\n또는 슬로우콜 ≥ 80%\n(슬라이딩 윈도우 10건, 최소 5건)

    OPEN --> HALF_OPEN : 대기 30초 경과\n(waitDurationInOpenState)

    HALF_OPEN --> CLOSED : 허용 호출 성공\n(permittedCallsInHalfOpenState)
    HALF_OPEN --> OPEN : 허용 호출 실패

    note right of CLOSED
        모든 호출 허용
        슬라이딩 윈도우로 통계 수집
    end note

    note right of OPEN
        모든 호출 즉시 차단
        CallNotPermittedException 발생
    end note

    note right of HALF_OPEN
        제한적 호출 허용
        회복 여부 판단
    end note
```

## 리마인더 동작

- `AppointmentReminderScheduler` 는 매시간 실행되어 대상 날짜의 `CONFIRMED` 예약을 조회합니다.
- 리마인더 대상 조회는 특정 클리닉에 고정하지 않고, 해당 날짜의 전체 예약을 기준으로 수행합니다.
- `NotificationHistoryRepository.existsByAppointmentAndEventType(...)` 로 성공 이력을 확인해 중복 발송을 막습니다.

## 설정

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
    resilience:
      circuit-breaker:
        failure-rate-threshold: 50
        slow-call-rate-threshold: 80
        wait-duration-in-open-state: 30s
        sliding-window-size: 10
        minimum-number-of-calls: 5
      retry:
        max-attempts: 3
        wait-duration: 500ms
      bulkhead:
        max-concurrent-calls: 10
        max-wait-duration: 1s
```

## 주요 컴포넌트

| 클래스 | 설명 |
|--------|------|
| `NotificationChannel` | 알림 채널 인터페이스 |
| `DummyNotificationChannel` | 더미 구현체 (로그 + DB 이력) |
| `ResilientNotificationChannel` | Resilience4j 데코레이터 (CB + Retry + Bulkhead) |
| `NotificationEventListener` | 도메인 이벤트 → 알림 발송 리스너 |
| `AppointmentReminderScheduler` | 리마인더 스케줄러 (매시간, HA 리더 선출) |
| `NotificationHistoryRepository` | 알림 이력 저장/조회 |
| `NotificationProperties` | 알림 설정 프로퍼티 |
| `NotificationResilienceProperties` | Resilience4j 설정 프로퍼티 |
| `NotificationAutoConfiguration` | Spring Boot Auto-Configuration |

## HA 구성

분산 환경(다중 인스턴스)에서 리마인더 스케줄러가 중복 실행되지 않도록 `LettuceLeaderGroupElection`을 사용합니다.

```kotlin
// Redis 연결이 있으면 자동으로 리더 선출 활성화
// maxLeaders = 1 (기본값) → 1개 인스턴스만 스케줄러 실행
@Bean
@ConditionalOnBean(StatefulRedisConnection::class)
fun notificationLeaderElection(connection: StatefulRedisConnection<String, String>) =
    connection.leaderGroupElection()
```

Redis가 없으면 리더 선출 없이 모든 인스턴스에서 실행됩니다 (단일 인스턴스 환경).

### LeaderGroupElection — 분산 리더 선출 흐름

```mermaid
sequenceDiagram
    participant I1 as 인스턴스 1\n(AppointmentReminderScheduler)
    participant I2 as 인스턴스 2\n(AppointmentReminderScheduler)
    participant I3 as 인스턴스 3\n(AppointmentReminderScheduler)
    participant LGE as LettuceLeaderGroupElection
    participant Redis as Redis\n(SET NX PX lockKey)

    Note over I1,I3: @Scheduled(fixedRate=3600000) 동시 트리거

    par 인스턴스 1
        I1->>LGE: runIfLeader("scheduling:reminder-scheduler")
        LGE->>Redis: SET reminder-scheduler:lock I1 NX PX ttl
        Redis-->>LGE: OK (락 획득)
        LGE->>I1: 블록 실행
        I1->>I1: doCheckReminders()
    and 인스턴스 2
        I2->>LGE: runIfLeader("scheduling:reminder-scheduler")
        LGE->>Redis: SET reminder-scheduler:lock I2 NX PX ttl
        Redis-->>LGE: nil (락 실패)
        LGE-->>I2: (skip — 리더 아님)
    and 인스턴스 3
        I3->>LGE: runIfLeader("scheduling:reminder-scheduler")
        LGE->>Redis: SET reminder-scheduler:lock I3 NX PX ttl
        Redis-->>LGE: nil (락 실패)
        LGE-->>I3: (skip — 리더 아님)
    end

    Note over I1: 리마인더 처리 완료 후 락 해제
```

### 리마인더 스케줄러 처리 흐름

```mermaid
flowchart TD
    TRIGGER([매시간 @Scheduled 트리거]) --> LEADER{LettuceLeaderGroupElection\n리더 여부}
    LEADER -- 리더 아님 --> SKIP([skip])
    LEADER -- 리더 --> DB_QUERY

    subgraph sg_db["내일 리마인더 (DAY_BEFORE)"]
        DB_QUERY[내일 날짜 CONFIRMED 예약 조회] --> DB_CHECK{성공 이력 있음?\nREMINDER_DAY_BEFORE}
        DB_CHECK -- 있음 --> DB_SKIP[발송 skip]
        DB_CHECK -- 없음 --> DB_SEND[sendReminder\nDAY_BEFORE]
        DB_SEND --> DB_HIST[이력 저장\nNotificationHistory]
    end

    DB_HIST --> SD_QUERY
    DB_SKIP --> SD_QUERY

    subgraph sg_sd["당일 리마인더 (SAME_DAY)"]
        SD_QUERY[오늘 날짜 CONFIRMED 예약 조회\nsameDayHoursBefore 이전] --> SD_CHECK{성공 이력 있음?\nREMINDER_SAME_DAY}
        SD_CHECK -- 있음 --> SD_SKIP[발송 skip]
        SD_CHECK -- 없음 --> SD_SEND[sendReminder\nSAME_DAY]
        SD_SEND --> SD_HIST[이력 저장\nNotificationHistory]
    end

    SD_HIST --> DONE([완료])
    SD_SKIP --> DONE
```

## 장애 격리

외부 알림 서비스 호출 시 `ResilientNotificationChannel`이 자동 적용됩니다.

| 패턴 | 기본값 | 설명 |
|------|--------|------|
| CircuitBreaker | 실패율 50%, 대기 30s | 연속 실패 시 회로 차단 |
| Retry | 최대 3회, 간격 500ms | 일시적 장애 재시도 |
| Bulkhead | 동시 10건, 대기 1s | 동시 호출 제한 |

## 사용 예제

```kotlin
// 커스텀 알림 채널 구현 (Feign 등)
@Component
class FeignNotificationChannel(
    private val notificationClient: NotificationFeignClient,
    private val historyRepository: NotificationHistoryRepository,
) : NotificationChannel {
    override val channelType = "FEIGN"

    override fun sendCreated(appointment: AppointmentRecord) {
        notificationClient.send(CreateNotificationRequest(appointment))
    }
    // ...
}
```

## 테스트

```bash
./gradlew :appointment-notification:test
```

- `DummyNotificationChannelTest` — 알림 발송 → 이력 저장 검증 (8개)
- `NotificationEventListenerTest` — 이벤트 수신 → 알림 호출 검증 (8개)
- `NotificationHistoryRepositoryTest` — 이력 CRUD + 중복 체크 검증 (6개)
- `ResilientNotificationChannelTest` — CircuitBreaker + Retry + Bulkhead 검증 (8개)
- `AppointmentReminderSchedulerTest` — 날짜 기준 조회 + 중복 발송 방지 검증 (2개)

2026-03-28 기준 모듈 테스트 41건 통과.

## 의존성

- `appointment-core` — 예약/클리닉/의사/진료유형 조회
- `appointment-event` — 도메인 이벤트 구독
- Resilience4j (`CircuitBreaker`, `Retry`, `Bulkhead`)
- Lettuce / leader election — 다중 인스턴스 스케줄러 조율
