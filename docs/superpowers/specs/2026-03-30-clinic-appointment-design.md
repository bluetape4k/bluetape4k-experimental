# clinic-appointment 독립 저장소 설계

## 1. 프로젝트 개요

`bluetape4k-experimental/scheduling/` 하위 6개 모듈을 독립 GitHub 저장소 `bluetape4k/clinic-appointment`로 분리한다.
목표: bluetape4k-experimental과 무관하게 독립 빌드/배포 가능한 의료 예약 관리 시스템 구축.

### 목표

| # | 목표 | 측정 기준 |
|---|------|----------|
| G1 | 독립 빌드 | `./gradlew build` 성공 (bluetape4k-experimental 불필요) |
| G2 | 패키지 정리 | `io.bluetape4k.clinic.appointment.*` 통일 |
| G3 | bluetape4k 라이브러리 의존성 | Maven Central 좌표 사용 (includeBuild 없이) |
| G4 | CI/CD 독립 | GitHub Actions 파이프라인 자체 보유 |
| G5 | 향후 확장 용이 | 모듈 추가 시 settings.gradle.kts만 수정 |

---

## 2. 설계 결정

### 2.1 디렉토리 구조: 플랫 vs 계층 vs 하이브리드

| 접근법 | 구조 | 장점 | 단점 |
|--------|------|------|------|
| **A. 플랫** | `appointment-core/`, `appointment-api/` 등 루트 직하 | 단순, Gradle 설정 최소, IDE 탐색 용이 | 모듈 10개 이상 시 루트 혼잡 |
| **B. 계층** | `backend/core/`, `backend/api/`, `frontend/` | 논리적 그룹핑, 대규모에 적합 | Gradle 경로 복잡, includeModules 필요 |
| **C. 하이브리드** | 백엔드 플랫 + `frontend/` 분리 | Angular가 Kotlin과 빌드 체계 다름을 반영 | 약간의 비일관성 |

**추천: C. 하이브리드** -- 현재 6개 모듈은 플랫으로 충분하나, Angular 프론트엔드는 Node.js 기반이므로 `frontend/` 분리가 자연스럽다. 향후 `appointment-gateway`, `appointment-batch` 등 추가해도 10개 미만이면 플랫 유지.

### 2.2 buildSrc 구성 전략

| 접근법 | 설명 | 장점 | 단점 |
|--------|------|------|------|
| **A. 전체 복사** | bluetape4k-experimental의 Libs.kt 전체 가져오기 | 빠른 시작, 검증 완료 | 1600줄 중 사용하는 건 ~15%, 유지보수 부담 |
| **B. 최소화** | 실제 사용 의존성만 추출 | 깔끔, 이해 용이, 버전 관리 명확 | 초기 작업 소요 |
| **C. Version Catalog (libs.versions.toml)** | Gradle 공식 TOML 카탈로그 | 업계 표준, IDE 자동완성 | bluetape4k-projects와 방식 불일치 |

**추천: B. 최소화** -- 사용하는 의존성만 Libs.kt에 포함. 구조(object Versions / object Libs / object Plugins)는 bluetape4k와 동일하게 유지하여 친숙함을 보존. 향후 Version Catalog 전환 여지는 남김.

### 2.3 bluetape4k-projects 의존성 연결

| 접근법 | 설명 | 장점 | 단점 |
|--------|------|------|------|
| **A. Maven Central 좌표** | `io.github.bluetape4k:bluetape4k-xxx:1.5.0` | 독립적, CI에서 바로 빌드, 배포 무관 | SNAPSHOT 테스트 시 Central 배포 필요 |
| **B. includeBuild** | `settings.gradle.kts`에서 로컬 경로 참조 | 로컬 개발 시 즉각 반영 | 특정 디렉토리 구조 강제, CI 불가 |
| **C. 조건부 하이브리드** | 로컬에 bluetape4k-projects 있으면 includeBuild, 없으면 Maven | 양쪽 장점 | settings.gradle.kts 복잡도 증가 |

**추천: C. 조건부 하이브리드** -- 기존 bluetape4k-experimental과 동일한 패턴. `if (bluetape4kProjectsDir.exists())` 조건으로 로컬 개발 편의성 확보, CI에서는 Maven Central 자동 폴백.

