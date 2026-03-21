# 병원 예약 스케줄링 시스템 - Angular 프론트엔드 설계 Spec

> 작성일: 2026-03-21
> 대상 모듈: `scheduling/appointment-frontend/`
> Angular 19 + Angular Material
> MVP 1차 범위

---

## 1. 아키텍처 개요

### 1.1 모듈 구조

```
appointment-frontend/
├── build.gradle.kts              # Gradle-Node 통합
├── package.json
├── angular.json
├── tsconfig.json
├── src/
│   ├── main.ts                   # standalone bootstrap
│   ├── index.html
│   ├── styles.scss               # 글로벌 테마 (Angular Material)
│   └── app/
│       ├── app.component.ts
│       ├── app.config.ts         # provideRouter, provideHttpClient 등
│       ├── app.routes.ts         # 최상위 라우팅
│       ├── core/                 # 싱글턴 서비스, 인터셉터, 가드
│       │   ├── services/
│       │   │   ├── appointment.service.ts
│       │   │   ├── slot.service.ts
│       │   │   ├── doctor.service.ts
│       │   │   ├── treatment-type.service.ts
│       │   │   └── auth.service.ts
│       │   ├── interceptors/
│       │   │   ├── auth.interceptor.ts       # JWT Bearer 토큰 주입
│       │   │   └── error.interceptor.ts      # ApiResponse.error 통합 처리
│       │   ├── guards/
│       │   │   └── role.guard.ts             # ADMIN/STAFF vs PATIENT 분기
│       │   └── models/
│       │       ├── api-response.model.ts
│       │       ├── appointment.model.ts
│       │       ├── slot.model.ts
│       │       ├── doctor.model.ts
│       │       ├── treatment-type.model.ts
│       │       └── clinic.model.ts
│       ├── features/
│       │   ├── appointments/     # 예약 CRUD
│       │   │   ├── appointment-list/
│       │   │   ├── appointment-detail/
│       │   │   ├── appointment-form/
│       │   │   └── appointments.routes.ts
│       │   ├── calendar/         # 캘린더 뷰
│       │   │   ├── calendar-view/
│       │   │   ├── day-view/
│       │   │   ├── week-view/
│       │   │   ├── month-view/
│       │   │   └── calendar.routes.ts
│       │   └── management/      # 의사/진료 유형 관리
│       │       ├── doctor-list/
│       │       ├── treatment-type-list/
│       │       └── management.routes.ts
│       └── shared/               # 재사용 컴포넌트
│           ├── components/
│           │   ├── status-badge/            # 예약 상태 배지
│           │   ├── time-slot-picker/        # 슬롯 선택 UI
│           │   └── confirm-dialog/          # 취소/변경 확인
│           └── pipes/
│               ├── status-label.pipe.ts     # 상태 한글 변환
│               └── time-range.pipe.ts       # "09:00 ~ 09:30" 형식
```

### 1.2 라우팅

```
/                           → /calendar (리다이렉트)
/calendar                   → 캘린더 뷰 (기본: 주간)
/calendar/day/:date         → 일간 뷰
/calendar/week/:date        → 주간 뷰
/calendar/month/:date       → 월간 뷰
/appointments               → 예약 목록
/appointments/new           → 예약 생성 (ADMIN/STAFF 전용)
/appointments/:id           → 예약 상세
/appointments/:id/edit      → 예약 수정 (ADMIN/STAFF 전용)
/management/doctors         → 의사 관리 (ADMIN 전용)
/management/treatment-types → 진료 유형 관리 (ADMIN 전용)
```

### 1.3 상태 관리 전략

Angular 19 Signals 기반 경량 상태 관리 (NgRx 미도입).

- 각 서비스에서 `signal()`로 로컬 상태 관리
- 서비스 간 공유가 필요한 상태: `computed()`로 파생
- 캘린더 날짜/뷰 모드: `CalendarStateService`에서 signal로 관리
- 서버 데이터: `HttpClient` + RxJS로 요청, 결과를 signal에 저장

