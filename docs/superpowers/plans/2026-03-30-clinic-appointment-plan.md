# clinic-appointment 독립 저장소 이전 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `bluetape4k-experimental/scheduling/` 6개 모듈을 독립 GitHub 저장소 `bluetape4k/clinic-appointment`로 분리하고, 패키지를 `io.bluetape4k.clinic.appointment`로 rename하며, 독립 빌드가 가능하도록 한다.

**Architecture:** 의존성 leaf부터 순차 이전 (core → event → solver → notification → api → frontend). 각 모듈 이전 후 빌드 검증. buildSrc는 최소화된 Libs.kt를 사용하며, bluetape4k-projects는 조건부 includeBuild로 연결한다.

**Tech Stack:** Kotlin 2.3, Java 25, Spring Boot 4, Exposed ORM, Timefold Solver, Resilience4j, Gatling, Angular 18, Gradle 8.x

---

## File Structure

### 신규 저장소 루트 파일

| 파일 | 역할 |
|------|------|
| `settings.gradle.kts` | 모듈 등록 + 조건부 includeBuild |
| `build.gradle.kts` | Root build (plugins, allprojects, subprojects 공통 설정) |
| `gradle.properties` | Gradle JVM args, Kotlin 설정, 프로젝트 버전 |
| `buildSrc/build.gradle.kts` | kotlin-dsl 플러그인 |
| `buildSrc/src/main/kotlin/Libs.kt` | 최소화된 의존성 카탈로그 |
| `.gitignore` | Gradle/Kotlin/Node.js 무시 패턴 |
| `CLAUDE.md` | clinic-appointment 전용 에이전트 지침 |
| `README.md` | 프로젝트 소개 |

### 모듈별 파일 (소스에서 복사 후 패키지 rename)

| 소스 (bluetape4k-experimental) | 대상 (clinic-appointment) |
|------|------|
| `scheduling/appointment-core/` | `appointment-core/` |
| `scheduling/appointment-event/` | `appointment-event/` |
| `scheduling/appointment-solver/` | `appointment-solver/` |
| `scheduling/appointment-notification/` | `appointment-notification/` |
| `scheduling/appointment-api/` | `appointment-api/` |
| `scheduling/appointment-frontend/` | `frontend/appointment-frontend/` |

### 패키지 rename 대상

모든 `.kt` 파일의 디렉토리 경로와 파일 내용:
- `io/bluetape4k/scheduling/appointment/` → `io/bluetape4k/clinic/appointment/`
- `io.bluetape4k.scheduling.appointment` → `io.bluetape4k.clinic.appointment`

---

## Phase 0: 저장소 준비

### Task 0.1: GitHub 저장소 생성 및 로컬 클론 — `complexity: low`

**Files:**
- Create: `/Users/debop/work/bluetape4k/clinic-appointment/` (empty repo)

- [ ] **Step 1: GitHub 저장소 생성**

```bash
gh repo create bluetape4k/clinic-appointment --private --description "Medical appointment scheduling system" --clone
```

Expected: `Cloning into 'clinic-appointment'...`

- [ ] **Step 2: 저장소 디렉토리로 이동 확인**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git status
```

Expected: `On branch main` (빈 저장소)

- [ ] **Step 3: Commit**

```bash
# 아직 커밋할 내용 없음 — 다음 태스크에서 초기 구조와 함께 커밋
```

---

### Task 0.2: Gradle Wrapper 및 gradle.properties 설정 — `complexity: low`

**Files:**
- Copy: `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (from bluetape4k-experimental)
- Create: `gradle.properties`

- [ ] **Step 1: Gradle Wrapper 복사**

```bash
SRC=/Users/debop/work/bluetape4k/bluetape4k-experimental
DST=/Users/debop/work/bluetape4k/clinic-appointment

cp "$SRC/gradlew" "$DST/gradlew"
cp "$SRC/gradlew.bat" "$DST/gradlew.bat"
mkdir -p "$DST/gradle/wrapper"
cp "$SRC/gradle/wrapper/gradle-wrapper.jar" "$DST/gradle/wrapper/"
cp "$SRC/gradle/wrapper/gradle-wrapper.properties" "$DST/gradle/wrapper/"
chmod +x "$DST/gradlew"
```

- [ ] **Step 1-b: detekt 설정 복사**

```bash
SRC=/Users/debop/work/bluetape4k/bluetape4k-experimental
DST=/Users/debop/work/bluetape4k/clinic-appointment

mkdir -p $DST/config/detekt
cp -R $SRC/config/detekt/. $DST/config/detekt/
```

- [ ] **Step 2: gradle.properties 작성**

Create `/Users/debop/work/bluetape4k/clinic-appointment/gradle.properties`:

```properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.workers.max=6
org.gradle.caching=true
org.gradle.caching.debug=false
org.gradle.configuration-cache=true
org.gradle.configuration-cache.problems=warn
org.gradle.vfs.watch=true
org.gradle.daemon.idletimeout=1800000
org.gradle.dependency.verification=lenient

org.gradle.jvmargs=-Xms2G -Xmx4G \
  -XX:+UseG1GC \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Dfile.encoding=UTF-8 \
  -XX:+UnlockExperimentalVMOptions \
  --enable-preview \
  -Didea.io.use.nio2=true \
  --enable-native-access=ALL-UNNAMED

org.gradle.warning.mode=summary

# Dokka
org.jetbrains.dokka.experimental.gradle.pluginMode=V2Enabled
org.jetbrains.dokka.experimental.gradle.pluginMode.noWarn=true

# Kotlin
kotlin.code.style=official
kotlin.incremental=true
kotlin.daemon.jvmargs=-Xms2G -Xmx4G -XX:+UseG1GC -Dfile.encoding=UTF-8 --enable-preview --enable-native-access=ALL-UNNAMED
kapt.incremental.apt=true
kapt.include.compile.classpath=false
kapt.use.k2=true

# atomicfu
kotlinx.atomicfu.enableJvmIrTransformation=true
kotlinx.atomicfu.jvm.variant=VH
kotlinx.atomicfu.jvm.languageLevel=21

# project version
projectGroup=io.bluetape4k.clinic
baseVersion=0.1.0
snapshotVersion=
```

