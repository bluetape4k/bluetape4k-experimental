# 병원 예약 스케줄링 시스템 — Angular 프론트엔드 구현 계획

> **For agentic workers:**
> REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (
`- [ ]`) syntax for tracking.

**Goal:**
`scheduling/appointment-frontend/` 디렉토리에 Angular 19 + Angular Material 기반의 병원 예약 스케줄링 프론트엔드를 구현한다. MVP 1차 범위에 해당하며, 캘린더 뷰(일/주/월), 예약 CRUD, 관리 페이지, JWT 인증, 반응형 UI를 포함한다.

**Architecture:**

- Angular 19 standalone components (NgModule 없음)
- Angular Material 테마 + SCSS
- Signals 기반 상태 관리 (NgRx 미사용)
- 캘린더 자체 구현 (외부 라이브러리 미사용)
- Gradle Node Plugin 통합 빌드
- Karma + Jasmine 단위 테스트

**Tech Stack:**
Node.js 25, npm 11, Angular 19, Angular Material, TypeScript, SCSS, Karma/Jasmine, Gradle Node Plugin 7.1.0

---

## 의존 관계 그래프

```
Task 1 (프로젝트 생성)
  ├── Task 2 (Gradle 통합) — 병렬 가능: Task 3, 4
  ├── Task 3 (모델 + 핵심 서비스)
  │     └── Task 5 (인터셉터/가드) — Task 3 완료 후
  ├── Task 4 (앱 셸 + 라우팅 + 반응형 네비게이션)
  │     └── Task 7 (캘린더 뷰) — Task 3, 4 완료 후
  ├── Task 6 (공유 컴포넌트/파이프) — Task 3 완료 후
  │     └── Task 8 (예약 CRUD) — Task 5, 6, 7 완료 후
  │     └── Task 9 (관리 페이지) — Task 5, 6 완료 후
  └── Task 10 (테스트 강화) — Task 8, 9 완료 후
      └── Task 11 (최종 통합 검증) — Task 10 완료 후
```

---

## Task 1: Angular 프로젝트 생성 및 초기 설정

**complexity: low**
**의존: 없음 (최초 태스크)**
**병렬: 불가 — 모든 후속 태스크의 전제 조건**

- [ ] Angular CLI 전역 설치 (`npm install -g @angular/cli`)
- [ ] `scheduling/appointment-frontend/` 디렉토리에 Angular 19 프로젝트 생성
  ```bash
  cd scheduling && npx @angular/cli@19 new appointment-frontend \
    --style=scss --routing --standalone --skip-git --skip-tests=false
  ```
- [ ] Angular Material 추가
  ```bash
  cd scheduling/appointment-frontend && npx ng add @angular/material --skip-confirmation
  ```
- [ ] `angular.json` 확인: SCSS 설정, 빌드 출력 경로 `dist/appointment-frontend`
- [ ] `proxy.conf.json` 생성 (개발 서버용 API 프록시)
  ```json
  {
    "/api": {
      "target": "http://localhost:8080",
      "secure": false
    }
  }
  ```
- [ ] `package.json`의 `start` 스크립트에 프록시 설정 추가: `ng serve --proxy-config proxy.conf.json`

**산출물:** 빌드/서브 가능한 빈 Angular 프로젝트

---

## Task 2: Gradle Node Plugin 통합

**complexity: medium**
**의존: Task 1**
**병렬: Task 3, 4와 병렬 가능**