```typescript
// 예시: appointment.service.ts
@Injectable({ providedIn: 'root' })
export class AppointmentService {
  private http = inject(HttpClient);

  appointments = signal<Appointment[]>([]);
  loading = signal(false);

  loadByDateRange(clinicId: number, startDate: string, endDate: string) {
    this.loading.set(true);
    this.http.get<ApiResponse<Appointment[]>>('/api/appointments', {
      params: { clinicId, startDate, endDate }
    }).subscribe(res => {
      this.appointments.set(res.data ?? []);
      this.loading.set(false);
    });
  }
}
```

---

## 2. 페이지/컴포넌트 설계

### 2.1 캘린더 뷰 (`/calendar`)

| 컴포넌트 | 역할 | 접근 권한 |
|---------|------|----------|
| `CalendarViewComponent` | 뷰 모드 전환 툴바 + 날짜 네비게이션 | 전체 |
| `DayViewComponent` | 시간 축 그리드에 예약 블록 표시 | 전체 |
| `WeekViewComponent` | 7일 x 시간 그리드 | 전체 |
| `MonthViewComponent` | 달력 셀에 예약 건수 + 클릭 시 일간 뷰 이동 | 전체 |

### 2.2 예약 CRUD (`/appointments`)

| 컴포넌트 | 역할 | 접근 권한 |
|---------|------|----------|
| `AppointmentListComponent` | 기간별 예약 목록, 필터(의사/상태), 페이지네이션 | 전체 |
| `AppointmentDetailComponent` | 단건 상세 + 상태 전이 버튼 | 전체 (상태 변경은 ADMIN/STAFF) |
| `AppointmentFormComponent` | 생성/수정 폼 (의사, 진료유형, 날짜, 슬롯 선택) | ADMIN/STAFF |

### 2.3 관리 (`/management`)

| 컴포넌트 | 역할 | 접근 권한 |
|---------|------|----------|
| `DoctorListComponent` | 의사 목록 조회 | ADMIN |
| `TreatmentTypeListComponent` | 진료 유형 목록 조회 | ADMIN |

> MVP에서는 의사/진료유형 **조회만** 지원. 생성/수정은 2차 범위.

### 2.4 역할 기반 접근 제어

백엔드 `SchedulingRole`과 동일한 4개 역할:

| 역할 | 읽기 | 예약 생성/수정 | 상태 변경 | 관리 메뉴 |
|------|------|--------------|----------|----------|
| ADMIN | O | O | O | O |
| STAFF | O | O | O | X |
| DOCTOR | O | X | X | X |
| PATIENT | O (본인만) | X | X | X |

JWT 토큰의 `roles` claim에서 역할을 추출하며, `RoleGuard`가 라우트 단위로 접근 제어.

---

## 3. API 연동

### 3.1 백엔드 API 엔드포인트 매핑

백엔드 응답은 모두 `ApiResponse<T>` 래퍼:
```typescript
interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
}
```

#### AppointmentService

| 프론트엔드 메서드 | HTTP | 백엔드 엔드포인트 | 비고 |
|----------------|------|-----------------|------|
| `getByDateRange(clinicId, startDate, endDate)` | GET | `/api/appointments?clinicId=&startDate=&endDate=` | 목록 조회 |
| `getById(id)` | GET | `/api/appointments/{id}` | 단건 조회 |
| `create(request)` | POST | `/api/appointments` | 예약 생성 |
| `updateStatus(id, status, reason?)` | PATCH | `/api/appointments/{id}/status` | 상태 전이 |
| `cancel(id)` | DELETE | `/api/appointments/{id}` | 예약 취소 |

#### SlotService

| 프론트엔드 메서드 | HTTP | 백엔드 엔드포인트 |
|----------------|------|-----------------|
| `getAvailableSlots(clinicId, doctorId, treatmentTypeId, date, durationMinutes?)` | GET | `/api/clinics/{clinicId}/slots?doctorId=&treatmentTypeId=&date=&requestedDurationMinutes=` |

#### RescheduleService (MVP에서 최소 지원)

| 프론트엔드 메서드 | HTTP | 백엔드 엔드포인트 |
|----------------|------|-----------------|
| `getCandidates(appointmentId)` | GET | `/api/appointments/{id}/reschedule/candidates` |
| `confirmReschedule(appointmentId, candidateId)` | POST | `/api/appointments/{id}/reschedule/confirm/{candidateId}` |
| `autoReschedule(appointmentId)` | POST | `/api/appointments/{id}/reschedule/auto` |