### 2.4 모듈 이름 컨벤션

| 접근법 | 예시 | 장점 | 단점 |
|--------|------|------|------|
| **A. prefix 없음** | `:appointment-core` | 기존과 동일, 마이그레이션 최소 | 다른 프로젝트와 혼동 가능성 |
| **B. clinic- prefix** | `:clinic-appointment-core` | 명확한 네임스페이스 | 이름 길어짐, build.gradle.kts 수정 많음 |
| **C. 짧은 prefix** | `:clinic-core`, `:clinic-api` | 간결하면서 도메인 명확 | 기존 패턴과 다름, 호환성 깨짐 |

**추천: A. prefix 없음** -- 독립 저장소이므로 저장소명(`clinic-appointment`) 자체가 네임스페이스 역할. 모듈 내부에서 `project(":appointment-core")` 그대로 사용 가능하여 마이그레이션 비용 최소.

### 2.5 이전 순서

| 접근법 | 설명 | 장점 | 단점 |
|--------|------|------|------|
| **A. 빅뱅** | 6개 모듈 한 번에 이전 | 한 번에 끝남 | 빌드 실패 시 디버깅 복잡 |
| **B. 의존성 순서** | core -> event -> solver -> notification -> api -> frontend | 각 단계 검증 가능, 롤백 용이 | 시간 소요 |
| **C. 코어+API 먼저** | core+api 이전 -> 나머지 병렬 | 핵심 동작 빠른 검증 | event 없이 api 동작 확인 제한적 |

**추천: B. 의존성 순서** -- 각 모듈 이전 후 `./gradlew :appointment-xxx:build` 성공을 확인하며 진행. 의존성 그래프의 leaf부터 시작.

### 2.6 git 히스토리 보존 전략

| 접근법 | 설명 | 장점 | 단점 |
|--------|------|------|------|
| **A. git filter-repo** | 서브디렉토리 히스토리 추출 후 새 저장소에 이식 | 커밋 이력 보존 | 복잡, 커밋 해시 변경, 타 모듈 참조 정리 필요 |
| **B. 단순 소스 복사** | 파일만 복사, 새 저장소에서 초기 커밋 | 단순, 깨끗한 히스토리, 빠른 진행 | 과거 blame/log 불가 |

**결정: B. 단순 소스 복사** -- scheduling 모듈의 커밋 히스토리가 짧고(수십 커밋), 독립 저장소의 깨끗한 시작이 더 가치 있다. 필요 시 bluetape4k-experimental의 원본 히스토리를 참조할 수 있으므로 정보 손실은 없다.

---

## 3. 디렉토리 구조