- [ ] **Step 3: .gitignore 작성**

Create `/Users/debop/work/bluetape4k/clinic-appointment/.gitignore`:

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
*.ipr
*.iws
out/

# Kotlin
*.class

# OS
.DS_Store
Thumbs.db

# Node.js (frontend)
node_modules/
dist/
.angular/

# Logs
*.log
```

- [ ] **Step 4: 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
ls gradlew gradle/wrapper/gradle-wrapper.jar gradle.properties .gitignore
```

Expected: 4개 파일 모두 존재

---

### Task 0.3: buildSrc — 최소화된 Libs.kt — `complexity: medium`

**Files:**
- Create: `buildSrc/build.gradle.kts`
- Create: `buildSrc/src/main/kotlin/Libs.kt`

- [ ] **Step 1: buildSrc/build.gradle.kts 작성**

Create `/Users/debop/work/bluetape4k/clinic-appointment/buildSrc/build.gradle.kts`:

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

- [ ] **Step 2: Libs.kt 복사 (스펙에서 생성된 최소화 버전)**

Copy from `/Users/debop/work/bluetape4k/bluetape4k-experimental/docs/superpowers/specs/clinic-appointment-Libs.kt`
to `/Users/debop/work/bluetape4k/clinic-appointment/buildSrc/src/main/kotlin/Libs.kt`

```bash
SRC=/Users/debop/work/bluetape4k/bluetape4k-experimental/docs/superpowers/specs/clinic-appointment-Libs.kt
DST=/Users/debop/work/bluetape4k/clinic-appointment/buildSrc/src/main/kotlin/Libs.kt
mkdir -p "$(dirname "$DST")"
cp "$SRC" "$DST"
```

- [ ] **Step 3: Libs.kt에 누락된 항목 추가**

스펙의 최소화 Libs.kt에 다음 항목이 누락되어 있으므로 추가해야 한다. `Libs` object 내에:

```kotlin
// bluetape4k 추가 모듈 (appointment-core/event에서 사용)
val bluetape4k_exposed_r2dbc = bluetape4k("exposed-r2dbc")
val bluetape4k_exposed_r2dbc_tests = bluetape4k("exposed-r2dbc-tests")

// R2DBC (appointment-core/event 테스트에서 사용)
const val r2dbc_h2 = "io.r2dbc:r2dbc-h2:1.1.0.RELEASE"
```

`Versions` object에 `assertj_core`, `datafaker`, `random_beans` 버전도 필요:

```kotlin
const val assertj_core = "3.27.6"
const val datafaker = "2.5.4"
const val random_beans = "3.9.0"
```

`Libs` object에:

```kotlin
const val assertj_core = "org.assertj:assertj-core:${Versions.assertj_core}"
const val datafaker = "net.datafaker:datafaker:${Versions.datafaker}"
const val random_beans = "io.github.benas:random-beans:${Versions.random_beans}"
```

- [ ] **Step 3-b: bluetape4k SNAPSHOT 버전 확인**

`Libs.kt`의 `Versions.bluetape4k`가 SNAPSHOT 버전인 경우 처리:

> `Versions.bluetape4k`가 SNAPSHOT인 경우 안정 버전으로 변경하거나, `build.gradle.kts`의 `allprojects.repositories`에 `central-snapshots` 리포지토리가 추가되어 있는지 확인.
> `central-snapshots` 리포지토리는 Task 0.5의 Root build.gradle.kts 예시에 이미 포함되어 있으므로, SNAPSHOT을 유지한다면 해당 리포지토리가 빠지지 않도록 주의.

```bash
grep 'bluetape4k' /Users/debop/work/bluetape4k/clinic-appointment/buildSrc/src/main/kotlin/Libs.kt | grep -i version
```

- [ ] **Step 4: 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
ls buildSrc/build.gradle.kts buildSrc/src/main/kotlin/Libs.kt
```

Expected: 2개 파일 존재

---

### Task 0.4: settings.gradle.kts 작성 — `complexity: medium`

**Files:**
- Create: `settings.gradle.kts`

- [ ] **Step 1: settings.gradle.kts 작성**

Create `/Users/debop/work/bluetape4k/clinic-appointment/settings.gradle.kts`:

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
            substitute(module("io.github.bluetape4k:bluetape4k-exposed-r2dbc-tests")).using(project(":bluetape4k-exposed-r2dbc-tests"))
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

- [ ] **Step 2: 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
cat settings.gradle.kts | head -5
```

Expected: `pluginManagement {`

---

### Task 0.5: Root build.gradle.kts 작성 — `complexity: high`

**Files:**
- Create: `build.gradle.kts`

- [ ] **Step 1: Root build.gradle.kts 작성**