### 3.2 TypeScript 모델 (백엔드 DTO 매핑)

```typescript
// appointment.model.ts
export interface Appointment {
  id: number;
  clinicId: number;
  doctorId: number;
  treatmentTypeId: number;
  equipmentId?: number;
  patientName: string;
  patientPhone?: string;
  appointmentDate: string;       // "2026-03-21" (ISO LocalDate)
  startTime: string;             // "09:00" (ISO LocalTime)
  endTime: string;               // "09:30"
  status: AppointmentStatus;
  timezone?: string;
  locale?: string;
  createdAt?: string;            // ISO Instant
  updatedAt?: string;
}

export type AppointmentStatus =
  | 'REQUESTED' | 'CONFIRMED' | 'CHECKED_IN'
  | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  | 'NO_SHOW' | 'PENDING_RESCHEDULE' | 'RESCHEDULED';

export interface CreateAppointmentRequest {
  clinicId: number;
  doctorId: number;
  treatmentTypeId: number;
  equipmentId?: number;
  patientName: string;
  patientPhone?: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
}

export interface UpdateStatusRequest {
  status: string;
  reason?: string;
}

// slot.model.ts
export interface AvailableSlot {
  date: string;
  startTime: string;
  endTime: string;
  doctorId: number;
  equipmentIds: number[];
  remainingCapacity: number;
}

// doctor.model.ts
export interface Doctor {
  id: number;
  clinicId: number;
  name: string;
  specialty?: string;
  providerType: string;          // "DOCTOR"
  maxConcurrentPatients?: number;
}

// treatment-type.model.ts
export interface TreatmentType {
  id: number;
  clinicId: number;
  name: string;
  category: string;              // "TREATMENT"
  defaultDurationMinutes: number;
  requiredProviderType: string;
  consultationMethod?: string;
  requiresEquipment: boolean;
  maxConcurrentPatients?: number;
}

// clinic.model.ts
export interface Clinic {
  id: number;
  name: string;
  slotDurationMinutes: number;
  timezone: string;
  locale: string;
  maxConcurrentPatients: number;
  openOnHolidays: boolean;
}

// reschedule-candidate.model.ts
export interface RescheduleCandidate {
  id: number;
  originalAppointmentId: number;
  candidateDate: string;
  startTime: string;
  endTime: string;
  doctorId: number;
  priority: number;
  selected: boolean;
}
```

### 3.3 인터셉터

**AuthInterceptor**: `Authorization: Bearer <token>` 헤더 자동 주입.
토큰은 `AuthService`가 로컬 스토리지에서 관리 (외부 인증 서비스 발급 JWT).

**ErrorInterceptor**: HTTP 에러 및 `ApiResponse.success === false` 시 MatSnackBar로 에러 메시지 표시.
백엔드 에러 매핑:

| HTTP Status | 백엔드 Exception | 프론트 처리 |
|-------------|-----------------|-----------|
| 400 | `IllegalArgumentException` | 폼 유효성 에러 표시 |
| 404 | `NoSuchElementException` | "해당 예약을 찾을 수 없습니다" |
| 409 | `IllegalStateException` | 상태 전이 불가 안내 |
| 500 | `Exception` | 일반 에러 스낵바 |

---

## 4. 캘린더 뷰 구현 전략

### 4.1 라이브러리 선택

**자체 구현** (Angular Material 컴포넌트 조합).

이유:
- FullCalendar 등 외부 라이브러리는 Angular Material 테마와 불일치
- 병원 예약 특화 UX가 필요 (슬롯 단위 표시, 동시 수용 인원 표시 등)
- MVP 범위에서는 3개 뷰만 필요하므로 복잡도 제한적

### 4.2 뷰별 구현

#### 일간 뷰 (DayView)

```
┌─────────────────────────────────────────────┐
│ ← 2026-03-21 (토) →           [일|주|월]     │
├──────┬──────────────────────────────────────┤
│      │  Dr. 김철수    │  Dr. 이영희          │
├──────┼──────────────┼──────────────────────┤
│ 09:00│ ███ 홍길동    │                      │
│ 09:30│ ███ (진료중)  │ ███ 김환자            │
│ 10:00│              │ ███ (확정)            │
│ 10:30│ ███ 박환자    │                      │
│  ... │              │                      │
└──────┴──────────────┴──────────────────────┘
```