```
clinic-appointment/
├── .github/
│   └── workflows/
│       ├── ci.yml                    # PR 빌드 + 테스트
│       └── release.yml               # 태그 기반 릴리스
├── buildSrc/
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── Libs.kt                   # Versions + Plugins + Libs (최소화)
├── appointment-core/
│   ├── build.gradle.kts
│   ├── README.md
│   └── src/
│       ├── main/kotlin/io/bluetape4k/clinic/appointment/
│       │   ├── model/
│       │   │   ├── dto/              # AppointmentRecord, ClinicRecord, DoctorRecord 등 16개 Record 클래스
│       │   │   └── tables/           # Exposed Table 정의 (Appointments, Clinics, Doctors 등 16개 테이블)
│       │   ├── repository/           # AppointmentRepository, ClinicRepository 등 8개 + RecordMappers
│       │   ├── service/
│       │   │   ├── model/            # AvailableSlot, SlotQuery, TimeRange
│       │   │   ├── ClosureRescheduleService.kt
│       │   │   ├── ConcurrencyResolver.kt
│       │   │   └── SlotCalculationService.kt
│       │   ├── statemachine/         # AppointmentState, AppointmentEvent, AppointmentStateMachine
│       │   └── timezone/             # ClinicTimezoneService
│       └── test/kotlin/io/bluetape4k/clinic/appointment/
├── appointment-event/
│   ├── build.gradle.kts
│   ├── README.md
│   └── src/
│       ├── main/kotlin/io/bluetape4k/clinic/appointment/event/
│       │   ├── AppointmentDomainEvent.kt
│       │   ├── AppointmentEventPublisher.kt
│       │   └── AppointmentEventListener.kt
│       └── test/kotlin/io/bluetape4k/clinic/appointment/event/
├── appointment-notification/
│   ├── build.gradle.kts
│   ├── README.md
│   └── src/
│       ├── main/kotlin/io/bluetape4k/clinic/appointment/notification/
│       │   ├── AppointmentReminderScheduler.kt
│       │   ├── DummyNotificationChannel.kt
│       │   ├── NotificationAutoConfiguration.kt   # Spring Boot 자동 구성
│       │   ├── NotificationChannel.kt
│       │   ├── NotificationEventListener.kt
│       │   ├── NotificationHistory.kt              # Table 정의
│       │   ├── NotificationHistoryRepository.kt    # 이력 조회/저장
│       │   ├── NotificationMessageProvider.kt      # 메시지 템플릿 제공
│       │   ├── NotificationProperties.kt
│       │   ├── NotificationResilienceProperties.kt # CircuitBreaker/Retry/Bulkhead 설정
│       │   └── ResilientNotificationChannel.kt
│       └── test/kotlin/io/bluetape4k/clinic/appointment/notification/
│
│   NOTE: META-INF/spring/ 자동 구성 파일은 현재 존재하지 않음.
│         NotificationAutoConfiguration.kt가 @Configuration으로 직접 스캔됨.
│         독립 저장소 전환 시 spring.factories 또는
│         META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│         등록 여부를 검토할 것.
├── appointment-solver/
│   ├── build.gradle.kts
│   ├── README.md
│   └── src/
│       ├── main/kotlin/io/bluetape4k/clinic/appointment/solver/
│       │   ├── domain/               # Planning entities
│       │   ├── constraint/           # Constraint providers
│       │   └── service/              # SolverService
│       └── test/kotlin/io/bluetape4k/clinic/appointment/solver/
├── appointment-api/
│   ├── build.gradle.kts
│   ├── README.md
│   └── src/
│       ├── main/kotlin/io/bluetape4k/clinic/appointment/api/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── config/
│       │   ├── security/
│       │   └── AppointmentApiApplication.kt
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/        # Flyway scripts
│       ├── test/kotlin/io/bluetape4k/clinic/appointment/api/
│       └── gatling/                  # Gatling load tests
├── frontend/
│   └── appointment-frontend/
│       ├── build.gradle.kts          # node-gradle plugin
│       ├── angular.json
│       ├── package.json
│       ├── tsconfig.json
│       └── src/
├── config/
│   └── detekt/
│       └── detekt.yml
├── gradle/
│   └── wrapper/
├── build.gradle.kts                  # Root build script
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── CLAUDE.md
├── README.md
└── .gitignore
```

---

## 4. 모듈별 역할 및 의존성

### 의존성 그래프

```
appointment-core  (leaf -- 외부 의존만)
    ├── appointment-event       (core)
    ├── appointment-solver      (core)
    ├── appointment-notification (core, event)
    └── appointment-api         (core, event, solver)

appointment-frontend            (독립 -- Node.js)
```

> **Note**: `appointment-api`는 `appointment-notification`에 의존하지 않는다.
> API 모듈은 core, event, solver만 참조하며, 알림은 이벤트 기반으로 독립 동작한다.

### 모듈별 외부 의존성 요약

| 모듈 | bluetape4k 의존성 | 주요 외부 의존성 |
|------|-------------------|-----------------|
| `appointment-core` | exposed-core, exposed-r2dbc, exposed-jdbc, coroutines | Exposed ORM, kotlinx-coroutines |
| `appointment-event` | (core 경유) | Spring Context |
| `appointment-notification` | leader, lettuce, resilience4j, exposed-jdbc | Resilience4j, Lettuce, Spring Web |
| `appointment-solver` | exposed-jdbc | Timefold Solver |
| `appointment-api` | exposed-jdbc | Spring Boot Web/Security/Validation, JWT, Flyway, Springdoc, Gatling |
| `appointment-frontend` | (없음) | Angular 18, Node 22 |

---