Create `/Users/debop/work/bluetape4k/clinic-appointment/build.gradle.kts`:

```kotlin
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    base
    kotlin("jvm") version Versions.kotlin

    kotlin("plugin.spring") version Versions.kotlin apply false
    kotlin("plugin.allopen") version Versions.kotlin apply false
    id("org.jetbrains.kotlinx.atomicfu") version "0.31.0"

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
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(1, java.util.concurrent.TimeUnit.DAYS)
    }
}

subprojects {
    apply {
        plugin<JavaLibraryPlugin>()
        plugin("org.jetbrains.kotlin.jvm")
        plugin("org.jetbrains.kotlinx.atomicfu")
        plugin(Plugins.dependency_management)
        plugin(Plugins.dokka)
        plugin(Plugins.testLogger)
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    kotlin {
        jvmToolchain(25)
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_2_3)
            apiVersion.set(KotlinVersion.KOTLIN_2_3)
            freeCompilerArgs = listOf(
                "-Xjsr305=strict",
                "-jvm-default=enable",
                "-Xstring-concat=indy",
                "-Xcontext-parameters",
                "-Xannotation-default-target=param-property",
            )
            val experimentalAnnotations = listOf(
                "kotlin.RequiresOptIn",
                "kotlin.ExperimentalStdlibApi",
                "kotlin.contracts.ExperimentalContracts",
                "kotlin.experimental.ExperimentalTypeInference",
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
                "kotlinx.coroutines.InternalCoroutinesApi",
                "kotlinx.coroutines.FlowPreview",
                "kotlinx.coroutines.DelicateCoroutinesApi",
            )
            freeCompilerArgs.addAll(experimentalAnnotations.map { "-opt-in=$it" })
        }
    }

    tasks {
        compileJava {
            options.isIncremental = true
        }
        test {
            useJUnitPlatform()
            jvmArgs(
                "--enable-preview",
                "--enable-native-access=ALL-UNNAMED",
            )
        }
    }

    // dependencyManagement — 최소화 버전 (스펙의 clinic-appointment-build-dm.gradle.kts 참조)
    dependencyManagement {
        setApplyMavenExclusions(false)

        imports {
            mavenBom(Libs.bluetape4k_bom)
            mavenBom(Libs.spring_boot4_dependencies)
            mavenBom(Libs.testcontainers_bom)
            mavenBom(Libs.junit_bom)
            mavenBom(Libs.kotlinx_coroutines_bom)
            mavenBom(Libs.kotlin_bom)
            mavenBom(Libs.timefold_solver_bom)
        }

        dependencies {
            dependency(Libs.jetbrains_annotations)
            dependency(Libs.kotlinx_coroutines_core)
            dependency(Libs.kotlinx_coroutines_core_jvm)
            dependency(Libs.kotlinx_coroutines_reactor)
            dependency(Libs.kotlinx_coroutines_slf4j)
            dependency(Libs.kotlinx_coroutines_debug)
            dependency(Libs.kotlinx_coroutines_test)
            dependency(Libs.kotlinx_coroutines_test_jvm)

            dependency(Libs.junit_jupiter)
            dependency(Libs.junit_jupiter_api)
            dependency(Libs.junit_jupiter_engine)
            dependency(Libs.junit_jupiter_params)
            dependency(Libs.junit_platform_commons)
            dependency(Libs.junit_platform_engine)
            dependency(Libs.junit_platform_launcher)

            dependency(Libs.kluent)
            dependency(Libs.mockk)
            dependency(Libs.lettuce_core)
        }
    }

    dependencies {
        val api by configurations
        val implementation by configurations
        val testImplementation by configurations
        val compileOnly by configurations
        val testRuntimeOnly by configurations

        compileOnly(platform(Libs.bluetape4k_bom))
        compileOnly(platform(Libs.spring_boot4_dependencies))
        compileOnly(platform(Libs.kotlinx_coroutines_bom))

        implementation(Libs.kotlin_stdlib)
        implementation(Libs.kotlin_reflect)
        testImplementation(Libs.kotlin_test)
        testImplementation(Libs.kotlin_test_junit5)

        implementation(Libs.kotlinx_coroutines_core)

        implementation(Libs.slf4j_api)
        implementation(Libs.bluetape4k_logging)
        implementation(Libs.logback)
        testImplementation(Libs.jcl_over_slf4j)
        testImplementation(Libs.jul_to_slf4j)
        testImplementation(Libs.log4j_over_slf4j)

        testImplementation(Libs.bluetape4k_junit5)
        testImplementation(Libs.junit_jupiter)
        testRuntimeOnly(Libs.junit_platform_engine)

        testImplementation(Libs.kluent)
        testImplementation(Libs.mockk)
    }
}
```

- [ ] **Step 2: Gradle tasks 실행으로 빌드 인프라 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew tasks --no-daemon 2>&1 | head -20
```

Expected: `BUILD SUCCESSFUL` (모듈 디렉토리 없어도 tasks 명령은 성공해야 함)

- [ ] **Step 3: 초기 커밋**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git add .
git commit -m "chore: initial project setup with buildSrc, settings, root build"
```

---

### Task 0.6: CLAUDE.md 및 README.md 작성 — `complexity: low`

**Files:**
- Create: `CLAUDE.md`
- Create: `README.md`

- [ ] **Step 1: CLAUDE.md 작성**

Create `/Users/debop/work/bluetape4k/clinic-appointment/CLAUDE.md`:

```markdown
# clinic-appointment Kotlin Dev Agent

Multi-module Gradle project — Kotlin 2.3 + Java 25 + Spring Boot 4.

## Role

- Apply `bluetape4k-patterns` skill on all Kotlin code. No exceptions.
- Communicate in Korean. Keep code identifiers and technical terms in English.

## Principles

- **Think Before Coding** — ask when uncertain, never guess
- **Simplicity First** — implement only what was asked
- **Surgical Changes** — every changed line must trace back to a request
- **Goal-Driven** — "write a failing test then fix it" not "fix the bug"

## Build

```bash
# Per-module only
./gradlew :<module>:build
./gradlew :<module>:test
./gradlew :<module>:test --tests "fully.qualified.ClassName.methodName"
```

## Key Files

| Purpose | Path |
|---------|------|
| Dependency versions | `buildSrc/src/main/kotlin/Libs.kt` |
| Module registration | `settings.gradle.kts` |

## Module Layout

```
clinic-appointment/
├── appointment-core/          # 도메인 모델, 테이블, 리포지토리, 서비스
├── appointment-event/         # 도메인 이벤트 정의/로깅
├── appointment-solver/        # Timefold 기반 일정 최적화
├── appointment-notification/  # 알림 발송 (Resilience4j, Leader election)
├── appointment-api/           # Spring Boot REST API
└── frontend/
    └── appointment-frontend/  # Angular 18 SPA
```

## Dependency Graph

```
appointment-core  (leaf)
    ├── appointment-event       (core)
    ├── appointment-solver      (core)
    ├── appointment-notification (core, event)
    └── appointment-api         (core, event, solver)
```

## Testcontainers

Use bluetape4k singleton pattern:

```kotlin
companion object {
    @JvmStatic val redisServer = RedisServer.Standalone.start()
}
```
```

- [ ] **Step 2: README.md 작성**

Create `/Users/debop/work/bluetape4k/clinic-appointment/README.md` with project overview, module descriptions, build instructions, tech stack.

- [ ] **Step 3: 커밋**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git add CLAUDE.md README.md
git commit -m "docs: add CLAUDE.md and README.md"
```

---

## Phase 1: appointment-core (leaf 모듈)

### Task 1.1: 소스 복사 및 패키지 rename — `complexity: high`

**Files:**
- Copy: `scheduling/appointment-core/` → `appointment-core/`
- Rename: 모든 `.kt` 파일 내 `io.bluetape4k.scheduling.appointment` → `io.bluetape4k.clinic.appointment`
- Rename: 디렉토리 경로 `scheduling/appointment` → `clinic/appointment`

- [ ] **Step 1: 소스 디렉토리 복사**

```bash
SRC=/Users/debop/work/bluetape4k/bluetape4k-experimental/scheduling/appointment-core
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-core

cp -R "$SRC" "$DST"
```

- [ ] **Step 2: 디렉토리 구조 rename (scheduling → clinic)**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-core

# main 소스
mkdir -p "$DST/src/main/kotlin/io/bluetape4k/clinic"
mv "$DST/src/main/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/main/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/main/kotlin/io/bluetape4k/scheduling"

# test 소스
mkdir -p "$DST/src/test/kotlin/io/bluetape4k/clinic"
mv "$DST/src/test/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/test/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/test/kotlin/io/bluetape4k/scheduling"
```

- [ ] **Step 3: 파일 내용 패키지명 치환**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-core

fd -e kt -e kts -e xml -e yml -e properties . "$DST" | \
  xargs sd 'io\.bluetape4k\.scheduling\.appointment' 'io.bluetape4k.clinic.appointment'
```

- [ ] **Step 4: test/resources 확인 — 없으면 생성**

appointment-core에는 원래 test/resources가 없다. 신규 모듈 규칙에 따라 복사한다:

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-core
mkdir -p "$DST/src/test/resources"

cat > "$DST/src/test/resources/junit-platform.properties" << 'EOF'
junit.jupiter.extensions.autodetection.enabled=true
junit.jupiter.testinstance.lifecycle.default=per_class

junit.jupiter.execution.parallel.enabled=false
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=concurrent
EOF

cat > "$DST/src/test/resources/logback-test.xml" << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- @formatter:off -->
    <property name="CONSOLE_LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5p) %magenta(${PID:- }) --- [%25.25t] %cyan(%-40.40logger{39}) : %m%n"/>

    <appender name="console" class="ch.qos.logback.core.ConsoleAppender">
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>DEBUG</level>
        </filter>
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
            <charset>utf8</charset>
        </encoder>
    </appender>
    <!-- formatter:on -->

    <logger name="io.bluetape4k.clinic" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="console"/>
    </root>
</configuration>
XMLEOF
```

- [ ] **Step 5: build.gradle.kts 내용 확인 — 그대로 유지**

appointment-core의 build.gradle.kts는 프로젝트 간 참조 없이 외부 의존성만 사용하므로 변경 불필요:

```bash
cat /Users/debop/work/bluetape4k/clinic-appointment/appointment-core/build.gradle.kts
```

Expected: `project(":appointment-core")`가 없고, `Libs.exposed_*`, `Libs.bluetape4k_*`만 참조