- [ ] `scheduling/appointment-frontend/build.gradle.kts` 작성
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

      val build by registering { dependsOn(npmBuild) }
      val test by registering { dependsOn(npmTest) }

      val copyToApi by registering(Copy::class) {
          dependsOn(npmBuild)
          from("dist/appointment-frontend/browser")
          into("${project(":appointment-api").layout.buildDirectory.get()}/resources/main/static")
      }
  }
  ```
- [ ] `settings.gradle.kts`에 `scheduling` 카테고리가 `includeModules`에 등록되어 있는지 확인
  - 미등록 시 `includeModules("scheduling", false, false)` 추가
- [ ] `./gradlew :appointment-frontend:build` 실행 검증
- [ ] `./gradlew :appointment-frontend:test` 실행 검증

**산출물:** Gradle에서 npm build/test를 실행할 수 있는 통합 빌드

---

## Task 3: TypeScript 모델 정의 + 핵심 서비스 구현

**complexity: medium**
**의존: Task 1**
**병렬: Task 2, 4와 병렬 가능**

### 3-1. 모델 정의

- [ ] `src/app/core/models/api-response.model.ts` — `ApiResponse<T>` 인터페이스
- [ ] `src/app/core/models/appointment.model.ts` — `Appointment`, `AppointmentStatus`, `CreateAppointmentRequest`, `UpdateStatusRequest`
- [ ] `src/app/core/models/slot.model.ts` — `AvailableSlot`
- [ ] `src/app/core/models/doctor.model.ts` — `Doctor`
- [ ] `src/app/core/models/treatment-type.model.ts` — `TreatmentType`
- [ ] `src/app/core/models/clinic.model.ts` — `Clinic`
- [ ] `src/app/core/models/reschedule-candidate.model.ts` — `RescheduleCandidate`
- [ ] 모델 barrel export: `src/app/core/models/index.ts`

### 3-2. 핵심 서비스

- [ ] `src/app/core/services/auth.service.ts`
  - JWT 토큰 로컬 스토리지 관리
  - `roles` claim 파싱 (`signal<string[]>`)
  - `isAdmin()`, `isStaff()`, `isDoctor()`, `isPatient()` computed signals
- [ ] `src/app/core/services/appointment.service.ts`
  - `appointments = signal<Appointment[]>([])`
  - `loading = signal(false)`
  - `getByDateRange()`, `getById()`, `create()`, `updateStatus()`, `cancel()`
- [ ] `src/app/core/services/slot.service.ts`
  - `getAvailableSlots()` — 가용 슬롯 조회
- [ ] `src/app/core/services/doctor.service.ts`
  - `doctors = signal<Doctor[]>([])`
  - `loadByClinic(clinicId)` — 의사 목록 조회
- [ ] `src/app/core/services/treatment-type.service.ts`
  - `treatmentTypes = signal<TreatmentType[]>([])`
  - `loadByClinic(clinicId)` — 진료 유형 목록 조회
- [ ] `src/app/core/services/calendar-state.service.ts`
  - `currentDate = signal<string>(today)`
  - `viewMode = signal<'day'|'week'|'month'>('week')`
  - `navigateNext()`, `navigatePrev()`, `goToToday()`
- [ ] 서비스 barrel export: `src/app/core/services/index.ts`

**산출물:** 모든 모델 타입 + API 연동 서비스 + 캘린더 상태 서비스

---

## Task 4: 앱 셸 + 라우팅 + 반응형 네비게이션

**complexity: medium**
**의존: Task 1**
**병렬: Task 2, 3과 병렬 가능**

- [ ] `src/app/app.config.ts` 설정
  - `provideRouter(routes)`
  - `provideHttpClient(withInterceptors([...]))`
  - `provideAnimationsAsync()`
- [ ] `src/app/app.routes.ts` — 최상위 라우팅 정의
  ```
  /               → redirect to /calendar
  /calendar       → lazy load calendar routes
  /appointments   → lazy load appointments routes
  /management     → lazy load management routes (canActivate: RoleGuard ADMIN)
  ```
- [ ] `src/app/app.component.ts` — 앱 셸 구현
  - 데스크톱: `mat-sidenav` 좌측 네비게이션
  - 모바일: 하단 `mat-tab-nav-bar` 탭 바
  - `BreakpointObserver`로 Mobile/Tablet/Desktop 분기
  - 상단 툴바: 앱 제목 + 사용자 역할 표시
- [ ] `src/styles.scss` — Angular Material 커스텀 테마 (병원 UX 적합 색상)
  - 상태 색상 CSS 변수 정의 (REQUESTED=파랑, CONFIRMED=초록 등)
- [ ] `src/index.html` — viewport meta, safe-area-inset 설정

**산출물:** 반응형 앱 셸 (사이드 네비 + 하단 탭) + 라우팅 골격

---

## Task 5: 인터셉터 + 가드

**complexity: high**
**의존: Task 3 (AuthService, 모델 필요)**
**병렬: Task 6과 병렬 가능**

- [ ] `src/app/core/interceptors/auth.interceptor.ts`
  - `HttpInterceptorFn` 함수형 인터셉터
  - `AuthService`에서 토큰 가져와 `Authorization: Bearer <token>` 헤더 주입
  - 토큰 없으면 헤더 미추가
- [ ] `src/app/core/interceptors/error.interceptor.ts`
  - HTTP 에러 상태코드별 분기 처리 (400/404/409/500)
  - `ApiResponse.success === false` 시 `error` 메시지 추출
  - `MatSnackBar`로 에러 메시지 표시
- [ ] `src/app/core/guards/role.guard.ts`
  - `CanActivateFn` 함수형 가드
  - 라우트 데이터의 `requiredRoles`와 JWT roles 비교
  - 미인가 시 `/calendar`로 리다이렉트
- [ ] `app.config.ts`에 인터셉터 등록: `withInterceptors([authInterceptor, errorInterceptor])`
- [ ] **단위 테스트**
  - `auth.interceptor.spec.ts`: 토큰 존재 시 헤더 주입, 미존재 시 미주입
  - `error.interceptor.spec.ts`: 상태코드별 스낵바 메시지 검증
  - `role.guard.spec.ts`: 역할별 접근 허용/거부 검증 (ADMIN, STAFF, DOCTOR, PATIENT)

**산출물:** JWT 인증 인터셉터, 에러 핸들링, 역할 기반 접근 제어 + 테스트

---

## Task 6: 공유 컴포넌트 및 파이프

**complexity: low**
**의존: Task 3 (모델 타입 필요)**
**병렬: Task 5와 병렬 가능**

- [ ] `src/app/shared/pipes/status-label.pipe.ts`
  - `AppointmentStatus` → 한글 라벨 변환
  - REQUESTED→"요청", CONFIRMED→"확정", CHECKED_IN→"체크인", IN_PROGRESS→"진료중", COMPLETED→"완료", CANCELLED→"취소", NO_SHOW→"미방문", PENDING_RESCHEDULE→"재배정 대기", RESCHEDULED→"재배정 완료"
- [ ] `src/app/shared/pipes/time-range.pipe.ts`
  - `{startTime, endTime}` → `"09:00 ~ 09:30"` 형식
- [ ] `src/app/shared/components/status-badge/status-badge.component.ts`
  - 상태별 색상 배지 (CSS 변수 활용)
  - Input: `status: AppointmentStatus`
- [ ] `src/app/shared/components/time-slot-picker/time-slot-picker.component.ts`
  - Input: `availableSlots: AvailableSlot[]`
  - Output: `slotSelected: EventEmitter<AvailableSlot>`
  - 슬롯 그리드 렌더링, 선택 하이라이트
- [ ] `src/app/shared/components/confirm-dialog/confirm-dialog.component.ts`
  - `MatDialog` 기반 확인/취소 다이얼로그
  - Input: 제목, 메시지, 확인 버튼 텍스트
- [ ] **단위 테스트**
  - `status-label.pipe.spec.ts`: 모든 9개 상태 변환 검증 (커버리지 100%)
  - `time-range.pipe.spec.ts`: 정상 입력 + 엣지 케이스
  - `status-badge.component.spec.ts`: 상태별 CSS 클래스 렌더링 검증
  - `time-slot-picker.component.spec.ts`: 슬롯 선택 이벤트 발행 검증

**산출물:** 재사용 가능한 공유 UI 컴포넌트 + 파이프 + 테스트

---

## Task 7: 캘린더 뷰 (자체 구현)

**complexity: high**
**의존: Task 3 (서비스), Task 4 (라우팅/셸)**
**병렬: 불가 — Task 3, 4 완료 후 진행**

### 7-1. 캘린더 라우팅 + 상위 컴포넌트

- [ ] `src/app/features/calendar/calendar.routes.ts`
  ```
  /calendar           → CalendarViewComponent (기본 주간 뷰)
  /calendar/day/:date → DayViewComponent
  /calendar/week/:date → WeekViewComponent
  /calendar/month/:date → MonthViewComponent
  ```
- [ ] `src/app/features/calendar/calendar-view/calendar-view.component.ts`
  - 뷰 모드 전환 툴바 (일/주/월 토글 버튼)
  - 날짜 네비게이션 (이전/다음/오늘)
  - `CalendarStateService` 연동
  - 하위 뷰 `<router-outlet>` 렌더링

### 7-2. 일간 뷰

- [ ] `src/app/features/calendar/day-view/day-view.component.ts`
  - X축: 의사별 컬럼 (`DoctorService.doctors` signal)
  - Y축: 시간 슬롯 그리드 (클리닉 `slotDurationMinutes` 기반, 기본 30분)
  - 예약 블록: 상태 색상 코딩 + 환자명 + 시간
  - 블록 클릭 시 예약 상세 다이얼로그 열기
  - CSS Grid 레이아웃

### 7-3. 주간 뷰

- [ ] `src/app/features/calendar/week-view/week-view.component.ts`
  - 7일 x 시간대 그리드
  - 각 셀: 해당 시간대 예약 건수 표시
  - 색상: 수용량 대비 예약 비율 그라데이션 (녹/황/적)
  - 셀 클릭 시 해당 날짜의 일간 뷰로 이동

### 7-4. 월간 뷰

- [ ] `src/app/features/calendar/month-view/month-view.component.ts`
  - 커스텀 달력 그리드 (7열 x 5~6행)
  - 각 날짜 셀: 예약 건수 배지 표시
  - 날짜 클릭 시 일간 뷰로 이동
  - 현재 월 이외 날짜 흐리게 표시

### 7-5. 반응형

- [ ] 모바일 (<600px): 일간 뷰 기본, 의사 컬럼 수평 스크롤
- [ ] 태블릿/데스크톱: 주간 뷰 기본
- [ ] 터치 스와이프 제스처로 날짜 이동 (CDK drag 또는 터치 이벤트)

**산출물:** 3종 캘린더 뷰 (일/주/월) + 날짜 네비게이션 + 반응형 레이아웃

---

## Task 8: 예약 CRUD 페이지

**complexity: high**
**의존: Task 5 (가드), Task 6 (공유 컴포넌트), Task 7 (캘린더 연동)**
**병렬: Task 9와 병렬 가능**

### 8-1. 라우팅

- [ ] `src/app/features/appointments/appointments.routes.ts`
  ```
  /appointments          → AppointmentListComponent
  /appointments/new      → AppointmentFormComponent (canActivate: RoleGuard ADMIN|STAFF)
  /appointments/:id      → AppointmentDetailComponent
  /appointments/:id/edit → AppointmentFormComponent (canActivate: RoleGuard ADMIN|STAFF)
  ```

### 8-2. 예약 목록

- [ ] `src/app/features/appointments/appointment-list/appointment-list.component.ts`
  - 기간별 필터 (날짜 범위 선택)
  - 의사 필터 (드롭다운)
  - 상태 필터 (체크박스 그룹)
  - `mat-table` 테이블 렌더링 + 페이지네이션 (`mat-paginator`)
  - 각 행 클릭 시 상세 페이지 이동
  - 빈 상태 UI (예약 없음 안내)

### 8-3. 예약 상세

- [ ] `src/app/features/appointments/appointment-detail/appointment-detail.component.ts`
  - 예약 정보 카드 (환자명, 의사, 진료유형, 날짜/시간, 상태)
  - 상태 전이 버튼: 현재 상태에 따라 허용 가능한 다음 상태만 표시
    - REQUESTED → [확정, 취소]
    - CONFIRMED → [체크인, 재배정 요청, 취소]
    - CHECKED_IN → [진료 시작, 취소]
    - IN_PROGRESS → [진료 완료]
    - PENDING_RESCHEDULE → [재배정 확인, 취소]
  - 상태 변경 시 `ConfirmDialogComponent`로 확인
  - ADMIN/STAFF만 상태 변경 버튼 표시

### 8-4. 예약 생성/수정 폼

- [ ] `src/app/features/appointments/appointment-form/appointment-form.component.ts`
  - Reactive Forms (`FormGroup`)
  - 필드: 의사 선택, 진료 유형 선택, 날짜 선택 (`mat-datepicker`), 슬롯 선택 (`TimeSlotPickerComponent`)
  - 의사/진료유형 변경 시 가용 슬롯 자동 조회 (`SlotService`)
  - 슬롯 선택 시 시작/종료 시간 자동 설정
  - 환자 정보 입력 (이름 필수, 전화번호 선택)
  - 유효성 검증 + 에러 메시지
  - 수정 모드: 기존 데이터 로딩 (`:id` 파라미터)

**산출물:** 예약 목록/상세/생성/수정 페이지 + 상태 전이 UI

---

## Task 9: 관리 페이지 (의사/진료유형 조회)

**complexity: low**
**의존: Task 5 (RoleGuard), Task 6 (공유 컴포넌트)**
**병렬: Task 8과 병렬 가능**

- [ ] `src/app/features/management/management.routes.ts`
  ```
  /management/doctors          → DoctorListComponent (canActivate: RoleGuard ADMIN)
  /management/treatment-types  → TreatmentTypeListComponent (canActivate: RoleGuard ADMIN)
  ```
- [ ] `src/app/features/management/doctor-list/doctor-list.component.ts`
  - `mat-table`로 의사 목록 표시 (이름, 전문 분야, 동시 진료 수)
  - MVP에서는 조회만 (생성/수정/삭제 버튼 없음)
- [ ] `src/app/features/management/treatment-type-list/treatment-type-list.component.ts`
  - `mat-table`로 진료 유형 목록 표시 (이름, 카테고리, 소요 시간, 장비 필요 여부)
  - MVP에서는 조회만

**산출물:** 관리 메뉴 (의사/진료유형 조회 전용)

---

## Task 10: 테스트 강화

**complexity: medium**
**의존: Task 8, Task 9 (모든 컴포넌트 구현 완료 후)**
**병렬: 불가**

### 10-1. 서비스 테스트

- [ ] `appointment.service.spec.ts` — `HttpClientTestingModule`로 API 호출 mock, signal 상태 변경 검증
- [ ] `slot.service.spec.ts` — 가용 슬롯 조회 mock + 응답 검증
- [ ] `doctor.service.spec.ts` — 의사 목록 조회 mock
- [ ] `treatment-type.service.spec.ts` — 진료 유형 목록 조회 mock
- [ ] `auth.service.spec.ts` — JWT 파싱, 역할 추출, 토큰 저장/삭제
- [ ] `calendar-state.service.spec.ts` — 날짜 네비게이션 로직 검증

### 10-2. 컴포넌트 테스트

- [ ] `appointment-form.component.spec.ts` — 필수 필드 유효성, 슬롯 선택 시 시간 자동 설정
- [ ] `appointment-list.component.spec.ts` — 필터 변경 시 목록 갱신, 빈 상태 UI
- [ ] `appointment-detail.component.spec.ts` — 상태별 버튼 표시/숨김

### 10-3. 커버리지 목표

| 대상 | 목표 |
|------|------|
| Services | 90% |
| Pipes | 100% |
| Guards | 100% |
| Interceptors | 90% |
| Components | 80% |

**산출물:** 전체 테스트 스위트, 커버리지 리포트

---

## Task 11: 최종 통합 검증

**complexity: medium**
**의존: Task 10 (모든 테스트 통과 후)**
**병렬: 불가 — 최종 태스크**

- [ ] `./gradlew :appointment-frontend:build` — 프로덕션 빌드 성공 확인
- [ ] `./gradlew :appointment-frontend:test` — 전체 테스트 통과 확인
- [ ] `npx ng serve --proxy-config proxy.conf.json` — 개발 서버 기동 확인
- [ ] 브라우저에서 주요 라우트 접근 확인
  - `/calendar` (기본 주간 뷰 렌더링)
  - `/appointments` (목록 페이지 렌더링)
  - `/management/doctors` (ADMIN 가드 동작)
- [ ] 반응형 확인: 모바일 뷰포트에서 하단 탭 바 렌더링
- [ ] 빌드 산출물 크기 확인 (`dist/appointment-frontend/`)
- [ ] `README.md` 작성 (개발 환경 설정, 빌드/실행 방법, 프로젝트 구조)

**산출물:** 빌드/테스트 통과, 동작 검증 완료

---

## 요약: 태스크별 실행 순서

| 순서 | 태스크 | complexity | 병렬 가능 |
|------|--------|-----------|----------|
| 1 | Task 1: 프로젝트 생성 | low | - |
| 2 | Task 2: Gradle 통합 | medium | Task 3, 4와 병렬 |
| 2 | Task 3: 모델 + 서비스 | medium | Task 2, 4와 병렬 |
| 2 | Task 4: 앱 셸 + 라우팅 | medium | Task 2, 3과 병렬 |
| 3 | Task 5: 인터셉터 + 가드 | high | Task 6과 병렬 |
| 3 | Task 6: 공유 컴포넌트 | low | Task 5와 병렬 |
| 4 | Task 7: 캘린더 뷰 | high | - |
| 5 | Task 8: 예약 CRUD | high | Task 9와 병렬 |
| 5 | Task 9: 관리 페이지 | low | Task 8과 병렬 |
| 6 | Task 10: 테스트 강화 | medium | - |
| 7 | Task 11: 최종 통합 검증 | medium | - |

**총 11개 태스크 / 7단계 (병렬 실행 시)**

### 예상 파일 수

| 카테고리 | 파일 수 |
|---------|--------|
| 모델 (*.model.ts) | 7 |
| 서비스 (*.service.ts) | 6 |
| 인터셉터/가드 | 3 |
| 컴포넌트 (*.component.ts) | 14 |
| 파이프 (*.pipe.ts) | 2 |
| 라우팅 (*.routes.ts) | 4 |
| 테스트 (*.spec.ts) | ~25 |
| 설정/빌드 | 5 |
| **합계** | **~66** |
