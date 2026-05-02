// ============================================================
// clinic-appointment 프로젝트용 최소화된 dependencyManagement 블록
//
// 기준:
//  - Spring Boot BOM이 이미 버전을 관리하는 라이브러리는 dependency() 핀 제거
//  - scheduling 모듈(appointment-*) 5개에서 실제 사용하지 않는 BOM 제거
//  - kotlinx_atomicfu는 scheduling 모듈에서 직접 사용하지 않으므로 제거
//
// NOTE: libs.versions.toml 기반 — Libs.kt 참조 제거 (2026-05 마이그레이션)
// ============================================================

val rootLibs = libs

// subprojects 내의 dependencyManagement 블록
dependencyManagement {
    setApplyMavenExclusions(false)

    imports {
        // 필수 BOM — 항상 유지
        mavenBom(rootLibs.bluetape4k.bom.get().toString())
        mavenBom(rootLibs.spring.boot4.dependencies.get().toString())

        // 테스트 인프라 BOM
        mavenBom(rootLibs.testcontainers.bom.get().toString())
        mavenBom(rootLibs.junit.bom.get().toString())

        // Kotlin/Coroutines BOM
        mavenBom(rootLibs.kotlinx.coroutines.bom.get().toString())
        mavenBom(rootLibs.kotlin.bom.get().toString())

        // Timefold Solver BOM (appointment-solver 모듈에서 사용)
        // mavenBom(rootLibs.timefold.solver.bom.get().toString())  // libs.versions.toml에 추가 필요

        // ── 삭제된 BOM ──────────────────────────────────────────────
        // feign_bom          → scheduling 모듈 미사용
        // micrometer_bom     → scheduling 모듈 미사용
        // micrometer_tracing_bom → scheduling 모듈 미사용
        // opentelemetry_bom  → scheduling 모듈 미사용
        // log4j_bom          → Spring Boot BOM이 커버
        // okhttp3_bom        → scheduling 모듈 미사용
        // netty_bom          → scheduling 모듈 미사용
        // jackson_bom        → Spring Boot BOM이 커버
        // jackson3_bom       → scheduling 모듈 미사용
    }

    dependencies {
        // JetBrains annotations (Spring Boot BOM 미포함)
        dependency(rootLibs.jetbrains.annotations.get().toString())

        // 테스트 유틸리티 (Spring Boot BOM 미포함)
        dependency(rootLibs.kluent.get().toString())
        dependency(rootLibs.assertj.core.get().toString())
        dependency(rootLibs.mockk.get().toString())
        dependency(rootLibs.datafaker.get().toString())
        dependency(rootLibs.random.beans.get().toString())

        // Redis — appointment-notification에서 lettuce_core 직접 사용
        // (Spring Boot BOM은 lettuce-core를 관리하지만 version override 필요시 유지)
        dependency(rootLibs.lettuce.core.get().toString())

        // ── 삭제된 dependency() 항목 ────────────────────────────────
        // kotlinx-coroutines-* → BOM으로 import했으므로 dependency 핀 불필요
        // junit-* (개별 artifact) → junit.bom BOM으로 커버
        //
        // commons-* → scheduling 모듈 직접 미사용; Spring Boot BOM/transitives로 해결
        //
        // slf4j-api, jcl-over-slf4j, jul-to-slf4j, log4j-over-slf4j,
        // logback-classic, logback-core → Spring Boot BOM이 커버
        //
        // jakarta-* → Spring Boot BOM이 커버
        //
        // jackson-*, jackson3-* → Spring Boot BOM이 커버 / 미사용
        //
        // snappy-java, lz4-java, zstd-jni   → scheduling 모듈 미사용
        // findbugs, guava                    → scheduling 모듈 미사용
        // kryo5, fory-kotlin                 → scheduling 모듈 미사용
        // caffeine, caffeine-jcache          → scheduling 모듈 미사용
        // objenesis, ow2-asm, reflectasm     → scheduling 모듈 미사용
        // jsonpath, jsonassert               → scheduling 모듈 미사용
        // redisson                           → scheduling 모듈 미사용
    }
}

// ============================================================
// subprojects { dependencies {} } — 최소화 버전
//
// 제거 항목:
//  - kotlinx-atomicfu: scheduling 모듈 직접 미사용
//    (root build.gradle.kts의 atomicfu plugin 설정도 clinic-appointment에 불필요)
//  - awaitility-kotlin: scheduling 모듈 미사용 (필요 시 개별 모듈에서 추가)
//
// 유지 항목:
//  - kotlin-stdlib, kotlin-reflect, kotlin-test, kotlin-test-junit5: 필수
//  - kotlinx-coroutines-core: appointment-core에서 api()로 노출
//  - slf4j-api, bluetape4k-logging, logback-classic: 로깅 인프라 필수
//  - jcl-over-slf4j, jul-to-slf4j, log4j-over-slf4j: SLF4J bridge (test)
//  - bluetape4k-junit5, junit-jupiter-all, junit-platform-engine: 테스트 필수
//  - kluent, mockk, datafaker, random-beans: 테스트 유틸리티
// ============================================================

subprojects {
    // ... (plugins apply, java toolchain, kotlin, tasks 설정은 root 그대로 유지)

    dependencies {
        val api by configurations
        val implementation by configurations
        val testImplementation by configurations
        val compileOnly by configurations
        val testCompileOnly by configurations
        val testRuntimeOnly by configurations

        compileOnly(platform(rootLibs.bluetape4k.bom))
        compileOnly(platform(rootLibs.spring.boot4.dependencies))
        compileOnly(platform(rootLibs.kotlinx.coroutines.bom))

        implementation(rootLibs.kotlin.stdlib)
        implementation(rootLibs.kotlin.reflect)
        testImplementation(rootLibs.kotlin.test)
        testImplementation(rootLibs.kotlin.test.junit5)

        implementation(rootLibs.kotlinx.coroutines.core)
        // kotlinx-atomicfu 제거 — scheduling 모듈 미사용

        implementation(rootLibs.slf4j.api)
        implementation(rootLibs.bluetape4k.logging)
        implementation(rootLibs.logback.classic)
        testImplementation(rootLibs.jcl.over.slf4j)
        testImplementation(rootLibs.jul.to.slf4j)
        testImplementation(rootLibs.log4j.over.slf4j)

        // JUnit 5
        testImplementation(rootLibs.bluetape4k.junit5)
        testImplementation(rootLibs.junit.jupiter.all)
        testRuntimeOnly(rootLibs.junit.platform.engine)

        testImplementation(rootLibs.kluent)
        testImplementation(rootLibs.mockk)
        // awaitility-kotlin 제거 — scheduling 모듈 미사용

        testImplementation(rootLibs.datafaker)
        testImplementation(rootLibs.random.beans)
    }
}