- [ ] **Step 6: 잔여 scheduling 참조 전수검사**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
rg 'io\.bluetape4k\.scheduling' appointment-core/ || echo "No remaining references - OK"
```

Expected: `No remaining references - OK`

- [ ] **Step 7: logback-test.xml 로거 패키지 확인**

logback-test.xml이 이미 존재하는 모듈(solver, notification, api)에서는 `io.bluetape4k.scheduling` 로거를 `io.bluetape4k.clinic`으로 변경해야 한다. Step 3의 sd 명령에서 `.xml`도 포함했으므로 자동 처리됨. 하지만 Step 4에서 신규 생성한 파일은 이미 `io.bluetape4k.clinic`으로 작성했으므로 OK.

- [ ] **Step 8: 빌드 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-core:build --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: 테스트 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-core:test --no-daemon
```

Expected: 테스트 전체 PASS (TableSchemaTest, SlotCalculationServiceTest, ClosureRescheduleServiceTest, ResolveMaxConcurrentTest, TimeRangeTest, AppointmentStateMachineTest, ClinicTimezoneServiceTest)

- [ ] **Step 10: 커밋**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git add appointment-core/
git commit -m "feat: migrate appointment-core with package rename to io.bluetape4k.clinic.appointment"
```

---

## Phase 2: appointment-event

### Task 2.1: 소스 복사, 패키지 rename, 빌드 검증 — `complexity: medium`

**Files:**
- Copy: `scheduling/appointment-event/` → `appointment-event/`
- Rename: 패키지명 `scheduling.appointment` → `clinic.appointment`

- [ ] **Step 1: 소스 디렉토리 복사**

```bash
SRC=/Users/debop/work/bluetape4k/bluetape4k-experimental/scheduling/appointment-event
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-event

cp -R "$SRC" "$DST"
```

- [ ] **Step 2: 디렉토리 구조 rename**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-event

# main
mkdir -p "$DST/src/main/kotlin/io/bluetape4k/clinic"
mv "$DST/src/main/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/main/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/main/kotlin/io/bluetape4k/scheduling"

# test
mkdir -p "$DST/src/test/kotlin/io/bluetape4k/clinic"
mv "$DST/src/test/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/test/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/test/kotlin/io/bluetape4k/scheduling"
```

- [ ] **Step 3: 파일 내용 패키지명 치환**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-event

fd -e kt -e kts -e xml -e yml -e properties . "$DST" | \
  xargs sd 'io\.bluetape4k\.scheduling\.appointment' 'io.bluetape4k.clinic.appointment'
```

- [ ] **Step 4: test/resources 생성**

appointment-event에는 원래 test/resources가 없다:

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-event
mkdir -p "$DST/src/test/resources"

cat > "$DST/src/test/resources/junit-platform.properties" << 'EOF'
junit.jupiter.extensions.autodetection.enabled=true
junit.jupiter.testinstance.lifecycle.default=per_class

junit.jupiter.execution.parallel.enabled=false
junit.jupiter.execution.parallel.mode.default=same_thread
junit.jupiter.execution.parallel.mode.classes.default=concurrent
EOF

cat > "$DST/src/test/resources/logback-test.xml" << 'XMLEOF'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="CONSOLE_LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5p) %magenta(${PID:- }) --- [%25.25t] %cyan(%-40.40logger{39}) : %m%n"/>

    <appender name="console" class="ch.qos.logback.core.ConsoleAppender">
        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
            <level>DEBUG</level>
        </filter>
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
            <charset>utf8</charset>
        </encoder>
    </appender>

    <logger name="io.bluetape4k.clinic" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="console"/>
    </root>
</configuration>
XMLEOF
```

- [ ] **Step 5: 잔여 참조 검사**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
rg 'io\.bluetape4k\.scheduling' appointment-event/ || echo "No remaining references - OK"
```

Expected: `No remaining references - OK`

- [ ] **Step 6: 빌드 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-event:build --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 테스트 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-event:test --no-daemon
```

Expected: EventLogTest PASS

- [ ] **Step 8: 커밋**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git add appointment-event/
git commit -m "feat: migrate appointment-event with package rename"
```

---

## Phase 3: appointment-solver

### Task 3.1: 소스 복사, 패키지 rename, 빌드 검증 — `complexity: medium`

**Files:**
- Copy: `scheduling/appointment-solver/` → `appointment-solver/`
- Rename: 패키지명 `scheduling.appointment` → `clinic.appointment`

- [ ] **Step 1: 소스 디렉토리 복사**

```bash
SRC=/Users/debop/work/bluetape4k/bluetape4k-experimental/scheduling/appointment-solver
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-solver

cp -R "$SRC" "$DST"
```

- [ ] **Step 2: 디렉토리 구조 rename**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-solver

# main
mkdir -p "$DST/src/main/kotlin/io/bluetape4k/clinic"
mv "$DST/src/main/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/main/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/main/kotlin/io/bluetape4k/scheduling"

# test
mkdir -p "$DST/src/test/kotlin/io/bluetape4k/clinic"
mv "$DST/src/test/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/test/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/test/kotlin/io/bluetape4k/scheduling"
```

- [ ] **Step 3: 파일 내용 패키지명 치환**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-solver

fd -e kt -e kts -e xml -e yml -e properties . "$DST" | \
  xargs sd 'io\.bluetape4k\.scheduling\.appointment' 'io.bluetape4k.clinic.appointment'
```

- [ ] **Step 4: test/resources 확인**

appointment-solver는 이미 `junit-platform.properties`와 `logback-test.xml`을 가지고 있다. Step 3의 sd 명령으로 logback-test.xml 내 로거 패키지가 이미 변경되었는지 확인:

```bash
rg 'scheduling' /Users/debop/work/bluetape4k/clinic-appointment/appointment-solver/src/test/resources/ || echo "Clean - OK"
```

Expected: `Clean - OK`

- [ ] **Step 5: 잔여 참조 검사**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
rg 'io\.bluetape4k\.scheduling' appointment-solver/ || echo "No remaining references - OK"
```