## 5. settings.gradle.kts 템플릿

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

rootProject.name = "clinic-appointment"

// 로컬 개발: bluetape4k-projects 소스 직접 참조 (있으면)
val bluetape4kProjectsDir = file("../bluetape4k-projects")
if (bluetape4kProjectsDir.exists()) {
    includeBuild(bluetape4kProjectsDir) {
        dependencySubstitution {
            // Exposed 관련
            substitute(module("io.github.bluetape4k:bluetape4k-exposed-core")).using(project(":bluetape4k-exposed-core"))
            substitute(module("io.github.bluetape4k:bluetape4k-exposed-jdbc")).using(project(":bluetape4k-exposed-jdbc"))
            substitute(module("io.github.bluetape4k:bluetape4k-exposed-r2dbc")).using(project(":bluetape4k-exposed-r2dbc"))
            // Coroutines
            substitute(module("io.github.bluetape4k:bluetape4k-coroutines")).using(project(":bluetape4k-coroutines"))
            // Infra
            substitute(module("io.github.bluetape4k:bluetape4k-leader")).using(project(":bluetape4k-leader"))
            substitute(module("io.github.bluetape4k:bluetape4k-lettuce")).using(project(":bluetape4k-lettuce"))
            substitute(module("io.github.bluetape4k:bluetape4k-resilience4j")).using(project(":bluetape4k-resilience4j"))
            // Test
            substitute(module("io.github.bluetape4k:bluetape4k-junit5")).using(project(":bluetape4k-junit5"))
        }
    }
}

// Backend modules (플랫 구조)
include("appointment-core")
include("appointment-event")
include("appointment-notification")
include("appointment-solver")
include("appointment-api")

// Frontend (계층 구조)
include("appointment-frontend")
project(":appointment-frontend").projectDir = file("frontend/appointment-frontend")
```

---

## 6. buildSrc 구성 전략

### buildSrc/build.gradle.kts

```kotlin
repositories {
    mavenCentral()
    google()
}

plugins {
    `kotlin-dsl`
}

kotlin {
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
    }
}
```

### Libs.kt 최소화 원칙

bluetape4k-experimental의 Libs.kt(1600줄+)에서 **실제 사용하는 항목만** 추출:

```kotlin
// 포함 대상 (약 120줄)
object Versions {
    const val bluetape4k = "1.5.0"  // SNAPSHOT -> 안정판 전환 후 이전
    const val kotlin = "2.3.20"
    const val kotlinx_coroutines = "1.10.2"
    const val kotlinx_atomicfu = "0.31.0"
    const val spring_boot4 = "4.0.3"
    const val exposed = "1.1.1"
    const val jjwt = "0.13.0"
    const val springdoc_openapi = "3.0.0"
    const val resilience4j = "2.3.0"
    const val timefold_solver = "1.31.0"
    const val flyway = "11.15.0"
    const val lettuce = "6.8.2.RELEASE"
    const val gatling = "3.15.0"
    // + slf4j, logback, test deps
}

object Plugins {
    // detekt, dokka, dependency-management, spring-boot, gatling, testLogger, shadow
}

object Libs {
    // bluetape4k 함수 + 사용 모듈만
    // exposed 함수 + 사용 모듈만 (아래 3개 필수 포함)
    //   - exposed_spring7_transaction  ("org.jetbrains.exposed:spring7-transaction:${Versions.exposed}")
    //   - exposed_spring_boot4_starter (exposed("spring-boot4-starter"))
    //   - exposed_migration_jdbc       (exposed("migration-jdbc"))
    // spring boot starter 함수
    // resilience4j 함수 + 사용 모듈만
    // timefold 함수 + 사용 모듈만
    // jjwt, flyway, springdoc, lettuce
    // test deps: junit, kluent, mockk, h2, r2dbc-h2, awaitility, datafaker
}
```

**절대 포함하지 않을 것**: hibernate, cassandra, kafka, grpc, aws, protobuf, elasticsearch, hazelcast 등 scheduling 무관 의존성.

---

## 7. 패키지명 변경 전략

### Before -> After

```
io.bluetape4k.scheduling.appointment.*
    -> io.bluetape4k.clinic.appointment.*