- X축: 의사별 컬럼 (clinicId 기준 의사 목록 조회)
- Y축: 시간 슬롯 (클리닉 `slotDurationMinutes` 기준, 기본 30분)
- 예약 블록: 상태에 따른 색상 코딩 + 환자명 표시
- 클릭: 상세 다이얼로그 열기

#### 주간 뷰 (WeekView)

```
┌──────┬────┬────┬────┬────┬────┬────┬────┐
│      │ 월 │ 화 │ 수 │ 목 │ 금 │ 토 │ 일 │
├──────┼────┼────┼────┼────┼────┼────┼────┤
│ 09:00│ 2  │ 3  │ 1  │ 4  │ 2  │ -  │ -  │
│ 09:30│ 1  │ 2  │ 3  │ 2  │ 0  │ -  │ -  │
│  ... │    │    │    │    │    │    │    │
└──────┴────┴────┴────┴────┴────┴────┴────┘
```

- 각 셀: 해당 시간대 예약 건수 표시
- 셀 클릭: 해당 일간 뷰로 이동
- 색상: 수용량 대비 예약 비율로 그라데이션 (녹/황/적)

#### 월간 뷰 (MonthView)

- Angular Material의 `mat-calendar`는 날짜 선택기 용도이므로, 커스텀 그리드 구현
- 각 날짜 셀: 예약 건수 배지 표시
- 날짜 클릭: 일간 뷰로 이동

### 4.3 상태 색상 코딩

| 상태 | 색상 | Material 팔레트 |
|------|------|----------------|
| REQUESTED | 파랑 | `primary` |
| CONFIRMED | 초록 | `accent` (green) |
| CHECKED_IN | 청록 | teal |
| IN_PROGRESS | 주황 | orange |
| COMPLETED | 회색 | grey |
| CANCELLED | 빨강 (취소선) | warn |
| NO_SHOW | 빨강 | warn |
| PENDING_RESCHEDULE | 노랑 | yellow |
| RESCHEDULED | 보라 | purple |

---

## 5. 반응형/웹뷰 대응

### 5.1 브레이크포인트

Angular CDK의 `BreakpointObserver` 사용:

| 브레이크포인트 | 너비 | 뷰 전략 |
|--------------|------|---------|
| Mobile | < 600px | 일간 뷰 기본, 목록은 카드 레이아웃 |
| Tablet | 600px ~ 1024px | 주간 뷰 기본, 축약 컬럼 |
| Desktop | > 1024px | 주간 뷰 기본, 전체 레이아웃 |

### 5.2 모바일 웹뷰 최적화

- **터치 제스처**: 좌우 스와이프로 날짜 이동 (HammerJS 또는 Angular CDK drag)
- **뷰포트**: `<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">`
- **네비게이션**: 하단 탭 바 (모바일), 사이드 네비게이션 (데스크톱)
- **폰트/터치 영역**: 최소 44px 터치 타겟, 14px 이상 폰트
- **Safe area**: iOS notch 대응 `env(safe-area-inset-*)`

### 5.3 네비게이션 구조

```
[Desktop]
┌─────────┬──────────────────────┐
│ 사이드   │                      │
│ 네비     │   메인 콘텐츠          │
│ ├ 캘린더 │                      │
│ ├ 예약   │                      │
│ └ 관리   │                      │
└─────────┴──────────────────────┘

[Mobile]
┌──────────────────────┐
│      메인 콘텐츠       │
│                      │
│                      │
├──────────────────────┤
│ 캘린더 │ 예약 │ 관리   │  ← 하단 탭
└──────────────────────┘
```

---

## 6. 빌드 통합

### 6.1 Gradle 설정

`scheduling/appointment-frontend/build.gradle.kts`:

```kotlin
plugins {
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    version.set("22.14.0")
    npmVersion.set("10.9.0")
    download.set(true)
}

tasks {
    val npmInstall by getting

    val npmBuild by registering(com.github.gradle.node.npm.task.NpmTask::class) {
        dependsOn(npmInstall)
        args.set(listOf("run", "build", "--", "--configuration=production"))
        inputs.dir("src")
        inputs.files("angular.json", "tsconfig.json", "package.json")
        outputs.dir("dist/appointment-frontend")
    }

    val npmTest by registering(com.github.gradle.node.npm.task.NpmTask::class) {
        dependsOn(npmInstall)
        args.set(listOf("run", "test", "--", "--watch=false", "--browsers=ChromeHeadless"))
    }

    val build by registering {
        dependsOn(npmBuild)
    }

    val test by registering {
        dependsOn(npmTest)
    }

    // API 서버 JAR에 정적 파일 포함 시
    val copyToApi by registering(Copy::class) {
        dependsOn(npmBuild)
        from("dist/appointment-frontend/browser")
        into("${project(":appointment-api").layout.buildDirectory.get()}/resources/main/static")
    }
}
```

### 6.2 개발 워크플로우

```bash
# 프론트엔드만 개발 서버 실행
cd scheduling/appointment-frontend && npx ng serve --proxy-config proxy.conf.json

# proxy.conf.json — 백엔드 API 프록시
# {
#   "/api": {
#     "target": "http://localhost:8080",
#     "secure": false
#   }
# }

# Gradle 통합 빌드
./gradlew :appointment-frontend:build

# 프론트엔드 테스트
./gradlew :appointment-frontend:test
```

### 6.3 settings.gradle.kts 등록

`scheduling/` 디렉토리는 `includeModules("scheduling", false, false)` 패턴이므로,
`appointment-frontend/` 폴더를 만들고 `build.gradle.kts`를 배치하면 자동 감지된다.

---

## 7. 테스트 전략

### 7.1 단위 테스트 (Karma + Jasmine)

| 대상 | 테스트 내용 | 커버리지 목표 |
|------|-----------|-------------|
| Services | API 호출 mock (`HttpClientTestingModule`), signal 상태 변경 검증 | 90% |
| Pipes | `StatusLabelPipe`: 모든 상태 한글 변환 검증 | 100% |
| Guards | `RoleGuard`: 역할별 접근 허용/거부 | 100% |
| Interceptors | JWT 토큰 주입, 에러 핸들링 | 90% |

### 7.2 컴포넌트 테스트

| 대상 | 테스트 내용 |
|------|-----------|
| `AppointmentFormComponent` | 필수 필드 유효성, 슬롯 선택 시 시간 자동 설정 |
| `AppointmentListComponent` | 필터 변경 시 목록 갱신, 빈 상태 UI |
| `StatusBadgeComponent` | 상태별 색상/라벨 렌더링 |
| `TimeSlotPickerComponent` | 가용 슬롯 표시, 선택 이벤트 발행 |

### 7.3 E2E 테스트 (향후 2차)

MVP에서는 E2E 미포함. 2차에서 Playwright로 다음 시나리오 커버 예정:
- 예약 생성 전체 플로우 (의사 선택 -> 슬롯 선택 -> 정보 입력 -> 확인)
- 캘린더 뷰 전환 및 날짜 탐색
- 상태 전이 플로우

---

## 부록: 예약 상태 전이 다이어그램

백엔드 `AppointmentStateMachine`에 정의된 상태 전이를 프론트엔드에서 반영:

```
PENDING → REQUESTED → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED
                 ↘          ↘           ↘
              CANCELLED   NO_SHOW    CANCELLED
                         PENDING_RESCHEDULE → RESCHEDULED
                                           ↘ CANCELLED
```

프론트엔드 `AppointmentDetailComponent`에서 현재 상태에 따라 허용 가능한 다음 상태 버튼만 활성화:

| 현재 상태 | 가능한 액션 버튼 |
|----------|---------------|
| REQUESTED | 확정, 취소 |
| CONFIRMED | 체크인, 재배정 요청, 취소 |
| CHECKED_IN | 진료 시작, 취소 |
| IN_PROGRESS | 진료 완료 |
| PENDING_RESCHEDULE | 재배정 확인, 취소 |

---

## 부록: MVP 제외 항목 (2차 범위)

- 알림 이력 조회
- 의사/진료유형 생성/수정/삭제 CRUD
- 환자 자가 예약 포털 (별도 앱 또는 별도 라우트)
- 다국어(i18n) 지원 (MVP는 한국어 only)
- E2E 테스트
- PWA / 오프라인 지원
- 임시휴진 관리 UI (`/api/appointments/{id}/reschedule/closure` 트리거 UI)