Expected: `No remaining references - OK`

- [ ] **Step 6: 빌드 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-solver:build --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 테스트 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-solver:test --no-daemon
```

Expected: ConstraintVerifierTest, SolverServiceTest, BenchmarkTest PASS

- [ ] **Step 8: 커밋**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git add appointment-solver/
git commit -m "feat: migrate appointment-solver with package rename"
```

---

## Phase 4: appointment-notification

### Task 4.1: 소스 복사, 패키지 rename, 빌드 검증 — `complexity: high`

**Files:**
- Copy: `scheduling/appointment-notification/` → `appointment-notification/`
- Rename: 패키지명 `scheduling.appointment` → `clinic.appointment`

이 모듈은 `appointment-core`와 `appointment-event` 두 모듈에 의존하며, Resilience4j, Lettuce, Leader election 등 복잡한 외부 의존성을 가진다.

- [ ] **Step 1: 소스 디렉토리 복사**

```bash
SRC=/Users/debop/work/bluetape4k/bluetape4k-experimental/scheduling/appointment-notification
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-notification

cp -R "$SRC" "$DST"
```

- [ ] **Step 2: 디렉토리 구조 rename**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-notification

# main
mkdir -p "$DST/src/main/kotlin/io/bluetape4k/clinic"
mv "$DST/src/main/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/main/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/main/kotlin/io/bluetape4k/scheduling"

# test
mkdir -p "$DST/src/test/kotlin/io/bluetape4k/clinic"
mv "$DST/src/test/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/test/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/test/kotlin/io/bluetape4k/scheduling"
```

- [ ] **Step 3: 파일 내용 패키지명 치환**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-notification

fd -e kt -e kts -e xml -e yml -e properties -e md . "$DST" | \
  xargs sd 'io\.bluetape4k\.scheduling\.appointment' 'io.bluetape4k.clinic.appointment'
```

- [ ] **Step 4: test/resources 확인**

appointment-notification은 이미 test/resources를 가지고 있다:

```bash
ls /Users/debop/work/bluetape4k/clinic-appointment/appointment-notification/src/test/resources/
rg 'scheduling' /Users/debop/work/bluetape4k/clinic-appointment/appointment-notification/src/test/resources/ || echo "Clean - OK"
```

Expected: `junit-platform.properties`, `logback-test.xml` 존재, `Clean - OK`

- [ ] **Step 5: 잔여 참조 검사**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
rg 'io\.bluetape4k\.scheduling' appointment-notification/ || echo "No remaining references - OK"
```

Expected: `No remaining references - OK`

- [ ] **Step 6: 빌드 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-notification:build --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 테스트 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-notification:test --no-daemon
```

Expected: AppointmentReminderSchedulerTest, DummyNotificationChannelTest, NotificationEventListenerTest, NotificationHistoryRepositoryTest, NotificationMessageProviderTest, ResilientNotificationChannelTest PASS

- [ ] **Step 7-b: AutoConfiguration 등록 확인**

> `appointment-notification`의 `NotificationAutoConfiguration`이 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 등록되어 있는지 확인. 없으면 독립 저장소에서 auto-configuration이 활성화되지 않을 수 있으므로 등록 필요.

```bash
cat /Users/debop/work/bluetape4k/clinic-appointment/appointment-notification/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 2>/dev/null || echo "MISSING: AutoConfiguration.imports not found — 수동 등록 필요"
```

파일이 없거나 `NotificationAutoConfiguration`이 누락된 경우:
```bash
mkdir -p /Users/debop/work/bluetape4k/clinic-appointment/appointment-notification/src/main/resources/META-INF/spring
echo "io.bluetape4k.clinic.appointment.notification.NotificationAutoConfiguration" \
  >> /Users/debop/work/bluetape4k/clinic-appointment/appointment-notification/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

- [ ] **Step 8: 커밋**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git add appointment-notification/
git commit -m "feat: migrate appointment-notification with package rename"
```

---

## Phase 5: appointment-api

### Task 5.1: 소스 복사, 패키지 rename, 빌드 검증 — `complexity: high`

**Files:**
- Copy: `scheduling/appointment-api/` → `appointment-api/`
- Rename: 패키지명 `scheduling.appointment` → `clinic.appointment`
- Check: `application.yml`, `application-flyway.yml`, `application-postgresql.yml`, `application-test.yml` 내 패키지 참조

이 모듈은 Spring Boot 앱으로 `appointment-core`, `appointment-event`, `appointment-solver`에 의존하며 JWT 보안, Flyway, Gatling 부하 테스트를 포함한다.

- [ ] **Step 1: 소스 디렉토리 복사**

```bash
SRC=/Users/debop/work/bluetape4k/bluetape4k-experimental/scheduling/appointment-api
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-api

cp -R "$SRC" "$DST"
```

- [ ] **Step 2: 디렉토리 구조 rename**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-api

# main
mkdir -p "$DST/src/main/kotlin/io/bluetape4k/clinic"
mv "$DST/src/main/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/main/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/main/kotlin/io/bluetape4k/scheduling"

# test
mkdir -p "$DST/src/test/kotlin/io/bluetape4k/clinic"
mv "$DST/src/test/kotlin/io/bluetape4k/scheduling/appointment" "$DST/src/test/kotlin/io/bluetape4k/clinic/appointment"
rmdir "$DST/src/test/kotlin/io/bluetape4k/scheduling"