```

### 실행 방법

1. IntelliJ의 `Refactor > Rename Package`를 사용 (import, 참조 자동 갱신)
2. 또는 일괄 치환:
   ```bash
   # 파일 내용 치환
   fd -e kt -e java -e xml -e yml -e properties | \
     xargs sd 'io\.bluetape4k\.scheduling\.appointment' 'io.bluetape4k.clinic.appointment'

   # 디렉토리 구조 이동
   # src/main/kotlin/io/bluetape4k/scheduling/appointment/
   #   -> src/main/kotlin/io/bluetape4k/clinic/appointment/
   ```
3. Flyway 마이그레이션 SQL은 패키지와 무관하므로 그대로 유지
4. `application.yml` 내 패키지 참조 확인 (component-scan 경로 등)

### 검증

```bash
./gradlew :appointment-core:build    # 컴파일 + 테스트
./gradlew :appointment-api:build     # 전체 통합
```

---

## 8. 이전 순서 및 체크리스트

### Phase 0: 저장소 준비

- [ ] GitHub에 `bluetape4k/clinic-appointment` 저장소 생성
- [ ] 로컬 클론 + 초기 구조 생성 (buildSrc, root build.gradle.kts, settings.gradle.kts)
- [ ] `.gitignore`, `gradle.properties`, Gradle Wrapper 복사
- [ ] `config/detekt/detekt.yml` 복사
- [ ] CLAUDE.md 작성 (clinic-appointment 전용)
- [ ] `./gradlew tasks` 성공 확인

### Phase 1: appointment-core (leaf 모듈)

- [ ] 소스 복사: `scheduling/appointment-core/` -> `appointment-core/`
- [ ] 패키지 rename: `scheduling.appointment` -> `clinic.appointment`
- [ ] build.gradle.kts 조정 (project 참조 없음, 외부 의존성만)
- [ ] `src/test/resources/` 복사 확인 (`junit-platform.properties`, `logback-test.xml`)
- [ ] `./gradlew :appointment-core:build` 성공 확인
- [ ] 커밋

### Phase 2: appointment-event

- [ ] 소스 복사 + 패키지 rename
- [ ] `project(":appointment-core")` 참조 확인
- [ ] `src/test/resources/` 복사 확인 (`junit-platform.properties`, `logback-test.xml`)
- [ ] `./gradlew :appointment-event:build` 성공 확인
- [ ] 커밋

### Phase 3: appointment-solver

- [ ] 소스 복사 + 패키지 rename
- [ ] Timefold Solver 의존성 확인
- [ ] `src/test/resources/` 복사 확인 (`junit-platform.properties`, `logback-test.xml`)
- [ ] `./gradlew :appointment-solver:build` 성공 확인
- [ ] 커밋

### Phase 4: appointment-notification

- [ ] 소스 복사 + 패키지 rename
- [ ] `project(":appointment-core")`, `project(":appointment-event")` 참조 확인
- [ ] Resilience4j, Lettuce, Leader 의존성 확인
- [ ] `src/test/resources/` 복사 확인 (`junit-platform.properties`, `logback-test.xml`)
- [ ] `./gradlew :appointment-notification:build` 성공 확인
- [ ] 커밋

### Phase 5: appointment-api

- [ ] 소스 복사 + 패키지 rename
- [ ] Spring Boot, Security, Flyway, Swagger, JWT, Gatling 의존성 확인
- [ ] Flyway 마이그레이션 파일 복사 (변경 없이)
- [ ] `application.yml` 내 패키지 참조 갱신
- [ ] `src/test/resources/` 복사 확인 (`junit-platform.properties`, `logback-test.xml`, `application-test.yml`)
- [ ] `./gradlew :appointment-api:build` 성공 확인
- [ ] `./gradlew :appointment-api:bootRun` 기동 확인
- [ ] 커밋

### Phase 6: appointment-frontend

- [ ] `frontend/appointment-frontend/` 디렉토리 생성
- [ ] 소스 복사 (Angular 프로젝트 전체)
- [ ] settings.gradle.kts에 project 경로 매핑 추가
- [ ] `./gradlew :appointment-frontend:build` 성공 확인
- [ ] 커밋

### Phase 7: CI/CD + 마무리

- [ ] GitHub Actions workflow 작성 (ci.yml)
- [ ] `./gradlew build` 전체 빌드 성공 확인
- [ ] README.md 작성 (프로젝트 소개, 빌드 방법, 모듈 구조)
- [ ] bluetape4k-experimental에서 `scheduling/` 디렉토리 삭제
- [ ] bluetape4k-experimental의 settings.gradle.kts에서 `includeModules("scheduling", ...)` 제거
- [ ] 양쪽 저장소 빌드 최종 확인

---

## 9. Root build.gradle.kts 템플릿 (요약)

```kotlin
plugins {
    base
    kotlin("jvm") version Versions.kotlin
    kotlin("plugin.spring") version Versions.kotlin apply false
    kotlin("plugin.allopen") version Versions.kotlin apply false
    id("org.jetbrains.kotlinx.atomicfu") version Versions.kotlinx_atomicfu

    id(Plugins.detekt) version Plugins.Versions.detekt
    id(Plugins.dependency_management) version Plugins.Versions.dependency_management
    id(Plugins.spring_boot) version Plugins.Versions.spring_boot4 apply false
    id(Plugins.dokka) version Plugins.Versions.dokka
    id(Plugins.testLogger) version Plugins.Versions.testLogger
    id(Plugins.shadow) version Plugins.Versions.shadow apply false
    id(Plugins.gatling) version Plugins.Versions.gatling apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven {
            name = "central-snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}

subprojects {
    // bluetape4k-experimental과 동일한 subprojects 블록
    // Java 25, Kotlin 2.3, atomicfu, dependencyManagement, dokka, testLogger
    // 공통 dependencies (kotlin-stdlib, coroutines, slf4j, logging, junit5, kluent, mockk)
}
```

---

## 10. 향후 확장 포인트

| 확장 | 설명 | 예상 모듈 |
|------|------|----------|
| **알림 채널 구현** | Email (SendGrid), SMS (Twilio), Push (FCM) 실제 연동 | `appointment-notification` 확장 |
| **멀티테넌시** | 병원 그룹 단위 데이터 격리 | `appointment-core` schema 분리 |
| **환자 포털** | 환자 직접 예약/변경 웹앱 | `appointment-patient-portal` 신규 |
| **대시보드** | 관리자 통계/분석 | `appointment-dashboard` 신규 |
| **메시지 큐** | 이벤트 -> Kafka/RabbitMQ 비동기 처리 | `appointment-messaging` 신규 |
| **API Gateway** | Rate limiting, API key 관리 | `appointment-gateway` 신규 |
| **모바일 BFF** | 모바일 전용 경량 API | `appointment-mobile-bff` 신규 |
| **배치 작업** | 일간 리포트, 데이터 정리 | `appointment-batch` 신규 |
| **Docker Compose** | 로컬 개발 환경 (PostgreSQL + Redis + App) | `docker/` 디렉토리 |
| **Version Catalog** | `libs.versions.toml` 전환 | buildSrc 리팩토링 |

---

## 11. 위험 요소 및 완화

| 위험 | 영향 | 완화 |
|------|------|------|
| bluetape4k SNAPSHOT 의존 | CI 빌드 불안정 | Phase 0에서 안정 버전(1.5.0) 확정, snapshot 리포 조건부 사용 |
| 패키지 rename 누락 | 컴파일 에러 | `rg 'io\.bluetape4k\.scheduling'`으로 잔여 참조 전수검사 |
| Gatling Java 21 제약 | Java 25 환경에서 Gatling 실패 가능 | `compileGatlingJava { options.release.set(21) }` 유지 |
| Angular Node 버전 | node-gradle 호환성 | `node { version.set("22.14.0") }` 고정 |
| Flyway 마이그레이션 | 스키마 불일치 | 마이그레이션 파일 변경 없이 복사, 새 환경에서 재실행 검증 |

---

## 12. 비포함 (Out of Scope)

- Docker 이미지 빌드 / Kubernetes 매니페스트 (향후 별도 설계)
- CI/CD 파이프라인 상세 (별도 스펙)
- bluetape4k-projects 내부 리팩토링
- 프로덕션 배포 전략
