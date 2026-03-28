# appointment-frontend

병원 예약 스케줄링 시스템의 Angular 프론트엔드입니다.

## 기술 스택

- Angular 21 (Standalone Components + Signals)
- Angular Material + SCSS
- TypeScript 5.9
- Vitest (단위 테스트)
- Gradle Node Plugin 7.1.0 (빌드 통합)

## 개발 환경 설정

```bash
# 의존성 설치
npm install

# 개발 서버 실행 (API 프록시 포함)
npm start
# → http://localhost:4200 (API → http://localhost:8080/api 프록시)

# 프로덕션 빌드
npm run build

# 테스트 실행
ng test
```

### Gradle 통합 빌드

```bash
# 빌드
./gradlew :appointment-frontend:build

# 테스트
./gradlew :appointment-frontend:test
```

## 프로젝트 구조

```
src/app/
├── core/
│   ├── models/          # TypeScript 인터페이스 (Appointment, Doctor, Slot 등)
│   ├── services/        # API 연동 서비스 (Signals 기반 상태 관리)
│   ├── interceptors/    # HTTP 인터셉터 (JWT 인증, 에러 핸들링)
│   └── guards/          # 라우트 가드 (역할 기반 접근 제어)
├── shared/
│   ├── pipes/           # StatusLabel, TimeRange 파이프
│   └── components/      # StatusBadge, TimeSlotPicker, ConfirmDialog
├── features/
│   ├── calendar/        # 캘린더 뷰 (일/주/월 - CSS Grid 자체 구현)
│   ├── appointments/    # 예약 CRUD (목록/상세/생성/수정)
│   └── management/      # 관리 페이지 (클리닉/의사/진료유형 조회)
└── app.ts               # 앱 셸 (반응형 사이드네비/하단탭)
```

## 주요 기능

- **캘린더 뷰**: 일간(의사별 컬럼), 주간(히트맵), 월간(배지) 3종 뷰
- **예약 관리**: 목록 필터링, 상세 조회, 상태 전이, 생성/수정 폼
- **관리 페이지**: 클리닉/의사/진료유형 조회 (읽기 전용 MVP)
- **인증**: JWT 기반 역할 관리 (ADMIN, STAFF, DOCTOR, PATIENT)
- **반응형**: 데스크톱(사이드네비) / 모바일(하단탭)

## 라우팅 메모

- 기본 진입 경로는 `/calendar` 입니다.
- 캘린더는 `day`, `week`, `month` 라우트로 분기되며 `CalendarStateService` 로 현재 날짜와 뷰 상태를 관리합니다.
- 권한이 없는 사용자는 `role.guard` 를 통해 `/calendar` 로 리다이렉트됩니다.

## API 프록시

개발 시 `/api/*` 요청은 `http://localhost:8080`으로 프록시됩니다.
백엔드(`appointment-api`) 모듈을 먼저 실행해야 합니다.

## 의존성

- 런타임 백엔드 의존성은 `appointment-api` 모듈입니다.
- 개발 시 Angular dev server 프록시를 통해 `/api/*` 요청을 `http://localhost:8080` 으로 전달합니다.