# gatling (존재하는 경우)
if [ -d "$DST/src/gatling/java/io/bluetape4k/scheduling/appointment" ]; then
  mkdir -p "$DST/src/gatling/java/io/bluetape4k/clinic/appointment"
  mv "$DST/src/gatling/java/io/bluetape4k/scheduling/appointment" \
     "$DST/src/gatling/java/io/bluetape4k/clinic/appointment"
  rmdir "$DST/src/gatling/java/io/bluetape4k/scheduling"
fi
```

- [ ] **Step 3: 파일 내용 패키지명 치환 (Kotlin, YML, XML, properties)**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-api

fd -e kt -e kts -e xml -e yml -e properties -e md . "$DST" | \
  xargs sd 'io\.bluetape4k\.scheduling\.appointment' 'io.bluetape4k.clinic.appointment'
```

- [ ] **Step 4: application.yml 내 패키지 참조 확인**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-api
rg 'scheduling' "$DST/src/main/resources/" || echo "Clean - OK"
rg 'scheduling' "$DST/src/test/resources/" || echo "Clean - OK"
```

Expected: 모두 `Clean - OK`. 만약 잔여 참조가 있으면 수동 수정.

> **주의**: `db/migration/*.sql` 파일의 테이블명(`scheduling_clinics`, `scheduling_appointments` 등)은 DB 스키마명이므로 절대 변경하지 말 것. `rg` 결과에서 SQL 파일의 매치는 무시할 것.

- [ ] **Step 5: Gatling 소스 확인**

Gatling 소스가 존재하면 패키지 치환이 필요할 수 있다:

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/appointment-api
ls "$DST/src/gatling/" 2>/dev/null || echo "No gatling sources"
```

Gatling 소스가 있다면:
```bash
fd -e java -e scala -e kt . "$DST/src/gatling/" | xargs sd 'io\.bluetape4k\.scheduling\.appointment' 'io.bluetape4k.clinic.appointment'
```

- [ ] **Step 6: test/resources 확인**

```bash
ls /Users/debop/work/bluetape4k/clinic-appointment/appointment-api/src/test/resources/
```

Expected: `junit-platform.properties`, `logback-test.xml`, `application-test.yml` 존재

- [ ] **Step 7: 잔여 참조 전수검사**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
rg 'io\.bluetape4k\.scheduling' appointment-api/ || echo "No remaining references - OK"
```

Expected: `No remaining references - OK`

- [ ] **Step 8: 빌드 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-api:build --no-daemon
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: 테스트 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-api:test --no-daemon
```

Expected: AppointmentControllerTest, JwtTokenParserTest PASS

- [ ] **Step 10: bootRun 기동 확인 (선택)**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
timeout 15 ./gradlew :appointment-api:bootRun --no-daemon 2>&1 | tail -10 || true
```

Expected: `Started AppointmentApiApplication` 또는 DB 미연결로 인한 예상된 실패

- [ ] **Step 11: 커밋**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git add appointment-api/
git commit -m "feat: migrate appointment-api with package rename"
```

---

## Phase 6: appointment-frontend

### Task 6.1: Angular 프로젝트 복사 및 빌드 검증 — `complexity: medium`

**Files:**
- Copy: `scheduling/appointment-frontend/` → `frontend/appointment-frontend/`
- Check: `settings.gradle.kts`의 project 경로 매핑 (이미 Task 0.4에서 설정)

- [ ] **Step 1: 소스 디렉토리 복사 (node_modules, dist 제외)**

```bash
SRC=/Users/debop/work/bluetape4k/bluetape4k-experimental/scheduling/appointment-frontend
DST=/Users/debop/work/bluetape4k/clinic-appointment/frontend/appointment-frontend

mkdir -p "$(dirname "$DST")"
rsync -av --exclude='node_modules' --exclude='dist' --exclude='build' --exclude='.angular' "$SRC/" "$DST/"
```

- [ ] **Step 2: 파일 내용에서 패키지명 치환 (API URL 등)**

```bash
DST=/Users/debop/work/bluetape4k/clinic-appointment/frontend/appointment-frontend

fd -e ts -e json -e html -e scss -e yml . "$DST/src" | \
  xargs sd 'scheduling\.appointment' 'clinic.appointment' 2>/dev/null || true

fd -e ts -e json -e html . "$DST/src" | \
  xargs sd 'scheduling-appointment' 'clinic-appointment' 2>/dev/null || true
```

Note: Angular 프론트엔드에서 백엔드 패키지명을 직접 참조할 가능성은 낮지만, API 경로나 주석에 있을 수 있으므로 치환.

- [ ] **Step 3: build.gradle.kts 확인**

```bash
cat /Users/debop/work/bluetape4k/clinic-appointment/frontend/appointment-frontend/build.gradle.kts
```

Expected: node-gradle plugin + npm install/build/test 태스크

- [ ] **Step 4: settings.gradle.kts 매핑 확인**

```bash
rg 'appointment-frontend' /Users/debop/work/bluetape4k/clinic-appointment/settings.gradle.kts
```

Expected: `include("appointment-frontend")` + `project(":appointment-frontend").projectDir = file("frontend/appointment-frontend")`

- [ ] **Step 5: npm install 및 빌드 검증**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew :appointment-frontend:build --no-daemon
```

Expected: `BUILD SUCCESSFUL` (npm install + Angular build 성공)

- [ ] **Step 6: 커밋**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git add frontend/
git commit -m "feat: migrate appointment-frontend (Angular 18)"
```

---

## Phase 7: CI/CD + 마무리

### Task 7.1: GitHub Actions CI workflow — `complexity: medium`

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: CI workflow 작성**

Create `/Users/debop/work/bluetape4k/clinic-appointment/.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '25'

      - name: Set up Node.js 22
        uses: actions/setup-node@v4
        with:
          node-version: '22'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build
        run: ./gradlew build --no-daemon

      - name: Test Report
        uses: dorny/test-reporter@v1
        if: success() || failure()
        with:
          name: Test Results
          path: '**/build/test-results/test/*.xml'
          reporter: java-junit
```

- [ ] **Step 2: 커밋**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git add .github/
git commit -m "ci: add GitHub Actions CI workflow"
```

---

### Task 7.2: 전체 빌드 검증 — `complexity: high`

- [ ] **Step 1: 전체 빌드**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL` (모든 6개 모듈)

- [ ] **Step 2: 전체 잔여 참조 최종 검사**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
rg 'io\.bluetape4k\.scheduling' --type kotlin --type xml --type properties || echo "All clean - OK"
```

Expected: `All clean - OK`

- [ ] **Step 3: 각 모듈 README 존재 확인**

```bash
for m in appointment-core appointment-event appointment-solver appointment-notification appointment-api; do
  ls "/Users/debop/work/bluetape4k/clinic-appointment/$m/README.md" 2>/dev/null || echo "MISSING: $m/README.md"
done
```

원래 모듈에 README가 있었다면 복사에서 이미 포함됨. 패키지명 변경 사항이 README에도 반영되었는지 확인:

```bash
rg 'scheduling' /Users/debop/work/bluetape4k/clinic-appointment/*/README.md 2>/dev/null || echo "Clean"
```

잔여 `scheduling` 참조 있으면 수동 수정.

---

### Task 7.3: bluetape4k-experimental에서 scheduling 디렉토리 삭제 — `complexity: low`

**Files:**
- Delete: `bluetape4k-experimental/scheduling/` 전체
- Verify: `bluetape4k-experimental` 빌드에 영향 없음

- [ ] **Step 1: scheduling 디렉토리 삭제**

```bash
rm -rf /Users/debop/work/bluetape4k/bluetape4k-experimental/scheduling/
```

- [ ] **Step 2: settings.gradle.kts에서 scheduling includeModules 제거**

`/Users/debop/work/bluetape4k/bluetape4k-experimental/settings.gradle.kts`에서:

```kotlin
// 이 줄을 삭제:
includeModules("scheduling", false, false)
```

- [ ] **Step 3: bluetape4k-experimental 빌드 확인**

```bash
cd /Users/debop/work/bluetape4k/bluetape4k-experimental
./gradlew tasks --no-daemon 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL` (scheduling 모듈 없이도 정상)

- [ ] **Step 4: 양쪽 커밋**

```bash
# clinic-appointment — 최종 커밋 (필요 시)
cd /Users/debop/work/bluetape4k/clinic-appointment
git add -A && git diff --cached --quiet || git commit -m "chore: finalize clinic-appointment migration"

# bluetape4k-experimental — scheduling 삭제
cd /Users/debop/work/bluetape4k/bluetape4k-experimental
git add -A
git commit -m "chore: remove scheduling/ modules (migrated to clinic-appointment)"
```

---

### Task 7.4: 원격 저장소 push — `complexity: low`

- [ ] **Step 1: clinic-appointment push**

```bash
cd /Users/debop/work/bluetape4k/clinic-appointment
git push -u origin main
```

- [ ] **Step 2: bluetape4k-experimental push (선택)**

```bash
cd /Users/debop/work/bluetape4k/bluetape4k-experimental
git push origin main
```

---

## 태스크 요약

| Phase | Task | 설명 | Complexity |
|-------|------|------|------------|
| 0 | 0.1 | GitHub 저장소 생성 및 로컬 클론 | low |
| 0 | 0.2 | Gradle Wrapper + gradle.properties + .gitignore | low |
| 0 | 0.3 | buildSrc — 최소화된 Libs.kt | medium |
| 0 | 0.4 | settings.gradle.kts (조건부 includeBuild) | medium |
| 0 | 0.5 | Root build.gradle.kts (subprojects 공통 설정) | high |
| 0 | 0.6 | CLAUDE.md + README.md | low |
| 1 | 1.1 | appointment-core 이전 + 패키지 rename + 빌드 검증 | high |
| 2 | 2.1 | appointment-event 이전 + 패키지 rename + 빌드 검증 | medium |
| 3 | 3.1 | appointment-solver 이전 + 패키지 rename + 빌드 검증 | medium |
| 4 | 4.1 | appointment-notification 이전 + 패키지 rename + 빌드 검증 | high |
| 5 | 5.1 | appointment-api 이전 + 패키지 rename + 빌드 검증 | high |
| 6 | 6.1 | appointment-frontend 이전 + 빌드 검증 | medium |
| 7 | 7.1 | GitHub Actions CI workflow | medium |
| 7 | 7.2 | 전체 빌드 검증 + 잔여 참조 최종 검사 | high |
| 7 | 7.3 | bluetape4k-experimental에서 scheduling/ 삭제 | low |
| 7 | 7.4 | 원격 저장소 push | low |

**총 16개 태스크**: high 6개, medium 6개, low 4개
